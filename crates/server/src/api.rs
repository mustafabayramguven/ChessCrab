use std::sync::{Arc, Mutex};

use axum::extract::{FromRequestParts, Path, State};
use axum::http::{HeaderValue, StatusCode, header, request::Parts};
use axum::response::{Html, IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::Deserialize;
use serde_json::{Value, json};
use tower_http::services::{ServeDir, ServeFile};
use tower_http::set_header::SetResponseHeaderLayer;

use chesscrab_sync::SyncRequest;

use crate::db;

#[derive(Clone, Debug)]
pub struct Config {
    pub allow_register: bool,
}

impl Config {
    pub fn from_env() -> Self {
        Config { allow_register: std::env::var("CHESSCRAB_ALLOW_REGISTER").map(|v| v == "1" || v == "true").unwrap_or(false) }
    }
}

pub struct AppState {
    db: Mutex<rusqlite::Connection>,
    cfg: Config,
}

impl AppState {
    pub fn new(conn: rusqlite::Connection, cfg: Config) -> Self {
        AppState { db: Mutex::new(conn), cfg }
    }
}

type St = Arc<AppState>;

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

type R<T> = Result<Json<T>, ApiError>;

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
    let res = tokio::task::spawn_blocking(move || {
        let conn = s.db.lock().expect("kilit");
        if !s.cfg.allow_register && db::user_count(&conn)? > 0 {
            return Err(ApiError(StatusCode::FORBIDDEN, "kayıt kapalı (CHESSCRAB_ALLOW_REGISTER=1 ile açılır)".into()));
        }
        let uid = db::register(&conn, &c.username, &c.password)?;
        let token = db::create_token(&conn, uid, &c.device)?;
        Ok(json!({ "token": token, "username": c.username.trim() }))
    })
    .await
    .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))??;
    Ok(Json(res))
}

async fn login(State(s): State<St>, Json(c): Json<Credentials>) -> R<Value> {
    let res = tokio::task::spawn_blocking(move || {
        let conn = s.db.lock().expect("kilit");
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

async fn sync(State(s): State<St>, u: AuthUser, Json(req): Json<SyncRequest>) -> R<chesscrab_sync::SyncResponse> {
    let mut conn = s.db.lock().expect("kilit");
    Ok(Json(db::sync(&mut conn, u.id, &req)?))
}

async fn records(State(s): State<St>, u: AuthUser, Path(collection): Path<String>) -> R<Vec<chesscrab_sync::Record>> {
    Ok(Json(db::list_records(&s.db.lock().expect("kilit"), u.id, &collection)?))
}

async fn health() -> Json<Value> {
    Json(json!({ "ok": true, "version": env!("CARGO_PKG_VERSION"), "role": "sync" }))
}

async fn no_web() -> Html<&'static str> {
    Html("<!doctype html><meta charset=utf-8><title>ChessCrab</title><p>ChessCrab senkronizasyon sunucusu çalışıyor. Web uygulaması bu kurulumda yok (CHESSCRAB_WEB_DIR).</p>")
}

pub fn router(state: St, web_dir: &str) -> Router {
    let api = Router::new()
        .route("/api/health", get(health))
        .route("/api/auth/register", post(register))
        .route("/api/auth/login", post(login))
        .route("/api/auth/logout", post(logout))
        .route("/api/auth/me", get(me))
        .route("/api/sync", post(sync))
        .route("/api/records/{collection}", get(records))
        .layer(axum::extract::DefaultBodyLimit::max(8 * 1024 * 1024))
        .layer(tower_http::cors::CorsLayer::permissive())
        .with_state(state);

    let index = std::path::Path::new(web_dir).join("index.html");
    if index.exists() {
        let files = ServeDir::new(web_dir).fallback(ServeFile::new(index));
        api.fallback_service(files).layer(SetResponseHeaderLayer::if_not_present(
            header::CACHE_CONTROL,
            HeaderValue::from_static("no-cache"),
        ))
    } else {
        api.fallback(get(no_web))
    }
}
