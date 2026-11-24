//! Mini açılış kitabı: çekirdekteki açılış hatlarından ve ders repertuvarından
//! oyunun devamı olan hamleleri bulur. Motorun her oyunda aynı oynamamasını sağlar.

use chesscrab_core::opening::OPENINGS;

use crate::level::Rng;

/// Oyun SAN dizisinin devamı olabilecek kitap hamleleri (tekrarsız).
pub fn book_moves<S: AsRef<str>>(sans: &[S], extra_lines: &[&str]) -> Vec<String> {
    let norm = |s: &str| s.trim_end_matches(['+', '#', '!', '?']).to_string();
    let game: Vec<String> = sans.iter().map(|s| norm(s.as_ref())).collect();
    let mut out: Vec<String> = Vec::new();
    let lines = OPENINGS.iter().map(|o| o.moves).chain(extra_lines.iter().copied());
    for line in lines {
        let toks: Vec<String> = chesscrab_core::tokenize_line(line).map(norm).collect();
        if toks.len() > game.len() && toks.iter().zip(&game).all(|(a, b)| a == b) {
            let next = toks[game.len()].clone();
            if !out.contains(&next) {
                out.push(next);
            }
        }
    }
    out
}

pub fn pick_book_move<S: AsRef<str>>(sans: &[S], extra_lines: &[&str], rng: &mut Rng) -> Option<String> {
    let moves = book_moves(sans, extra_lines);
    if moves.is_empty() {
        None
    } else {
        Some(moves[rng.below(moves.len())].clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn finds_continuations() {
        let m = book_moves(&["e4", "e5", "Nf3"], &[]);
        assert!(m.contains(&"Nc6".to_string()));
        assert!(m.contains(&"Nf6".to_string()));
        assert!(book_moves::<&str>(&[], &[]).contains(&"e4".to_string()));
    }
}
