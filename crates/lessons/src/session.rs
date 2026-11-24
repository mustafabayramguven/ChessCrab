//! Ders oturumu: adım adım ilerleyen durum makinesi. Android, web ve masaüstü aynı
//! mantığı kullanır; arayüz sadece `view()` çıktısını çizer.

use serde::Serialize;
use shakmaty::{Chess, Position};

use chesscrab_core::{START_FEN, parse_fen, parse_move, play_line, san_of, to_fen, uci_of};

use crate::model::{Lesson, Orientation, Step};

#[derive(Debug, thiserror::Error)]
pub enum LessonError {
    #[error("{lesson}: adım {step}: {msg}")]
    Invalid { lesson: String, step: usize, msg: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Mode {
    /// Okuma: "Devam" ile ilerle
    Read,
    /// Kullanıcı hamle yapmalı
    Move,
    /// Çoktan seçmeli soru
    Quiz,
    /// Ders bitti
    Done,
}

#[derive(Debug, Clone, Serialize)]
pub struct QuizView {
    pub question: String,
    pub options: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct StepView {
    pub lesson_id: String,
    pub index: usize,
    pub total: usize,
    pub mode: Mode,
    pub text: String,
    /// Doğru hamle / doğru cevap sonrası geri bildirim
    pub feedback: Option<String>,
    pub fen: String,
    pub orientation: Orientation,
    pub arrows: Vec<String>,
    pub highlights: Vec<String>,
    pub last_move: Option<String>,
    pub quiz: Option<QuizView>,
    pub has_hint: bool,
    /// Hamle modunda yasal hamleler (UCI), arayüzün sürükle-bırak için
    pub legal_moves: Vec<String>,
    pub mistakes: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Verdict {
    Correct,
    /// Bu da iyi ama ana hatla devam ediyoruz
    AlsoGood,
    Wrong,
    Illegal,
}

#[derive(Debug, Clone, Serialize)]
pub struct MoveFeedback {
    pub verdict: Verdict,
    pub message: String,
    pub played_san: Option<String>,
    pub reply_san: Option<String>,
}

pub struct LessonSession {
    lesson: Lesson,
    index: usize,
    pos: Chess,
    last_move: Option<String>,
    mode: Mode,
    feedback: Option<String>,
    mistakes: u32,
    hints_used: u32,
}

/// Dersin tüm adımlarını beklenen hamlelerle oynayarak doğrula.
pub fn validate(lesson: &Lesson) -> Result<(), LessonError> {
    let err = |step: usize, msg: String| LessonError::Invalid { lesson: lesson.id.clone(), step, msg };
    let mut s = LessonSession::new(lesson.clone()).map_err(|e| err(0, e.to_string()))?;
    let mut guard = 0;
    while s.mode != Mode::Done {
        guard += 1;
        if guard > 10_000 {
            return Err(err(s.index, "sonsuz döngü".into()));
        }
        let step = &lesson.steps[s.index];
        match s.mode {
            Mode::Read => s.next(),
            Mode::Move => {
                let exp = step.expect.clone().expect("hamle modunda expect var");
                for alt in &step.also {
                    parse_move(&s.pos, alt).map_err(|_| err(s.index, format!("alternatif {alt} yasal değil")))?;
                }
                let fb = s.submit_move(&exp);
                if fb.verdict != Verdict::Correct {
                    return Err(err(s.index, format!("beklenen {exp} kabul edilmedi: {}", fb.message)));
                }
                s.next();
            }
            Mode::Quiz => {
                let q = step.quiz.as_ref().expect("quiz");
                if q.answer >= q.options.len() {
                    return Err(err(s.index, "quiz cevabı şıklar dışında".into()));
                }
                if !s.answer(q.answer).0 {
                    return Err(err(s.index, "doğru cevap kabul edilmedi".into()));
                }
                s.next();
            }
            Mode::Done => {}
        }
    }
    Ok(())
}

impl LessonSession {
    pub fn new(lesson: Lesson) -> Result<Self, chesscrab_core::GameError> {
        let start = parse_fen(lesson.fen.as_deref().unwrap_or(START_FEN))?;
        let pos = match &lesson.setup {
            Some(line) => play_line(&start, line)?.0,
            None => start,
        };
        let mut s = LessonSession {
            lesson,
            index: 0,
            pos,
            last_move: None,
            mode: Mode::Read,
            feedback: None,
            mistakes: 0,
            hints_used: 0,
        };
        s.enter_step()?;
        Ok(s)
    }

    pub fn lesson(&self) -> &Lesson {
        &self.lesson
    }
    pub fn mistakes(&self) -> u32 {
        self.mistakes
    }
    pub fn hints_used(&self) -> u32 {
        self.hints_used
    }
    pub fn is_done(&self) -> bool {
        self.mode == Mode::Done
    }
    pub fn index(&self) -> usize {
        self.index
    }

    fn step(&self) -> Option<&Step> {
        self.lesson.steps.get(self.index)
    }

    fn enter_step(&mut self) -> Result<(), chesscrab_core::GameError> {
        self.feedback = None;
        let Some(step) = self.step().cloned() else {
            self.mode = Mode::Done;
            return Ok(());
        };
        if let Some(fen) = &step.fen {
            self.pos = parse_fen(fen)?;
            self.last_move = None;
        }
        if let Some(line) = &step.play {
            let (pos, moves) = play_line(&self.pos, line)?;
            self.last_move = moves.last().map(|m| uci_of(*m));
            self.pos = pos;
        }
        self.mode = if step.expect.is_some() {
            Mode::Move
        } else if step.quiz.is_some() {
            Mode::Quiz
        } else {
            Mode::Read
        };
        Ok(())
    }

    /// Sonraki adıma geç (Read modunda ya da geri bildirimden sonra).
    pub fn next(&mut self) {
        if self.mode == Mode::Done {
            return;
        }
        if matches!(self.mode, Mode::Move | Mode::Quiz) && self.feedback.is_none() {
            // Görev çözülmeden atlanamaz
            return;
        }
        self.index += 1;
        // İçerik testlerle doğrulandığından hata beklenmez; yine de dersi bitir.
        if self.enter_step().is_err() {
            self.mode = Mode::Done;
        }
    }

    pub fn hint(&mut self) -> Option<String> {
        let step = self.step()?;
        let h = step.hint.clone().or_else(|| {
            // Varsayılan ipucu: hangi taş oynanacak
            let exp = step.expect.as_ref()?;
            let m = parse_move(&self.pos, exp).ok()?;
            let name = match m.role() {
                shakmaty::Role::Pawn => "piyon",
                shakmaty::Role::Knight => "at",
                shakmaty::Role::Bishop => "fil",
                shakmaty::Role::Rook => "kale",
                shakmaty::Role::Queen => "vezir",
                shakmaty::Role::King => "şah",
            };
            Some(format!("Bir {name} hamlesi ara."))
        });
        if h.is_some() {
            self.hints_used += 1;
        }
        h
    }

    pub fn submit_move(&mut self, text: &str) -> MoveFeedback {
        let Some(step) = self.step().cloned() else {
            return MoveFeedback { verdict: Verdict::Illegal, message: "Ders bitti.".into(), played_san: None, reply_san: None };
        };
        if self.mode != Mode::Move || self.feedback.is_some() {
            return MoveFeedback { verdict: Verdict::Illegal, message: "Şu an hamle beklenmiyor.".into(), played_san: None, reply_san: None };
        }
        let Ok(m) = parse_move(&self.pos, text) else {
            return MoveFeedback { verdict: Verdict::Illegal, message: "Bu hamle yasal değil.".into(), played_san: None, reply_san: None };
        };
        let expected = step.expect.as_ref().and_then(|e| parse_move(&self.pos, e).ok());
        let played_san = san_of(&self.pos, m);
        let is_alt = step.also.iter().any(|a| parse_move(&self.pos, a).ok() == Some(m));

        if Some(m) != expected && !is_alt {
            self.mistakes += 1;
            let msg = step.wrong.clone().unwrap_or_else(|| "Bu değil, tekrar dene.".into());
            return MoveFeedback { verdict: Verdict::Wrong, message: msg, played_san: Some(played_san), reply_san: None };
        }

        let exp = expected.expect("doğrulanmış içerik");
        let verdict = if Some(m) == expected { Verdict::Correct } else { Verdict::AlsoGood };
        let exp_san = san_of(&self.pos, exp);
        self.pos.play_unchecked(exp);
        self.last_move = Some(uci_of(exp));
        let mut reply_san = None;
        if let Some(r) = &step.reply {
            if let Ok(rm) = parse_move(&self.pos, r) {
                reply_san = Some(san_of(&self.pos, rm));
                self.pos.play_unchecked(rm);
                self.last_move = Some(uci_of(rm));
            }
        }
        let mut msg = match verdict {
            Verdict::AlsoGood => format!("{played_san} de iyi, ama biz {exp_san} ile devam edelim."),
            _ => step.success.clone().unwrap_or_else(|| "Doğru!".into()),
        };
        if verdict == Verdict::AlsoGood {
            if let Some(s) = &step.success {
                msg.push(' ');
                msg.push_str(s);
            }
        }
        self.feedback = Some(msg.clone());
        MoveFeedback { verdict, message: msg, played_san: Some(played_san), reply_san }
    }

    /// Quiz cevabı. Dönüş: (doğru mu, açıklama)
    pub fn answer(&mut self, option: usize) -> (bool, String) {
        let Some(q) = self.step().and_then(|s| s.quiz.clone()) else {
            return (false, String::new());
        };
        if self.mode != Mode::Quiz {
            return (false, String::new());
        }
        if option == q.answer {
            let msg = if q.explain.is_empty() { "Doğru!".to_string() } else { format!("Doğru! {}", q.explain) };
            self.feedback = Some(msg.clone());
            (true, msg)
        } else {
            self.mistakes += 1;
            (false, "Yanlış, tekrar dene.".into())
        }
    }

    pub fn view(&self) -> StepView {
        let step = self.step();
        let legal_moves = if self.mode == Mode::Move && self.feedback.is_none() {
            self.pos.legal_moves().into_iter().map(uci_of).collect()
        } else {
            Vec::new()
        };
        StepView {
            lesson_id: self.lesson.id.clone(),
            index: self.index,
            total: self.lesson.steps.len(),
            mode: self.mode,
            text: step.map(|s| s.text.clone()).unwrap_or_else(|| "Ders tamamlandı!".into()),
            feedback: self.feedback.clone(),
            fen: to_fen(&self.pos),
            orientation: self.lesson.orientation,
            arrows: step.map(|s| s.arrows.clone()).unwrap_or_default(),
            highlights: step.map(|s| s.highlights.clone()).unwrap_or_default(),
            last_move: self.last_move.clone(),
            quiz: step.and_then(|s| s.quiz.as_ref()).map(|q| QuizView { question: q.question.clone(), options: q.options.clone() }),
            has_hint: step.map(|s| s.expect.is_some()).unwrap_or(false),
            legal_moves,
            mistakes: self.mistakes,
        }
    }

    /// 0-3 yıldız: hatasız ve ipuçsuz = 3
    pub fn stars(&self) -> u8 {
        let penalty = self.mistakes + self.hints_used;
        match penalty {
            0 => 3,
            1..=2 => 2,
            _ => 1,
        }
    }
}
