//! ChessCrab analiz: oyun doğruluğu (lichess formülü), ACPL, chess.com tarzı hamle
//! sınıflandırma (Kitap, Parlak, Harika, En iyi, Mükemmel, İyi, Belirsiz, Hata,
//! Kaçırılan, Büyük hata, Zorunlu), kritik anlar ve Türkçe yorumlar.
//!
//! Pozisyonlar iş parçacıkları arasında paylaştırılır: her iş parçacığı kendi motoruyla
//! ardışık bir dilimi analiz eder (TT'den faydalanmak için). Go brr.

pub mod accuracy;

use std::sync::atomic::{AtomicUsize, Ordering};

use chesscrab_core::opening::{book_depth, identify_opening};
use chesscrab_core::{Game, san_of, to_fen, uci_of};
use chesscrab_engine::external::UciEngine;
use chesscrab_engine::see::see;
use chesscrab_engine::{Limits, Score, SearchResult, Searcher};
use serde::{Deserialize, Serialize};
use shakmaty::zobrist::Zobrist64;
use shakmaty::{Chess, Color, EnPassantMode, Move, Position, Role};

pub use accuracy::{estimate_elo, game_accuracy, move_accuracy, win_percent};

/// Bir pozisyonu analiz edebilen her şey (CrabFish ya da harici UCI motoru).
pub trait PositionAnalyzer {
    fn analyse(&mut self, pos: &Chess, history: &[u64], limits: &Limits) -> SearchResult;
}

impl PositionAnalyzer for Searcher {
    fn analyse(&mut self, pos: &Chess, history: &[u64], limits: &Limits) -> SearchResult {
        self.search(pos, history, limits, |_| {})
    }
}

impl PositionAnalyzer for UciEngine {
    fn analyse(&mut self, pos: &Chess, _history: &[u64], limits: &Limits) -> SearchResult {
        UciEngine::analyse(self, pos, limits).unwrap_or_else(|_| SearchResult {
            best: None,
            best_uci: None,
            score: Score::Cp(0),
            depth: 0,
            seldepth: 0,
            nodes: 0,
            time_ms: 0,
            lines: Vec::new(),
        })
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AnalysisConfig {
    pub depth: Option<u32>,
    pub nodes: Option<u64>,
    pub movetime_ms: Option<u64>,
    /// 0 = otomatik (çekirdek sayısı)
    pub threads: usize,
    pub hash_mb: usize,
}

impl Default for AnalysisConfig {
    fn default() -> Self {
        Self { depth: Some(12), nodes: None, movetime_ms: None, threads: 0, hash_mb: 32 }
    }
}

impl AnalysisConfig {
    pub fn quick() -> Self {
        Self { depth: Some(10), ..Default::default() }
    }
    pub fn deep() -> Self {
        Self { depth: Some(16), hash_mb: 64, ..Default::default() }
    }
    fn limits(&self) -> Limits {
        Limits { depth: self.depth, nodes: self.nodes, movetime_ms: self.movetime_ms, multipv: 2, ..Default::default() }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MoveClass {
    Book,
    Brilliant,
    Great,
    Best,
    Excellent,
    Good,
    Inaccuracy,
    Mistake,
    Miss,
    Blunder,
    Forced,
}

impl MoveClass {
    pub const ALL: [MoveClass; 11] = [
        MoveClass::Brilliant,
        MoveClass::Great,
        MoveClass::Best,
        MoveClass::Excellent,
        MoveClass::Good,
        MoveClass::Book,
        MoveClass::Inaccuracy,
        MoveClass::Mistake,
        MoveClass::Miss,
        MoveClass::Blunder,
        MoveClass::Forced,
    ];

    pub fn label_tr(self) -> &'static str {
        match self {
            MoveClass::Book => "Kitap",
            MoveClass::Brilliant => "Parlak",
            MoveClass::Great => "Harika",
            MoveClass::Best => "En iyi",
            MoveClass::Excellent => "Mükemmel",
            MoveClass::Good => "İyi",
            MoveClass::Inaccuracy => "Belirsiz",
            MoveClass::Mistake => "Hata",
            MoveClass::Miss => "Kaçırılan",
            MoveClass::Blunder => "Büyük hata",
            MoveClass::Forced => "Zorunlu",
        }
    }

    pub fn symbol(self) -> &'static str {
        match self {
            MoveClass::Brilliant => "!!",
            MoveClass::Great => "!",
            MoveClass::Inaccuracy => "?!",
            MoveClass::Mistake | MoveClass::Miss => "?",
            MoveClass::Blunder => "??",
            _ => "",
        }
    }

    /// Renk (UI için, #RRGGBB)
    pub fn color(self) -> &'static str {
        match self {
            MoveClass::Book => "#a88865",
            MoveClass::Brilliant => "#1baca6",
            MoveClass::Great => "#5c8bb0",
            MoveClass::Best => "#98bc49",
            MoveClass::Excellent => "#98bc49",
            MoveClass::Good => "#97af8b",
            MoveClass::Inaccuracy => "#f0c15c",
            MoveClass::Mistake => "#e6912c",
            MoveClass::Miss => "#ff7769",
            MoveClass::Blunder => "#ca3431",
            MoveClass::Forced => "#97af8b",
        }
    }
}

/// Bir pozisyonun motor değerlendirmesi (beyaz açısından).
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct PositionEval {
    pub fen: String,
    /// Beyaz açısından skor
    pub score: Score,
    /// Beyaz açısından santipiyon (mat → ±10000 civarı)
    pub cp: i32,
    pub best_uci: Option<String>,
    pub best_san: Option<String>,
    /// En iyi devam (SAN, ilk 8 yarı hamle)
    pub pv_san: Vec<String>,
    /// İkinci en iyi hamlenin beyaz açısından cp değeri
    pub second_cp: Option<i32>,
    pub depth: u32,
    pub nodes: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct MoveAnalysis {
    pub ply: usize,
    pub move_number: u32,
    pub white: bool,
    pub san: String,
    pub uci: String,
    pub class: MoveClass,
    pub accuracy: f64,
    /// Hamleyi yapan açısından kazanma yüzdesi, önce/sonra
    pub win_before: f64,
    pub win_after: f64,
    /// Hamleyi yapan açısından santipiyon kaybı (≥0, ±1000'e kırpılmış)
    pub cp_loss: i32,
    pub eval_before: Score,
    pub eval_after: Score,
    pub best_san: Option<String>,
    pub best_uci: Option<String>,
    pub best_line: Vec<String>,
    pub comment: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct SideSummary {
    pub accuracy: Option<f64>,
    pub acpl: f64,
    pub estimated_elo: Option<u32>,
    pub counts: Vec<(MoveClass, usize)>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct KeyMoment {
    pub ply: usize,
    pub san: String,
    pub class: MoveClass,
    pub swing: f64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct GameAnalysis {
    pub opening: Option<(String, String)>,
    pub white: SideSummary,
    pub black: SideSummary,
    pub moves: Vec<MoveAnalysis>,
    pub evals: Vec<PositionEval>,
    pub key_moments: Vec<KeyMoment>,
    pub engine: String,
    pub total_nodes: u64,
    pub time_ms: u64,
}

fn white_pov(score: Score, turn: Color) -> Score {
    match turn {
        Color::White => score,
        Color::Black => score.negate(),
    }
}

/// Tek pozisyonu analiz et, beyaz açısından sonuç döndür.
fn eval_position<A: PositionAnalyzer + ?Sized>(
    a: &mut A,
    pos: &Chess,
    history: &[u64],
    limits: &Limits,
) -> PositionEval {
    let fen = to_fen(pos);
    if pos.legal_moves().is_empty() {
        let score = if pos.is_check() {
            // sırası gelen mat oldu
            Score::Mate(0)
        } else {
            Score::Cp(0)
        };
        let cp = match score {
            Score::Mate(_) => {
                if pos.turn() == Color::White { -10_000 } else { 10_000 }
            }
            Score::Cp(c) => c,
        };
        return PositionEval { fen, score, cp, best_uci: None, best_san: None, pv_san: vec![], second_cp: None, depth: 0, nodes: 0 };
    }
    if pos.is_insufficient_material() {
        return PositionEval { fen, score: Score::Cp(0), cp: 0, best_uci: None, best_san: None, pv_san: vec![], second_cp: None, depth: 0, nodes: 0 };
    }
    let r = a.analyse(pos, history, limits);
    let turn = pos.turn();
    let score = white_pov(r.score, turn);
    let cp = score.to_cp();
    let best = r.best.or_else(|| r.lines.first().and_then(|l| l.pv.first().copied()));
    let mut pv_san = Vec::new();
    if let Some(l) = r.lines.first() {
        let mut p = pos.clone();
        for m in l.pv.iter().take(8) {
            if !p.is_legal(*m) {
                break;
            }
            pv_san.push(san_of(&p, *m));
            p.play_unchecked(*m);
        }
    }
    let second_cp = r.lines.get(1).map(|l| white_pov(l.score, turn).to_cp());
    PositionEval {
        fen,
        score,
        cp,
        best_uci: best.map(uci_of),
        best_san: best.map(|m| san_of(pos, m)),
        pv_san,
        second_cp,
        depth: r.depth,
        nodes: r.nodes,
    }
}

/// Oyunu CrabFish ile analiz et (çok iş parçacıklı).
pub fn analyse_game(game: &Game, cfg: &AnalysisConfig, progress: Option<&(dyn Fn(usize, usize) + Sync)>) -> GameAnalysis {
    let threads = if cfg.threads == 0 {
        std::thread::available_parallelism().map(|n| n.get()).unwrap_or(2)
    } else {
        cfg.threads
    };
    let hash_mb = cfg.hash_mb;
    analyse_game_with(game, cfg, threads, &|| Box::new(Searcher::new(hash_mb)) as Box<dyn PositionAnalyzer>, progress, "CrabFish")
}

/// Oyunu harici bir UCI motoruyla analiz et (tek süreç, sıralı).
pub fn analyse_game_uci(game: &Game, cfg: &AnalysisConfig, path: &str) -> Result<GameAnalysis, String> {
    let mut e = UciEngine::spawn(path, &[("Hash", &cfg.hash_mb.to_string())]).map_err(|e| e.to_string())?;
    let name = e.name.clone();
    let positions = game.positions();
    let limits = cfg.limits();
    let start = std::time::Instant::now();
    let evals: Vec<PositionEval> = positions.iter().map(|p| eval_position(&mut e, p, &[], &limits)).collect();
    Ok(build_analysis(game, &positions, evals, name, start.elapsed().as_millis() as u64))
}

pub fn analyse_game_with(
    game: &Game,
    cfg: &AnalysisConfig,
    threads: usize,
    factory: &(dyn Fn() -> Box<dyn PositionAnalyzer> + Sync),
    progress: Option<&(dyn Fn(usize, usize) + Sync)>,
    engine_name: &str,
) -> GameAnalysis {
    let start = std::time::Instant::now();
    let positions = game.positions();
    let hashes: Vec<u64> = positions.iter().map(|p| p.zobrist_hash::<Zobrist64>(EnPassantMode::Legal).0).collect();
    let n = positions.len();
    let limits = cfg.limits();
    let threads = threads.clamp(1, n.max(1));
    let chunk = n.div_ceil(threads);
    let done = AtomicUsize::new(0);
    let mut evals: Vec<Option<PositionEval>> = vec![None; n];

    std::thread::scope(|s| {
        let mut handles = Vec::new();
        for (t, slot) in evals.chunks_mut(chunk).enumerate() {
            let positions = &positions;
            let hashes = &hashes;
            let limits = &limits;
            let done = &done;
            handles.push(s.spawn(move || {
                let mut a = factory();
                let base = t * chunk;
                // Sondan başa analiz: TT'de sonraki pozisyonların bilgisi önceki
                // pozisyonların aramasını hızlandırır.
                for i in (0..slot.len()).rev() {
                    let idx = base + i;
                    slot[i] = Some(eval_position(a.as_mut(), &positions[idx], &hashes[..idx], limits));
                    let d = done.fetch_add(1, Ordering::Relaxed) + 1;
                    if let Some(p) = progress {
                        p(d, n);
                    }
                }
            }));
        }
        for h in handles {
            let _ = h.join();
        }
    });

    let evals: Vec<PositionEval> = evals.into_iter().map(|e| e.expect("tüm pozisyonlar analiz edildi")).collect();
    build_analysis(game, &positions, evals, engine_name.to_string(), start.elapsed().as_millis() as u64)
}

fn is_sacrifice(pos: &Chess, m: Move) -> bool {
    match m {
        Move::Normal { role, .. } if role != Role::Pawn && role != Role::King => {
            // Taşı, alındığında net materyal kaybettiren bir kareye koyuyoruz
            see(pos, m) <= -200
        }
        _ => false,
    }
}

fn mover_cp(white_cp: i32, white_moved: bool) -> i32 {
    if white_moved { white_cp } else { -white_cp }
}

fn fmt_score_tr(s: Score) -> String {
    match s {
        Score::Cp(c) => format!("{:+.2}", c as f64 / 100.0),
        Score::Mate(n) if n > 0 => format!("M{n}"),
        Score::Mate(n) if n < 0 => format!("-M{}", -n),
        Score::Mate(_) => "#".into(),
    }
}

fn build_analysis(game: &Game, positions: &[Chess], evals: Vec<PositionEval>, engine: String, time_ms: u64) -> GameAnalysis {
    let start = &positions[0];
    let white_first = start.turn() == Color::White;
    let sans = game.sans();
    let book = book_depth(&sans);
    let mut moves: Vec<MoveAnalysis> = Vec::with_capacity(game.moves().len());
    let fullmove0 = u32::from(start.fullmoves());

    for (i, &m) in game.moves().iter().enumerate() {
        let pos = &positions[i];
        let white_moved = pos.turn() == Color::White;
        let before = &evals[i];
        let after = &evals[i + 1];
        let cp_b = mover_cp(before.cp, white_moved);
        let cp_a = mover_cp(after.cp, white_moved);
        let win_b = win_percent(cp_b);
        let win_a = win_percent(cp_a);
        let acc = move_accuracy(win_b, win_a);
        let loss_ep = ((win_b - win_a) / 100.0).max(0.0);
        let cp_loss = (cp_b.clamp(-1000, 1000) - cp_a.clamp(-1000, 1000)).max(0);
        let uci = uci_of(m);
        let is_best = before.best_uci.as_deref() == Some(uci.as_str());
        let n_legal = pos.legal_moves().len();

        let prev_class = moves.last().map(|x: &MoveAnalysis| x.class);
        let mut class = if i < book {
            MoveClass::Book
        } else if n_legal == 1 {
            MoveClass::Forced
        } else if is_best || loss_ep <= 0.0 {
            MoveClass::Best
        } else if loss_ep <= 0.02 {
            MoveClass::Excellent
        } else if loss_ep <= 0.05 {
            MoveClass::Good
        } else if loss_ep <= 0.10 {
            MoveClass::Inaccuracy
        } else if loss_ep <= 0.20 {
            MoveClass::Mistake
        } else {
            MoveClass::Blunder
        };

        // Kaçırılan fırsat: rakip hata yapmıştı, biz cezalandırmadık
        if matches!(class, MoveClass::Inaccuracy | MoveClass::Mistake | MoveClass::Blunder)
            && matches!(prev_class, Some(MoveClass::Mistake) | Some(MoveClass::Blunder))
            && win_b >= 65.0
            && win_a >= 30.0
        {
            class = MoveClass::Miss;
        }

        let mover_before_s = if white_moved { before.score } else { before.score.negate() };
        let mover_after_s = if white_moved { after.score } else { after.score.negate() };
        let forced_mate_sac = matches!(mover_after_s, Score::Mate(n) if n > 0) && matches!(mover_before_s, Score::Mate(n) if n > 0);
        if matches!(class, MoveClass::Best | MoveClass::Excellent) {
            // Parlak: iyi bir taş fedası; pozisyon zaten bitmiş değil ya da feda zorunlu matın parçası
            if is_sacrifice(pos, m) && win_a >= 50.0 && (win_b < 97.0 || forced_mate_sac) && loss_ep <= 0.02 {
                class = MoveClass::Brilliant;
            } else if is_best {
                // Harika: tek iyi hamle (ikinci en iyi belirgin şekilde kötü)
                if let Some(sec) = before.second_cp {
                    let win_sec = win_percent(mover_cp(sec, white_moved));
                    let recapture = i > 0 && m.is_capture() && game.moves()[i - 1].to() == m.to();
                    if win_b - win_sec >= 15.0 && win_b < 95.0 && !recapture && win_a >= 40.0 {
                        class = MoveClass::Great;
                    }
                }
            }
        }

        // Yorum
        let best_san = before.best_san.clone();
        let mut comment = String::new();
        let mover_after = if white_moved { after.score } else { after.score.negate() };
        let mover_before = if white_moved { before.score } else { before.score.negate() };
        match class {
            MoveClass::Book => comment.push_str("Bilinen açılış hamlesi."),
            MoveClass::Forced => comment.push_str("Tek yasal hamle."),
            MoveClass::Brilliant => comment.push_str("Parlak bir feda! Materyal veriyorsun ama pozisyon bunu karşılıyor."),
            MoveClass::Great => comment.push_str("Harika — buradaki tek iyi hamle."),
            MoveClass::Best => comment.push_str("Motorun da en iyi bulduğu hamle."),
            MoveClass::Excellent => comment.push_str("Neredeyse en iyisi kadar güçlü."),
            MoveClass::Good => comment.push_str("Sağlam bir hamle."),
            _ => {}
        }
        if matches!(class, MoveClass::Inaccuracy | MoveClass::Mistake | MoveClass::Miss | MoveClass::Blunder) {
            comment.push_str(match class {
                MoveClass::Inaccuracy => "Belirsiz hamle.",
                MoveClass::Mistake => "Hata.",
                MoveClass::Miss => "Fırsat kaçtı: rakibin hatasını cezalandırmadın.",
                _ => "Büyük hata!",
            });
            if let Score::Mate(n) = mover_after {
                if n < 0 {
                    comment.push_str(&format!(" Bu hamleden sonra {} hamlede mat oluyorsun.", -n));
                }
            }
            if let Score::Mate(n) = mover_before {
                if n > 0 && !matches!(mover_after, Score::Mate(k) if k > 0) {
                    comment.push_str(&format!(" {n} hamlede mat vardı."));
                }
            }
            if let Some(b) = &best_san {
                comment.push_str(&format!(" En iyisi {b} ({}).", fmt_score_tr(before.score)));
            }
        }

        let move_number = fullmove0 + ((i as u32 + if white_first { 0 } else { 1 }) / 2);
        moves.push(MoveAnalysis {
            ply: i + 1,
            move_number,
            white: white_moved,
            san: sans[i].clone(),
            uci,
            class,
            accuracy: acc,
            win_before: win_b,
            win_after: win_a,
            cp_loss,
            eval_before: before.score,
            eval_after: after.score,
            best_san,
            best_uci: before.best_uci.clone(),
            best_line: before.pv_san.clone(),
            comment,
        });
    }

    let cps: Vec<i32> = evals.iter().map(|e| e.cp).collect();
    let (wacc, bacc) = game_accuracy(&cps, white_first);
    let summary = |white: bool, acc: Option<f64>| -> SideSummary {
        let mine: Vec<&MoveAnalysis> = moves.iter().filter(|m| m.white == white).collect();
        let acpl = if mine.is_empty() { 0.0 } else { mine.iter().map(|m| m.cp_loss as f64).sum::<f64>() / mine.len() as f64 };
        let counts = MoveClass::ALL
            .iter()
            .map(|c| (*c, mine.iter().filter(|m| m.class == *c).count()))
            .filter(|(_, n)| *n > 0)
            .collect();
        SideSummary { accuracy: acc, acpl, estimated_elo: (mine.len() >= 10).then(|| estimate_elo(acpl)), counts }
    };
    let white = summary(true, wacc);
    let black = summary(false, bacc);

    let mut key_moments: Vec<KeyMoment> = moves
        .iter()
        .filter(|m| !matches!(m.class, MoveClass::Book | MoveClass::Forced))
        .filter(|m| matches!(m.class, MoveClass::Brilliant | MoveClass::Great | MoveClass::Mistake | MoveClass::Miss | MoveClass::Blunder))
        .map(|m| KeyMoment { ply: m.ply, san: m.san.clone(), class: m.class, swing: m.win_before - m.win_after })
        .collect();
    key_moments.sort_by(|a, b| b.swing.abs().partial_cmp(&a.swing.abs()).unwrap_or(std::cmp::Ordering::Equal));
    key_moments.truncate(6);
    key_moments.sort_by_key(|k| k.ply);

    let opening = if start == &Chess::default() {
        identify_opening(&sans).map(|(o, _)| (o.eco.to_string(), o.name.to_string()))
    } else {
        None
    };
    let total_nodes = evals.iter().map(|e| e.nodes).sum();
    GameAnalysis { opening, white, black, moves, evals, key_moments, engine, total_nodes, time_ms }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn analyses_short_game() {
        let mut g = Game::new();
        // Beyaz 4. hamlede veziri bırakıyor
        for m in ["e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "Qe2", "Nd4", "Nxe5", "Nxe2"] {
            g.play(m).unwrap();
        }
        let cfg = AnalysisConfig { depth: Some(8), threads: 2, ..Default::default() };
        let a = analyse_game(&g, &cfg, None);
        assert_eq!(a.moves.len(), 10);
        assert_eq!(a.moves[0].class, MoveClass::Book);
        let nxe5 = &a.moves[8];
        assert_eq!(nxe5.san, "Nxe5");
        assert!(matches!(nxe5.class, MoveClass::Blunder | MoveClass::Mistake | MoveClass::Miss), "{:?}", nxe5.class);
        assert!(a.black.accuracy.unwrap() > a.white.accuracy.unwrap());
        assert_eq!(a.opening.as_ref().unwrap().1, "İki At Savunması");
    }

    #[test]
    fn mate_detected_in_final_position() {
        let mut g = Game::new();
        for m in ["f3", "e5", "g4", "Qh4#"] {
            g.play(m).unwrap();
        }
        let a = analyse_game(&g, &AnalysisConfig { depth: Some(6), threads: 1, ..Default::default() }, None);
        assert_eq!(a.evals.last().unwrap().cp, -10_000);
        assert_eq!(a.moves[2].class, MoveClass::Blunder);
        assert!(a.moves[2].comment.contains("mat"), "{}", a.moves[2].comment);
    }
}
