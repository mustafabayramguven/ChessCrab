//! Açılış isimleri (ECO). Oyunun SAN dizisine en uzun önek eşleşmesiyle bulunur.

use serde::Serialize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Opening {
    pub eco: &'static str,
    pub name: &'static str,
    pub moves: &'static str,
}

macro_rules! o {
    ($eco:expr, $name:expr, $moves:expr) => {
        Opening { eco: $eco, name: $name, moves: $moves }
    };
}

/// Bilinen açılışlar. Hamleler SAN, boşlukla ayrılmış.
pub static OPENINGS: &[Opening] = &[
    // --- 1.e4 e5
    o!("C20", "Şah Piyonu Oyunu", "e4 e5"),
    o!("C20", "Erken Vezir Atağı (Çoban Matı denemesi)", "e4 e5 Qh5"),
    o!("C20", "Çoban Matı denemesi", "e4 e5 Bc4 Nc6 Qh5"),
    o!("C23", "Fil Açılışı", "e4 e5 Bc4"),
    o!("C25", "Viyana Oyunu", "e4 e5 Nc3"),
    o!("C30", "Şah Gambiti", "e4 e5 f4"),
    o!("C33", "Şah Gambiti Kabulü", "e4 e5 f4 exf4"),
    o!("C30", "Şah Gambiti Reddi", "e4 e5 f4 Bc5"),
    o!("C40", "Şah Atı Açılışı", "e4 e5 Nf3"),
    o!("C40", "Letonya Gambiti", "e4 e5 Nf3 f5"),
    o!("C41", "Philidor Savunması", "e4 e5 Nf3 d6"),
    o!("C42", "Petrov Savunması", "e4 e5 Nf3 Nf6"),
    o!("C42", "Petrov Savunması: Klasik", "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4"),
    o!("C44", "Şah Piyonu Oyunu: İki At", "e4 e5 Nf3 Nc6"),
    o!("C44", "Ponziani Açılışı", "e4 e5 Nf3 Nc6 c3"),
    o!("C45", "İskoç Oyunu", "e4 e5 Nf3 Nc6 d4"),
    o!("C45", "İskoç Oyunu", "e4 e5 Nf3 Nc6 d4 exd4 Nxd4"),
    o!("C44", "İskoç Gambiti", "e4 e5 Nf3 Nc6 d4 exd4 Bc4"),
    o!("C46", "Üç At Oyunu", "e4 e5 Nf3 Nc6 Nc3"),
    o!("C47", "Dört At Oyunu", "e4 e5 Nf3 Nc6 Nc3 Nf6"),
    o!("C50", "İtalyan Oyunu", "e4 e5 Nf3 Nc6 Bc4"),
    o!("C50", "İtalyan Oyunu: Giuoco Piano", "e4 e5 Nf3 Nc6 Bc4 Bc5"),
    o!("C53", "İtalyan Oyunu: Klasik (c3)", "e4 e5 Nf3 Nc6 Bc4 Bc5 c3"),
    o!("C50", "İtalyan Oyunu: Giuoco Pianissimo", "e4 e5 Nf3 Nc6 Bc4 Bc5 d3"),
    o!("C51", "Evans Gambiti", "e4 e5 Nf3 Nc6 Bc4 Bc5 b4"),
    o!("C50", "Macar Savunması", "e4 e5 Nf3 Nc6 Bc4 Be7"),
    o!("C55", "İki At Savunması", "e4 e5 Nf3 Nc6 Bc4 Nf6"),
    o!("C57", "İki At Savunması: Ng5", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5"),
    o!("C57", "İki At: Kızarmış Ciğer (Fried Liver)", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7"),
    o!("C58", "İki At: Ana Hat (Na5)", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5"),
    o!("C57", "İki At: Traxler Karşı Atağı", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5"),
    o!("C55", "İki At: Modern (d3)", "e4 e5 Nf3 Nc6 Bc4 Nf6 d3"),
    o!("C60", "İspanyol Oyunu (Ruy Lopez)", "e4 e5 Nf3 Nc6 Bb5"),
    o!("C65", "İspanyol: Berlin Savunması", "e4 e5 Nf3 Nc6 Bb5 Nf6"),
    o!("C67", "İspanyol: Berlin Duvarı", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8"),
    o!("C68", "İspanyol: Değişim Varyantı", "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6"),
    o!("C70", "İspanyol: Morphy Savunması", "e4 e5 Nf3 Nc6 Bb5 a6"),
    o!("C78", "İspanyol: Morphy, Ba4 Nf6", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6"),
    o!("C80", "İspanyol: Açık Varyant", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4"),
    o!("C84", "İspanyol: Kapalı Varyant", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7"),
    o!("C88", "İspanyol: Kapalı, Re1 b5 Bb3", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3"),
    o!("C89", "İspanyol: Marshall Atağı", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3 d5"),
    o!("C92", "İspanyol: Kapalı Ana Hat", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3"),
    o!("C62", "İspanyol: Eski Steinitz", "e4 e5 Nf3 Nc6 Bb5 d6"),
    o!("C64", "İspanyol: Klasik", "e4 e5 Nf3 Nc6 Bb5 Bc5"),
    // --- Sicilya
    o!("B20", "Sicilya Savunması", "e4 c5"),
    o!("B22", "Sicilya: Alapin (c3)", "e4 c5 c3"),
    o!("B23", "Sicilya: Kapalı", "e4 c5 Nc3"),
    o!("B21", "Sicilya: Smith-Morra Gambiti", "e4 c5 d4 cxd4 c3"),
    o!("B27", "Sicilya: Nf3", "e4 c5 Nf3"),
    o!("B50", "Sicilya: d6", "e4 c5 Nf3 d6"),
    o!("B51", "Sicilya: Moskova (Bb5+)", "e4 c5 Nf3 d6 Bb5+"),
    o!("B54", "Açık Sicilya", "e4 c5 Nf3 d6 d4 cxd4 Nxd4"),
    o!("B56", "Açık Sicilya: Klasik Hat", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3"),
    o!("B90", "Sicilya: Najdorf", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6"),
    o!("B90", "Sicilya: Najdorf, İngiliz Atağı", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3"),
    o!("B96", "Sicilya: Najdorf, Bg5", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5"),
    o!("B92", "Sicilya: Najdorf, Be2", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2"),
    o!("B70", "Sicilya: Ejderha (Dragon)", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6"),
    o!("B76", "Sicilya: Ejderha, Yugoslav Atağı", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3"),
    o!("B80", "Sicilya: Scheveningen", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6"),
    o!("B57", "Sicilya: Klasik (Nc6)", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 Nc6"),
    o!("B30", "Sicilya: Nc6", "e4 c5 Nf3 Nc6"),
    o!("B30", "Sicilya: Rossolimo", "e4 c5 Nf3 Nc6 Bb5"),
    o!("B33", "Sicilya: Sveshnikov", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5"),
    o!("B35", "Sicilya: Hızlandırılmış Ejderha", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 g6"),
    o!("B40", "Sicilya: e6", "e4 c5 Nf3 e6"),
    o!("B45", "Sicilya: Taimanov", "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6"),
    o!("B41", "Sicilya: Kan (Paulsen)", "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6"),
    // --- Fransız
    o!("C00", "Fransız Savunması", "e4 e6"),
    o!("C01", "Fransız: Değişim", "e4 e6 d4 d5 exd5"),
    o!("C02", "Fransız: İlerleme", "e4 e6 d4 d5 e5"),
    o!("C02", "Fransız: İlerleme, Ana Hat", "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6"),
    o!("C03", "Fransız: Tarrasch", "e4 e6 d4 d5 Nd2"),
    o!("C10", "Fransız: Paulsen (Nc3)", "e4 e6 d4 d5 Nc3"),
    o!("C10", "Fransız: Rubinstein", "e4 e6 d4 d5 Nc3 dxe4"),
    o!("C11", "Fransız: Klasik", "e4 e6 d4 d5 Nc3 Nf6"),
    o!("C11", "Fransız: Steinitz", "e4 e6 d4 d5 Nc3 Nf6 e5"),
    o!("C15", "Fransız: Winawer", "e4 e6 d4 d5 Nc3 Bb4"),
    o!("C18", "Fransız: Winawer, Ana Hat", "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3"),
    // --- Caro-Kann
    o!("B10", "Caro-Kann Savunması", "e4 c6"),
    o!("B12", "Caro-Kann: İlerleme", "e4 c6 d4 d5 e5"),
    o!("B12", "Caro-Kann: İlerleme, Bf5", "e4 c6 d4 d5 e5 Bf5"),
    o!("B13", "Caro-Kann: Değişim", "e4 c6 d4 d5 exd5 cxd5"),
    o!("B14", "Caro-Kann: Panov Atağı", "e4 c6 d4 d5 exd5 cxd5 c4"),
    o!("B15", "Caro-Kann: Ana Hat (Nc3)", "e4 c6 d4 d5 Nc3"),
    o!("B18", "Caro-Kann: Klasik (Bf5)", "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5"),
    o!("B17", "Caro-Kann: Karpov (Nd7)", "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nd7"),
    o!("B11", "Caro-Kann: İki At", "e4 c6 Nc3 d5 Nf3"),
    // --- Diğer 1.e4
    o!("B01", "İskandinav Savunması", "e4 d5"),
    o!("B01", "İskandinav: Qxd5", "e4 d5 exd5 Qxd5"),
    o!("B01", "İskandinav: Ana Hat (Qa5)", "e4 d5 exd5 Qxd5 Nc3 Qa5"),
    o!("B01", "İskandinav: Qd6", "e4 d5 exd5 Qxd5 Nc3 Qd6"),
    o!("B01", "İskandinav: Modern (Nf6)", "e4 d5 exd5 Nf6"),
    o!("B07", "Pirc Savunması", "e4 d6 d4 Nf6 Nc3 g6"),
    o!("B06", "Modern Savunma", "e4 g6"),
    o!("B02", "Alekhine Savunması", "e4 Nf6"),
    o!("B00", "Nimzowitsch Savunması", "e4 Nc6"),
    o!("B00", "Owen Savunması", "e4 b6"),
    // --- 1.d4 d5
    o!("D00", "Vezir Piyonu Oyunu", "d4 d5"),
    o!("D02", "Londra Sistemi", "d4 d5 Nf3 Nf6 Bf4"),
    o!("D02", "Londra Sistemi", "d4 d5 Bf4"),
    o!("A48", "Londra Sistemi", "d4 Nf6 Nf3 g6 Bf4"),
    o!("D00", "Londra Sistemi", "d4 Nf6 Bf4"),
    o!("D06", "Vezir Gambiti", "d4 d5 c4"),
    o!("D20", "Vezir Gambiti Kabulü", "d4 d5 c4 dxc4"),
    o!("D30", "Vezir Gambiti Reddi", "d4 d5 c4 e6"),
    o!("D35", "VGR: Değişim (Carlsbad)", "d4 d5 c4 e6 Nc3 Nf6 cxd5 exd5"),
    o!("D37", "VGR: Klasik", "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7"),
    o!("D53", "VGR: Ortodoks (Bg5)", "d4 d5 c4 e6 Nc3 Nf6 Bg5"),
    o!("D10", "Slav Savunması", "d4 d5 c4 c6"),
    o!("D15", "Slav: Ana Hat", "d4 d5 c4 c6 Nf3 Nf6 Nc3"),
    o!("D43", "Yarı Slav", "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6"),
    o!("D08", "Albin Karşı Gambiti", "d4 d5 c4 e5"),
    o!("D07", "Chigorin Savunması", "d4 d5 c4 Nc6"),
    // --- Hint savunmaları
    o!("A45", "Hint Savunması", "d4 Nf6"),
    o!("A46", "Hint: Nf3", "d4 Nf6 Nf3"),
    o!("E00", "Hint: c4 e6", "d4 Nf6 c4 e6"),
    o!("E20", "Nimzo-Hint Savunması", "d4 Nf6 c4 e6 Nc3 Bb4"),
    o!("E32", "Nimzo-Hint: Klasik (Qc2)", "d4 Nf6 c4 e6 Nc3 Bb4 Qc2"),
    o!("E40", "Nimzo-Hint: Rubinstein (e3)", "d4 Nf6 c4 e6 Nc3 Bb4 e3"),
    o!("E12", "Vezir Hint Savunması", "d4 Nf6 c4 e6 Nf3 b6"),
    o!("E11", "Bogo-Hint Savunması", "d4 Nf6 c4 e6 Nf3 Bb4+"),
    o!("E01", "Katalan Açılışı", "d4 Nf6 c4 e6 g3"),
    o!("E60", "Şah Hint Savunması", "d4 Nf6 c4 g6"),
    o!("E61", "Şah Hint Savunması", "d4 Nf6 c4 g6 Nc3 Bg7"),
    o!("E90", "Şah Hint: Klasik", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3"),
    o!("E97", "Şah Hint: Mar del Plata", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7"),
    o!("E80", "Şah Hint: Sämisch", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3"),
    o!("E76", "Şah Hint: Dört Piyon Atağı", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4"),
    o!("D80", "Grünfeld Savunması", "d4 Nf6 c4 g6 Nc3 d5"),
    o!("D85", "Grünfeld: Değişim", "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3"),
    o!("A56", "Benoni Savunması", "d4 Nf6 c4 c5"),
    o!("A60", "Modern Benoni", "d4 Nf6 c4 c5 d5 e6"),
    o!("A57", "Benko Gambiti", "d4 Nf6 c4 c5 d5 b5"),
    o!("A80", "Hollanda Savunması", "d4 f5"),
    o!("A40", "Vezir Piyonu: İngiliz Savunması", "d4 e6"),
    // --- Yan açılışlar
    o!("A10", "İngiliz Açılışı", "c4"),
    o!("A20", "İngiliz: Ters Sicilya", "c4 e5"),
    o!("A30", "İngiliz: Simetrik", "c4 c5"),
    o!("A15", "İngiliz: Hint", "c4 Nf6"),
    o!("A04", "Réti Açılışı", "Nf3"),
    o!("A09", "Réti: d5 c4", "Nf3 d5 c4"),
    o!("A07", "Şah Hint Atağı", "Nf3 d5 g3"),
    o!("A02", "Bird Açılışı", "f4"),
    o!("A01", "Larsen Açılışı", "b3"),
    o!("A00", "Polonya (Orangutan) Açılışı", "b4"),
    o!("A00", "Grob Açılışı", "g4"),
];

/// Oyunun SAN dizisine en iyi uyan açılışı ve kaç yarı hamle "kitap" olduğunu bul.
/// Dönüş: (açılış, eşleşen yarı hamle sayısı)
pub fn identify_opening<S: AsRef<str>>(sans: &[S]) -> Option<(&'static Opening, usize)> {
    let norm = |s: &str| s.trim_end_matches(['+', '#', '!', '?']).to_string();
    let game: Vec<String> = sans.iter().map(|s| norm(s.as_ref())).collect();
    let mut best: Option<(&'static Opening, usize)> = None;
    for op in OPENINGS {
        let line: Vec<String> = op.moves.split_whitespace().map(norm).collect();
        if line.len() <= game.len() && line.iter().zip(&game).all(|(a, b)| a == b) {
            if best.map(|(_, n)| line.len() > n).unwrap_or(true) {
                best = Some((op, line.len()));
            }
        }
    }
    best
}

/// Kaçıncı yarı hamleye kadar oyun bilinen bir açılış hattının içinde?
/// (Herhangi bir hattın öneki olan en uzun prefix.)
pub fn book_depth<S: AsRef<str>>(sans: &[S]) -> usize {
    let norm = |s: &str| s.trim_end_matches(['+', '#', '!', '?']).to_string();
    let game: Vec<String> = sans.iter().map(|s| norm(s.as_ref())).collect();
    let mut depth = 0;
    for op in OPENINGS {
        let n = op
            .moves
            .split_whitespace()
            .map(norm)
            .zip(&game)
            .take_while(|(a, b)| a == *b)
            .count();
        depth = depth.max(n);
    }
    depth
}

#[cfg(test)]
mod tests {
    use super::*;
    use shakmaty::Chess;

    #[test]
    fn all_openings_legal() {
        for op in OPENINGS {
            crate::play_line(&Chess::default(), op.moves)
                .unwrap_or_else(|e| panic!("{} ({}) yasal değil: {e}", op.name, op.moves));
        }
    }

    #[test]
    fn identifies() {
        let sans = ["e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6", "Be3", "e5"];
        let (op, n) = identify_opening(&sans).unwrap();
        assert_eq!(op.name, "Sicilya: Najdorf, İngiliz Atağı");
        assert_eq!(n, 11);
        assert_eq!(book_depth(&sans), 11);
    }
}
