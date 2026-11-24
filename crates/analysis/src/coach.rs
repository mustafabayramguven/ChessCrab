//! Canlı Koç: oyun oynanırken her hamleyi anında değerlendirir.
//!
//! Pozisyon değerlendirmeleri önbelleğe alınır: bir hamlenin "sonrası" bir sonraki
//! hamlenin "öncesi" olduğu için her yeni hamle sadece bir arama maliyetindedir.
//! Oyun bitince önbellekten anında tam inceleme üretilir.

use std::collections::HashMap;

use chesscrab_core::Game;
use chesscrab_core::opening::book_depth;
use chesscrab_engine::{Limits, Searcher};
use serde::Serialize;
use shakmaty::zobrist::Zobrist64;
use shakmaty::{Chess, Color, EnPassantMode, Move, Position};

use crate::{GameAnalysis, MoveAnalysis, MoveClass, PositionEval, analyse_move, build_analysis, eval_position};

pub struct LiveCoach {
    searcher: Searcher,
    pub depth: u32,
    cache: HashMap<String, PositionEval>,
}

#[derive(Debug, Clone, Serialize)]
pub struct Hint {
    pub best_uci: Option<String>,
    pub best_san: Option<String>,
    pub line_san: Vec<String>,
    /// Beyaz açısından
    pub score: chesscrab_engine::Score,
    pub cp: i32,
}

#[derive(Debug, Clone, Serialize)]
pub struct Comparison {
    pub best_uci: Option<String>,
    pub best_san: Option<String>,
    /// Hamleyi yapan açısından kayıp (santipiyon, ≥0)
    pub loss_cp: i32,
    pub is_best: bool,
}

fn hashes(positions: &[Chess]) -> Vec<u64> {
    positions.iter().map(|p| p.zobrist_hash::<Zobrist64>(EnPassantMode::Legal).0).collect()
}

impl LiveCoach {
    pub fn new(depth: u32, hash_mb: usize) -> Self {
        LiveCoach { searcher: Searcher::new(hash_mb), depth: depth.clamp(4, 30), cache: HashMap::new() }
    }

    pub fn clear(&mut self) {
        self.cache.clear();
        self.searcher.new_game();
    }

    fn limits(&self) -> Limits {
        Limits { depth: Some(self.depth), multipv: 2, ..Default::default() }
    }

    fn eval_cached(&mut self, pos: &Chess, history: &[u64]) -> PositionEval {
        let key = chesscrab_core::to_fen(pos);
        if let Some(e) = self.cache.get(&key) {
            return e.clone();
        }
        let limits = self.limits();
        let e = eval_position(&mut self.searcher, pos, history, &limits);
        if self.cache.len() > 5000 {
            self.cache.clear();
        }
        self.cache.insert(key, e.clone());
        e
    }

    /// Mevcut pozisyonun değerlendirmesi (beyaz açısından).
    pub fn evaluate(&mut self, game: &Game) -> PositionEval {
        let positions = game.positions();
        let h = hashes(&positions);
        let n = positions.len() - 1;
        self.eval_cached(&positions[n], &h[..n])
    }

    /// Oyundaki son hamleyi değerlendir. `prev_class`: önceki hamlenin sınıfı (Kaçırılan tespiti için).
    pub fn review_last(&mut self, game: &Game, prev_class: Option<MoveClass>) -> Option<MoveAnalysis> {
        let n = game.ply();
        if n == 0 {
            return None;
        }
        let positions = game.positions();
        let h = hashes(&positions);
        let before = self.eval_cached(&positions[n - 1], &h[..n - 1]);
        let after = self.eval_cached(&positions[n], &h[..n]);
        let book = if game.start_fen() == chesscrab_core::START_FEN { book_depth(&game.sans()) } else { 0 };
        Some(analyse_move(game, &positions, n - 1, &before, &after, book, prev_class))
    }

    /// Sıradaki hamle için ipucu.
    pub fn hint(&mut self, game: &Game) -> Hint {
        let e = self.evaluate(game);
        Hint { best_uci: e.best_uci, best_san: e.best_san, line_san: e.pv_san, score: e.score, cp: e.cp }
    }

    /// Bir pozisyonda bir hamle en iyiden ne kadar kötü? ("Daha iyisini dene" için)
    pub fn compare(&mut self, pos: &Chess, m: Move) -> Comparison {
        let best = self.eval_cached(pos, &[]);
        let mover_white = pos.turn() == Color::White;
        let mut child = pos.clone();
        child.play_unchecked(m);
        let after = self.eval_cached(&child, &[]);
        let mc = |cp: i32| if mover_white { cp } else { -cp };
        let uci = chesscrab_core::uci_of(m);
        let is_best = best.best_uci.as_deref() == Some(uci.as_str());
        let loss = if is_best { 0 } else { (mc(best.cp).clamp(-1000, 1000) - mc(after.cp).clamp(-1000, 1000)).max(0) };
        Comparison { best_uci: best.best_uci, best_san: best.best_san, loss_cp: loss, is_best }
    }

    /// Önbellekteki değerlendirmelerle (eksikleri hesaplayarak) anında tam inceleme.
    pub fn full_review(&mut self, game: &Game) -> GameAnalysis {
        let t = web_time::Instant::now();
        let positions = game.positions();
        let h = hashes(&positions);
        let evals: Vec<PositionEval> = (0..positions.len()).map(|i| self.eval_cached(&positions[i], &h[..i])).collect();
        build_analysis(game, &positions, evals, "CrabFish · Canlı Koç".into(), t.elapsed().as_millis() as u64)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn live_review_flow() {
        let mut c = LiveCoach::new(8, 8);
        let mut g = Game::new();
        g.play("e4").unwrap();
        let r = c.review_last(&g, None).unwrap();
        assert_eq!(r.class, MoveClass::Book);
        for m in ["e5", "Nf3", "Nc6", "Bc4", "Nf6", "Qe2", "Nd4"] {
            g.play(m).unwrap();
        }
        g.play("Nxe5").unwrap(); // veziri bırakan hata
        let r = c.review_last(&g, None).unwrap();
        assert!(matches!(r.class, MoveClass::Blunder | MoveClass::Mistake), "{:?}", r.class);
        assert_eq!(r.refutation_san.as_deref(), Some("Nxe2"));
        assert!(r.comment.contains("Nxe2"), "{}", r.comment);
        let hint = c.hint(&g);
        assert_eq!(hint.best_san.as_deref(), Some("Nxe2"));
        // Önbellekten tam inceleme
        let full = c.full_review(&g);
        assert_eq!(full.moves.len(), 9);
        let pos = g.positions()[8].clone();
        let bad = chesscrab_core::parse_move(&pos, "Nxe5").unwrap();
        assert!(c.compare(&pos, bad).loss_cp > 150);
    }
}
