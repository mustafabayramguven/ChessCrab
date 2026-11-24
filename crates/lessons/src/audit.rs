//! İçerik denetimi: derslerdeki her beklenen hamleyi motorla puanlar.
//! Testlerde ve `examples/audit.rs`'te kullanılır.

use chesscrab_core::{parse_fen, parse_move, san_of};
use chesscrab_engine::{Limits, Searcher};

use crate::{Lesson, LessonSession, Mode};

#[derive(Debug)]
pub struct Finding {
    pub lesson: String,
    pub step: usize,
    pub expected: String,
    pub best: String,
    pub loss_cp: i32,
    pub puzzle: bool,
    pub fen: String,
}

/// Beklenen hamle en iyiden ne kadar kötü? (sırası gelen açısından cp)
pub fn audit_lesson(lesson: &Lesson, depth: u32, puzzle_depth: u32) -> Vec<Finding> {
    let mut out = Vec::new();
    let Ok(mut s) = LessonSession::new(lesson.clone()) else { return out };
    let mut guard = 0;
    while !s.is_done() && guard < 1000 {
        guard += 1;
        let v = s.view();
        match v.mode {
            Mode::Read => s.next(),
            Mode::Quiz => {
                let a = lesson.steps[s.index()].quiz.as_ref().map(|q| q.answer).unwrap_or(0);
                s.answer(a);
                s.next();
            }
            Mode::Move => {
                let step = &lesson.steps[s.index()];
                let exp = step.expect.clone().unwrap_or_default();
                let pos = parse_fen(&v.fen).expect("fen");
                if let Ok(m) = parse_move(&pos, &exp) {
                    let d = if step.puzzle { puzzle_depth } else { depth };
                    let mut a = Searcher::new(16);
                    let best = a.search(&pos, &[], &Limits::depth(d), |_| {});
                    let mut b = Searcher::new(16);
                    let mine = b.search(&pos, &[], &Limits { depth: Some(d), search_moves: vec![m], ..Default::default() }, |_| {});
                    let bc = best.score.to_cp().clamp(-3000, 3000);
                    let mc = mine.score.to_cp().clamp(-3000, 3000);
                    out.push(Finding {
                        lesson: lesson.id.clone(),
                        step: s.index(),
                        expected: exp.clone(),
                        best: best.best.map(|x| san_of(&pos, x)).unwrap_or_default(),
                        loss_cp: (bc - mc).max(0),
                        puzzle: step.puzzle,
                        fen: v.fen.clone(),
                    });
                }
                s.submit_move(&exp);
                s.next();
            }
            Mode::Done => {}
        }
    }
    out
}
