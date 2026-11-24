//! Sunucu veritabanı: kullanıcılar, oturum anahtarları ve senkronize kayıtlar.

use argon2::Argon2;
use chesscrab_sync::{Hlc, Record, SyncRequest, SyncResponse};
use rusqlite::{Connection, OptionalExtension, params};

const SCHEMA: &str = r#"
PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    pw TEXT NOT NULL,
    created_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tokens (
    token TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device TEXT NOT NULL DEFAULT '',
    created_ms INTEGER NOT NULL,
    last_used_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS records (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    collection TEXT NOT NULL,
    id TEXT NOT NULL,
    data TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    hlc_wall INTEGER NOT NULL,
    hlc_counter INTEGER NOT NULL,
    hlc_node TEXT NOT NULL,
    seq INTEGER NOT NULL,
    PRIMARY KEY (user_id, collection, id)
);
CREATE INDEX IF NOT EXISTS records_seq ON records(user_id, seq);
"#;

#[derive(Debug, thiserror::Error)]
pub enum DbError {
    #[error("veritabanı: {0}")]
    Sql(#[from] rusqlite::Error),
    #[error("json: {0}")]
    Json(#[from] serde_json::Error),
    #[error("kullanıcı adı alınmış")]
    UserExists,
    #[error("kullanıcı adı ya da şifre hatalı")]
    BadCredentials,
    #[error("geçersiz giriş: {0}")]
    Invalid(String),
}

pub fn open(path: &str) -> Result<Connection, DbError> {
    let c = Connection::open(path)?;
    c.execute_batch(SCHEMA)?;
    Ok(c)
}

fn random_hex(n: usize) -> String {
    let mut buf = vec![0u8; n];
    getrandom::fill(&mut buf).expect("işletim sistemi rastgele sayı üretemedi");
    buf.iter().map(|b| format!("{b:02x}")).collect()
}

fn hash_password(pw: &str, salt_hex: &str) -> String {
    let mut out = [0u8; 32];
    Argon2::default()
        .hash_password_into(pw.as_bytes(), salt_hex.as_bytes(), &mut out)
        .expect("argon2 parametreleri geçerli");
    out.iter().map(|b| format!("{b:02x}")).collect()
}

fn ct_eq(a: &str, b: &str) -> bool {
    a.len() == b.len() && a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

pub fn user_count(c: &Connection) -> Result<i64, DbError> {
    Ok(c.query_row("SELECT COUNT(*) FROM users", [], |r| r.get(0))?)
}

pub fn register(c: &Connection, username: &str, pw: &str) -> Result<i64, DbError> {
    let username = username.trim();
    if username.len() < 3 || username.len() > 32 || !username.chars().all(|ch| ch.is_alphanumeric() || ch == '_' || ch == '-') {
        return Err(DbError::Invalid("kullanıcı adı 3-32 karakter, harf/rakam/_/- olmalı".into()));
    }
    if pw.len() < 6 {
        return Err(DbError::Invalid("şifre en az 6 karakter olmalı".into()));
    }
    let salt = random_hex(16);
    let stored = format!("argon2id${salt}${}", hash_password(pw, &salt));
    match c.execute(
        "INSERT INTO users(username,pw,created_ms) VALUES(?1,?2,?3)",
        params![username, stored, chesscrab_sync::now_ms()],
    ) {
        Ok(_) => Ok(c.last_insert_rowid()),
        Err(rusqlite::Error::SqliteFailure(e, _)) if e.code == rusqlite::ErrorCode::ConstraintViolation => Err(DbError::UserExists),
        Err(e) => Err(e.into()),
    }
}

pub fn login(c: &Connection, username: &str, pw: &str) -> Result<i64, DbError> {
    let row: Option<(i64, String)> = c
        .query_row("SELECT id,pw FROM users WHERE username=?1", [username.trim()], |r| Ok((r.get(0)?, r.get(1)?)))
        .optional()?;
    let Some((id, stored)) = row else {
        // Zamanlama farkını azaltmak için yine de hash hesapla
        let _ = hash_password(pw, "0000000000000000");
        return Err(DbError::BadCredentials);
    };
    let parts: Vec<&str> = stored.split('$').collect();
    if parts.len() == 3 && ct_eq(&hash_password(pw, parts[1]), parts[2]) {
        Ok(id)
    } else {
        Err(DbError::BadCredentials)
    }
}

pub fn create_token(c: &Connection, user_id: i64, device: &str) -> Result<String, DbError> {
    let t = random_hex(32);
    let now = chesscrab_sync::now_ms();
    c.execute(
        "INSERT INTO tokens(token,user_id,device,created_ms,last_used_ms) VALUES(?1,?2,?3,?4,?4)",
        params![t, user_id, device, now],
    )?;
    Ok(t)
}

pub fn user_for_token(c: &Connection, token: &str) -> Result<Option<(i64, String)>, DbError> {
    let r = c
        .query_row(
            "SELECT u.id, u.username FROM tokens t JOIN users u ON u.id=t.user_id WHERE t.token=?1",
            [token],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()?;
    if r.is_some() {
        c.execute("UPDATE tokens SET last_used_ms=?1 WHERE token=?2", params![chesscrab_sync::now_ms(), token])?;
    }
    Ok(r)
}

pub fn revoke_token(c: &Connection, token: &str) -> Result<(), DbError> {
    c.execute("DELETE FROM tokens WHERE token=?1", [token])?;
    Ok(())
}

fn row_record(r: &rusqlite::Row<'_>) -> rusqlite::Result<(i64, Record)> {
    let data: String = r.get(2)?;
    Ok((
        r.get(7)?,
        Record {
            collection: r.get(0)?,
            id: r.get(1)?,
            data: serde_json::from_str(&data).unwrap_or(serde_json::Value::Null),
            deleted: r.get::<_, i64>(3)? != 0,
            hlc: Hlc { wall_ms: r.get(4)?, counter: r.get::<_, i64>(5)? as u32, node: r.get(6)? },
        },
    ))
}

const MAX_RECORD_BYTES: usize = 256 * 1024;
const MAX_CHANGES: usize = 5000;

/// LWW birleştirme ve imleçten sonraki değişiklikleri döndürme.
pub fn sync(c: &mut Connection, user_id: i64, req: &SyncRequest) -> Result<SyncResponse, DbError> {
    if req.changes.len() > MAX_CHANGES {
        return Err(DbError::Invalid(format!("bir seferde en fazla {MAX_CHANGES} değişiklik")));
    }
    let tx = c.transaction()?;
    let mut seq: i64 = tx.query_row("SELECT COALESCE(MAX(seq),0) FROM records WHERE user_id=?1", [user_id], |r| r.get(0))?;
    let mut accepted = 0;
    for ch in &req.changes {
        if !chesscrab_sync::collections::ALL.contains(&ch.collection.as_str()) {
            return Err(DbError::Invalid(format!("bilinmeyen koleksiyon: {}", ch.collection)));
        }
        let data = serde_json::to_string(&ch.data)?;
        if data.len() > MAX_RECORD_BYTES || ch.id.len() > 200 {
            return Err(DbError::Invalid("kayıt çok büyük".into()));
        }
        let existing: Option<(i64, Record)> = tx
            .query_row(
                "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,seq FROM records WHERE user_id=?1 AND collection=?2 AND id=?3",
                params![user_id, ch.collection, ch.id],
                row_record,
            )
            .optional()?;
        let take = existing.as_ref().map(|(_, r)| r.superseded_by(ch)).unwrap_or(true);
        if take {
            seq += 1;
            tx.execute(
                "INSERT INTO records(user_id,collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,seq)
                 VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)
                 ON CONFLICT(user_id,collection,id) DO UPDATE SET data=excluded.data, deleted=excluded.deleted,
                   hlc_wall=excluded.hlc_wall, hlc_counter=excluded.hlc_counter, hlc_node=excluded.hlc_node, seq=excluded.seq",
                params![user_id, ch.collection, ch.id, data, ch.deleted as i64, ch.hlc.wall_ms, ch.hlc.counter as i64, ch.hlc.node, seq],
            )?;
            accepted += 1;
        }
    }
    let changes: Vec<Record> = {
        let mut st = tx.prepare(
            "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,seq FROM records WHERE user_id=?1 AND seq>?2 ORDER BY seq",
        )?;
        let rows = st.query_map(params![user_id, req.cursor], row_record)?;
        rows.filter_map(Result::ok).map(|(_, r)| r).collect()
    };
    tx.commit()?;
    Ok(SyncResponse { cursor: seq, accepted, changes })
}

pub fn list_records(c: &Connection, user_id: i64, collection: &str) -> Result<Vec<Record>, DbError> {
    let mut st = c.prepare(
        "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,seq FROM records
         WHERE user_id=?1 AND collection=?2 AND deleted=0 ORDER BY hlc_wall DESC",
    )?;
    let rows = st.query_map(params![user_id, collection], row_record)?;
    Ok(rows.filter_map(Result::ok).map(|(_, r)| r).collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn auth_and_sync() {
        let mut c = open(":memory:").unwrap();
        let uid = register(&c, "mustafa", "gizli123").unwrap();
        assert!(matches!(register(&c, "Mustafa", "x12345"), Err(DbError::UserExists)));
        assert!(login(&c, "mustafa", "yanlis").is_err());
        assert_eq!(login(&c, "mustafa", "gizli123").unwrap(), uid);
        let t = create_token(&c, uid, "test").unwrap();
        assert_eq!(user_for_token(&c, &t).unwrap().unwrap().0, uid);

        let mut store = chesscrab_sync::store::LocalStore::open_in_memory().unwrap();
        store.put("games", "a", serde_json::json!({"pgn": "*"})).unwrap();
        let req = store.prepare_sync().unwrap();
        let resp = sync(&mut c, uid, &req).unwrap();
        assert_eq!(resp.accepted, 1);
        assert_eq!(resp.cursor, 1);
        store.apply_sync(&req, &resp).unwrap();
        // Tekrar gönderilen eski kayıt kabul edilmez
        let resp2 = sync(&mut c, uid, &req).unwrap();
        assert_eq!(resp2.accepted, 0);
        assert_eq!(list_records(&c, uid, "games").unwrap().len(), 1);
    }
}
