//! PGN'i stdin'den okuyup analiz eder: `cargo run --release -p chesscrab-analysis --example analyse < oyun.pgn`
//! İsteğe bağlı: derinlik ilk argüman (varsayılan 14).

use std::io::Read;

use chesscrab_analysis::{AnalysisConfig, analyse_game};

fn main() {
    let mut pgn = String::new();
    std::io::stdin().read_to_string(&mut pgn).expect("stdin");
    let depth = std::env::args().nth(1).and_then(|d| d.parse().ok()).unwrap_or(14);
    let game = chesscrab_core::pgn::from_pgn(&pgn).expect("PGN");
    let cfg = AnalysisConfig { depth: Some(depth), ..Default::default() };
    let a = analyse_game(&game, &cfg, None);
    if let Some((eco, name)) = &a.opening {
        println!("Açılış: {eco} {name}");
    }
    for m in &a.moves {
        println!(
            "{:>3}{} {:<8} {:<11} doğruluk {:>5.1}  kayıp {:>4}  en iyi {:<7} {}",
            m.move_number,
            if m.white { "." } else { "..." },
            format!("{}{}", m.san, m.class.symbol()),
            m.class.label_tr(),
            m.accuracy,
            m.cp_loss,
            m.best_san.clone().unwrap_or_default(),
            m.comment
        );
    }
    for (name, s) in [("Beyaz", &a.white), ("Siyah", &a.black)] {
        println!(
            "{name}: doğruluk {:.1}%  ACPL {:.0}  tahmini Elo {:?}  {:?}",
            s.accuracy.unwrap_or(0.0),
            s.acpl,
            s.estimated_elo,
            s.counts.iter().map(|(c, n)| format!("{} {}", c.label_tr(), n)).collect::<Vec<_>>()
        );
    }
    println!("{} düğüm, {} ms, {} kn/s", a.total_nodes, a.time_ms, a.total_nodes / a.time_ms.max(1));
}
