//! Zorluk seviyeleri: Elo hedefine göre derinlik/düğüm sınırı + MultiPV içinden
//! gürültülü seçim. Zayıf seviyeler insan gibi hata yapar, güçlü seviyeler tam güç.

use serde::{Deserialize, Serialize};

/// Basit xorshift; harici rand bağımlılığı istemiyoruz.
#[derive(Clone, Debug)]
pub struct Rng(u64);

impl Rng {
    pub fn new(seed: u64) -> Self {
        Rng(seed | 1)
    }
    pub fn from_time() -> Self {
        let t = web_time::SystemTime::now()
            .duration_since(web_time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0x9E37_79B9_7F4A_7C15);
        Rng::new(t ^ 0x9E37_79B9_7F4A_7C15)
    }
    pub fn next_u64(&mut self) -> u64 {
        let mut x = self.0;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.0 = x;
        x
    }
    /// [0, 1)
    pub fn f64(&mut self) -> f64 {
        (self.next_u64() >> 11) as f64 / (1u64 << 53) as f64
    }
    pub fn below(&mut self, n: usize) -> usize {
        (self.next_u64() % n.max(1) as u64) as usize
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct Level {
    /// Hedef güç (yaklaşık Elo). 3000+ = tam güç.
    pub elo: u32,
}

impl Level {
    /// lichess'teki gibi 1-8 seviyeleri
    pub const PRESETS: [(u8, u32, &'static str); 8] = [
        (1, 400, "Yeni başlayan"),
        (2, 700, "Acemi"),
        (3, 1000, "Kulüp oyuncusu adayı"),
        (4, 1300, "Orta"),
        (5, 1600, "İyi"),
        (6, 1900, "Güçlü"),
        (7, 2300, "Usta"),
        (8, 3000, "Tam güç"),
    ];

    pub fn preset(n: u8) -> Level {
        let n = n.clamp(1, 8) as usize;
        Level { elo: Self::PRESETS[n - 1].1 }
    }

    pub fn full() -> Level {
        Level { elo: 3000 }
    }

    pub fn is_full(&self) -> bool {
        self.elo >= 3000
    }

    /// 0.0 (tam güç) .. 1.0 (en zayıf)
    fn weakness(&self) -> f64 {
        ((3000.0 - self.elo.clamp(300, 3000) as f64) / 2700.0).clamp(0.0, 1.0)
    }

    /// Arama düğüm sınırı
    pub fn node_limit(&self) -> Option<u64> {
        if self.is_full() {
            return None;
        }
        let w = self.weakness();
        // 400 Elo ≈ 300 düğüm, 2300 ≈ ~200k düğüm
        Some((300.0 * (1.0 / (w + 0.02)).powf(2.0)).min(2_000_000.0) as u64)
    }

    pub fn depth_limit(&self) -> Option<u32> {
        if self.is_full() {
            return None;
        }
        Some(match self.elo {
            0..=500 => 1,
            501..=800 => 2,
            801..=1100 => 3,
            1101..=1400 => 5,
            1401..=1700 => 7,
            1701..=2000 => 9,
            _ => 14,
        })
    }

    pub fn multipv(&self) -> usize {
        if self.is_full() { 1 } else { 4 }
    }

    /// Aday hamleler arasından seç. `scores` santipiyon, en iyi ilk sırada.
    /// Stockfish "Skill Level" mantığına benzer: zayıf seviyede gürültü daha büyük.
    pub fn pick(&self, scores: &[i32], rng: &mut Rng) -> usize {
        if scores.len() <= 1 || self.is_full() {
            return 0;
        }
        let w = self.weakness();
        let top = scores[0];
        let spread = (scores[0] - scores[scores.len() - 1]).clamp(0, 300) as f64;
        let noise_amp = 40.0 + 260.0 * w * w + spread * w;
        let mut best_i = 0;
        let mut best_v = f64::MIN;
        for (i, &s) in scores.iter().enumerate() {
            // Kazanan bir alımı/matı tamamen bırakmayı nadir yap
            let loss = (top - s).max(0) as f64;
            if loss > 800.0 && rng.f64() > w * 0.3 {
                continue;
            }
            let v = s as f64 + noise_amp * rng.f64();
            if v > best_v {
                best_v = v;
                best_i = i;
            }
        }
        best_i
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn full_strength_picks_best() {
        let mut r = Rng::new(1);
        assert_eq!(Level::full().pick(&[50, 40, -300], &mut r), 0);
    }
    #[test]
    fn weak_level_varies() {
        let mut r = Rng::new(42);
        let lvl = Level::preset(1);
        let picks: std::collections::HashSet<_> = (0..200).map(|_| lvl.pick(&[30, 20, 10, 0], &mut r)).collect();
        assert!(picks.len() > 1);
        assert!(lvl.node_limit().unwrap() < Level::preset(5).node_limit().unwrap());
    }
}
