//! Pozisyon + hamle kontrolü: her satır "FEN | hamle". En iyi hamleyi ve verilen
//! hamlenin skorunu yazar. `cargo run --release -p chesscrab-engine --example check < dosya`
use std::io::BufRead;

use chesscrab_core::{parse_fen, parse_move, san_of};
use chesscrab_engine::{Limits, Searcher};

fn main() {
    let depth: u32 = std::env::args().nth(1).and_then(|s| s.parse().ok()).unwrap_or(12);
    for line in std::io::stdin().lock().lines().map_while(Result::ok) {
        let line = line.trim().to_string();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let (fen, mv) = line.split_once('|').map(|(a, b)| (a.trim(), b.trim())).unwrap_or((line.as_str(), ""));
        let pos = match parse_fen(fen) {
            Ok(p) => p,
            Err(e) => {
                println!("GEÇERSİZ {fen}: {e}");
                continue;
            }
        };
        let mut s = Searcher::new(32);
        let r = s.search(&pos, &[], &Limits { depth: Some(depth), multipv: 2, ..Default::default() }, |_| {});
        let best = r.best.map(|m| san_of(&pos, m)).unwrap_or_default();
        let second = r.lines.get(1).map(|l| format!("{} {:?}", san_of(&pos, l.pv[0]), l.score)).unwrap_or_default();
        let mine = if mv.is_empty() {
            String::new()
        } else {
            match parse_move(&pos, mv) {
                Ok(m) => {
                    let mut s2 = Searcher::new(32);
                    let rr = s2.search(&pos, &[], &Limits { depth: Some(depth), search_moves: vec![m], ..Default::default() }, |_| {});
                    format!("{mv}: {:?}", rr.score)
                }
                Err(_) => format!("{mv}: YASAL DEĞİL"),
            }
        };
        println!("en iyi {best} {:?} | 2. {second} | {mine}   [{fen}]", r.score);
    }
}
