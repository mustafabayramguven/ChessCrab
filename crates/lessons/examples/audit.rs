//! `cargo run --release -p chesscrab-lessons --features audit --example audit [filtre]`
use chesscrab_lessons::{Catalog, audit::audit_lesson};

fn main() {
    let filter = std::env::args().nth(1).unwrap_or_default();
    let cat = Catalog::parse().expect("içerik");
    for e in cat.validate() {
        println!("HATA: {e}");
    }
    let mut ids: Vec<_> = cat.lessons.keys().filter(|k| k.contains(&filter)).cloned().collect();
    ids.sort();
    for id in ids {
        for f in audit_lesson(&cat.lessons[&id], 9, 12) {
            let flag = if (f.puzzle && f.loss_cp > 60) || f.loss_cp > 150 { "!!" } else if f.loss_cp > 60 { " ?" } else { "  " };
            println!("{flag} {:<26} #{:<2} {:<7} kayıp {:>4}  (motor: {})  {}", f.lesson, f.step, f.expected, f.loss_cp, f.best, if flag == "  " { "" } else { &f.fen });
        }
    }
}
