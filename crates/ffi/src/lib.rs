//! ChessCrab FFI: Android (Kotlin) ve ileride iOS (Swift) için.
//!
//! Karmaşık veri yapıları JSON dizesi olarak geçer (Kotlin tarafında org.json ile
//! okunur); böylece FFI yüzeyi küçük ve sağlam kalır. Tüm ağır işler (motor, analiz)
//! çağıran iş parçacığını bloklar: Kotlin'de `Dispatchers.Default` üzerinden çağır.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use chesscrab_analysis::{AnalysisConfig, analyse_game};
use chesscrab_core::{Game, GameState, Termination, pgn};
use chesscrab_engine::{Engine, Level, Limits};
use chesscrab_lessons::{Card, Catalog, Drill, Grade, LessonSession};
use chesscrab_sync::store::LocalStore;
use chesscrab_sync::{SyncRequest, SyncResponse};
use shakmaty::Position;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CrabError {
    #[error("{0}")]
    Failed(String),
}

fn fail<E: std::fmt::Display>(e: E) -> CrabError {
    CrabError::Failed(e.to_string())
}

fn to_json<T: serde::Serialize>(v: &T) -> String {
    serde_json::to_string(v).unwrap_or_else(|_| "null".into())
}

// ---------------- genel ----------------

#[uniffi::export]
pub fn crab_version() -> String {
    format!("ChessCrab {} · {} {}", env!("CARGO_PKG_VERSION"), chesscrab_engine::NAME, chesscrab_engine::VERSION)
}

#[uniffi::export]
pub fn courses_json() -> String {
    to_json(&Catalog::get().course_summaries())
}

#[uniffi::export]
pub fn repertoires_json() -> String {
    let reps: Vec<serde_json::Value> = Catalog::get()
        .repertoires
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.id, "title": r.title, "description": r.description, "side": r.side,
                "lines": r.lines.iter().enumerate().map(|(i, l)| serde_json::json!({
                    "index": i, "name": l.name, "moves": l.moves, "card_id": r.card_id(i)
                })).collect::<Vec<_>>()
            })
        })
        .collect();
    to_json(&reps)
}

#[uniffi::export]
pub fn engine_levels_json() -> String {
    let v: Vec<serde_json::Value> = Level::PRESETS
        .iter()
        .map(|(n, elo, name)| serde_json::json!({"level": n, "elo": elo, "name": name}))
        .collect();
    to_json(&v)
}

/// SRS kartını değerlendir. `card_json` yoksa yeni kart. grade: again|hard|good|easy
#[uniffi::export]
pub fn srs_review_json(card_json: Option<String>, card_id: String, grade: String, now_ms: i64) -> String {
    let mut card: Card = card_json
        .and_then(|c| serde_json::from_str(&c).ok())
        .unwrap_or_else(|| Card::new(card_id, now_ms));
    let g = match grade.as_str() {
        "again" => Grade::Again,
        "hard" => Grade::Hard,
        "easy" => Grade::Easy,
        _ => Grade::Good,
    };
    card.review(g, now_ms);
    to_json(&card)
}

// ---------------- oyun ----------------

#[derive(uniffi::Object)]
pub struct ChessGame {
    inner: Mutex<Game>,
}

#[uniffi::export]
impl ChessGame {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(ChessGame { inner: Mutex::new(Game::new()) })
    }

    #[uniffi::constructor]
    pub fn from_fen(fen: String) -> Result<Arc<Self>, CrabError> {
        Ok(Arc::new(ChessGame { inner: Mutex::new(Game::from_fen(&fen).map_err(fail)?) }))
    }

    #[uniffi::constructor]
    pub fn from_pgn(text: String) -> Result<Arc<Self>, CrabError> {
        Ok(Arc::new(ChessGame { inner: Mutex::new(pgn::from_pgn(&text).map_err(fail)?) }))
    }

    pub fn state_json(&self) -> String {
        to_json(&GameState::of(&self.inner.lock().expect("kilit")))
    }

    /// SAN ya da UCI hamle oyna; yeni durumu döndürür.
    pub fn play(&self, mv: String) -> Result<String, CrabError> {
        let mut g = self.inner.lock().expect("kilit");
        g.play(&mv).map_err(fail)?;
        Ok(to_json(&GameState::of(&g)))
    }

    /// Hamleyi saat bilgisiyle (hamleden sonra kalan ms) oyna.
    pub fn play_with_clock(&self, mv: String, clock_ms: u64) -> Result<String, CrabError> {
        let mut g = self.inner.lock().expect("kilit");
        let m = chesscrab_core::parse_move(g.position(), &mv).map_err(fail)?;
        g.play_move(m, Some(clock_ms)).map_err(fail)?;
        Ok(to_json(&GameState::of(&g)))
    }

    pub fn undo(&self) -> bool {
        self.inner.lock().expect("kilit").undo().is_some()
    }

    pub fn resign(&self, white: bool) {
        let side = if white { chesscrab_core::game::Side::White } else { chesscrab_core::game::Side::Black };
        self.inner.lock().expect("kilit").resign(side);
    }

    /// `loser_white` tarafın süresi bitti.
    pub fn timeout(&self, loser_white: bool) {
        use chesscrab_core::game::Side;
        let winner = if loser_white { Side::Black } else { Side::White };
        let mut g = self.inner.lock().expect("kilit");
        // Rakibin mat edecek materyali yoksa beraberlik
        let pos = g.position().clone();
        let winner_color: shakmaty::Color = winner.into();
        if pos.has_insufficient_material(winner_color) {
            g.finish(None, Termination::Timeout);
        } else {
            g.finish(Some(winner), Termination::Timeout);
        }
    }

    pub fn set_tag(&self, name: String, value: String) {
        self.inner.lock().expect("kilit").set_tag(&name, value);
    }

    pub fn pgn(&self) -> String {
        pgn::to_pgn(&self.inner.lock().expect("kilit"))
    }

    pub fn ply(&self) -> u32 {
        self.inner.lock().expect("kilit").ply() as u32
    }

    pub fn copy(&self) -> Arc<ChessGame> {
        Arc::new(ChessGame { inner: Mutex::new(self.inner.lock().expect("kilit").clone()) })
    }
}

// ---------------- motor ----------------

#[derive(uniffi::Object)]
pub struct CrabEngine {
    inner: Mutex<Engine>,
    stop: Arc<AtomicBool>,
}

#[uniffi::export]
impl CrabEngine {
    #[uniffi::constructor]
    pub fn new(hash_mb: u32) -> Arc<Self> {
        let e = Engine::new(hash_mb.clamp(1, 512) as usize);
        let stop = e.searcher.stop_handle();
        Arc::new(CrabEngine { inner: Mutex::new(e), stop })
    }

    /// Seviyeye göre motor hamlesi (JSON: uci, san, score, from_book). Oyun bittiyse None.
    pub fn best_move(&self, game: Arc<ChessGame>, level: u8, movetime_ms: Option<u64>) -> Option<String> {
        let g = game.inner.lock().expect("kilit").clone();
        self.stop.store(false, Ordering::Relaxed);
        let lines = Catalog::get().book_lines();
        let mut e = self.inner.lock().expect("kilit");
        e.play(&g, Level::preset(level), movetime_ms, &lines).map(|m| to_json(&m))
    }

    /// Pozisyonu değerlendir (analiz tahtası). JSON: depth, score, best, lines[{score, pv_uci, pv_san}]
    pub fn evaluate(&self, fen: String, depth: u32, multipv: u32, movetime_ms: Option<u64>) -> Result<String, CrabError> {
        let pos = chesscrab_core::parse_fen(&fen).map_err(fail)?;
        self.stop.store(false, Ordering::Relaxed);
        let mut e = self.inner.lock().expect("kilit");
        let r = e.analyse(&pos, &[], &Limits { depth: Some(depth.clamp(1, 40)), multipv: multipv.clamp(1, 5) as usize, movetime_ms, ..Default::default() });
        let lines: Vec<serde_json::Value> = r
            .lines
            .iter()
            .map(|l| {
                let mut p = pos.clone();
                let mut sans = Vec::new();
                for m in l.pv.iter().take(12) {
                    sans.push(chesscrab_core::san_of(&p, *m));
                    p.play_unchecked(*m);
                }
                serde_json::json!({ "score": l.score, "pv_uci": l.pv_uci, "pv_san": sans })
            })
            .collect();
        Ok(to_json(&serde_json::json!({
            "depth": r.depth, "nodes": r.nodes, "time_ms": r.time_ms, "score": r.score, "best": r.best_uci, "lines": lines
        })))
    }

    /// Süren aramayı durdur (başka iş parçacığından).
    pub fn stop(&self) {
        self.stop.store(true, Ordering::Relaxed);
    }

    pub fn new_game(&self) {
        self.inner.lock().expect("kilit").searcher.new_game();
    }
}

// ---------------- analiz ----------------

#[uniffi::export(callback_interface)]
pub trait AnalysisProgress: Send + Sync {
    fn on_progress(&self, done: u32, total: u32);
}

/// Bütün oyunu analiz et (çok iş parçacıklı). threads = 0 → tüm çekirdekler.
#[uniffi::export]
pub fn analyze_pgn(pgn_text: String, depth: u32, threads: u32, progress: Option<Box<dyn AnalysisProgress>>) -> Result<String, CrabError> {
    let g = pgn::from_pgn(&pgn_text).map_err(fail)?;
    if g.ply() == 0 {
        return Err(CrabError::Failed("analiz edilecek hamle yok".into()));
    }
    let cfg = AnalysisConfig { depth: Some(depth.clamp(4, 30)), threads: threads as usize, hash_mb: 32, ..Default::default() };
    let cb = progress.map(Arc::<dyn AnalysisProgress>::from);
    let f = move |d: usize, n: usize| {
        if let Some(c) = &cb {
            c.on_progress(d as u32, n as u32);
        }
    };
    let a = analyse_game(&g, &cfg, Some(&f));
    Ok(to_json(&a))
}

// ---------------- canlı koç ----------------

fn parse_class(c: &str) -> Option<chesscrab_analysis::MoveClass> {
    serde_json::from_value(serde_json::Value::String(c.to_string())).ok()
}

/// Oyun sırasında her hamleyi anında değerlendiren koç (kendi motoruyla).
#[derive(uniffi::Object)]
pub struct LiveCoach {
    inner: Mutex<chesscrab_analysis::coach::LiveCoach>,
}

#[uniffi::export]
impl LiveCoach {
    #[uniffi::constructor]
    pub fn new(depth: u32) -> Arc<Self> {
        Arc::new(LiveCoach { inner: Mutex::new(chesscrab_analysis::coach::LiveCoach::new(depth, 32)) })
    }

    pub fn clear(&self) {
        self.inner.lock().expect("kilit").clear();
    }

    pub fn set_depth(&self, depth: u32) {
        self.inner.lock().expect("kilit").depth = depth.clamp(4, 30);
    }

    /// Son hamlenin incelemesi: {"move": MoveAnalysis, "white_cp": int}. Hamle yoksa None.
    pub fn review_last(&self, game: Arc<ChessGame>, prev_class: Option<String>) -> Option<String> {
        let g = game.inner.lock().expect("kilit").clone();
        let mut c = self.inner.lock().expect("kilit");
        let mv = c.review_last(&g, prev_class.as_deref().and_then(parse_class))?;
        let cp = c.evaluate(&g).cp;
        Some(to_json(&serde_json::json!({ "move": mv, "white_cp": cp })))
    }

    /// Mevcut pozisyon için en iyi hamle (ipucu).
    pub fn hint(&self, game: Arc<ChessGame>) -> String {
        let g = game.inner.lock().expect("kilit").clone();
        to_json(&self.inner.lock().expect("kilit").hint(&g))
    }

    /// Bir pozisyonda bir hamlenin en iyiye göre kaybı ("Daha iyisini dene").
    pub fn compare(&self, fen: String, mv: String) -> Result<String, CrabError> {
        let pos = chesscrab_core::parse_fen(&fen).map_err(fail)?;
        let m = chesscrab_core::parse_move(&pos, &mv).map_err(fail)?;
        Ok(to_json(&self.inner.lock().expect("kilit").compare(&pos, m)))
    }

    /// Önbellekten anında tam oyun incelemesi (GameAnalysis JSON).
    pub fn full_review(&self, game: Arc<ChessGame>) -> String {
        let g = game.inner.lock().expect("kilit").clone();
        to_json(&self.inner.lock().expect("kilit").full_review(&g))
    }
}

/// Derslerden çıkarılmış bulmacalar (günün bulmacası için).
#[uniffi::export]
pub fn puzzles_json() -> String {
    to_json(&Catalog::get().puzzles())
}

// ---------------- dersler ----------------

#[derive(uniffi::Object)]
pub struct LessonPlayer {
    inner: Mutex<LessonSession>,
}

#[uniffi::export]
impl LessonPlayer {
    #[uniffi::constructor]
    pub fn new(lesson_id: String) -> Result<Arc<Self>, CrabError> {
        let l = Catalog::get().lesson(&lesson_id).cloned().ok_or_else(|| CrabError::Failed("ders yok".into()))?;
        Ok(Arc::new(LessonPlayer { inner: Mutex::new(LessonSession::new(l).map_err(fail)?) }))
    }

    pub fn title(&self) -> String {
        self.inner.lock().expect("kilit").lesson().title.clone()
    }

    pub fn view_json(&self) -> String {
        to_json(&self.inner.lock().expect("kilit").view())
    }

    /// Hamle gönder; JSON MoveFeedback döner.
    pub fn submit_move(&self, mv: String) -> String {
        to_json(&self.inner.lock().expect("kilit").submit_move(&mv))
    }

    pub fn next(&self) {
        self.inner.lock().expect("kilit").next();
    }

    /// Quiz cevabı; JSON {correct, message}
    pub fn answer(&self, option: u32) -> String {
        let (ok, msg) = self.inner.lock().expect("kilit").answer(option as usize);
        to_json(&serde_json::json!({ "correct": ok, "message": msg }))
    }

    pub fn hint(&self) -> Option<String> {
        self.inner.lock().expect("kilit").hint()
    }

    pub fn stars(&self) -> u8 {
        self.inner.lock().expect("kilit").stars()
    }

    pub fn mistakes(&self) -> u32 {
        self.inner.lock().expect("kilit").mistakes()
    }

    pub fn is_done(&self) -> bool {
        self.inner.lock().expect("kilit").is_done()
    }
}

#[derive(uniffi::Object)]
pub struct RepertoireDrill {
    inner: Mutex<Drill>,
    card_id: String,
}

#[uniffi::export]
impl RepertoireDrill {
    #[uniffi::constructor]
    pub fn new(repertoire_id: String, line: u32) -> Result<Arc<Self>, CrabError> {
        let rep = Catalog::get().repertoire(&repertoire_id).ok_or_else(|| CrabError::Failed("repertuvar yok".into()))?;
        let d = Drill::new(rep, line as usize).map_err(fail)?;
        Ok(Arc::new(RepertoireDrill { inner: Mutex::new(d), card_id: rep.card_id(line as usize) }))
    }

    pub fn card_id(&self) -> String {
        self.card_id.clone()
    }

    pub fn view_json(&self) -> String {
        to_json(&self.inner.lock().expect("kilit").view())
    }

    pub fn submit(&self, mv: String) -> String {
        to_json(&self.inner.lock().expect("kilit").submit(&mv))
    }

    /// again|hard|good
    pub fn grade(&self) -> String {
        match Grade::from_mistakes(self.inner.lock().expect("kilit").mistakes()) {
            Grade::Again => "again",
            Grade::Hard => "hard",
            Grade::Good => "good",
            Grade::Easy => "easy",
        }
        .into()
    }
}

// ---------------- yerel depo + senkronizasyon ----------------

#[derive(uniffi::Object)]
pub struct SyncStore {
    inner: Mutex<LocalStore>,
}

#[uniffi::export]
impl SyncStore {
    #[uniffi::constructor]
    pub fn open(path: String) -> Result<Arc<Self>, CrabError> {
        Ok(Arc::new(SyncStore { inner: Mutex::new(LocalStore::open(&path).map_err(fail)?) }))
    }

    pub fn put(&self, collection: String, id: String, data_json: String) -> Result<(), CrabError> {
        let v: serde_json::Value = serde_json::from_str(&data_json).map_err(fail)?;
        self.inner.lock().expect("kilit").put(&collection, &id, v).map_err(fail)?;
        Ok(())
    }

    pub fn delete(&self, collection: String, id: String) -> Result<(), CrabError> {
        self.inner.lock().expect("kilit").delete(&collection, &id).map_err(fail)
    }

    pub fn get(&self, collection: String, id: String) -> Option<String> {
        self.inner.lock().expect("kilit").get(&collection, &id).ok().flatten().map(|r| to_json(&r.data))
    }

    /// JSON dizi: [{id, data, updated_ms}]
    pub fn list_json(&self, collection: String) -> String {
        let recs = self.inner.lock().expect("kilit").list(&collection).unwrap_or_default();
        let v: Vec<serde_json::Value> = recs
            .into_iter()
            .map(|r| serde_json::json!({ "id": r.id, "data": r.data, "updated_ms": r.hlc.wall_ms }))
            .collect();
        to_json(&v)
    }

    pub fn meta(&self, key: String) -> Option<String> {
        self.inner.lock().expect("kilit").meta(&key).ok().flatten()
    }

    pub fn set_meta(&self, key: String, value: String) -> Result<(), CrabError> {
        self.inner.lock().expect("kilit").set_meta(&key, &value).map_err(fail)
    }

    pub fn pending_count(&self) -> u32 {
        self.inner.lock().expect("kilit").pending().map(|p| p.len() as u32).unwrap_or(0)
    }

    /// Sunucuya POST edilecek gövde (/api/sync).
    pub fn prepare_sync_json(&self) -> Result<String, CrabError> {
        Ok(to_json(&self.inner.lock().expect("kilit").prepare_sync().map_err(fail)?))
    }

    /// Sunucu cevabını uygula; yerelde değişen kayıt sayısını döndürür.
    pub fn apply_sync_json(&self, request_json: String, response_json: String) -> Result<u32, CrabError> {
        let req: SyncRequest = serde_json::from_str(&request_json).map_err(fail)?;
        let resp: SyncResponse = serde_json::from_str(&response_json).map_err(fail)?;
        Ok(self.inner.lock().expect("kilit").apply_sync(&req, &resp).map_err(fail)? as u32)
    }

    /// Sunucu değiştiğinde ya da yeni hesapla girişte: her şeyi yeniden gönder.
    pub fn reset_sync(&self) -> Result<(), CrabError> {
        let s = self.inner.lock().expect("kilit");
        s.set_meta("cursor", "0").map_err(fail)?;
        s.mark_all_dirty().map_err(fail)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ffi_smoke() {
        let g = ChessGame::new();
        let s: serde_json::Value = serde_json::from_str(&g.play("e4".into()).unwrap()).unwrap();
        assert_eq!(s["sans"][0], "e4");
        let e = CrabEngine::new(8);
        let mv = e.best_move(g.clone(), 2, None).unwrap();
        assert!(mv.contains("uci"));
        let l = LessonPlayer::new("taktik-catal".into()).unwrap();
        l.next();
        let fb: serde_json::Value = serde_json::from_str(&l.submit_move("e4f6".into())).unwrap();
        assert_eq!(fb["verdict"], "correct");
        let a = analyze_pgn("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0".into(), 6, 1, None).unwrap();
        assert!(a.contains("brilliant") || a.contains("best"));
        let st = SyncStore::open(":memory:".into()).unwrap();
        st.put("games".into(), "a".into(), "{\"pgn\":\"*\"}".into()).unwrap();
        assert_eq!(st.pending_count(), 1);
        assert!(st.prepare_sync_json().unwrap().contains("\"games\""));
        assert!(courses_json().contains("Açılıştan Orta Oyuna"));
        let coach = LiveCoach::new(8);
        let g2 = ChessGame::new();
        g2.play("e4".into()).unwrap();
        let r: serde_json::Value = serde_json::from_str(&coach.review_last(g2.clone(), None).unwrap()).unwrap();
        assert_eq!(r["move"]["class"], "book");
        assert!(coach.full_review(g2.clone()).contains("accuracy"));
        let ps: serde_json::Value = serde_json::from_str(&puzzles_json()).unwrap();
        assert!(ps.as_array().unwrap().len() > 20);
        println!("bulmaca: {}", ps.as_array().unwrap().len());
    }
}
