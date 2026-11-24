//! İstemci tarafı yerel depo (SQLite). Android'de ve masaüstünde kullanılır.

use rusqlite::{Connection, OptionalExtension, params};

use crate::{Hlc, Record, SyncRequest, SyncResponse, now_ms};

#[derive(Debug, thiserror::Error)]
pub enum StoreError {
    #[error("sqlite: {0}")]
    Sql(#[from] rusqlite::Error),
    #[error("json: {0}")]
    Json(#[from] serde_json::Error),
}

pub struct LocalStore {
    conn: Connection,
    node: String,
    clock: Hlc,
}

const SCHEMA: &str = r#"
CREATE TABLE IF NOT EXISTS records (
    collection TEXT NOT NULL,
    id TEXT NOT NULL,
    data TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    hlc_wall INTEGER NOT NULL,
    hlc_counter INTEGER NOT NULL,
    hlc_node TEXT NOT NULL,
    dirty INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (collection, id)
);
CREATE INDEX IF NOT EXISTS records_dirty ON records(dirty) WHERE dirty = 1;
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"#;

fn row_to_record(r: &rusqlite::Row<'_>) -> rusqlite::Result<(Record, bool)> {
    let data: String = r.get(2)?;
    Ok((
        Record {
            collection: r.get(0)?,
            id: r.get(1)?,
            data: serde_json::from_str(&data).unwrap_or(serde_json::Value::Null),
            deleted: r.get::<_, i64>(3)? != 0,
            hlc: Hlc { wall_ms: r.get(4)?, counter: r.get::<_, i64>(5)? as u32, node: r.get(6)? },
        },
        r.get::<_, i64>(7)? != 0,
    ))
}

impl LocalStore {
    pub fn open(path: &str) -> Result<Self, StoreError> {
        let conn = Connection::open(path)?;
        Self::init(conn)
    }

    pub fn open_in_memory() -> Result<Self, StoreError> {
        Self::init(Connection::open_in_memory()?)
    }

    fn init(conn: Connection) -> Result<Self, StoreError> {
        conn.execute_batch("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;")?;
        conn.execute_batch(SCHEMA)?;
        let node: Option<String> = conn.query_row("SELECT value FROM meta WHERE key='node'", [], |r| r.get(0)).optional()?;
        let node = match node {
            Some(n) => n,
            None => {
                let n = uuid::Uuid::new_v4().simple().to_string()[..12].to_string();
                conn.execute("INSERT INTO meta(key,value) VALUES('node',?1)", [&n])?;
                n
            }
        };
        let clock: Option<String> = conn.query_row("SELECT value FROM meta WHERE key='clock'", [], |r| r.get(0)).optional()?;
        let clock = clock.and_then(|c| serde_json::from_str(&c).ok()).unwrap_or_else(|| Hlc::zero(&node));
        Ok(LocalStore { conn, node, clock })
    }

    pub fn node(&self) -> &str {
        &self.node
    }

    fn save_clock(&self) -> Result<(), StoreError> {
        self.conn.execute(
            "INSERT INTO meta(key,value) VALUES('clock',?1) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            [serde_json::to_string(&self.clock)?],
        )?;
        Ok(())
    }

    pub fn meta(&self, key: &str) -> Result<Option<String>, StoreError> {
        Ok(self.conn.query_row("SELECT value FROM meta WHERE key=?1", [key], |r| r.get(0)).optional()?)
    }

    pub fn set_meta(&self, key: &str, value: &str) -> Result<(), StoreError> {
        self.conn.execute(
            "INSERT INTO meta(key,value) VALUES(?1,?2) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            params![key, value],
        )?;
        Ok(())
    }

    fn write(&self, rec: &Record, dirty: bool) -> Result<(), StoreError> {
        self.conn.execute(
            "INSERT INTO records(collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty)
             VALUES(?1,?2,?3,?4,?5,?6,?7,?8)
             ON CONFLICT(collection,id) DO UPDATE SET data=excluded.data, deleted=excluded.deleted,
               hlc_wall=excluded.hlc_wall, hlc_counter=excluded.hlc_counter, hlc_node=excluded.hlc_node, dirty=excluded.dirty",
            params![
                rec.collection,
                rec.id,
                serde_json::to_string(&rec.data)?,
                rec.deleted as i64,
                rec.hlc.wall_ms,
                rec.hlc.counter as i64,
                rec.hlc.node,
                dirty as i64
            ],
        )?;
        Ok(())
    }

    /// Kayıt ekle/güncelle (yerel değişiklik → kirli).
    pub fn put(&mut self, collection: &str, id: &str, data: serde_json::Value) -> Result<Record, StoreError> {
        self.clock = self.clock.tick(now_ms());
        self.save_clock()?;
        let rec = Record { collection: collection.into(), id: id.into(), data, deleted: false, hlc: self.clock.clone() };
        self.write(&rec, true)?;
        Ok(rec)
    }

    pub fn delete(&mut self, collection: &str, id: &str) -> Result<(), StoreError> {
        self.clock = self.clock.tick(now_ms());
        self.save_clock()?;
        let rec = Record { collection: collection.into(), id: id.into(), data: serde_json::Value::Null, deleted: true, hlc: self.clock.clone() };
        self.write(&rec, true)
    }

    pub fn get(&self, collection: &str, id: &str) -> Result<Option<Record>, StoreError> {
        let r = self
            .conn
            .query_row(
                "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty FROM records WHERE collection=?1 AND id=?2",
                params![collection, id],
                row_to_record,
            )
            .optional()?;
        Ok(r.map(|(r, _)| r).filter(|r| !r.deleted))
    }

    /// Silinmemiş kayıtlar, en yeni değişiklik önce.
    pub fn list(&self, collection: &str) -> Result<Vec<Record>, StoreError> {
        let mut st = self.conn.prepare(
            "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty FROM records
             WHERE collection=?1 AND deleted=0 ORDER BY hlc_wall DESC, hlc_counter DESC",
        )?;
        let rows = st.query_map([collection], row_to_record)?;
        Ok(rows.filter_map(Result::ok).map(|(r, _)| r).collect())
    }

    pub fn pending(&self) -> Result<Vec<Record>, StoreError> {
        let mut st = self.conn.prepare(
            "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty FROM records WHERE dirty=1",
        )?;
        let rows = st.query_map([], row_to_record)?;
        Ok(rows.filter_map(Result::ok).map(|(r, _)| r).collect())
    }

    pub fn cursor(&self) -> Result<i64, StoreError> {
        Ok(self.meta("cursor")?.and_then(|c| c.parse().ok()).unwrap_or(0))
    }

    /// Sunucuya gönderilecek isteği hazırla.
    pub fn prepare_sync(&self) -> Result<SyncRequest, StoreError> {
        Ok(SyncRequest { cursor: self.cursor()?, changes: self.pending()?, device: self.node.clone() })
    }

    /// Sunucu cevabını uygula. `sent`: gönderilen istek (kirli bayraklarını temizlemek için).
    /// Dönüş: yerelde değişen kayıt sayısı.
    pub fn apply_sync(&mut self, sent: &SyncRequest, resp: &SyncResponse) -> Result<usize, StoreError> {
        let tx = self.conn.unchecked_transaction()?;
        // Gönderilenler artık sunucuda: aynı damgayı hâlâ taşıyorlarsa temizle
        for r in &sent.changes {
            tx.execute(
                "UPDATE records SET dirty=0 WHERE collection=?1 AND id=?2 AND hlc_wall=?3 AND hlc_counter=?4 AND hlc_node=?5",
                params![r.collection, r.id, r.hlc.wall_ms, r.hlc.counter as i64, r.hlc.node],
            )?;
        }
        let mut changed = 0;
        let now = now_ms();
        for remote in &resp.changes {
            self.clock = self.clock.receive(&remote.hlc, now);
            let local = tx
                .query_row(
                    "SELECT collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty FROM records WHERE collection=?1 AND id=?2",
                    params![remote.collection, remote.id],
                    row_to_record,
                )
                .optional()?;
            let take = match &local {
                None => true,
                Some((l, _)) => l.superseded_by(remote),
            };
            if take {
                tx.execute(
                    "INSERT INTO records(collection,id,data,deleted,hlc_wall,hlc_counter,hlc_node,dirty)
                     VALUES(?1,?2,?3,?4,?5,?6,?7,0)
                     ON CONFLICT(collection,id) DO UPDATE SET data=excluded.data, deleted=excluded.deleted,
                       hlc_wall=excluded.hlc_wall, hlc_counter=excluded.hlc_counter, hlc_node=excluded.hlc_node, dirty=0",
                    params![
                        remote.collection,
                        remote.id,
                        serde_json::to_string(&remote.data)?,
                        remote.deleted as i64,
                        remote.hlc.wall_ms,
                        remote.hlc.counter as i64,
                        remote.hlc.node
                    ],
                )?;
                changed += 1;
            }
        }
        tx.execute(
            "INSERT INTO meta(key,value) VALUES('cursor',?1) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            [resp.cursor.to_string()],
        )?;
        tx.commit()?;
        self.save_clock()?;
        Ok(changed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    /// Bellekte basit bir sunucu: LWW + sıra numarası
    #[derive(Default)]
    struct FakeServer {
        recs: Vec<(i64, Record)>,
        seq: i64,
    }
    impl FakeServer {
        fn sync(&mut self, req: &SyncRequest) -> SyncResponse {
            let mut accepted = 0;
            for c in &req.changes {
                let pos = self.recs.iter().position(|(_, r)| r.collection == c.collection && r.id == c.id);
                let take = pos.map(|p| self.recs[p].1.superseded_by(c)).unwrap_or(true);
                if take {
                    self.seq += 1;
                    if let Some(p) = pos {
                        self.recs.remove(p);
                    }
                    self.recs.push((self.seq, c.clone()));
                    accepted += 1;
                }
            }
            let changes = self.recs.iter().filter(|(s, _)| *s > req.cursor).map(|(_, r)| r.clone()).collect();
            SyncResponse { cursor: self.seq, accepted, changes }
        }
    }

    #[test]
    fn two_devices_converge() {
        let mut server = FakeServer::default();
        let mut phone = LocalStore::open_in_memory().unwrap();
        let mut laptop = LocalStore::open_in_memory().unwrap();

        phone.put("games", "g1", json!({"pgn": "1. e4 *"})).unwrap();
        phone.put("lesson_progress", "prensip-merkez", json!({"completed": true})).unwrap();
        let req = phone.prepare_sync().unwrap();
        assert_eq!(req.changes.len(), 2);
        let resp = server.sync(&req);
        phone.apply_sync(&req, &resp).unwrap();
        assert!(phone.pending().unwrap().is_empty());

        // Dizüstü ilk senkronizasyon: her şeyi alır
        let req = laptop.prepare_sync().unwrap();
        let resp = server.sync(&req);
        assert_eq!(laptop.apply_sync(&req, &resp).unwrap(), 2);
        assert_eq!(laptop.get("games", "g1").unwrap().unwrap().data["pgn"], "1. e4 *");

        // Çakışma: ikisi de aynı kaydı değiştirir, sonra yazan kazanır
        laptop.put("games", "g1", json!({"pgn": "1. d4 *"})).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(2));
        phone.put("games", "g1", json!({"pgn": "1. c4 *"})).unwrap();
        let r1 = laptop.prepare_sync().unwrap();
        let s1 = server.sync(&r1);
        laptop.apply_sync(&r1, &s1).unwrap();
        let r2 = phone.prepare_sync().unwrap();
        let s2 = server.sync(&r2);
        phone.apply_sync(&r2, &s2).unwrap();
        let r3 = laptop.prepare_sync().unwrap();
        let s3 = server.sync(&r3);
        laptop.apply_sync(&r3, &s3).unwrap();
        assert_eq!(phone.get("games", "g1").unwrap().unwrap().data["pgn"], "1. c4 *");
        assert_eq!(laptop.get("games", "g1").unwrap().unwrap().data["pgn"], "1. c4 *");

        // Silme yayılır
        phone.delete("games", "g1").unwrap();
        let r = phone.prepare_sync().unwrap();
        let s = server.sync(&r);
        phone.apply_sync(&r, &s).unwrap();
        let r = laptop.prepare_sync().unwrap();
        let s = server.sync(&r);
        laptop.apply_sync(&r, &s).unwrap();
        assert!(laptop.get("games", "g1").unwrap().is_none());
        assert_eq!(laptop.list("games").unwrap().len(), 0);
    }
}
