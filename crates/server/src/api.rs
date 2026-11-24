use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use axum::extract::{FromRequestParts, Path, State};
use axum::http::{HeaderMap, StatusCode, header, request::Parts};
use axum::response::{Html, IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::Deserialize;
use serde_json::{Value, json};
use tokio::sync::Semaphore;

use chesscrab_analysis::{AnalysisConfig, analyse_game, analyse_game_uci};
use chesscrab_core::{Game, pgn};
use chesscrab_engine::{Engine, Level, Limits};
use chesscrab_lessons::{Catalog, Drill, LessonSession};
use chesscrab_sync::SyncRequest;

use crate::db;

#[derive(Clone, Debug)]
pub struct Config {
    pub allow_register: bool,
    pub stockfish: Option<String>,
    pub analysis_threads: usize,
    pub max_depth: u32,
}

impl Config {
    pub fn from_env() -> Self {
        Config {
            allow_register: std::env::var("CHESSCRAB_ALLOW_REGISTER").map(|v| v == "1" || v == "true").unwrap_or(false),
            stockfish: std::env::var("CHESSCRAB_STOCKFISH").ok().filter(|s| !s.is_empty()),
            analysis_threads: std::env::var("CHESSCRAB_ANALYSIS_THREADS").ok().and_then(|v| v.parse().ok()).unwrap_or(0),
            max_depth: std::env::var("CHESSCRAB_MAX_DEPTH").ok().and_then(|v| v.parse().ok()).unwrap_or(18),
        }
    }
}

enum Session {
    Lesson(Box<LessonSession>),
    Drill(Box<Drill>),
}

pub struct AppState {
    db: Mutex<rusqlite::Connection>,
    cfg: Config,
    sessions: Mutex<HashMap<String, (Instant, Session)>>,
    analysis_slots: Semaphore,
    engine_slots: Semaphore,
}

impl AppState {
    pub fn new(conn: rusqlite::Connection, cfg: Config) -> Self {
        AppState {
            db: Mutex::new(conn),
            cfg,
            sessions: Mutex::new(HashMap::new()),
            analysis_slots: Semaphore::new(1),
            engine_slots: Semaphore::new(4),
        }
    }
}

type St = Arc<AppState>;

pub fn spawn_session_gc(state: St) {
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(300)).await;
            let mut s = state.sessions.lock().expect("kilit");
            s.retain(|_, (t, _)| t.elapsed() < Duration::from_secs(6 * 3600));
        }
    });
}

// ---------- hata ----------

pub struct ApiError(StatusCode, String);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({ "error": self.1 }))).into_response()
    }
}

impl From<db::DbError> for ApiError {
    fn from(e: db::DbError) -> Self {
        let code = match e {
            db::DbError::UserExists => StatusCode::CONFLICT,
            db::DbError::BadCredentials => StatusCode::UNAUTHORIZED,
            db::DbError::Invalid(_) => StatusCode::BAD_REQUEST,
            _ => StatusCode::INTERNAL_SERVER_ERROR,
        };
        ApiError(code, e.to_string())
    }
}

impl From<chesscrab_core::GameError> for ApiError {
    fn from(e: chesscrab_core::GameError) -> Self {
        ApiError(StatusCode::BAD_REQUEST, e.to_string())
    }
}

fn bad(msg: impl Into<String>) -> ApiError {
    ApiError(StatusCode::BAD_REQUEST, msg.into())
}

fn not_found(msg: impl Into<String>) -> ApiError {
    ApiError(StatusCode::NOT_FOUND, msg.into())
}

type R<T> = Result<Json<T>, ApiError>;

// ---------- kimlik ----------

pub struct AuthUser {
    pub id: i64,
    pub username: String,
    pub token: String,
}

impl FromRequestParts<St> for AuthUser {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, state: &St) -> Result<Self, Self::Rejection> {
        let token = parts
            .headers
            .get(header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer "))
            .ok_or_else(|| ApiError(StatusCode::UNAUTHORIZED, "giriş gerekli".into()))?
            .to_string();
        let c = state.db.lock().expect("kilit");
        match db::user_for_token(&c, &token)? {
            Some((id, username)) => Ok(AuthUser { id, username, token }),
            None => Err(ApiError(StatusCode::UNAUTHORIZED, "oturum geçersiz".into())),
        }
    }
}

#[derive(Deserialize)]
struct Credentials {
    username: String,
    password: String,
    #[serde(default)]
    device: String,
}

async fn register(State(s): State<St>, Json(c): Json<Credentials>) -> R<Value> {
    let conn = s.db.lock().expect("kilit");
    if !s.cfg.allow_register && db::user_count(&conn)? > 0 {
        return Err(ApiError(StatusCode::FORBIDDEN, "kayıt kapalı (CHESSCRAB_ALLOW_REGISTER=1 ile açılır)".into()));
    }
    let uid = db::register(&conn, &c.username, &c.password)?;
    let token = db::create_token(&conn, uid, &c.device)?;
    Ok(Json(json!({ "token": token, "username": c.username.trim() })))
}

async fn login(State(s): State<St>, Json(c): Json<Credentials>) -> R<Value> {
    // argon2 pahalı: kilidi tutarken çalıştırmamak için önce kontrol et
    let state = s.clone();
    let res = tokio::task::spawn_blocking(move || {
        let conn = state.db.lock().expect("kilit");
        let uid = db::login(&conn, &c.username, &c.password)?;
        let token = db::create_token(&conn, uid, &c.device)?;
        Ok::<_, db::DbError>(json!({ "token": token, "username": c.username.trim() }))
    })
    .await
    .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))??;
    Ok(Json(res))
}

async fn logout(State(s): State<St>, u: AuthUser) -> R<Value> {
    db::revoke_token(&s.db.lock().expect("kilit"), &u.token)?;
    Ok(Json(json!({ "ok": true })))
}

async fn me(u: AuthUser) -> R<Value> {
    Ok(Json(json!({ "id": u.id, "username": u.username })))
}

// ---------- senkronizasyon ----------

async fn sync(State(s): State<St>, u: AuthUser, Json(req): Json<SyncRequest>) -> R<chesscrab_sync::SyncResponse> {
    let mut conn = s.db.lock().expect("kilit");
    Ok(Json(db::sync(&mut conn, u.id, &req)?))
}

async fn records(State(s): State<St>, u: AuthUser, Path(collection): Path<String>) -> R<Vec<chesscrab_sync::Record>> {
    Ok(Json(db::list_records(&s.db.lock().expect("kilit"), u.id, &collection)?))
}

// ---------- oyun durumu (web istemcisi için durumsuz) ----------

#[derive(Deserialize)]
struct GameReq {
    #[serde(default)]
    start_fen: Option<String>,
    #[serde(default)]
    moves: Vec<String>,
}

fn build_game(req: &GameReq) -> Result<Game, ApiError> {
    let mut g = match &req.start_fen {
        Some(f) if !f.trim().is_empty() => Game::from_fen(f)?,
        _ => Game::new(),
    };
    for m in &req.moves {
        g.play(m)?;
    }
    Ok(g)
}

fn game_json(g: &Game) -> Value {
    serde_json::to_value(chesscrab_core::GameState::of(g)).unwrap_or(Value::Null)
}

async fn game_state(Json(req): Json<GameReq>) -> R<Value> {
    Ok(Json(game_json(&build_game(&req)?)))
}

#[derive(Deserialize)]
struct EngineReq {
    #[serde(flatten)]
    game: GameReq,
    #[serde(default = "default_level")]
    level: u8,
    #[serde(default)]
    movetime_ms: Option<u64>,
}
fn default_level() -> u8 {
    4
}

async fn engine_move(State(s): State<St>, Json(req): Json<EngineReq>) -> R<Value> {
    let g = build_game(&req.game)?;
    let _permit = s.engine_slots.acquire().await.map_err(|_| bad("meşgul"))?;
    let level = Level::preset(req.level);
    let movetime = req.movetime_ms.map(|m| m.min(10_000));
    let res = tokio::task::spawn_blocking(move || {
        let mut e = Engine::new(16);
        let lines = Catalog::get().book_lines();
        let mv = e.play(&g, level, movetime, &lines);
        (g, mv)
    })
    .await
    .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let (mut g, mv) = res;
    let Some(mv) = mv else { return Err(bad("oyun bitti")) };
    g.play(&mv.uci)?;
    Ok(Json(json!({ "move": mv, "game": game_json(&g) })))
}

#[derive(Deserialize)]
struct EvalReq {
    fen: String,
    #[serde(default)]
    depth: Option<u32>,
    #[serde(default)]
    multipv: Option<usize>,
}

async fn evaluate(State(s): State<St>, Json(req): Json<EvalReq>) -> R<Value> {
    let pos = chesscrab_core::parse_fen(&req.fen)?;
    let depth = req.depth.unwrap_or(14).min(s.cfg.max_depth);
    let multipv = req.multipv.unwrap_or(3).clamp(1, 5);
    let _permit = s.engine_slots.acquire().await.map_err(|_| bad("meşgul"))?;
    let r = tokio::task::spawn_blocking(move || {
        let mut e = Engine::new(32);
        let r = e.analyse(&pos, &[], &Limits { depth: Some(depth), multipv, movetime_ms: Some(5000), ..Default::default() });
        let lines: Vec<Value> = r
            .lines
            .iter()
            .map(|l| {
                let mut p = pos.clone();
                let mut sans = Vec::new();
                for m in l.pv.iter().take(10) {
                    sans.push(chesscrab_core::san_of(&p, *m));
                    shakmaty::Position::play_unchecked(&mut p, *m);
                }
                json!({ "score": l.score, "pv_uci": l.pv_uci, "pv_san": sans })
            })
            .collect();
        json!({ "depth": r.depth, "nodes": r.nodes, "time_ms": r.time_ms, "score": r.score, "best": r.best_uci, "lines": lines })
    })
    .await
    .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(r))
}

#[derive(Deserialize)]
struct AnalyzeReq {
    #[serde(default)]
    pgn: Option<String>,
    #[serde(flatten)]
    game: GameReq,
    #[serde(default)]
    depth: Option<u32>,
    /// "crabfish" | "stockfish"
    #[serde(default)]
    engine: Option<String>,
}

async fn analyze(State(s): State<St>, Json(req): Json<AnalyzeReq>) -> R<Value> {
    let g = match &req.pgn {
        Some(p) if !p.trim().is_empty() => pgn::from_pgn(p)?,
        _ => build_game(&req.game)?,
    };
    if g.ply() == 0 {
        return Err(bad("analiz edilecek hamle yok"));
    }
    if g.ply() > 600 {
        return Err(bad("oyun çok uzun"));
    }
    let depth = req.depth.unwrap_or(12).clamp(4, s.cfg.max_depth);
    let threads = s.cfg.analysis_threads;
    let stockfish = if req.engine.as_deref() == Some("stockfish") { s.cfg.stockfish.clone() } else { None };
    let _permit = s.analysis_slots.acquire().await.map_err(|_| bad("meşgul"))?;
    let a = tokio::task::spawn_blocking(move || {
        let cfg = AnalysisConfig { depth: Some(depth), threads, ..Default::default() };
        match stockfish {
            Some(path) => analyse_game_uci(&g, &cfg, &path).unwrap_or_else(|_| analyse_game(&g, &cfg, None)),
            None => analyse_game(&g, &cfg, None),
        }
    })
    .await
    .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::to_value(a).map_err(|e| bad(e.to_string()))?))
}

async fn import_pgn(Json(req): Json<Value>) -> R<Value> {
    let text = req.get("pgn").and_then(|v| v.as_str()).ok_or_else(|| bad("pgn alanı gerekli"))?;
    let g = pgn::from_pgn(text)?;
    let mut v = game_json(&g);
    v["start_fen"] = json!(g.start_fen());
    v["tags"] = json!(g.tags);
    Ok(Json(v))
}

async fn engine_info(State(s): State<St>) -> Json<Value> {
    Json(json!({
        "engine": chesscrab_engine::NAME,
        "version": chesscrab_engine::VERSION,
        "stockfish": s.cfg.stockfish.is_some(),
        "levels": Level::PRESETS.iter().map(|(n, elo, name)| json!({"level": n, "elo": elo, "name": name})).collect::<Vec<_>>(),
        "max_depth": s.cfg.max_depth,
    }))
}

// ---------- dersler ----------

async fn courses() -> Json<Value> {
    Json(json!(Catalog::get().course_summaries()))
}

async fn lesson(Path(id): Path<String>) -> R<chesscrab_lessons::Lesson> {
    Catalog::get().lesson(&id).cloned().map(Json).ok_or_else(|| not_found("ders yok"))
}

async fn repertoires() -> Json<Value> {
    let reps: Vec<Value> = Catalog::get()
        .repertoires
        .iter()
        .map(|r| {
            json!({
                "id": r.id, "title": r.title, "description": r.description, "side": r.side,
                "lines": r.lines.iter().enumerate().map(|(i, l)| json!({"index": i, "name": l.name, "moves": l.moves, "card_id": r.card_id(i)})).collect::<Vec<_>>()
            })
        })
        .collect();
    Json(json!(reps))
}

fn new_sid() -> String {
    uuid::Uuid::new_v4().simple().to_string()
}

async fn lesson_start(State(s): State<St>, Path(id): Path<String>) -> R<Value> {
    let l = Catalog::get().lesson(&id).cloned().ok_or_else(|| not_found("ders yok"))?;
    let sess = LessonSession::new(l)?;
    let view = sess.view();
    let sid = new_sid();
    s.sessions.lock().expect("kilit").insert(sid.clone(), (Instant::now(), Session::Lesson(Box::new(sess))));
    Ok(Json(json!({ "sid": sid, "view": view })))
}

#[derive(Deserialize)]
struct SessionAction {
    #[serde(default)]
    action: String,
    #[serde(default)]
    r#move: Option<String>,
    #[serde(default)]
    option: Option<usize>,
}

async fn lesson_act(State(s): State<St>, Path(sid): Path<String>, Json(a): Json<SessionAction>) -> R<Value> {
    let mut map = s.sessions.lock().expect("kilit");
    let (t, sess) = map.get_mut(&sid).ok_or_else(|| not_found("oturum yok (süresi dolmuş olabilir)"))?;
    *t = Instant::now();
    let Session::Lesson(sess) = sess else { return Err(bad("ders oturumu değil")) };
    let mut extra = json!({});
    match a.action.as_str() {
        "move" => {
            let m = a.r#move.ok_or_else(|| bad("hamle gerekli"))?;
            extra = json!({ "feedback": sess.submit_move(&m) });
        }
        "next" => sess.next(),
        "answer" => {
            let (ok, msg) = sess.answer(a.option.unwrap_or(usize::MAX));
            extra = json!({ "correct": ok, "message": msg });
        }
        "hint" => extra = json!({ "hint": sess.hint() }),
        "view" => {}
        other => return Err(bad(format!("bilinmeyen eylem: {other}"))),
    }
    Ok(Json(json!({ "view": sess.view(), "stars": sess.stars(), "done": sess.is_done(), "result": extra })))
}

#[derive(Deserialize)]
struct DrillStart {
    repertoire: String,
    line: usize,
}

async fn drill_start(State(s): State<St>, Json(d): Json<DrillStart>) -> R<Value> {
    let rep = Catalog::get().repertoire(&d.repertoire).ok_or_else(|| not_found("repertuvar yok"))?;
    let drill = Drill::new(rep, d.line)?;
    let view = drill.view();
    let sid = new_sid();
    s.sessions.lock().expect("kilit").insert(sid.clone(), (Instant::now(), Session::Drill(Box::new(drill))));
    Ok(Json(json!({ "sid": sid, "view": view, "card_id": rep.card_id(d.line) })))
}

async fn drill_move(State(s): State<St>, Path(sid): Path<String>, Json(a): Json<SessionAction>) -> R<Value> {
    let mut map = s.sessions.lock().expect("kilit");
    let (t, sess) = map.get_mut(&sid).ok_or_else(|| not_found("oturum yok"))?;
    *t = Instant::now();
    let Session::Drill(d) = sess else { return Err(bad("drill oturumu değil")) };
    let m = a.r#move.ok_or_else(|| bad("hamle gerekli"))?;
    let fb = d.submit(&m);
    Ok(Json(json!({ "feedback": fb, "view": d.view(), "grade": chesscrab_lessons::Grade::from_mistakes(d.mistakes()) })))
}

#[derive(Deserialize)]
struct ReviewReq {
    card: Option<chesscrab_lessons::Card>,
    card_id: String,
    grade: chesscrab_lessons::Grade,
}

/// SRS kartını değerlendir (saf fonksiyon; kartı istemci saklar ve senkronize eder).
async fn srs_review(Json(r): Json<ReviewReq>) -> Json<chesscrab_lessons::Card> {
    let now = chesscrab_sync::now_ms();
    let mut card = r.card.unwrap_or_else(|| chesscrab_lessons::Card::new(r.card_id, now));
    card.review(r.grade, now);
    Json(card)
}

// ---------- web arayüzü ----------

const INDEX_HTML: &str = include_str!("../static/index.html");

async fn index() -> impl IntoResponse {
    let mut h = HeaderMap::new();
    h.insert(header::CACHE_CONTROL, "no-cache".parse().expect("geçerli başlık"));
    (h, Html(INDEX_HTML))
}

async fn health() -> Json<Value> {
    Json(json!({ "ok": true, "version": env!("CARGO_PKG_VERSION") }))
}

pub fn router(state: St) -> Router {
    Router::new()
        .route("/", get(index))
        .route("/api/health", get(health))
        .route("/api/auth/register", post(register))
        .route("/api/auth/login", post(login))
        .route("/api/auth/logout", post(logout))
        .route("/api/auth/me", get(me))
        .route("/api/sync", post(sync))
        .route("/api/records/{collection}", get(records))
        .route("/api/game", post(game_state))
        .route("/api/game/import", post(import_pgn))
        .route("/api/engine", get(engine_info))
        .route("/api/engine/move", post(engine_move))
        .route("/api/engine/eval", post(evaluate))
        .route("/api/analyze", post(analyze))
        .route("/api/courses", get(courses))
        .route("/api/lessons/{id}", get(lesson))
        .route("/api/lessons/{id}/session", post(lesson_start))
        .route("/api/sessions/{sid}", post(lesson_act))
        .route("/api/repertoires", get(repertoires))
        .route("/api/drills", post(drill_start))
        .route("/api/drills/{sid}", post(drill_move))
        .route("/api/srs/review", post(srs_review))
        .layer(axum::extract::DefaultBodyLimit::max(4 * 1024 * 1024))
        .layer(tower_http::cors::CorsLayer::permissive())
        .with_state(state)
}

