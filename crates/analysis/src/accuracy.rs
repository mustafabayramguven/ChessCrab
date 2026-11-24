//! lichess'in açık kaynak doğruluk formülleri (lila: AccuracyPercent / WinPercent).

/// Santipiyon → kazanma yüzdesi (0..100), değerlendirilen taraf açısından.
pub fn win_percent(cp: i32) -> f64 {
    let cp = cp.clamp(-1000, 1000) as f64;
    50.0 + 50.0 * (2.0 / (1.0 + (-0.003_682_08 * cp).exp()) - 1.0)
}

/// Hamle doğruluğu (0..100). Kazanma yüzdeleri hamleyi yapan açısından.
pub fn move_accuracy(win_before: f64, win_after: f64) -> f64 {
    if win_after >= win_before {
        return 100.0;
    }
    let diff = win_before - win_after;
    let raw = 103.166_810_071_164_9 * (-0.043_544_153_867_539_51 * diff).exp() - 3.166_924_740_191_411;
    // +1: analiz belirsizliği bonusu (lichess ile aynı)
    (raw + 1.0).clamp(0.0, 100.0)
}

fn std_dev(xs: &[f64]) -> f64 {
    let n = xs.len() as f64;
    if n == 0.0 {
        return 0.0;
    }
    let mean = xs.iter().sum::<f64>() / n;
    (xs.iter().map(|x| (x - mean).powi(2)).sum::<f64>() / n).sqrt()
}

fn weighted_mean(pairs: &[(f64, f64)]) -> Option<f64> {
    let wsum: f64 = pairs.iter().map(|(_, w)| w).sum();
    if wsum <= 0.0 {
        return None;
    }
    Some(pairs.iter().map(|(v, w)| v * w).sum::<f64>() / wsum)
}

fn harmonic_mean(xs: &[f64]) -> Option<f64> {
    if xs.is_empty() {
        return None;
    }
    let s: f64 = xs.iter().map(|x| 1.0 / x.max(1.0)).sum();
    Some(xs.len() as f64 / s)
}

/// Oyun doğruluğu (beyaz, siyah). `white_cps`: her pozisyonun (başlangıç dahil)
/// BEYAZ açısından değerlendirmesi. `white_first`: ilk hamleyi beyaz mı yaptı.
///
/// lichess: oynaklığa göre ağırlıklı ortalama ile harmonik ortalamanın ortalaması.
pub fn game_accuracy(white_cps: &[i32], white_first: bool) -> (Option<f64>, Option<f64>) {
    if white_cps.len() < 2 {
        return (None, None);
    }
    let wins: Vec<f64> = white_cps.iter().map(|&c| win_percent(c)).collect();
    let n_moves = wins.len() - 1;
    let window = (n_moves / 10).clamp(2, 8).min(wins.len());
    let mut windows: Vec<&[f64]> = Vec::with_capacity(n_moves);
    for _ in 0..window.saturating_sub(2) {
        windows.push(&wins[..window]);
    }
    for w in wins.windows(window) {
        windows.push(w);
    }
    let weights: Vec<f64> = windows.iter().map(|w| std_dev(w).clamp(0.5, 12.0)).collect();

    let mut white: Vec<(f64, f64)> = Vec::new();
    let mut black: Vec<(f64, f64)> = Vec::new();
    for i in 0..n_moves {
        let white_moved = (i % 2 == 0) == white_first;
        let (before, after) = if white_moved {
            (wins[i], wins[i + 1])
        } else {
            (100.0 - wins[i], 100.0 - wins[i + 1])
        };
        let acc = move_accuracy(before, after);
        let w = weights.get(i).copied().unwrap_or(1.0);
        if white_moved {
            white.push((acc, w));
        } else {
            black.push((acc, w));
        }
    }
    let side = |v: &[(f64, f64)]| -> Option<f64> {
        let wm = weighted_mean(v)?;
        let accs: Vec<f64> = v.iter().map(|(a, _)| *a).collect();
        let hm = harmonic_mean(&accs)?;
        Some((wm + hm) / 2.0)
    };
    (side(&white), side(&black))
}

/// ACPL'den kaba Elo tahmini (sadece eğlence amaçlı; tek oyun çok gürültülü).
pub fn estimate_elo(acpl: f64) -> u32 {
    (3100.0 * (-0.011 * acpl).exp()).clamp(100.0, 3200.0) as u32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn win_percent_shape() {
        assert!((win_percent(0) - 50.0).abs() < 1e-9);
        assert!(win_percent(300) > 75.0 && win_percent(300) < 76.0);
        assert!((win_percent(5000) - win_percent(1000)).abs() < 1e-9);
        assert!((win_percent(-200) + win_percent(200) - 100.0).abs() < 1e-9);
    }

    #[test]
    fn move_accuracy_shape() {
        assert_eq!(move_accuracy(60.0, 60.0), 100.0);
        assert!(move_accuracy(60.0, 55.0) > 80.0);
        assert!(move_accuracy(80.0, 20.0) < 10.0);
    }

    #[test]
    fn perfect_game_is_100() {
        let cps = vec![20, 25, 22, 30, 28, 30, 25];
        let (w, b) = game_accuracy(&cps, true);
        assert!(w.unwrap() > 97.0, "{w:?}");
        assert!(b.unwrap() > 97.0, "{b:?}");
    }

    #[test]
    fn blunder_drops_accuracy() {
        // Beyaz 5. hamlede veziri bırakıyor
        let cps = vec![20, 25, 22, 30, 28, -900, -880, -900, -870];
        let (w, b) = game_accuracy(&cps, true);
        assert!(w.unwrap() < 70.0, "{w:?}");
        assert!(b.unwrap() > 90.0, "{b:?}");
    }
}
