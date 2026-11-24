//! CrabFish UCI arayüzü: `crabfish` ile çalıştır, herhangi bir UCI GUI'sine (Cute Chess,
//! En Croissant, Arena...) ekleyebilirsin.

use std::io::{BufRead, Write};
use std::sync::atomic::Ordering;
use std::sync::mpsc;
use std::thread;

use chesscrab_core::{parse_fen, parse_move, uci_of};
use chesscrab_engine::{Limits, NAME, Searcher, VERSION};
use shakmaty::zobrist::Zobrist64;
use shakmaty::{Chess, EnPassantMode, Position};

enum Job {
    Go(Chess, Vec<u64>, Limits),
    NewGame,
    Hash(usize),
    Quit,
}

fn main() {
    let (tx, rx) = mpsc::channel::<Job>();
    let mut searcher = Searcher::new(64);
    let stop = searcher.stop_handle();
    let worker = thread::spawn(move || {
        let stdout = std::io::stdout();
        for job in rx {
            match job {
                Job::Go(pos, hist, limits) => {
                    let r = searcher.search(&pos, &hist, &limits, |i| {
                        let pv: Vec<String> = i.pv.iter().map(|m| uci_of(*m)).collect();
                        let mut out = stdout.lock();
                        let _ = writeln!(
                            out,
                            "info depth {} seldepth {} multipv {} score {} nodes {} nps {} hashfull {} time {} pv {}",
                            i.depth, i.seldepth, i.multipv, i.score.uci(), i.nodes, i.nps, i.hashfull, i.time_ms, pv.join(" ")
                        );
                        let _ = out.flush();
                    });
                    let mut out = stdout.lock();
                    let best = r.best_uci.unwrap_or_else(|| "0000".into());
                    let ponder = r.lines.first().and_then(|l| l.pv_uci.get(1).cloned());
                    match ponder {
                        Some(p) => { let _ = writeln!(out, "bestmove {best} ponder {p}"); }
                        None => { let _ = writeln!(out, "bestmove {best}"); }
                    }
                    let _ = out.flush();
                }
                Job::NewGame => searcher.new_game(),
                Job::Hash(mb) => {
                    let h = searcher.stop_handle();
                    searcher = Searcher::new(mb);
                    searcher.set_stop_handle(h);
                }
                Job::Quit => break,
            }
        }
    });

    let mut pos = Chess::default();
    let mut history: Vec<u64> = Vec::new();
    let mut multipv = 1usize;
    let stdin = std::io::stdin();
    for line in stdin.lock().lines() {
        let Ok(line) = line else { break };
        let toks: Vec<&str> = line.split_whitespace().collect();
        match toks.first().copied() {
            Some("uci") => {
                println!("id name {NAME} {VERSION}");
                println!("id author ChessCrab");
                println!("option name Hash type spin default 64 min 1 max 4096");
                println!("option name MultiPV type spin default 1 min 1 max 64");
                println!("uciok");
            }
            Some("isready") => println!("readyok"),
            Some("ucinewgame") => {
                let _ = tx.send(Job::NewGame);
            }
            Some("setoption") => {
                let name_i = toks.iter().position(|t| *t == "name");
                let value_i = toks.iter().position(|t| *t == "value");
                if let (Some(n), Some(v)) = (name_i, value_i) {
                    let name = toks[n + 1..v].join(" ").to_lowercase();
                    let value = toks.get(v + 1).copied().unwrap_or("");
                    match name.as_str() {
                        "hash" => { if let Ok(mb) = value.parse() { let _ = tx.send(Job::Hash(mb)); } }
                        "multipv" => multipv = value.parse().unwrap_or(1),
                        _ => {}
                    }
                }
            }
            Some("position") => {
                let moves_i = toks.iter().position(|t| *t == "moves");
                let base = match toks.get(1).copied() {
                    Some("startpos") => Some(Chess::default()),
                    Some("fen") => {
                        let end = moves_i.unwrap_or(toks.len());
                        parse_fen(&toks[2..end].join(" ")).ok()
                    }
                    _ => None,
                };
                if let Some(mut p) = base {
                    history.clear();
                    if let Some(mi) = moves_i {
                        for t in &toks[mi + 1..] {
                            let Ok(m) = parse_move(&p, t) else { break };
                            history.push(p.zobrist_hash::<Zobrist64>(EnPassantMode::Legal).0);
                            p.play_unchecked(m);
                        }
                    }
                    pos = p;
                }
            }
            Some("go") => {
                let mut l = Limits { multipv, ..Default::default() };
                let mut i = 1;
                while i < toks.len() {
                    let v = toks.get(i + 1).and_then(|v| v.parse::<u64>().ok());
                    match toks[i] {
                        "depth" => l.depth = v.map(|x| x as u32),
                        "nodes" => l.nodes = v,
                        "movetime" => l.movetime_ms = v,
                        "wtime" => l.wtime = v,
                        "btime" => l.btime = v,
                        "winc" => l.winc = v,
                        "binc" => l.binc = v,
                        "movestogo" => l.movestogo = v.map(|x| x as u32),
                        "infinite" => { l.infinite = true; i += 1; continue; }
                        "searchmoves" => {
                            for t in &toks[i + 1..] {
                                if let Ok(m) = parse_move(&pos, t) { l.search_moves.push(m); }
                            }
                            break;
                        }
                        _ => { i += 1; continue; }
                    }
                    i += 2;
                }
                stop.store(false, Ordering::Relaxed);
                let _ = tx.send(Job::Go(pos.clone(), history.clone(), l));
            }
            Some("stop") => stop.store(true, Ordering::Relaxed),
            Some("d") => println!("{}", chesscrab_core::to_fen(&pos)),
            Some("eval") => println!("eval {} cp (sırası gelen açısından)", chesscrab_engine::eval::evaluate(&pos)),
            Some("perft") => {
                let d: u32 = toks.get(1).and_then(|s| s.parse().ok()).unwrap_or(4);
                let t = std::time::Instant::now();
                let n = shakmaty::perft(&pos, d);
                println!("perft {d}: {n} ({} ms)", t.elapsed().as_millis());
            }
            Some("quit") => {
                stop.store(true, Ordering::Relaxed);
                let _ = tx.send(Job::Quit);
                break;
            }
            _ => {}
        }
    }
    let _ = worker.join();
}
