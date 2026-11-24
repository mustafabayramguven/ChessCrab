//! Satranç saati. Zamanı dışarıdan (UI) besleriz; çekirdek sistem saatine bağlı değil.

use serde::{Deserialize, Serialize};

use crate::game::Side;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct TimeControl {
    pub base_ms: u64,
    pub increment_ms: u64,
}

impl TimeControl {
    pub const fn new(base_min: u64, inc_sec: u64) -> Self {
        Self { base_ms: base_min * 60_000, increment_ms: inc_sec * 1000 }
    }

    /// lichess tarzı kategori: tahmini süre = base + 40 * inc
    pub fn category(&self) -> &'static str {
        let est = self.base_ms / 1000 + 40 * self.increment_ms / 1000;
        match est {
            0..=29 => "ultrabullet",
            30..=179 => "bullet",
            180..=479 => "blitz",
            480..=1499 => "rapid",
            _ => "classical",
        }
    }

    pub fn label(&self) -> String {
        let base = self.base_ms as f64 / 60_000.0;
        let base = if base.fract() == 0.0 { format!("{}", base as u64) } else { format!("{base}") };
        format!("{}+{}", base, self.increment_ms / 1000)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Clock {
    pub control: TimeControl,
    pub white_ms: u64,
    pub black_ms: u64,
}

impl Clock {
    pub fn new(control: TimeControl) -> Self {
        Self { control, white_ms: control.base_ms, black_ms: control.base_ms }
    }

    pub fn remaining(&self, side: Side) -> u64 {
        match side {
            Side::White => self.white_ms,
            Side::Black => self.black_ms,
        }
    }

    /// `side` hamlesini `elapsed_ms` içinde yaptı. Süre bittiyse `false` döner.
    pub fn on_move(&mut self, side: Side, elapsed_ms: u64) -> bool {
        let inc = self.control.increment_ms;
        let t = match side {
            Side::White => &mut self.white_ms,
            Side::Black => &mut self.black_ms,
        };
        if elapsed_ms >= *t {
            *t = 0;
            return false;
        }
        *t = *t - elapsed_ms + inc;
        true
    }

    /// Hamle yapılmadan geçen süreyle bayrak düştü mü?
    pub fn flagged(&self, side: Side, elapsed_ms: u64) -> bool {
        elapsed_ms >= self.remaining(side)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn clock_increment() {
        let mut c = Clock::new(TimeControl::new(3, 2));
        assert_eq!(c.control.category(), "blitz");
        assert!(c.on_move(Side::White, 5_000));
        assert_eq!(c.white_ms, 180_000 - 5_000 + 2_000);
        assert!(!c.on_move(Side::Black, 200_000));
        assert_eq!(TimeControl::new(1, 0).category(), "bullet");
        assert_eq!(TimeControl::new(10, 0).category(), "rapid");
    }
}
