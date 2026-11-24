//! Aralıklı tekrar (SM-2 türevi). Repertuvar hatları ve bulmacalar için.

use serde::{Deserialize, Serialize};

const DAY_MS: i64 = 86_400_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Grade {
    /// Bilemedim
    Again,
    /// Zorlandım
    Hard,
    /// Bildim
    Good,
    /// Çok kolay
    Easy,
}

impl Grade {
    /// Bir drill'deki hata sayısından not
    pub fn from_mistakes(m: u32) -> Grade {
        match m {
            0 => Grade::Good,
            1 => Grade::Hard,
            _ => Grade::Again,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Card {
    pub id: String,
    pub ease: f64,
    pub interval_days: f64,
    pub reps: u32,
    pub lapses: u32,
    pub due_ms: i64,
    pub last_review_ms: Option<i64>,
}

impl Card {
    pub fn new(id: impl Into<String>, now_ms: i64) -> Self {
        Card { id: id.into(), ease: 2.5, interval_days: 0.0, reps: 0, lapses: 0, due_ms: now_ms, last_review_ms: None }
    }

    pub fn is_due(&self, now_ms: i64) -> bool {
        self.due_ms <= now_ms
    }

    pub fn review(&mut self, grade: Grade, now_ms: i64) {
        match grade {
            Grade::Again => {
                self.lapses += 1;
                self.reps = 0;
                self.ease = (self.ease - 0.2).max(1.3);
                // 10 dakika sonra tekrar
                self.interval_days = 0.0;
                self.due_ms = now_ms + 10 * 60_000;
            }
            g => {
                self.reps += 1;
                let q = match g {
                    Grade::Hard => 3.0,
                    Grade::Good => 4.0,
                    _ => 5.0,
                };
                self.ease = (self.ease + 0.1 - (5.0 - q) * (0.08 + (5.0 - q) * 0.02)).max(1.3);
                self.interval_days = match self.reps {
                    1 => if g == Grade::Easy { 3.0 } else { 1.0 },
                    2 => if g == Grade::Hard { 3.0 } else { 6.0 },
                    _ => {
                        let mult = match g {
                            Grade::Hard => 1.2,
                            Grade::Easy => self.ease * 1.3,
                            _ => self.ease,
                        };
                        (self.interval_days * mult).max(self.interval_days + 1.0)
                    }
                };
                self.due_ms = now_ms + (self.interval_days * DAY_MS as f64) as i64;
            }
        }
        self.last_review_ms = Some(now_ms);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn intervals_grow() {
        let mut c = Card::new("x", 0);
        c.review(Grade::Good, 0);
        assert_eq!(c.interval_days, 1.0);
        c.review(Grade::Good, c.due_ms);
        assert_eq!(c.interval_days, 6.0);
        c.review(Grade::Good, c.due_ms);
        assert!(c.interval_days > 14.0);
        c.review(Grade::Again, c.due_ms);
        assert_eq!(c.reps, 0);
        assert_eq!(c.lapses, 1);
    }
}
