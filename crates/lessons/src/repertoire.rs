//! Repertuvar çalışması (Chessable tarzı): rakibin hamleleri otomatik oynanır,
//! sen kendi hamlelerini hatırlamalısın. Her hat bir SRS kartıdır.

use std::collections::HashMap;

use serde::Serialize;
use shakmaty::{Chess, Color, Move, Position};

use chesscrab_core::{GameError, parse_move, play_line, san_of, to_fen, tokenize_line, uci_of};

use crate::model::{Orientation, Repertoire};

/// Pozisyon anahtarı (FEN'in ilk 4 alanı)
pub fn pos_key(pos: &Chess) -> String {
    to_fen(pos).split(' ').take(4).collect::<Vec<_>>().join(" ")
}

impl Repertoire {
    pub fn card_id(&self, line: usize) -> String {
        format!("rep:{}:{}", self.id, line)
    }

    fn color(&self) -> Color {
        match self.side {
            Orientation::White => Color::White,
            Orientation::Black => Color::Black,
        }
    }

    /// Bizim sıramız olan her pozisyon için repertuvardaki hamleler.
    pub fn tree(&self) -> Result<HashMap<String, Vec<Move>>, GameError> {
        let mut map: HashMap<String, Vec<Move>> = HashMap::new();
        for line in &self.lines {
            let mut pos = Chess::default();
            for tok in tokenize_line(&line.moves) {
                let m = parse_move(&pos, tok)?;
                if pos.turn() == self.color() {
                    let e = map.entry(pos_key(&pos)).or_default();
                    if !e.contains(&m) {
                        e.push(m);
                    }
                }
                pos.play_unchecked(m);
            }
        }
        Ok(map)
    }

    pub fn validate(&self) -> Result<(), String> {
        for l in &self.lines {
            play_line(&Chess::default(), &l.moves).map_err(|e| format!("{} / {}: {e}", self.id, l.name))?;
        }
        // Aynı pozisyonda bizim için iki farklı hamle olmamalı (repertuvar tutarlılığı)
        let tree = self.tree().map_err(|e| e.to_string())?;
        for (k, v) in tree {
            if v.len() > 1 {
                return Err(format!("{}: {k} pozisyonunda birden çok hamle var", self.id));
            }
        }
        Ok(())
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct DrillView {
    pub repertoire_id: String,
    pub line_index: usize,
    pub line_name: String,
    pub fen: String,
    pub orientation: Orientation,
    pub last_move: Option<String>,
    pub done: bool,
    pub mistakes: u32,
    pub progress: (usize, usize),
    pub note: String,
    pub legal_moves: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct DrillFeedback {
    pub correct: bool,
    pub expected_san: Option<String>,
    pub reply_san: Option<String>,
    pub done: bool,
}

pub struct Drill {
    rep_id: String,
    line_index: usize,
    line_name: String,
    note: String,
    side: Orientation,
    color: Color,
    moves: Vec<Move>,
    idx: usize,
    pos: Chess,
    last_move: Option<String>,
    mistakes: u32,
    tree: HashMap<String, Vec<Move>>,
}

impl Drill {
    pub fn new(rep: &Repertoire, line_index: usize) -> Result<Self, GameError> {
        let line = rep.lines.get(line_index).ok_or_else(|| GameError::Pgn("hat yok".into()))?;
        let (_, moves) = play_line(&Chess::default(), &line.moves)?;
        let mut d = Drill {
            rep_id: rep.id.clone(),
            line_index,
            line_name: line.name.clone(),
            note: line.note.clone(),
            side: rep.side,
            color: rep.color(),
            moves,
            idx: 0,
            pos: Chess::default(),
            last_move: None,
            mistakes: 0,
            tree: rep.tree()?,
        };
        d.auto_play();
        Ok(d)
    }

    fn auto_play(&mut self) {
        while self.idx < self.moves.len() && self.pos.turn() != self.color {
            let m = self.moves[self.idx];
            self.pos.play_unchecked(m);
            self.last_move = Some(uci_of(m));
            self.idx += 1;
        }
    }

    pub fn is_done(&self) -> bool {
        self.idx >= self.moves.len()
    }

    pub fn mistakes(&self) -> u32 {
        self.mistakes
    }

    pub fn view(&self) -> DrillView {
        DrillView {
            repertoire_id: self.rep_id.clone(),
            line_index: self.line_index,
            line_name: self.line_name.clone(),
            fen: to_fen(&self.pos),
            orientation: self.side,
            last_move: self.last_move.clone(),
            done: self.is_done(),
            mistakes: self.mistakes,
            progress: (self.idx, self.moves.len()),
            note: if self.is_done() { self.note.clone() } else { String::new() },
            legal_moves: if self.is_done() { vec![] } else { self.pos.legal_moves().into_iter().map(uci_of).collect() },
        }
    }

    /// Kullanıcının hamlesi. Yanlışsa doğru hamle gösterilir ve oynanır (hata sayılır).
    pub fn submit(&mut self, text: &str) -> DrillFeedback {
        if self.is_done() {
            return DrillFeedback { correct: false, expected_san: None, reply_san: None, done: true };
        }
        let expected = self.moves[self.idx];
        let exp_san = san_of(&self.pos, expected);
        let played = parse_move(&self.pos, text).ok();
        // Aynı pozisyon için repertuvardaki herhangi bir hamle (transpozisyon) doğru sayılır
        let ok_moves = self.tree.get(&pos_key(&self.pos)).cloned().unwrap_or_default();
        let correct = played == Some(expected) || played.map(|p| ok_moves.contains(&p)).unwrap_or(false);
        if !correct {
            // İlerleme yok: doğru hamleyi göster, kullanıcı oynasın
            self.mistakes += 1;
            return DrillFeedback { correct: false, expected_san: Some(exp_san), reply_san: None, done: false };
        }
        self.pos.play_unchecked(expected);
        self.last_move = Some(uci_of(expected));
        self.idx += 1;
        let reply = if self.idx < self.moves.len() && self.pos.turn() != self.color {
            Some(san_of(&self.pos, self.moves[self.idx]))
        } else {
            None
        };
        self.auto_play();
        DrillFeedback { correct: true, expected_san: None, reply_san: reply, done: self.is_done() }
    }
}
