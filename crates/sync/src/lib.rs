//! ChessCrab senkronizasyonu.
//!
//! Model: her cihaz kayıtları (oyunlar, ders ilerlemesi, SRS kartları, ayarlar)
//! yerelde tutar ve offline çalışır. Her değişiklik bir Hibrit Mantıksal Saat (HLC)
//! damgası taşır. Senkronizasyonda istemci kirli kayıtları gönderir, sunucu
//! "son yazan kazanır" kuralıyla birleştirir ve imleçten (cursor) sonraki tüm
//! değişiklikleri geri yollar. Silme = mezar taşı (tombstone).

#[cfg(feature = "sqlite")]
pub mod store;

use std::cmp::Ordering;

use serde::{Deserialize, Serialize};

pub mod collections {
    pub const GAMES: &str = "games";
    pub const LESSON_PROGRESS: &str = "lesson_progress";
    pub const SRS_CARDS: &str = "srs_cards";
    pub const SETTINGS: &str = "settings";
    pub const ALL: [&str; 4] = [GAMES, LESSON_PROGRESS, SRS_CARDS, SETTINGS];
}

/// Hibrit Mantıksal Saat damgası. Sıralama: (wall_ms, counter, node).
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Hlc {
    pub wall_ms: i64,
    pub counter: u32,
    pub node: String,
}

impl PartialOrd for Hlc {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}
impl Ord for Hlc {
    fn cmp(&self, other: &Self) -> Ordering {
        (self.wall_ms, self.counter, &self.node).cmp(&(other.wall_ms, other.counter, &other.node))
    }
}

impl Hlc {
    pub fn zero(node: &str) -> Self {
        Hlc { wall_ms: 0, counter: 0, node: node.to_string() }
    }

    /// Yerel olay: yeni damga üret.
    pub fn tick(&self, now_ms: i64) -> Hlc {
        if now_ms > self.wall_ms {
            Hlc { wall_ms: now_ms, counter: 0, node: self.node.clone() }
        } else {
            Hlc { wall_ms: self.wall_ms, counter: self.counter + 1, node: self.node.clone() }
        }
    }

    /// Uzak damga alındığında saati ilerlet.
    pub fn receive(&self, remote: &Hlc, now_ms: i64) -> Hlc {
        let wall = now_ms.max(self.wall_ms).max(remote.wall_ms);
        let counter = if wall == self.wall_ms && wall == remote.wall_ms {
            self.counter.max(remote.counter) + 1
        } else if wall == self.wall_ms {
            self.counter + 1
        } else if wall == remote.wall_ms {
            remote.counter + 1
        } else {
            0
        };
        Hlc { wall_ms: wall, counter, node: self.node.clone() }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Record {
    pub collection: String,
    pub id: String,
    /// Kayıt içeriği (JSON). Silinmişse null.
    pub data: serde_json::Value,
    #[serde(default)]
    pub deleted: bool,
    pub hlc: Hlc,
}

impl Record {
    /// LWW: `other` bu kaydın yerini almalı mı?
    pub fn superseded_by(&self, other: &Record) -> bool {
        other.hlc > self.hlc
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SyncRequest {
    /// Sunucunun son verdiği imleç (ilk senkronizasyonda 0)
    pub cursor: i64,
    pub changes: Vec<Record>,
    #[serde(default)]
    pub device: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SyncResponse {
    pub cursor: i64,
    /// Sunucuda kabul edilen (daha yeni olan) değişiklik sayısı
    pub accepted: usize,
    /// İmleçten sonra sunucuda değişen kayıtlar
    pub changes: Vec<Record>,
}

/// Senkronize edilen veri türleri (JSON olarak saklanır).
pub mod types {
    use serde::{Deserialize, Serialize};

    #[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
    pub struct GameRecord {
        pub id: String,
        pub pgn: String,
        #[serde(default)]
        pub white: String,
        #[serde(default)]
        pub black: String,
        #[serde(default)]
        pub result: String,
        /// "engine" | "local" | "import"
        #[serde(default)]
        pub mode: String,
        #[serde(default)]
        pub engine_level: Option<u8>,
        #[serde(default)]
        pub created_ms: i64,
        #[serde(default)]
        pub opening: Option<String>,
        #[serde(default)]
        pub white_accuracy: Option<f64>,
        #[serde(default)]
        pub black_accuracy: Option<f64>,
    }

    #[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
    pub struct LessonProgress {
        pub lesson_id: String,
        pub completed: bool,
        pub stars: u8,
        pub best_mistakes: u32,
        pub completed_ms: Option<i64>,
    }
}

pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Bir kayıt dizisini LWW ile birleştir (aynı anahtar için en yeni kazanır).
pub fn merge_lww(a: Option<Record>, b: Record) -> Record {
    match a {
        Some(x) if !x.superseded_by(&b) => x,
        _ => b,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hlc_monotonic() {
        let h = Hlc::zero("a");
        let h1 = h.tick(100);
        let h2 = h1.tick(100);
        let h3 = h2.tick(50); // saat geri gitse bile
        assert!(h1 < h2 && h2 < h3);
        let remote = Hlc { wall_ms: 500, counter: 7, node: "b".into() };
        let h4 = h3.receive(&remote, 200);
        assert!(h4 > remote);
        assert_eq!(h4.node, "a");
    }

    #[test]
    fn lww_tiebreak_by_node() {
        let a = Hlc { wall_ms: 1, counter: 0, node: "a".into() };
        let b = Hlc { wall_ms: 1, counter: 0, node: "b".into() };
        assert!(b > a);
    }
}
