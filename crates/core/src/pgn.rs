//! PGN içe/dışa aktarma (ana hat; varyasyonlar atlanır).

use std::ops::ControlFlow;

use pgn_reader::{RawComment, RawTag, Reader, SanPlus, Skip, Visitor};

use crate::game::{Game, GameError, GameStatus, Side, Termination};

/// Oyunu PGN olarak yaz.
pub fn to_pgn(game: &Game) -> String {
    let mut out = String::new();
    let result = game.status().result_str();
    let mut tags: Vec<(String, String)> = Vec::new();
    for key in ["Event", "Site", "Date", "Round", "White", "Black"] {
        let v = game.tag(key).unwrap_or(match key {
            "Event" => "ChessCrab",
            "Site" => "ChessCrab",
            "Round" => "-",
            _ => "?",
        });
        tags.push((key.into(), v.into()));
    }
    tags.push(("Result".into(), result.into()));
    if game.start_fen() != crate::START_FEN {
        tags.push(("SetUp".into(), "1".into()));
        tags.push(("FEN".into(), game.start_fen().into()));
    }
    if let GameStatus::Finished { termination, .. } = game.status() {
        let t = match termination {
            Termination::Timeout => "time forfeit",
            Termination::Abandoned => "abandoned",
            _ => "normal",
        };
        tags.push(("Termination".into(), t.into()));
    }
    for (k, v) in &game.tags {
        if !tags.iter().any(|(tk, _)| tk == k) {
            tags.push((k.clone(), v.clone()));
        }
    }
    for (k, v) in tags {
        out.push_str(&format!("[{} \"{}\"]\n", k, v.replace('\\', "\\\\").replace('"', "\\\"")));
    }
    out.push('\n');

    let start = game.start_position();
    let mut fullmove = u32::from(shakmaty::Position::fullmoves(start));
    let mut white_to_move = shakmaty::Position::turn(start).is_white();
    let mut line = String::new();
    for (i, rec) in game.records().iter().enumerate() {
        if white_to_move {
            line.push_str(&format!("{fullmove}. "));
        } else if i == 0 {
            line.push_str(&format!("{fullmove}... "));
        }
        line.push_str(&rec.san);
        if let Some(ms) = rec.clock_ms {
            let s = ms / 1000;
            line.push_str(&format!(" {{[%clk {}:{:02}:{:02}]}}", s / 3600, (s / 60) % 60, s % 60));
        }
        line.push(' ');
        if !white_to_move {
            fullmove += 1;
        }
        white_to_move = !white_to_move;
    }
    line.push_str(result);
    // 80 karakterde satır kır
    let mut col = 0;
    for word in line.split(' ') {
        if col + word.len() + 1 > 80 && col > 0 {
            out.push('\n');
            col = 0;
        } else if col > 0 {
            out.push(' ');
            col += 1;
        }
        out.push_str(word);
        col += word.len();
    }
    out.push('\n');
    out
}

struct Collector;

struct Movetext {
    game: Game,
    error: Option<GameError>,
    pending_clock: Option<u64>,
    result: Option<String>,
}

fn parse_clk(comment: &str) -> Option<u64> {
    let i = comment.find("[%clk ")?;
    let rest = &comment[i + 6..];
    let end = rest.find(']')?;
    let parts: Vec<&str> = rest[..end].trim().split(':').collect();
    let (h, m, s) = match parts.as_slice() {
        [h, m, s] => (h.parse::<u64>().ok()?, m.parse::<u64>().ok()?, s.parse::<f64>().ok()?),
        [m, s] => (0, m.parse::<u64>().ok()?, s.parse::<f64>().ok()?),
        _ => return None,
    };
    Some(h * 3_600_000 + m * 60_000 + (s * 1000.0) as u64)
}

impl Visitor for Collector {
    type Tags = Vec<(String, String)>;
    type Movetext = Movetext;
    type Output = Result<Game, GameError>;

    fn begin_tags(&mut self) -> ControlFlow<Self::Output, Self::Tags> {
        ControlFlow::Continue(Vec::new())
    }

    fn tag(&mut self, tags: &mut Self::Tags, name: &[u8], value: RawTag<'_>) -> ControlFlow<Self::Output> {
        tags.push((String::from_utf8_lossy(name).into_owned(), value.decode_utf8_lossy().into_owned()));
        ControlFlow::Continue(())
    }

    fn begin_movetext(&mut self, tags: Self::Tags) -> ControlFlow<Self::Output, Self::Movetext> {
        let fen = tags.iter().find(|(k, _)| k == "FEN").map(|(_, v)| v.as_str());
        let game = match fen {
            Some(f) => match Game::from_fen(f) {
                Ok(g) => g,
                Err(e) => return ControlFlow::Break(Err(e)),
            },
            None => Game::new(),
        };
        let mut mt = Movetext { game, error: None, pending_clock: None, result: None };
        for (k, v) in tags {
            match k.as_str() {
                "FEN" | "SetUp" => {}
                "Result" => mt.result = Some(v),
                _ => mt.game.tags.push((k, v)),
            }
        }
        ControlFlow::Continue(mt)
    }

    fn san(&mut self, mt: &mut Self::Movetext, san_plus: SanPlus) -> ControlFlow<Self::Output> {
        if mt.error.is_some() {
            return ControlFlow::Continue(());
        }
        let pos = mt.game.position().clone();
        match san_plus.san.to_move(&pos) {
            Ok(m) => {
                if let Err(e) = mt.game.play_move(m, None) {
                    mt.error = Some(e);
                }
            }
            Err(_) => mt.error = Some(GameError::IllegalMove(san_plus.to_string())),
        }
        let _ = mt.pending_clock.take();
        ControlFlow::Continue(())
    }

    fn comment(&mut self, mt: &mut Self::Movetext, comment: RawComment<'_>) -> ControlFlow<Self::Output> {
        let text = String::from_utf8_lossy(comment.as_bytes());
        if let Some(ms) = parse_clk(&text) {
            mt.game.set_last_clock(ms);
        }
        ControlFlow::Continue(())
    }

    fn begin_variation(&mut self, _mt: &mut Self::Movetext) -> ControlFlow<Self::Output, Skip> {
        ControlFlow::Continue(Skip(true))
    }

    fn end_game(&mut self, mut mt: Self::Movetext) -> Self::Output {
        if let Some(e) = mt.error {
            return Err(e);
        }
        if !mt.game.status().is_over() {
            match mt.result.as_deref() {
                Some("1-0") => mt.game.finish(Some(Side::White), Termination::Resignation),
                Some("0-1") => mt.game.finish(Some(Side::Black), Termination::Resignation),
                Some("1/2-1/2") => mt.game.finish(None, Termination::Agreement),
                _ => {}
            }
        }
        Ok(mt.game)
    }
}

/// Tek bir PGN oyununu oku.
pub fn from_pgn(pgn: &str) -> Result<Game, GameError> {
    let mut reader = Reader::new(std::io::Cursor::new(pgn.as_bytes()));
    match reader.read_game(&mut Collector) {
        Ok(Some(r)) => r,
        Ok(None) => Err(GameError::Pgn("oyun bulunamadı".into())),
        Err(e) => Err(GameError::Pgn(e.to_string())),
    }
}

/// Birden çok oyun içeren PGN'i oku (hatalı oyunlar atlanır).
pub fn from_pgn_multi(pgn: &str) -> Vec<Result<Game, GameError>> {
    let mut reader = Reader::new(std::io::Cursor::new(pgn.as_bytes()));
    let mut out = Vec::new();
    while let Ok(Some(r)) = reader.read_game(&mut Collector) {
        out.push(r);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip() {
        let mut g = Game::new();
        for m in ["e4", "e5", "Nf3", "Nc6", "Bc4", "Nd4", "Nxe5", "Qg5", "Nxf7", "Qxg2", "Rf1", "Qxe4+", "Be2", "Nf3#"] {
            g.play(m).unwrap();
        }
        g.set_tag("White", "Mustafa");
        let pgn = to_pgn(&g);
        assert!(pgn.contains("[White \"Mustafa\"]"));
        assert!(pgn.replace('\n', " ").contains("7. Be2 Nf3# 0-1"), "{pgn}");
        let back = from_pgn(&pgn).unwrap();
        assert_eq!(back.sans(), g.sans());
        assert_eq!(back.status(), g.status());
    }

    #[test]
    fn lichess_style_pgn_with_clock_and_variations() {
        let pgn = r#"[Event "Rated Blitz game"]
[White "a"]
[Black "b"]
[Result "1-0"]

1. e4 { [%clk 0:03:00] } 1... c5 { [%clk 0:03:00] } 2. Nf3 (2. c3 d5) 2... d6 { [%clk 0:02:58] } 3. d4 1-0"#;
        let g = from_pgn(pgn).unwrap();
        assert_eq!(g.sans(), vec!["e4", "c5", "Nf3", "d6", "d4"]);
        assert_eq!(g.records()[0].clock_ms, Some(180_000));
        assert_eq!(g.records()[3].clock_ms, Some(178_000));
        assert_eq!(g.status().result_str(), "1-0");
    }

    #[test]
    fn from_fen_black_to_move() {
        let mut g = Game::from_fen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1").unwrap();
        g.play("e5").unwrap();
        let pgn = to_pgn(&g);
        assert!(pgn.contains("1... e5"));
        assert_eq!(from_pgn(&pgn).unwrap().sans(), vec!["e5"]);
    }
}
