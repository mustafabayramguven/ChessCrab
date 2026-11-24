//! Ders içeriğinin bütünlüğü: her hamle yasal, her bulmaca motorca doğrulanmış.

use chesscrab_lessons::{Catalog, Drill, Grade, LessonSession, Mode, Verdict, audit::audit_lesson};

#[test]
fn catalog_is_valid() {
    let cat = Catalog::parse().expect("TOML ayrıştırılmalı");
    let errs = cat.validate();
    assert!(errs.is_empty(), "içerik hataları:\n{}", errs.join("\n"));
    assert!(cat.lessons.len() >= 45, "ders sayısı: {}", cat.lessons.len());
    assert!(cat.courses.len() >= 2);
}

#[test]
fn expected_moves_are_sound() {
    let cat = Catalog::parse().unwrap();
    let mut bad = Vec::new();
    let mut ids: Vec<_> = cat.lessons.keys().cloned().collect();
    ids.sort();
    for id in ids {
        for f in audit_lesson(&cat.lessons[&id], 8, 11) {
            let limit = if f.puzzle { 80 } else { 160 };
            if f.loss_cp > limit {
                bad.push(format!("{} #{}: {} kaybı {} (motor: {}) {}", f.lesson, f.step, f.expected, f.loss_cp, f.best, f.fen));
            }
        }
    }
    assert!(bad.is_empty(), "şüpheli hamleler:\n{}", bad.join("\n"));
}

#[test]
fn session_flow() {
    let cat = Catalog::get();
    let mut s = LessonSession::new(cat.lesson("prensip-merkez").unwrap().clone()).unwrap();
    assert_eq!(s.view().mode, Mode::Read);
    s.next();
    s.next();
    assert_eq!(s.view().mode, Mode::Move);
    // yasal ama yanlış
    let fb = s.submit_move("a4");
    assert_eq!(fb.verdict, Verdict::Wrong);
    assert_eq!(s.mistakes(), 1);
    // görev çözülmeden atlanamaz
    let before = s.index();
    s.next();
    assert_eq!(s.index(), before);
    // alternatif: d4 de kabul, ama ana hat e4 ile devam
    let fb = s.submit_move("d2d4");
    assert_eq!(fb.verdict, Verdict::AlsoGood);
    assert_eq!(fb.reply_san.as_deref(), Some("e5"));
    assert!(s.view().fen.contains("4p3/4P3"));
    assert_eq!(s.stars(), 2);
}

#[test]
fn repertoire_drill() {
    let cat = Catalog::get();
    let rep = cat.repertoire("siyah-caro").unwrap();
    let mut d = Drill::new(rep, 1).unwrap();
    // Siyah: rakibin ilk hamlesi (e4) otomatik oynandı
    assert_eq!(d.view().progress.0, 1);
    let fb = d.submit("e5");
    assert!(!fb.correct);
    assert_eq!(fb.expected_san.as_deref(), Some("c6"));
    let fb = d.submit("c6");
    assert!(fb.correct);
    while !d.is_done() {
        let line: Vec<&str> = rep.lines[1].moves.split_whitespace().collect();
        let idx = d.view().progress.0;
        d.submit(line[idx]);
    }
    assert_eq!(Grade::from_mistakes(d.mistakes()), Grade::Hard);
}
