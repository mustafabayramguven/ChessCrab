//! Değerlendirme: PeSTO tapered tablolar + piyon yapısı, hareketlilik, fil çifti,
//! açık hat kaleleri ve şah güvenliği. Skor santipiyon, sırası gelen taraf açısından.

use shakmaty::{Bitboard, Chess, Color, File, Position, Rank, Role, Square, attacks};

pub const PAWN: i32 = 100;
pub const KNIGHT: i32 = 320;
pub const BISHOP: i32 = 330;
pub const ROOK: i32 = 500;
pub const QUEEN: i32 = 950;

/// SEE / sıralama için kaba taş değerleri.
pub const fn piece_value(role: Role) -> i32 {
    match role {
        Role::Pawn => PAWN,
        Role::Knight => KNIGHT,
        Role::Bishop => BISHOP,
        Role::Rook => ROOK,
        Role::Queen => QUEEN,
        Role::King => 20_000,
    }
}

const MG_VALUE: [i32; 6] = [82, 337, 365, 477, 1025, 0];
const EG_VALUE: [i32; 6] = [94, 281, 297, 512, 936, 0];
const PHASE_INC: [i32; 6] = [0, 1, 1, 2, 4, 0];

// Tablolar a8..h8, a7..h7, ... a1..h1 sırasıyla (görsel tahta, beyaz açısından).
#[rustfmt::skip]
const MG_PAWN: [i32; 64] = [
      0,   0,   0,   0,   0,   0,  0,   0,
     98, 134,  61,  95,  68, 126, 34, -11,
     -6,   7,  26,  31,  65,  56, 25, -20,
    -14,  13,   6,  21,  23,  12, 17, -23,
    -27,  -2,  -5,  12,  17,   6, 10, -25,
    -26,  -4,  -4, -10,   3,   3, 33, -12,
    -35,  -1, -20, -23, -15,  24, 38, -22,
      0,   0,   0,   0,   0,   0,  0,   0,
];
#[rustfmt::skip]
const EG_PAWN: [i32; 64] = [
      0,   0,   0,   0,   0,   0,   0,   0,
    178, 173, 158, 134, 147, 132, 165, 187,
     94, 100,  85,  67,  56,  53,  82,  84,
     32,  24,  13,   5,  -2,   4,  17,  17,
     13,   9,  -3,  -7,  -7,  -8,   3,  -1,
      4,   7,  -6,   1,   0,  -5,  -1,  -8,
     13,   8,   8,  10,  13,   0,   2,  -7,
      0,   0,   0,   0,   0,   0,   0,   0,
];
#[rustfmt::skip]
const MG_KNIGHT: [i32; 64] = [
    -167, -89, -34, -49,  61, -97, -15, -107,
     -73, -41,  72,  36,  23,  62,   7,  -17,
     -47,  60,  37,  65,  84, 129,  73,   44,
      -9,  17,  19,  53,  37,  69,  18,   22,
     -13,   4,  16,  13,  28,  19,  21,   -8,
     -23,  -9,  12,  10,  19,  17,  25,  -16,
     -29, -53, -12,  -3,  -1,  18, -14,  -19,
    -105, -21, -58, -33, -17, -28, -19,  -23,
];
#[rustfmt::skip]
const EG_KNIGHT: [i32; 64] = [
    -58, -38, -13, -28, -31, -27, -63, -99,
    -25,  -8, -25,  -2,  -9, -25, -24, -52,
    -24, -20,  10,   9,  -1,  -9, -19, -41,
    -17,   3,  22,  22,  22,  11,   8, -18,
    -18,  -6,  16,  25,  16,  17,   4, -18,
    -23,  -3,  -1,  15,  10,  -3, -20, -22,
    -42, -20, -10,  -5,  -2, -20, -23, -44,
    -29, -51, -23, -15, -22, -18, -50, -64,
];
#[rustfmt::skip]
const MG_BISHOP: [i32; 64] = [
    -29,   4, -82, -37, -25, -42,   7,  -8,
    -26,  16, -18, -13,  30,  59,  18, -47,
    -16,  37,  43,  40,  35,  50,  37,  -2,
     -4,   5,  19,  50,  37,  37,   7,  -2,
     -6,  13,  13,  26,  34,  12,  10,   4,
      0,  15,  15,  15,  14,  27,  18,  10,
      4,  15,  16,   0,   7,  21,  33,   1,
    -33,  -3, -14, -21, -13, -12, -39, -21,
];
#[rustfmt::skip]
const EG_BISHOP: [i32; 64] = [
    -14, -21, -11,  -8, -7,  -9, -17, -24,
     -8,  -4,   7, -12, -3, -13,  -4, -14,
      2,  -8,   0,  -1, -2,   6,   0,   4,
     -3,   9,  12,   9, 14,  10,   3,   2,
     -6,   3,  13,  19,  7,  10,  -3,  -9,
    -12,  -3,   8,  10, 13,   3,  -7, -15,
    -14, -18,  -7,  -1,  4,  -9, -15, -27,
    -23,  -9, -23,  -5, -9, -16,  -5, -17,
];
#[rustfmt::skip]
const MG_ROOK: [i32; 64] = [
     32,  42,  32,  51, 63,  9,  31,  43,
     27,  32,  58,  62, 80, 67,  26,  44,
     -5,  19,  26,  36, 17, 45,  61,  16,
    -24, -11,   7,  26, 24, 35,  -8, -20,
    -36, -26, -12,  -1,  9, -7,   6, -23,
    -45, -25, -16, -17,  3,  0,  -5, -33,
    -44, -16, -20,  -9, -1, 11,  -6, -71,
    -19, -13,   1,  17, 16,  7, -37, -26,
];
#[rustfmt::skip]
const EG_ROOK: [i32; 64] = [
    13, 10, 18, 15, 12,  12,   8,   5,
    11, 13, 13, 11, -3,   3,   8,   3,
     7,  7,  7,  5,  4,  -3,  -5,  -3,
     4,  3, 13,  1,  2,   1,  -1,   2,
     3,  5,  8,  4, -5,  -6,  -8, -11,
    -4,  0, -5, -1, -7, -12,  -8, -16,
    -6, -6,  0,  2, -9,  -9, -11,  -3,
    -9,  2,  3, -1, -5, -13,   4, -20,
];
#[rustfmt::skip]
const MG_QUEEN: [i32; 64] = [
    -28,   0,  29,  12,  59,  44,  43,  45,
    -24, -39,  -5,   1, -16,  57,  28,  54,
    -13, -17,   7,   8,  29,  56,  47,  57,
    -27, -27, -16, -16,  -1,  17,  -2,   1,
     -9, -26,  -9, -10,  -2,  -4,   3,  -3,
    -14,   2, -11,  -2,  -5,   2,  14,   5,
    -35,  -8,  11,   2,   8,  15,  -3,   1,
     -1, -18,  -9,  10, -15, -25, -31, -50,
];
#[rustfmt::skip]
const EG_QUEEN: [i32; 64] = [
     -9,  22,  22,  27,  27,  19,  10,  20,
    -17,  20,  32,  41,  58,  25,  30,   0,
    -20,   6,   9,  49,  47,  35,  19,   9,
      3,  22,  24,  45,  57,  40,  57,  36,
    -18,  28,  19,  47,  31,  34,  39,  23,
    -16, -27,  15,   6,   9,  17,  10,   5,
    -22, -23, -30, -16, -16, -23, -36, -32,
    -33, -28, -22, -43,  -5, -32, -20, -41,
];
#[rustfmt::skip]
const MG_KING: [i32; 64] = [
    -65,  23,  16, -15, -56, -34,   2,  13,
     29,  -1, -20,  -7,  -8,  -4, -38, -29,
     -9,  24,   2, -16, -20,   6,  22, -22,
    -17, -20, -12, -27, -30, -25, -14, -36,
    -49,  -1, -27, -39, -46, -44, -33, -51,
    -14, -14, -22, -46, -44, -30, -15, -27,
      1,   7,  -8, -64, -43, -16,   9,   8,
    -15,  36,  12, -54,   8, -28,  24,  14,
];
#[rustfmt::skip]
const EG_KING: [i32; 64] = [
    -74, -35, -18, -18, -11,  15,   4, -17,
    -12,  17,  14,  17,  17,  38,  23,  11,
     10,  17,  23,  15,  20,  45,  44,  13,
     -8,  22,  24,  27,  26,  33,  26,   3,
    -18,  -4,  21,  24,  27,  23,   9, -11,
    -19,  -3,  11,  21,  23,  16,   7,  -9,
    -27, -11,   4,  13,  14,   4,  -5, -17,
    -53, -34, -21, -11, -28, -14, -24, -43,
];

const MG_TABLES: [&[i32; 64]; 6] = [&MG_PAWN, &MG_KNIGHT, &MG_BISHOP, &MG_ROOK, &MG_QUEEN, &MG_KING];
const EG_TABLES: [&[i32; 64]; 6] = [&EG_PAWN, &EG_KNIGHT, &EG_BISHOP, &EG_ROOK, &EG_QUEEN, &EG_KING];

/// Geçer piyon bonusu (ilerlediği yatay sayısına göre: 0 = başlangıç yatayı).
const PASSED_MG: [i32; 8] = [0, 5, 10, 15, 30, 55, 90, 0];
const PASSED_EG: [i32; 8] = [0, 10, 18, 30, 55, 95, 150, 0];

#[inline]
fn pst_index(sq: Square, color: Color) -> usize {
    // shakmaty: a1 = 0. Tablolar a8 = 0 olduğundan beyaz için dikey çevir.
    match color {
        Color::White => sq as usize ^ 56,
        Color::Black => sq as usize,
    }
}

fn file_bb(f: File) -> Bitboard {
    Bitboard::from_file(f)
}

fn adjacent_files(f: File) -> Bitboard {
    let mut bb = Bitboard::EMPTY;
    if let Some(l) = f.offset(-1) {
        bb |= file_bb(l);
    }
    if let Some(r) = f.offset(1) {
        bb |= file_bb(r);
    }
    bb
}

/// `sq`'daki `color` piyonunun önündeki (geçer piyon kontrolü için) alan.
fn front_span(sq: Square, color: Color) -> Bitboard {
    let files = file_bb(sq.file()) | adjacent_files(sq.file());
    let r = sq.rank() as u32;
    let mut ranks = Bitboard::EMPTY;
    match color {
        Color::White => {
            for rr in (r + 1)..8 {
                ranks |= Bitboard::from_rank(Rank::new(rr));
            }
        }
        Color::Black => {
            for rr in 0..r {
                ranks |= Bitboard::from_rank(Rank::new(rr));
            }
        }
    }
    files & ranks
}

/// Oyun fazı 0 (son oyun) .. 24 (orta oyun).
pub fn phase(pos: &Chess) -> i32 {
    let b = pos.board();
    let mut p = 0;
    for role in Role::ALL {
        p += PHASE_INC[role as usize - 1] * b.by_role(role).count() as i32;
    }
    p.min(24)
}

/// Statik değerlendirme, sırası gelen taraf açısından.
pub fn evaluate(pos: &Chess) -> i32 {
    let b = pos.board();
    let occ = b.occupied();
    let mut mg = [0i32; 2];
    let mut eg = [0i32; 2];
    let ph = phase(pos);

    for color in Color::ALL {
        let ci = color as usize;
        let us = b.by_color(color);
        let them = b.by_color(color.other());
        let our_pawns = b.pawns() & us;
        let their_pawns = b.pawns() & them;

        // Rakip piyonların kontrol ettiği kareler (hareketlilikte sayma)
        let mut their_pawn_att = Bitboard::EMPTY;
        for sq in their_pawns {
            their_pawn_att |= attacks::pawn_attacks(color.other(), sq);
        }
        let mob_area = !us & !their_pawn_att;

        for role in Role::ALL {
            let ri = role as usize - 1;
            for sq in b.by_role(role) & us {
                let idx = pst_index(sq, color);
                mg[ci] += MG_VALUE[ri] + MG_TABLES[ri][idx];
                eg[ci] += EG_VALUE[ri] + EG_TABLES[ri][idx];

                match role {
                    Role::Pawn => {
                        let f = sq.file();
                        // çift piyon
                        if (our_pawns & file_bb(f)).count() > 1 {
                            mg[ci] -= 6;
                            eg[ci] -= 12;
                        }
                        // izole piyon
                        if (our_pawns & adjacent_files(f)).is_empty() {
                            mg[ci] -= 10;
                            eg[ci] -= 12;
                        }
                        // geçer piyon
                        if (their_pawns & front_span(sq, color)).is_empty()
                            && (our_pawns & front_span(sq, color) & file_bb(f)).is_empty()
                        {
                            let adv = match color {
                                Color::White => sq.rank() as usize - 1,
                                Color::Black => 6 - sq.rank() as usize,
                            }
                            .min(7);
                            mg[ci] += PASSED_MG[adv];
                            eg[ci] += PASSED_EG[adv];
                        }
                    }
                    Role::Knight => {
                        let m = (attacks::knight_attacks(sq) & mob_area).count() as i32;
                        mg[ci] += 4 * (m - 4);
                        eg[ci] += 4 * (m - 4);
                    }
                    Role::Bishop => {
                        let m = (attacks::bishop_attacks(sq, occ) & mob_area).count() as i32;
                        mg[ci] += 5 * (m - 6);
                        eg[ci] += 5 * (m - 6);
                    }
                    Role::Rook => {
                        let m = (attacks::rook_attacks(sq, occ) & mob_area).count() as i32;
                        mg[ci] += 2 * (m - 7);
                        eg[ci] += 4 * (m - 7);
                        let f = file_bb(sq.file());
                        if (b.pawns() & f).is_empty() {
                            mg[ci] += 25;
                            eg[ci] += 10;
                        } else if (our_pawns & f).is_empty() {
                            mg[ci] += 12;
                            eg[ci] += 5;
                        }
                    }
                    Role::Queen => {
                        let m = (attacks::queen_attacks(sq, occ) & mob_area).count() as i32;
                        mg[ci] += (m - 13) / 2;
                        eg[ci] += m - 13;
                    }
                    Role::King => {
                        // piyon kalkanı (orta oyunda önemli)
                        let shield = attacks::king_attacks(sq) & our_pawns;
                        mg[ci] += 10 * shield.count().min(3) as i32;
                        // şahın çevresine saldıran rakip taşlar
                        let zone = attacks::king_attacks(sq);
                        let mut danger = 0;
                        for asq in b.knights() & them {
                            danger += 2 * (attacks::knight_attacks(asq) & zone).count() as i32;
                        }
                        for asq in b.bishops() & them {
                            danger += 2 * (attacks::bishop_attacks(asq, occ) & zone).count() as i32;
                        }
                        for asq in b.rooks() & them {
                            danger += 3 * (attacks::rook_attacks(asq, occ) & zone).count() as i32;
                        }
                        for asq in b.queens() & them {
                            danger += 5 * (attacks::queen_attacks(asq, occ) & zone).count() as i32;
                        }
                        mg[ci] -= danger * danger / 4;
                    }
                }
            }
        }

        if (b.bishops() & us).count() >= 2 {
            mg[ci] += 30;
            eg[ci] += 50;
        }
    }

    let w = Color::White as usize;
    let bl = Color::Black as usize;
    let mg_score = mg[w] - mg[bl];
    let eg_score = eg[w] - eg[bl];
    let mut score = (mg_score * ph + eg_score * (24 - ph)) / 24;

    // Basit son oyun ölçeklemesi: piyonsuz ve hafif taş avantajı → beraberliğe yakın
    let white_pawns = (b.pawns() & b.white()).count();
    let black_pawns = (b.pawns() & b.black()).count();
    if score > 0 && white_pawns == 0 && score < 400 {
        score /= 4;
    } else if score < 0 && black_pawns == 0 && score > -400 {
        score /= 4;
    }

    let tempo = 10;
    match pos.turn() {
        Color::White => score + tempo,
        Color::Black => -score + tempo,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chesscrab_core::parse_fen;

    #[test]
    fn symmetric_start() {
        let e = evaluate(&Chess::default());
        assert!(e.abs() < 30, "başlangıç değerlendirmesi dengeli olmalı: {e}");
    }

    #[test]
    fn mirrored_positions_equal() {
        let a = parse_fen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3").unwrap();
        let b = parse_fen("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 2 3").unwrap();
        assert_eq!(evaluate(&a), evaluate(&b));
    }

    #[test]
    fn material_counts() {
        // beyaz bir vezir fazla
        let p = parse_fen("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").unwrap();
        assert!(evaluate(&p) > 700);
    }
}
