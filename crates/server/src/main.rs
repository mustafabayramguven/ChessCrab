//! ChessCrab sunucusu.
//!
//! Ortam değişkenleri:
//! - `CHESSCRAB_ADDR` (varsayılan `0.0.0.0:3437`)
//! - `CHESSCRAB_DB` (varsayılan `./data/chesscrab.db`)
//! - `CHESSCRAB_ALLOW_REGISTER` = `1` ise herkes kayıt olabilir; aksi halde sadece ilk kullanıcı
//! - `CHESSCRAB_STOCKFISH` = harici UCI motorunun yolu (isteğe bağlı; analizde kullanılır)
//! - `CHESSCRAB_ANALYSIS_THREADS` (varsayılan: çekirdek sayısı)
//! - `CHESSCRAB_MAX_DEPTH` (varsayılan 18)

mod api;
mod db;

use std::sync::Arc;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().with_env_filter(
        tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "chesscrab_server=info,tower_http=warn".into()),
    ).init();

    let addr = std::env::var("CHESSCRAB_ADDR").unwrap_or_else(|_| "0.0.0.0:3437".into());
    let db_path = std::env::var("CHESSCRAB_DB").unwrap_or_else(|_| "./data/chesscrab.db".into());
    if let Some(dir) = std::path::Path::new(&db_path).parent()
        && !dir.as_os_str().is_empty() {
            std::fs::create_dir_all(dir).expect("veritabanı klasörü oluşturulamadı");
        }
    let conn = db::open(&db_path).expect("veritabanı açılamadı");
    let state = Arc::new(api::AppState::new(conn, api::Config::from_env()));
    // İçerik bozuksa sunucu başlamadan anlaşılsın
    let cat = chesscrab_lessons::Catalog::get();
    tracing::info!("{} kurs, {} ders, {} repertuvar yüklendi", cat.courses.len(), cat.lessons.len(), cat.repertoires.len());

    let app = api::router(state.clone());
    let listener = tokio::net::TcpListener::bind(&addr).await.expect("adres dinlenemedi");
    tracing::info!("ChessCrab http://{addr} adresinde");
    api::spawn_session_gc(state);
    axum::serve(listener, app).with_graceful_shutdown(async {
        let _ = tokio::signal::ctrl_c().await;
    })
    .await
    .expect("sunucu hatası");
}
