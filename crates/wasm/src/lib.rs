//! ChessCrab'ın tarayıcı sürümü. Sunucu sadece senkronizasyon yapar; motor, analiz
//! ve dersler bu modül sayesinde kullanıcının cihazında çalışır.
//!
//! Ağır işler (motor, analiz) web arayüzünde Web Worker içinde çağrılır.
//! Karmaşık veriler JSON dizesi olarak geçer (Android FFI ile aynı biçim).

use chesscrab_analysis::{AnalysisConfig, PositionEval, build_analysis, eval_position, positions_and_hashes};
use chesscrab_core::{Game, GameState, Termination, pgn};
use chesscrab_engine::{Engine, Level, Limits, Searcher};
use chesscrab_lessons::{Card, Catalog, Drill, Grade, LessonSession};
use shakmaty::{Chess, Position};
use wasm_bindgen::prelude::*;

fn to_json<T: serde::Serialize>(v: &T) -> String {
    serde_json::to_string(v).unwrap_or_else(|_| "null".into())
}

fn js_err<E: std::fmt::Display>(e: E) -> JsError {
    JsError::new(&e.to_string())
}

#[wasm_bindgen(start)]
pub fn start() {
    console_error_panic_hook::set_once();
}

#[wasm_bindgen]
pub fn version() -> String {
    format!("ChessCrab {} · {} (WASM)", env!("CARGO_PKG_VERSION"), chesscrab_engine::NAME)
}

#[wasm_bindgen]
pub fn courses_json() -> String {
    to_json(&Catalog::get().course_summaries())
}

#[wasm_bindgen]
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

#[wasm_bindgen]
pub fn levels_json() -> String {
    let v: Vec<serde_json::Value> = Level::PRESETS
        .iter()
        .map(|(n, elo, name)| serde_json::json!({"level": n, "elo": elo, "name": name}))
        .collect();
    to_json(&v)
}

/// grade: again|hard|good|easy; now_ms: Date.now()
#[wasm_bindgen]
pub fn srs_review(card_json: Option<String>, card_id: String, grade: String, now_ms: f64) -> String {
    let now = now_ms as i64;
    let mut card: Card = card_json.and_then(|c| serde_json::from_str(&c).ok()).unwrap_or_else(|| Card::new(card_id, now));
    let g = match grade.as_str() {
        "again" => Grade::Again,
        "hard" => Grade::Hard,
        "easy" => Grade::Easy,
        _ => Grade::Good,
    };
    card.review(g, now);
    to_json(&card)
}

// ---------------- oyun ----------------

#[wasm_bindgen]
pub struct CrabGame {
    inner: Game,
}

fn build_game(start_fen: Option<String>, ucis_json: &str) -> Result<Game, JsError> {
    let mut g = match start_fen.filter(|f| !f.trim().is_empty()) {
        Some(f) => Game::from_fen(&f).map_err(js_err)?,
        None => Game::new(),
    };
    let moves: Vec<String> = serde_json::from_str(ucis_json).map_err(js_err)?;
    for m in moves {
        g.play(&m).map_err(js_err)?;
    }
    Ok(g)
}

#[wasm_bindgen]
impl CrabGame {
    #[wasm_bindgen(constructor)]
    pub fn new() -> CrabGame {
        CrabGame { inner: Game::new() }
    }

    /// Başlangıç FEN'i (boş = normal) + UCI hamle listesi (JSON dizi)
    pub fn from_moves(start_fen: Option<String>, ucis_json: &str) -> Result<CrabGame, JsError> {
        Ok(CrabGame { inner: build_game(start_fen, ucis_json)? })
    }

    pub fn from_pgn(text: &str) -> Result<CrabGame, JsError> {
        Ok(CrabGame { inner: pgn::from_pgn(text).map_err(js_err)? })
    }

    pub fn state(&self) -> String {
        to_json(&GameState::of(&self.inner))
    }

    pub fn play(&mut self, mv: &str) -> Result<String, JsError> {
        self.inner.play(mv).map_err(js_err)?;
        Ok(self.state())
    }

    pub fn undo(&mut self) -> bool {
        self.inner.undo().is_some()
    }

    pub fn resign(&mut self, white: bool) {
        use chesscrab_core::game::Side;
        self.inner.resign(if white { Side::White } else { Side::Black });
    }

    pub fn timeout(&mut self, loser_white: bool) {
        use chesscrab_core::game::Side;
        let winner = if loser_white { Side::Black } else { Side::White };
        let c: shakmaty::Color = winner.into();
        if self.inner.position().has_insufficient_material(c) {
            self.inner.finish(None, Termination::Timeout);
        } else {
            self.inner.finish(Some(winner), Termination::Timeout);
        }
    }

    pub fn set_tag(&mut self, name: &str, value: &str) {
        self.inner.set_tag(name, value);
    }

    pub fn pgn(&self) -> String {
        pgn::to_pgn(&self.inner)
    }

    pub fn ply(&self) -> u32 {
        self.inner.ply() as u32
    }
}

impl Default for CrabGame {
    fn default() -> Self {
        Self::new()
    }
}

// ---------------- motor (Web Worker içinde) ----------------

#[wasm_bindgen]
pub struct CrabEngine {
    inner: Engine,
}

#[wasm_bindgen]
impl CrabEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(hash_mb: u32) -> CrabEngine {
        CrabEngine { inner: Engine::new(hash_mb.clamp(1, 256) as usize) }
    }

    /// Seviyeye göre hamle; JSON {uci, san, score, from_book} ya da undefined.
    pub fn best_move(&mut self, start_fen: Option<String>, ucis_json: &str, level: u8, movetime_ms: Option<u32>) -> Result<Option<String>, JsError> {
        let g = build_game(start_fen, ucis_json)?;
        let lines = Catalog::get().book_lines();
        Ok(self.inner.play(&g, Level::preset(level), movetime_ms.map(u64::from), &lines).map(|m| to_json(&m)))
    }

    pub fn evaluate(&mut self, fen: &str, depth: u32, multipv: u32, movetime_ms: Option<u32>) -> Result<String, JsError> {
        let pos = chesscrab_core::parse_fen(fen).map_err(js_err)?;
        let r = self.inner.analyse(
            &pos,
            &[],
            &Limits { depth: Some(depth.clamp(1, 40)), multipv: multipv.clamp(1, 5) as usize, movetime_ms: movetime_ms.map(u64::from), ..Default::default() },
        );
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

    pub fn new_game(&mut self) {
        self.inner.searcher.new_game();
    }
}

// ---------------- analiz (Web Worker'lara dağıtılır) ----------------

/// Bir oyunun pozisyonlarını tek tek değerlendiren analizci. Her worker bir dilim alır.
#[wasm_bindgen]
pub struct CrabAnalyzer {
    positions: Vec<Chess>,
    hashes: Vec<u64>,
    searcher: Searcher,
    cfg: AnalysisConfig,
}

#[wasm_bindgen]
impl CrabAnalyzer {
    #[wasm_bindgen(constructor)]
    pub fn new(pgn_text: &str, depth: u32) -> Result<CrabAnalyzer, JsError> {
        let g = pgn::from_pgn(pgn_text).map_err(js_err)?;
        let (positions, hashes) = positions_and_hashes(&g);
        Ok(CrabAnalyzer {
            positions,
            hashes,
            searcher: Searcher::new(32),
            cfg: AnalysisConfig { depth: Some(depth.clamp(4, 30)), threads: 1, hash_mb: 32, ..Default::default() },
        })
    }

    pub fn count(&self) -> u32 {
        self.positions.len() as u32
    }

    /// i. pozisyonun değerlendirmesi (JSON PositionEval).
    pub fn eval(&mut self, i: u32) -> Result<String, JsError> {
        let i = i as usize;
        let pos = self.positions.get(i).ok_or_else(|| JsError::new("pozisyon yok"))?;
        let limits = self.cfg.limits();
        Ok(to_json(&eval_position(&mut self.searcher, pos, &self.hashes[..i], &limits)))
    }
}

/// Worker'lardan gelen değerlendirmeleri (sırayla, JSON dizi) birleştirip tam analizi üret.
#[wasm_bindgen]
pub fn build_game_analysis(pgn_text: &str, evals_json: &str, time_ms: f64) -> Result<String, JsError> {
    let g = pgn::from_pgn(pgn_text).map_err(js_err)?;
    let evals: Vec<PositionEval> = serde_json::from_str(evals_json).map_err(js_err)?;
    let (positions, _) = positions_and_hashes(&g);
    if evals.len() != positions.len() {
        return Err(JsError::new("değerlendirme sayısı tutmuyor"));
    }
    Ok(to_json(&build_analysis(&g, &positions, evals, "CrabFish (WASM)".into(), time_ms as u64)))
}

// ---------------- dersler ----------------

#[wasm_bindgen]
pub struct CrabLesson {
    inner: LessonSession,
}

#[wasm_bindgen]
impl CrabLesson {
    #[wasm_bindgen(constructor)]
    pub fn new(lesson_id: &str) -> Result<CrabLesson, JsError> {
        let l = Catalog::get().lesson(lesson_id).cloned().ok_or_else(|| JsError::new("ders yok"))?;
        Ok(CrabLesson { inner: LessonSession::new(l).map_err(js_err)? })
    }
    pub fn title(&self) -> String {
        self.inner.lesson().title.clone()
    }
    pub fn view(&self) -> String {
        to_json(&self.inner.view())
    }
    pub fn submit_move(&mut self, mv: &str) -> String {
        to_json(&self.inner.submit_move(mv))
    }
    pub fn next(&mut self) {
        self.inner.next();
    }
    pub fn answer(&mut self, option: u32) -> String {
        let (ok, msg) = self.inner.answer(option as usize);
        to_json(&serde_json::json!({ "correct": ok, "message": msg }))
    }
    pub fn hint(&mut self) -> Option<String> {
        self.inner.hint()
    }
    pub fn stars(&self) -> u8 {
        self.inner.stars()
    }
    pub fn mistakes(&self) -> u32 {
        self.inner.mistakes()
    }
    pub fn is_done(&self) -> bool {
        self.inner.is_done()
    }
}

#[wasm_bindgen]
pub struct CrabDrill {
    inner: Drill,
    card_id: String,
}

#[wasm_bindgen]
impl CrabDrill {
    #[wasm_bindgen(constructor)]
    pub fn new(repertoire_id: &str, line: u32) -> Result<CrabDrill, JsError> {
        let rep = Catalog::get().repertoire(repertoire_id).ok_or_else(|| JsError::new("repertuvar yok"))?;
        Ok(CrabDrill { inner: Drill::new(rep, line as usize).map_err(js_err)?, card_id: rep.card_id(line as usize) })
    }
    pub fn card_id(&self) -> String {
        self.card_id.clone()
    }
    pub fn view(&self) -> String {
        to_json(&self.inner.view())
    }
    pub fn submit(&mut self, mv: &str) -> String {
        to_json(&self.inner.submit(mv))
    }
    pub fn grade(&self) -> String {
        match Grade::from_mistakes(self.inner.mistakes()) {
            Grade::Again => "again",
            Grade::Hard => "hard",
            Grade::Good => "good",
            Grade::Easy => "easy",
        }
        .into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Dağıtık analiz yolu tek parça analizle aynı sonucu vermeli.
    #[test]
    fn distributed_analysis_matches() {
        let pgn = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0";
        let mut a = CrabAnalyzer::new(pgn, 6).unwrap();
        let evals: Vec<serde_json::Value> = (0..a.count()).map(|i| serde_json::from_str(&a.eval(i).unwrap()).unwrap()).collect();
        let full: serde_json::Value = serde_json::from_str(&build_game_analysis(pgn, &serde_json::to_string(&evals).unwrap(), 1.0).unwrap()).unwrap();
        assert_eq!(full["moves"].as_array().unwrap().len(), 7);
        assert_eq!(full["moves"][5]["class"], "blunder");
        let mut g = CrabGame::from_moves(None, "[\"e2e4\"]").unwrap();
        assert!(g.play("e5").is_ok());
        let mut e = CrabEngine::new(8);
        assert!(e.best_move(None, "[\"e2e4\",\"e7e5\"]", 3, None).unwrap().is_some());
    }
}
