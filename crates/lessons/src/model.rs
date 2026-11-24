use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Orientation {
    #[default]
    White,
    Black,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Level {
    #[default]
    Baslangic,
    Orta,
    Ileri,
}

impl Level {
    pub fn label_tr(self) -> &'static str {
        match self {
            Level::Baslangic => "Başlangıç",
            Level::Orta => "Orta",
            Level::Ileri => "İleri",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Course {
    pub id: String,
    pub title: String,
    pub description: String,
    #[serde(default)]
    pub level: Level,
    #[serde(default)]
    pub order: u32,
    pub chapters: Vec<Chapter>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chapter {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub description: String,
    pub lessons: Vec<String>,
    /// Bu bölümün çalışılacak repertuvarı (varsa)
    #[serde(default)]
    pub repertoire: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Quiz {
    pub question: String,
    pub options: Vec<String>,
    /// Doğru şıkkın indeksi (0'dan)
    pub answer: usize,
    #[serde(default)]
    pub explain: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct Step {
    /// Açıklama (hamle beklenen adımda görev metni)
    #[serde(default)]
    pub text: String,
    /// Tahtayı bu FEN'e sıfırla
    #[serde(default)]
    pub fen: Option<String>,
    /// Adım gösterilmeden önce otomatik oynanacak hamleler (SAN)
    #[serde(default)]
    pub play: Option<String>,
    /// Kullanıcının oynaması beklenen ana hamle (SAN)
    #[serde(default)]
    pub expect: Option<String>,
    /// Kabul edilen ama ana hatta dönülen alternatifler
    #[serde(default)]
    pub also: Vec<String>,
    /// Doğru hamleden sonra rakibin otomatik cevabı
    #[serde(default)]
    pub reply: Option<String>,
    #[serde(default)]
    pub hint: Option<String>,
    /// Doğru hamle sonrası mesaj
    #[serde(default)]
    pub success: Option<String>,
    /// Yanlış hamle mesajı
    #[serde(default)]
    pub wrong: Option<String>,
    /// Oklar: "e2e4" biçiminde
    #[serde(default)]
    pub arrows: Vec<String>,
    /// Vurgulanan kareler: "e4"
    #[serde(default)]
    pub highlights: Vec<String>,
    #[serde(default)]
    pub quiz: Option<Quiz>,
    /// Bulmaca: testte motorla doğrulanır (beklenen hamle en iyiye yakın olmalı)
    #[serde(default)]
    pub puzzle: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Lesson {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub summary: String,
    #[serde(default)]
    pub level: Level,
    #[serde(default)]
    pub orientation: Orientation,
    /// Başlangıç FEN'i (yoksa normal başlangıç)
    #[serde(default)]
    pub fen: Option<String>,
    /// Başlangıçta sessizce oynanacak hamleler
    #[serde(default)]
    pub setup: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
    pub steps: Vec<Step>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepertoireLine {
    pub name: String,
    pub moves: String,
    #[serde(default)]
    pub note: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Repertoire {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub description: String,
    pub side: Orientation,
    pub lines: Vec<RepertoireLine>,
}
