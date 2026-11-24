//! CrabFish — ChessCrab'ın offline satranç motoru.
//!
//! Tamamen Rust, tek iş parçacığı başına bir `Searcher`. Analizde paralellik
//! pozisyonlar arası yapılır (her iş parçacığına bir motor).

pub mod book;
pub mod eval;
pub mod external;
pub mod level;
pub mod search;
pub mod see;
pub mod tt;

use chesscrab_core::{Game, san_of};
use serde::Serialize;
use shakmaty::zobrist::Zobrist64;
use shakmaty::{Chess, EnPassantMode, Position};

pub use level::{Level, Rng};
pub use search::{Info, Limits, Line, Score, SearchResult, Searcher};

pub const NAME: &str = "CrabFish";
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Bir oyundaki önceki pozisyonların zobrist anahtarları (mevcut hariç).
pub fn game_history_hashes(game: &Game) -> Vec<u64> {
    let mut positions = game.positions();
    positions.pop();
    positions.iter().map(|p| p.zobrist_hash::<Zobrist64>(EnPassantMode::Legal).0).collect()
}

#[derive(Clone, Debug, Serialize)]
pub struct EngineMove {
    pub uci: String,
    pub san: String,
    /// Sırası gelen (motor) açısından
    pub score: Score,
    pub from_book: bool,
}

/// Yüksek seviye motor arayüzü.
pub struct Engine {
    pub searcher: Searcher,
    pub rng: Rng,
}

impl Default for Engine {
    fn default() -> Self {
        Self::new(32)
    }
}

impl Engine {
    pub fn new(hash_mb: usize) -> Self {
        Self { searcher: Searcher::new(hash_mb), rng: Rng::from_time() }
    }

    pub fn analyse(&mut self, pos: &Chess, history: &[u64], limits: &Limits) -> SearchResult {
        self.searcher.search(pos, history, limits, |_| {})
    }

    /// Oyunda sırası gelen taraf için seviyeye göre hamle seç.
    /// `extra_book`: repertuvar hatları gibi ek kitap satırları.
    pub fn play(
        &mut self,
        game: &Game,
        level: Level,
        movetime_ms: Option<u64>,
        extra_book: &[&str],
    ) -> Option<EngineMove> {
        let pos = game.position();
        if pos.legal_moves().is_empty() {
            return None;
        }
        // Açılışta çeşitlilik için kitap (ilk 12 yarı hamle)
        if game.ply() < 12 && game.start_fen() == chesscrab_core::START_FEN {
            let sans = game.sans();
            if let Some(san) = book::pick_book_move(&sans, extra_book, &mut self.rng) {
                if let Ok(m) = chesscrab_core::parse_move(pos, &san) {
                    return Some(EngineMove { uci: chesscrab_core::uci_of(m), san: san_of(pos, m), score: Score::Cp(0), from_book: true });
                }
            }
        }
        let history = game_history_hashes(game);
        let limits = Limits {
            depth: level.depth_limit(),
            nodes: level.node_limit(),
            movetime_ms: movetime_ms.or(if level.is_full() { Some(1000) } else { None }),
            multipv: level.multipv(),
            ..Default::default()
        };
        let r = self.searcher.search(pos, &history, &limits, |_| {});
        let idx = if r.lines.is_empty() {
            None
        } else {
            let scores: Vec<i32> = r.lines.iter().map(|l| l.score.to_cp()).collect();
            Some(level.pick(&scores, &mut self.rng))
        };
        let (m, score) = match idx {
            Some(i) => (r.lines[i].pv[0], r.lines[i].score),
            None => (r.best?, r.score),
        };
        Some(EngineMove { uci: chesscrab_core::uci_of(m), san: san_of(pos, m), score, from_book: false })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn engine_plays_a_game_against_itself() {
        let mut g = Game::new();
        let mut e = Engine::new(8);
        for _ in 0..40 {
            if g.status().is_over() {
                break;
            }
            let m = e.play(&g, Level::preset(3), None, &[]).expect("hamle");
            g.play(&m.uci).expect("yasal");
        }
        assert!(g.ply() > 10);
    }
}
