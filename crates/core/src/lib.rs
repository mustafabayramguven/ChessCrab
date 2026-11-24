//! ChessCrab çekirdeği.
//!
//! Kurallar (hamle üretimi, FEN, SAN) için lichess'in de kullandığı
//! `shakmaty` kütüphanesini kullanıyoruz; oyun modeli, PGN, saat ve açılış
//! tanıma bizim.

pub mod clock;
pub mod game;
pub mod opening;
pub mod pgn;
pub mod state;

pub use shakmaty as chess;

pub use clock::{Clock, TimeControl};
pub use game::{Game, GameError, GameStatus, MoveRecord, Termination};
pub use opening::{Opening, identify_opening};
pub use state::GameState;

use shakmaty::{CastlingMode, Chess, EnPassantMode, Move, Position, fen::Fen, san::San, uci::UciMove};

/// Başlangıç pozisyonunun FEN'i.
pub const START_FEN: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

/// FEN'den pozisyon oku.
pub fn parse_fen(fen: &str) -> Result<Chess, GameError> {
    let fen: Fen = fen.trim().parse().map_err(|_| GameError::InvalidFen(fen.to_string()))?;
    fen.into_position(CastlingMode::Standard)
        .map_err(|_| GameError::InvalidFen("geçersiz pozisyon".into()))
}

/// Pozisyonun FEN'i.
pub fn to_fen(pos: &Chess) -> String {
    Fen::from_position(pos, EnPassantMode::Legal).to_string()
}

/// SAN ya da UCI hamleyi çöz.
pub fn parse_move(pos: &Chess, text: &str) -> Result<Move, GameError> {
    let text = text.trim();
    if let Ok(uci) = text.parse::<UciMove>()
        && let Ok(m) = uci.to_move(pos) {
            return Ok(m);
        }
    let clean = text.trim_end_matches(['+', '#', '!', '?']);
    let san: San = clean.parse().map_err(|_| GameError::IllegalMove(text.to_string()))?;
    san.to_move(pos).map_err(|_| GameError::IllegalMove(text.to_string()))
}

/// Hamlenin SAN gösterimi (şah/mat işaretiyle).
pub fn san_of(pos: &Chess, m: Move) -> String {
    shakmaty::san::SanPlus::from_move(pos.clone(), m).to_string()
}

/// Hamlenin UCI gösterimi.
pub fn uci_of(m: Move) -> String {
    m.to_uci(CastlingMode::Standard).to_string()
}

/// Bir SAN dizisini (ör. "e4 e5 Nf3") başlangıç pozisyonundan oyna.
/// Hamle numaraları ("1." "2...") yok sayılır.
pub fn play_line(start: &Chess, line: &str) -> Result<(Chess, Vec<Move>), GameError> {
    let mut pos = start.clone();
    let mut moves = Vec::new();
    for tok in tokenize_line(line) {
        let m = parse_move(&pos, tok)?;
        pos.play_unchecked(m);
        moves.push(m);
    }
    Ok((pos, moves))
}

/// "1. e4 e5 2. Nf3" → ["e4","e5","Nf3"]
pub fn tokenize_line(line: &str) -> impl Iterator<Item = &str> {
    line.split_whitespace().filter_map(|t| {
        let t = t.trim_start_matches(|c: char| c.is_ascii_digit() || c == '.');
        if t.is_empty() || t == "*" { None } else { Some(t) }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn line_parses() {
        let (pos, moves) = play_line(&Chess::default(), "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. O-O").unwrap();
        assert_eq!(moves.len(), 7);
        assert!(to_fen(&pos).starts_with("r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQ1RK1 b kq"));
    }

    #[test]
    fn uci_and_san() {
        let pos = Chess::default();
        let m = parse_move(&pos, "g1f3").unwrap();
        assert_eq!(san_of(&pos, m), "Nf3");
        assert_eq!(uci_of(parse_move(&pos, "Nf3").unwrap()), "g1f3");
    }
}
