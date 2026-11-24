//! ChessCrab dersleri: açılıştan orta oyuna kapsamlı kurs, temel dersler,
//! repertuvarlar ve aralıklı tekrar.
//!
//! İçerik `content/` altında TOML dosyaları olarak durur ve derleme sırasında
//! ikiliye gömülür (offline). Her hamle testlerde yasallık için, bulmacalar ise
//! CrabFish ile doğruluk için kontrol edilir.

pub mod model;
pub mod repertoire;
pub mod session;
pub mod srs;

use std::collections::HashMap;
use std::sync::OnceLock;

use serde::Serialize;

pub use model::{Chapter, Course, Lesson, Level, Orientation, Quiz, Repertoire, RepertoireLine, Step};
pub use repertoire::{Drill, DrillFeedback, DrillView};
pub use session::{LessonSession, MoveFeedback, Mode, StepView, Verdict};
pub use srs::{Card, Grade};

mod content {
    include!(concat!(env!("OUT_DIR"), "/content_files.rs"));
}

#[derive(Debug, Default)]
pub struct Catalog {
    pub courses: Vec<Course>,
    pub lessons: HashMap<String, Lesson>,
    pub repertoires: Vec<Repertoire>,
}

/// Derslerden çıkarılan, motorla doğrulanmış bulmaca.
#[derive(Debug, Clone, Serialize)]
pub struct Puzzle {
    pub id: String,
    pub lesson_id: String,
    pub lesson_title: String,
    pub prompt: String,
    pub fen: String,
    /// Çözüm: sırayla kullanıcı hamlesi, rakip cevabı, kullanıcı hamlesi... (UCI)
    pub moves: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct LessonSummary {
    pub id: String,
    pub title: String,
    pub summary: String,
    pub level: Level,
    pub steps: usize,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChapterSummary {
    pub id: String,
    pub title: String,
    pub description: String,
    pub repertoire: Option<String>,
    pub lessons: Vec<LessonSummary>,
}

#[derive(Debug, Clone, Serialize)]
pub struct CourseSummary {
    pub id: String,
    pub title: String,
    pub description: String,
    pub level: Level,
    pub lesson_count: usize,
    pub chapters: Vec<ChapterSummary>,
}

impl Catalog {
    /// Gömülü içeriği yükle (ilk çağrıda ayrıştırılır).
    pub fn get() -> &'static Catalog {
        static CAT: OnceLock<Catalog> = OnceLock::new();
        CAT.get_or_init(|| Catalog::parse().unwrap_or_else(|e| panic!("ders içeriği bozuk: {e}")))
    }

    pub fn parse() -> Result<Catalog, String> {
        let mut cat = Catalog::default();
        for (path, text) in content::FILES {
            if path.starts_with("courses/") {
                cat.courses.push(toml::from_str(text).map_err(|e| format!("{path}: {e}"))?);
            } else if path.starts_with("lessons/") {
                let l: Lesson = toml::from_str(text).map_err(|e| format!("{path}: {e}"))?;
                if cat.lessons.insert(l.id.clone(), l).is_some() {
                    return Err(format!("{path}: tekrar eden ders kimliği"));
                }
            } else if path.starts_with("repertoires/") {
                cat.repertoires.push(toml::from_str(text).map_err(|e| format!("{path}: {e}"))?);
            }
        }
        cat.courses.sort_by_key(|c| c.order);
        Ok(cat)
    }

    pub fn lesson(&self, id: &str) -> Option<&Lesson> {
        self.lessons.get(id)
    }

    pub fn course(&self, id: &str) -> Option<&Course> {
        self.courses.iter().find(|c| c.id == id)
    }

    pub fn repertoire(&self, id: &str) -> Option<&Repertoire> {
        self.repertoires.iter().find(|r| r.id == id)
    }

    pub fn course_summaries(&self) -> Vec<CourseSummary> {
        self.courses
            .iter()
            .map(|c| {
                let chapters: Vec<ChapterSummary> = c
                    .chapters
                    .iter()
                    .map(|ch| ChapterSummary {
                        id: ch.id.clone(),
                        title: ch.title.clone(),
                        description: ch.description.clone(),
                        repertoire: ch.repertoire.clone(),
                        lessons: ch
                            .lessons
                            .iter()
                            .filter_map(|id| self.lessons.get(id))
                            .map(|l| LessonSummary {
                                id: l.id.clone(),
                                title: l.title.clone(),
                                summary: l.summary.clone(),
                                level: l.level,
                                steps: l.steps.len(),
                            })
                            .collect(),
                    })
                    .collect();
                CourseSummary {
                    id: c.id.clone(),
                    title: c.title.clone(),
                    description: c.description.clone(),
                    level: c.level,
                    lesson_count: chapters.iter().map(|ch| ch.lessons.len()).sum(),
                    chapters,
                }
            })
            .collect()
    }

    /// `puzzle = true` işaretli ders adımlarından bulmacalar.
    pub fn puzzles(&self) -> Vec<Puzzle> {
        use chesscrab_core::{parse_fen, parse_move, uci_of};
        use shakmaty::Position;
        let mut ids: Vec<&String> = self.lessons.keys().collect();
        ids.sort();
        let mut out = Vec::new();
        for id in ids {
            let lesson = &self.lessons[id];
            let Ok(mut s) = LessonSession::new(lesson.clone()) else { continue };
            let mut cur: Option<Puzzle> = None;
            let mut guard = 0;
            while !s.is_done() && guard < 1000 {
                guard += 1;
                let v = s.view();
                let step = &lesson.steps[s.index()];
                let continues = step.fen.is_none() && step.play.is_none() && step.expect.is_some();
                if (!(v.mode == Mode::Move && continues) || step.puzzle)
                    && let Some(p) = cur.take() {
                        out.push(p);
                    }
                match v.mode {
                    Mode::Move => {
                        let exp = step.expect.clone().unwrap_or_default();
                        if step.puzzle && cur.is_none() {
                            cur = Some(Puzzle {
                                id: format!("{}#{}", lesson.id, s.index()),
                                lesson_id: lesson.id.clone(),
                                lesson_title: lesson.title.clone(),
                                prompt: step.text.clone(),
                                fen: v.fen.clone(),
                                moves: Vec::new(),
                            });
                        }
                        if let Some(p) = cur.as_mut()
                            && let Ok(mut pos) = parse_fen(&v.fen)
                                && let Ok(m) = parse_move(&pos, &exp) {
                                    p.moves.push(uci_of(m));
                                    pos.play_unchecked(m);
                                    if let Some(r) = &step.reply
                                        && let Ok(rm) = parse_move(&pos, r) {
                                            p.moves.push(uci_of(rm));
                                        }
                                }
                        s.submit_move(&exp);
                        s.next();
                    }
                    Mode::Quiz => {
                        let a = step.quiz.as_ref().map(|q| q.answer).unwrap_or(0);
                        s.answer(a);
                        s.next();
                    }
                    Mode::Read => s.next(),
                    Mode::Done => {}
                }
            }
            if let Some(p) = cur.take() {
                out.push(p);
            }
        }
        // Sonu rakip hamlesiyle biten çözümleri kırp (son hamle kullanıcının olmalı)
        for p in out.iter_mut() {
            if p.moves.len() % 2 == 0 {
                p.moves.pop();
            }
        }
        out.retain(|p| !p.moves.is_empty());
        out
    }

    /// Motorun kitabına eklenebilecek tüm repertuvar hatları.
    pub fn book_lines(&self) -> Vec<&str> {
        self.repertoires.iter().flat_map(|r| r.lines.iter().map(|l| l.moves.as_str())).collect()
    }

    /// Bütün içeriği doğrula: dersler oynanabilir, kurslar var olan derslere işaret
    /// ediyor, repertuvarlar tutarlı.
    pub fn validate(&self) -> Vec<String> {
        let mut errs = Vec::new();
        for l in self.lessons.values() {
            if let Err(e) = session::validate(l) {
                errs.push(e.to_string());
            }
        }
        for c in &self.courses {
            for ch in &c.chapters {
                for id in &ch.lessons {
                    if !self.lessons.contains_key(id) {
                        errs.push(format!("kurs {} / {}: ders yok: {id}", c.id, ch.id));
                    }
                }
                if let Some(r) = &ch.repertoire
                    && self.repertoire(r).is_none() {
                        errs.push(format!("kurs {} / {}: repertuvar yok: {r}", c.id, ch.id));
                    }
            }
        }
        for r in &self.repertoires {
            if let Err(e) = r.validate() {
                errs.push(e);
            }
        }
        // Her ders bir kursta olmalı
        for id in self.lessons.keys() {
            let used = self.courses.iter().any(|c| c.chapters.iter().any(|ch| ch.lessons.contains(id)));
            if !used {
                errs.push(format!("ders hiçbir kursta değil: {id}"));
            }
        }
        errs
    }
}

#[cfg(feature = "audit")]
pub mod audit;
