//! Harici UCI motoru (ör. Stockfish) sarmalayıcı. Masaüstü/sunucuda kurulu bir
//! motor varsa analiz için kullanılabilir; yoksa CrabFish devrede.

use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};

use shakmaty::{Chess, uci::UciMove};

use crate::search::{Limits, Line, Score, SearchResult};

pub struct UciEngine {
    child: Child,
    stdin: ChildStdin,
    stdout: BufReader<ChildStdout>,
    pub name: String,
}

#[derive(Debug)]
pub enum UciError {
    Io(std::io::Error),
    Protocol(String),
}


impl std::fmt::Display for UciError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            UciError::Io(e) => write!(f, "UCI G/Ç hatası: {e}"),
            UciError::Protocol(s) => write!(f, "UCI protokol hatası: {s}"),
        }
    }
}
impl std::error::Error for UciError {}
impl From<std::io::Error> for UciError {
    fn from(e: std::io::Error) -> Self {
        UciError::Io(e)
    }
}

impl UciEngine {
    pub fn spawn(path: &str, options: &[(&str, &str)]) -> Result<Self, UciError> {
        let mut child = Command::new(path).stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null()).spawn()?;
        let stdin = child.stdin.take().ok_or_else(|| UciError::Protocol("stdin yok".into()))?;
        let stdout = BufReader::new(child.stdout.take().ok_or_else(|| UciError::Protocol("stdout yok".into()))?);
        let mut e = UciEngine { child, stdin, stdout, name: path.to_string() };
        e.send("uci")?;
        loop {
            let l = e.read_line()?;
            if let Some(n) = l.strip_prefix("id name ") {
                e.name = n.to_string();
            }
            if l == "uciok" {
                break;
            }
        }
        for (k, v) in options {
            e.send(&format!("setoption name {k} value {v}"))?;
        }
        e.sync()?;
        Ok(e)
    }

    fn send(&mut self, cmd: &str) -> Result<(), UciError> {
        writeln!(self.stdin, "{cmd}")?;
        self.stdin.flush()?;
        Ok(())
    }

    fn read_line(&mut self) -> Result<String, UciError> {
        let mut s = String::new();
        if self.stdout.read_line(&mut s)? == 0 {
            return Err(UciError::Protocol("motor kapandı".into()));
        }
        Ok(s.trim().to_string())
    }

    fn sync(&mut self) -> Result<(), UciError> {
        self.send("isready")?;
        while self.read_line()? != "readyok" {}
        Ok(())
    }

    pub fn new_game(&mut self) -> Result<(), UciError> {
        self.send("ucinewgame")?;
        self.sync()
    }

    /// `fen` pozisyonunu analiz et.
    pub fn analyse(&mut self, pos: &Chess, limits: &Limits) -> Result<SearchResult, UciError> {
        let multipv = limits.multipv.max(1);
        self.send(&format!("setoption name MultiPV value {multipv}"))?;
        self.send(&format!("position fen {}", chesscrab_core::to_fen(pos)))?;
        let mut go = String::from("go");
        if let Some(d) = limits.depth {
            go += &format!(" depth {d}");
        }
        if let Some(n) = limits.nodes {
            go += &format!(" nodes {n}");
        }
        if let Some(t) = limits.movetime_ms {
            go += &format!(" movetime {t}");
        }
        if go == "go" {
            go += " depth 18";
        }
        self.send(&go)?;
        let mut lines: Vec<Option<Line>> = vec![None; multipv];
        let mut depth = 0;
        let mut seldepth = 0;
        let mut nodes = 0;
        let mut time_ms = 0;
        let best;
        loop {
            let l = self.read_line()?;
            if let Some(rest) = l.strip_prefix("bestmove ") {
                best = rest.split_whitespace().next().map(|s| s.to_string());
                break;
            }
            if !l.starts_with("info ") || !l.contains(" pv ") {
                continue;
            }
            let toks: Vec<&str> = l.split_whitespace().collect();
            let mut i = 1;
            let mut mpv = 1usize;
            let mut score = None;
            let mut pv: Vec<String> = Vec::new();
            while i < toks.len() {
                match toks[i] {
                    "depth" => { depth = toks.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(depth); i += 2; }
                    "seldepth" => { seldepth = toks.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(seldepth); i += 2; }
                    "nodes" => { nodes = toks.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(nodes); i += 2; }
                    "time" => { time_ms = toks.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(time_ms); i += 2; }
                    "multipv" => { mpv = toks.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(1); i += 2; }
                    "score" => {
                        let kind = toks.get(i + 1).copied().unwrap_or("");
                        let v: i32 = toks.get(i + 2).and_then(|v| v.parse().ok()).unwrap_or(0);
                        score = Some(if kind == "mate" { Score::Mate(v) } else { Score::Cp(v) });
                        i += 3;
                        if matches!(toks.get(i), Some(&"lowerbound") | Some(&"upperbound")) {
                            i += 1;
                        }
                    }
                    "pv" => { pv = toks[i + 1..].iter().map(|s| s.to_string()).collect(); break; }
                    _ => i += 1,
                }
            }
            if let (Some(score), true) = (score, mpv >= 1 && mpv <= multipv) {
                let mut p = pos.clone();
                let mut moves = Vec::new();
                for u in &pv {
                    let Ok(um) = u.parse::<UciMove>() else { break };
                    let Ok(m) = um.to_move(&p) else { break };
                    shakmaty::Position::play_unchecked(&mut p, m);
                    moves.push(m);
                }
                lines[mpv - 1] = Some(Line { score, pv: moves, pv_uci: pv });
            }
        }
        let lines: Vec<Line> = lines.into_iter().flatten().collect();
        let best_move = best
            .as_deref()
            .and_then(|b| b.parse::<UciMove>().ok())
            .and_then(|um| um.to_move(pos).ok());
        Ok(SearchResult {
            best: best_move,
            best_uci: best_move.map(chesscrab_core::uci_of),
            score: lines.first().map(|l| l.score).unwrap_or(Score::Cp(0)),
            depth,
            seldepth,
            nodes,
            time_ms,
            lines,
        })
    }
}

impl Drop for UciEngine {
    fn drop(&mut self) {
        let _ = self.send("quit");
        let _ = self.child.wait();
    }
}
