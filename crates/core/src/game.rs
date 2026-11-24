use serde::{Deserialize, Serialize};
use shakmaty::{Chess, Color, EnPassantMode, Move, Outcome, KnownOutcome, Position, fen::Fen};

use crate::{START_FEN, parse_fen, parse_move, san_of, to_fen, uci_of};

#[derive(Debug, thiserror::Error, Clone, PartialEq, Eq)]
pub enum GameError {
    #[error("geçersiz FEN: {0}")]
    InvalidFen(String),
    #[error("geçersiz hamle: {0}")]
    IllegalMove(String),
    #[error("oyun bitti")]
    GameOver,
    #[error("PGN okunamadı: {0}")]
    Pgn(String),
}

/// Oyunun nasıl bittiği.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Termination {
    Checkmate,
    Stalemate,
    InsufficientMaterial,
    ThreefoldRepetition,
    FiftyMoveRule,
    Resignation,
    Timeout,
    Agreement,
    Abandoned,
}

impl Termination {
    pub fn label_tr(self) -> &'static str {
        match self {
            Termination::Checkmate => "Şah mat",
            Termination::Stalemate => "Pat",
            Termination::InsufficientMaterial => "Yetersiz materyal",
            Termination::ThreefoldRepetition => "Üç kez tekrar",
            Termination::FiftyMoveRule => "50 hamle kuralı",
            Termination::Resignation => "Terk",
            Termination::Timeout => "Süre bitti",
            Termination::Agreement => "Anlaşmalı beraberlik",
            Termination::Abandoned => "Yarıda bırakıldı",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum GameStatus {
    Ongoing,
    /// winner: None = beraberlik
    Finished { winner: Option<Side>, termination: Termination },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Hash)]
#[serde(rename_all = "snake_case")]
pub enum Side {
    White,
    Black,
}

impl From<Color> for Side {
    fn from(c: Color) -> Self {
        match c {
            Color::White => Side::White,
            Color::Black => Side::Black,
        }
    }
}
impl From<Side> for Color {
    fn from(s: Side) -> Self {
        match s {
            Side::White => Color::White,
            Side::Black => Color::Black,
        }
    }
}

impl GameStatus {
    pub fn result_str(&self) -> &'static str {
        match self {
            GameStatus::Ongoing => "*",
            GameStatus::Finished { winner: Some(Side::White), .. } => "1-0",
            GameStatus::Finished { winner: Some(Side::Black), .. } => "0-1",
            GameStatus::Finished { winner: None, .. } => "1/2-1/2",
        }
    }
    pub fn is_over(&self) -> bool {
        !matches!(self, GameStatus::Ongoing)
    }
}

/// Oynanmış bir hamlenin kaydı.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MoveRecord {
    pub uci: String,
    pub san: String,
    /// Hamleden sonraki FEN.
    pub fen: String,
    /// Hamleden sonra hamleyi yapanın kalan süresi (ms), saatli oyunda.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub clock_ms: Option<u64>,
}

/// Bir satranç oyunu: başlangıç pozisyonu + hamleler + sonuç.
#[derive(Debug, Clone)]
pub struct Game {
    start_fen: String,
    start: Chess,
    pos: Chess,
    moves: Vec<Move>,
    records: Vec<MoveRecord>,
    /// Tekrar tespiti için pozisyon anahtarları (her yarı hamle sonrası).
    keys: Vec<String>,
    status: GameStatus,
    pub tags: Vec<(String, String)>,
}

impl Default for Game {
    fn default() -> Self {
        Self::new()
    }
}

fn rep_key(pos: &Chess) -> String {
    // FEN'in ilk 4 alanı: tahta, sıra, rok, geçerken alma
    let fen = Fen::from_position(pos, EnPassantMode::Legal).to_string();
    fen.split(' ').take(4).collect::<Vec<_>>().join(" ")
}

impl Game {
    pub fn new() -> Self {
        Self::from_fen(START_FEN).expect("başlangıç FEN'i geçerli")
    }

    pub fn from_fen(fen: &str) -> Result<Self, GameError> {
        let start = parse_fen(fen)?;
        let mut g = Game {
            start_fen: to_fen(&start),
            keys: vec![rep_key(&start)],
            pos: start.clone(),
            start,
            moves: Vec::new(),
            records: Vec::new(),
            status: GameStatus::Ongoing,
            tags: Vec::new(),
        };
        g.status = g.compute_status();
        Ok(g)
    }

    pub fn start_fen(&self) -> &str {
        &self.start_fen
    }
    pub fn start_position(&self) -> &Chess {
        &self.start
    }
    pub fn position(&self) -> &Chess {
        &self.pos
    }
    pub fn fen(&self) -> String {
        to_fen(&self.pos)
    }
    pub fn moves(&self) -> &[Move] {
        &self.moves
    }
    pub fn records(&self) -> &[MoveRecord] {
        &self.records
    }
    pub fn status(&self) -> GameStatus {
        self.status
    }
    pub fn turn(&self) -> Side {
        self.pos.turn().into()
    }
    pub fn ply(&self) -> usize {
        self.moves.len()
    }

    pub fn tag(&self, name: &str) -> Option<&str> {
        self.tags.iter().find(|(k, _)| k == name).map(|(_, v)| v.as_str())
    }
    pub fn set_tag(&mut self, name: &str, value: impl Into<String>) {
        let value = value.into();
        if let Some(t) = self.tags.iter_mut().find(|(k, _)| k == name) {
            t.1 = value;
        } else {
            self.tags.push((name.to_string(), value));
        }
    }

    /// Her yarı hamleden önceki pozisyonlar (0 = başlangıç) ve son pozisyon.
    pub fn positions(&self) -> Vec<Chess> {
        let mut out = Vec::with_capacity(self.moves.len() + 1);
        let mut p = self.start.clone();
        out.push(p.clone());
        for m in &self.moves {
            p.play_unchecked(*m);
            out.push(p.clone());
        }
        out
    }

    /// Mevcut pozisyondaki yasal hamleler (UCI).
    pub fn legal_uci(&self) -> Vec<String> {
        if self.status.is_over() {
            return Vec::new();
        }
        self.pos.legal_moves().into_iter().map(uci_of).collect()
    }

    /// SAN veya UCI hamle oyna.
    pub fn play(&mut self, text: &str) -> Result<&MoveRecord, GameError> {
        if self.status.is_over() {
            return Err(GameError::GameOver);
        }
        let m = parse_move(&self.pos, text)?;
        self.play_move(m, None)
    }

    pub fn play_move(&mut self, m: Move, clock_ms: Option<u64>) -> Result<&MoveRecord, GameError> {
        if self.status.is_over() {
            return Err(GameError::GameOver);
        }
        if !self.pos.is_legal(m) {
            return Err(GameError::IllegalMove(m.to_string()));
        }
        let san = san_of(&self.pos, m);
        let uci = uci_of(m);
        self.pos.play_unchecked(m);
        self.moves.push(m);
        self.keys.push(rep_key(&self.pos));
        self.records.push(MoveRecord { uci, san, fen: to_fen(&self.pos), clock_ms });
        self.status = self.compute_status();
        Ok(self.records.last().expect("az önce eklendi"))
    }

    /// Son yarı hamleyi geri al.
    pub fn undo(&mut self) -> Option<MoveRecord> {
        let rec = self.records.pop()?;
        self.moves.pop();
        self.keys.pop();
        let mut p = self.start.clone();
        for m in &self.moves {
            p.play_unchecked(*m);
        }
        self.pos = p;
        self.status = self.compute_status();
        Some(rec)
    }

    /// Oyunu dışarıdan bitir (terk, süre, anlaşma).
    pub fn finish(&mut self, winner: Option<Side>, termination: Termination) {
        if !self.status.is_over() {
            self.status = GameStatus::Finished { winner, termination };
        }
    }

    pub fn resign(&mut self, who: Side) {
        let winner = match who {
            Side::White => Side::Black,
            Side::Black => Side::White,
        };
        self.finish(Some(winner), Termination::Resignation);
    }

    /// Mevcut pozisyonun kaç kez tekrarlandığı.
    pub fn repetitions(&self) -> usize {
        let last = self.keys.last().expect("en az bir anahtar var");
        self.keys.iter().filter(|k| *k == last).count()
    }

    fn compute_status(&self) -> GameStatus {
        match self.pos.outcome() {
            Outcome::Known(KnownOutcome::Decisive { winner }) => {
                return GameStatus::Finished { winner: Some(winner.into()), termination: Termination::Checkmate };
            }
            Outcome::Known(KnownOutcome::Draw) => {
                let termination = if self.pos.is_stalemate() {
                    Termination::Stalemate
                } else {
                    Termination::InsufficientMaterial
                };
                return GameStatus::Finished { winner: None, termination };
            }
            Outcome::Unknown => {}
        }
        if self.keys.len() > 1 && self.repetitions() >= 3 {
            return GameStatus::Finished { winner: None, termination: Termination::ThreefoldRepetition };
        }
        if self.pos.halfmoves() >= 100 {
            return GameStatus::Finished { winner: None, termination: Termination::FiftyMoveRule };
        }
        GameStatus::Ongoing
    }

    /// Son hamlenin saat bilgisini ayarla (PGN içe aktarırken).
    pub fn set_last_clock(&mut self, ms: u64) {
        if let Some(r) = self.records.last_mut() {
            r.clock_ms = Some(ms);
        }
    }

    /// SAN hamle listesi.
    pub fn sans(&self) -> Vec<String> {
        self.records.iter().map(|r| r.san.clone()).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fools_mate() {
        let mut g = Game::new();
        for m in ["f3", "e5", "g4", "Qh4#"] {
            g.play(m).unwrap();
        }
        assert_eq!(
            g.status(),
            GameStatus::Finished { winner: Some(Side::Black), termination: Termination::Checkmate }
        );
        assert_eq!(g.records()[3].san, "Qh4#");
        assert!(g.play("e4").is_err());
    }

    #[test]
    fn threefold() {
        let mut g = Game::new();
        for m in ["Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8"] {
            g.play(m).unwrap();
        }
        assert!(matches!(
            g.status(),
            GameStatus::Finished { termination: Termination::ThreefoldRepetition, .. }
        ));
    }

    #[test]
    fn undo_works() {
        let mut g = Game::new();
        g.play("e4").unwrap();
        g.play("e5").unwrap();
        g.undo();
        assert_eq!(g.ply(), 1);
        assert_eq!(g.turn(), Side::Black);
    }

    #[test]
    fn stalemate() {
        let mut g = Game::from_fen("k7/8/1K6/2Q5/8/8/8/8 w - - 0 1").unwrap();
        g.play("Qc7").unwrap();
        assert!(matches!(g.status(), GameStatus::Finished { termination: Termination::Stalemate, .. }));
    }
}
