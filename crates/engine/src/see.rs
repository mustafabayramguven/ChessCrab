//! Statik Değişim Değerlendirmesi (SEE): bir karedeki alım zincirinin net materyal sonucu.

use shakmaty::{Bitboard, Chess, Move, Position, Role, Square};

use crate::eval::piece_value;

fn least_valuable(pos: &Chess, attackers: Bitboard) -> Option<(Square, Role)> {
    let b = pos.board();
    for role in Role::ALL {
        if let Some(sq) = (attackers & b.by_role(role)).first() {
            return Some((sq, role));
        }
    }
    None
}

/// Hamlenin SEE değeri (santipiyon, hamleyi yapan açısından).
pub fn see(pos: &Chess, m: Move) -> i32 {
    let (from, to, captured, mover, promo) = match m {
        Move::Normal { role, from, capture, to, promotion } => {
            (from, to, capture.map(piece_value).unwrap_or(0), role, promotion)
        }
        Move::EnPassant { .. } => return 0,
        _ => return 0,
    };
    let b = pos.board();
    let mut occupied = b.occupied() ^ Bitboard::from_square(from);
    let mut gain = [0i32; 40];
    let mut d = 0usize;
    gain[0] = captured + promo.map(|p| piece_value(p) - piece_value(Role::Pawn)).unwrap_or(0);
    let mut attacker_val = promo.map(piece_value).unwrap_or(piece_value(mover));
    let mut side = pos.turn().other();
    loop {
        d += 1;
        if d >= gain.len() {
            break;
        }
        gain[d] = attacker_val - gain[d - 1];
        if (-gain[d - 1]).max(gain[d]) < 0 {
            break;
        }
        let attackers = b.attacks_to(to, side, occupied) & occupied & b.by_color(side);
        let Some((sq, role)) = least_valuable(pos, attackers) else {
            break;
        };
        occupied ^= Bitboard::from_square(sq);
        attacker_val = piece_value(role);
        side = side.other();
    }
    while d > 1 {
        d -= 1;
        gain[d - 1] = -(-gain[d - 1]).max(gain[d]);
    }
    gain[0]
}

#[cfg(test)]
mod tests {
    use super::*;
    use chesscrab_core::{parse_fen, parse_move};

    #[test]
    fn see_basic() {
        // Kale korunmayan piyonu alır: +100
        let p = parse_fen("1k1r4/1pp4p/p7/4p3/8/P5P1/1PP4P/2K1R3 w - - 0 1").unwrap();
        let m = parse_move(&p, "Rxe5").unwrap();
        assert_eq!(see(&p, m), 100);
        // At korunan piyonu alır: 100 - 320 = -220
        let p = parse_fen("1k1r3q/1ppn3p/p4b2/4p3/8/P2N2P1/1PP1R1BP/2K1Q3 w - - 0 1").unwrap();
        let m = parse_move(&p, "Nxe5").unwrap();
        assert!(see(&p, m) < 0);
    }
}
