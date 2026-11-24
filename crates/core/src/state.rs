//! Arayüzlere (web, Android) gönderilen oyun durumu.

use serde::Serialize;
use shakmaty::Position;

use crate::game::{Game, GameStatus, Side};
use crate::{identify_opening, pgn};

#[derive(Debug, Clone, Serialize)]
pub struct OpeningInfo {
    pub eco: &'static str,
    pub name: &'static str,
}

#[derive(Debug, Clone, Serialize)]
pub struct GameState {
    pub fen: String,
    pub start_fen: String,
    pub turn: Side,
    pub legal: Vec<String>,
    pub sans: Vec<String>,
    pub ucis: Vec<String>,
    pub check: bool,
    pub status: GameStatus,
    pub result: &'static str,
    pub termination: Option<&'static str>,
    pub opening: Option<OpeningInfo>,
    pub pgn: String,
}

impl GameState {
    pub fn of(g: &Game) -> Self {
        let status = g.status();
        let opening = if g.start_fen() == crate::START_FEN {
            identify_opening(&g.sans()).map(|(o, _)| OpeningInfo { eco: o.eco, name: o.name })
        } else {
            None
        };
        GameState {
            fen: g.fen(),
            start_fen: g.start_fen().to_string(),
            turn: g.turn(),
            legal: g.legal_uci(),
            sans: g.sans(),
            ucis: g.records().iter().map(|r| r.uci.clone()).collect(),
            check: g.position().is_check(),
            status,
            result: status.result_str(),
            termination: match status {
                GameStatus::Finished { termination, .. } => Some(termination.label_tr()),
                GameStatus::Ongoing => None,
            },
            opening,
            pgn: pgn::to_pgn(g),
        }
    }
}
