//! Arama: iteratif derinleşme, aspirasyon pencereleri, PVS, transpozisyon tablosu,
//! null-move, LMR, ters futility, futility, geç hamle budama, killer + history,
//! SEE'li quiescence, tekrar/50 hamle tespiti ve MultiPV.

use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use shakmaty::zobrist::Zobrist64;
use shakmaty::{Chess, Color, EnPassantMode, Move, MoveList, Position, Role};

use crate::eval::{evaluate, piece_value};
use crate::see::see;
use crate::tt::{Bound, TranspositionTable};

pub const INF: i32 = 32_001;
pub const MATE: i32 = 32_000;
pub const MATE_BOUND: i32 = MATE - 1000;
pub const MAX_PLY: usize = 128;
pub const MAX_DEPTH: u32 = 64;

/// Dışarıya gösterilen skor (sırası gelen taraf açısından).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", content = "value", rename_all = "snake_case")]
pub enum Score {
    /// santipiyon
    Cp(i32),
    /// N hamlede mat (pozitif: sırası gelen mat eder)
    Mate(i32),
}

impl Score {
    pub fn from_internal(s: i32) -> Score {
        if s >= MATE_BOUND {
            Score::Mate((MATE - s + 1) / 2)
        } else if s <= -MATE_BOUND {
            Score::Mate(-((MATE + s + 1) / 2))
        } else {
            Score::Cp(s)
        }
    }

    /// Analiz için santipiyona çevir (mat = ±10000 civarı, yakın mat daha büyük).
    pub fn to_cp(self) -> i32 {
        match self {
            Score::Cp(c) => c,
            Score::Mate(n) if n > 0 => 10_000 - n * 10,
            Score::Mate(n) => -10_000 - n * 10,
        }
    }

    pub fn negate(self) -> Score {
        match self {
            Score::Cp(c) => Score::Cp(-c),
            Score::Mate(n) => Score::Mate(-n),
        }
    }

    pub fn uci(self) -> String {
        match self {
            Score::Cp(c) => format!("cp {c}"),
            Score::Mate(n) => format!("mate {n}"),
        }
    }
}

#[derive(Clone, Debug, Default)]
pub struct Limits {
    pub depth: Option<u32>,
    pub nodes: Option<u64>,
    pub movetime_ms: Option<u64>,
    pub wtime: Option<u64>,
    pub btime: Option<u64>,
    pub winc: Option<u64>,
    pub binc: Option<u64>,
    pub movestogo: Option<u32>,
    pub infinite: bool,
    pub multipv: usize,
    /// Sadece bu hamleleri ara (boş = hepsi)
    pub search_moves: Vec<Move>,
}

impl Limits {
    pub fn depth(d: u32) -> Self {
        Self { depth: Some(d), ..Default::default() }
    }
    pub fn movetime(ms: u64) -> Self {
        Self { movetime_ms: Some(ms), ..Default::default() }
    }
    pub fn nodes(n: u64) -> Self {
        Self { nodes: Some(n), ..Default::default() }
    }
}

#[derive(Clone, Debug, Serialize)]
pub struct Line {
    pub score: Score,
    #[serde(skip)]
    pub pv: Vec<Move>,
    pub pv_uci: Vec<String>,
}

#[derive(Clone, Debug, Serialize)]
pub struct SearchResult {
    #[serde(skip)]
    pub best: Option<Move>,
    pub best_uci: Option<String>,
    pub score: Score,
    pub depth: u32,
    pub seldepth: u32,
    pub nodes: u64,
    pub time_ms: u64,
    /// MultiPV satırları, en iyiden kötüye
    pub lines: Vec<Line>,
}

#[derive(Clone, Debug)]
pub struct Info {
    pub depth: u32,
    pub seldepth: u32,
    pub multipv: usize,
    pub score: Score,
    pub nodes: u64,
    pub nps: u64,
    pub time_ms: u64,
    pub hashfull: u32,
    pub pv: Vec<Move>,
}

pub struct Searcher {
    pub tt: TranspositionTable,
    killers: Vec<[Option<Move>; 2]>,
    history: Box<[[i32; 64]; 12]>,
    hashes: Vec<u64>,
    nodes: u64,
    seldepth: usize,
    stop: Arc<AtomicBool>,
    stopped: bool,
    soft_deadline: Option<Instant>,
    hard_deadline: Option<Instant>,
    node_limit: Option<u64>,
    start: Instant,
    pv: Vec<Vec<Move>>,
    root_excluded: Vec<Move>,
    root_only: Vec<Move>,
}

#[inline]
fn hash_of(pos: &Chess) -> u64 {
    pos.zobrist_hash::<Zobrist64>(EnPassantMode::Legal).0
}

#[inline]
fn piece_index(color: Color, role: Role) -> usize {
    color as usize * 6 + role as usize - 1
}

fn has_non_pawn_material(pos: &Chess) -> bool {
    let b = pos.board();
    let us = b.by_color(pos.turn());
    ((b.knights() | b.bishops() | b.rooks() | b.queens()) & us).any()
}

fn is_tactical(m: &Move) -> bool {
    m.is_capture() || m.is_promotion()
}

fn score_to_tt(s: i32, ply: usize) -> i32 {
    if s >= MATE_BOUND {
        s + ply as i32
    } else if s <= -MATE_BOUND {
        s - ply as i32
    } else {
        s
    }
}

fn score_from_tt(s: i32, ply: usize) -> i32 {
    if s >= MATE_BOUND {
        s - ply as i32
    } else if s <= -MATE_BOUND {
        s + ply as i32
    } else {
        s
    }
}

/// LMR indirgeme tablosu
fn lmr(depth: i32, idx: usize) -> i32 {
    if depth < 3 || idx < 3 {
        return 0;
    }
    let r = 0.75 + (depth as f64).ln() * (idx as f64).ln() / 2.25;
    r as i32
}

impl Searcher {
    pub fn new(hash_mb: usize) -> Self {
        Self {
            tt: TranspositionTable::new(hash_mb),
            killers: vec![[None; 2]; MAX_PLY + 2],
            history: Box::new([[0; 64]; 12]),
            hashes: Vec::with_capacity(512),
            nodes: 0,
            seldepth: 0,
            stop: Arc::new(AtomicBool::new(false)),
            stopped: false,
            soft_deadline: None,
            hard_deadline: None,
            node_limit: None,
            start: Instant::now(),
            pv: vec![Vec::new(); MAX_PLY + 2],
            root_excluded: Vec::new(),
            root_only: Vec::new(),
        }
    }

    /// Aramayı dışarıdan durdurmak için bayrak.
    pub fn stop_handle(&self) -> Arc<AtomicBool> {
        self.stop.clone()
    }

    pub fn set_stop_handle(&mut self, h: Arc<AtomicBool>) {
        self.stop = h;
    }

    pub fn new_game(&mut self) {
        self.tt.clear();
        self.history.iter_mut().for_each(|r| r.iter_mut().for_each(|v| *v = 0));
    }

    /// `history`: oyunda bu pozisyondan önceki pozisyonların zobrist anahtarları
    /// (tekrar tespiti için). Boş olabilir.
    pub fn search(
        &mut self,
        pos: &Chess,
        history: &[u64],
        limits: &Limits,
        mut on_info: impl FnMut(&Info),
    ) -> SearchResult {
        self.start = Instant::now();
        self.nodes = 0;
        self.stopped = false;
        self.tt.new_search();
        for k in self.killers.iter_mut() {
            *k = [None; 2];
        }
        for row in self.history.iter_mut() {
            for v in row.iter_mut() {
                *v /= 8;
            }
        }
        self.setup_time(pos, limits);
        self.node_limit = limits.nodes;
        self.hashes.clear();
        self.hashes.extend_from_slice(history);
        let root_hash = hash_of(pos);
        self.hashes.push(root_hash);
        self.root_only = limits.search_moves.clone();

        let legal = pos.legal_moves();
        let n_root = if self.root_only.is_empty() {
            legal.len()
        } else {
            legal.iter().filter(|m| self.root_only.contains(m)).count()
        };
        let multipv = limits.multipv.max(1).min(n_root.max(1));

        let mut result = SearchResult {
            best: None,
            best_uci: None,
            score: Score::Cp(0),
            depth: 0,
            seldepth: 0,
            nodes: 0,
            time_ms: 0,
            lines: Vec::new(),
        };

        if legal.is_empty() {
            result.score = if pos.is_check() { Score::Mate(0) } else { Score::Cp(0) };
            return result;
        }
        // Tek yasal hamle varsa ve süreli oyunsa hemen oyna
        let fallback = legal
            .iter()
            .find(|m| self.root_only.is_empty() || self.root_only.contains(m))
            .copied()
            .unwrap_or(legal[0]);
        result.best = Some(fallback);
        result.best_uci = Some(chesscrab_core::uci_of(fallback));

        let max_depth = limits.depth.unwrap_or(MAX_DEPTH).clamp(1, MAX_DEPTH);
        let mut prev_scores: Vec<i32> = vec![0; multipv];

        'deepening: for depth in 1..=max_depth {
            let mut lines: Vec<Line> = Vec::with_capacity(multipv);
            self.root_excluded.clear();
            self.seldepth = 0;
            for k in 0..multipv {
                let d = depth as i32;
                let mut delta = 25;
                let (mut alpha, mut beta) = if depth >= 5 {
                    (prev_scores[k] - delta, prev_scores[k] + delta)
                } else {
                    (-INF, INF)
                };
                let score = loop {
                    let s = self.negamax(pos, root_hash, d, alpha, beta, 0, true, false);
                    if self.stopped {
                        break s;
                    }
                    if s <= alpha {
                        beta = (alpha + beta) / 2;
                        alpha = (s - delta).max(-INF);
                    } else if s >= beta {
                        beta = (s + delta).min(INF);
                    } else {
                        break s;
                    }
                    delta += delta / 2;
                    if delta > 1000 {
                        alpha = -INF;
                        beta = INF;
                    }
                };
                if self.stopped {
                    // Bu derinliğin ilk satırı bile bitmediyse önceki derinliği koru
                    if k == 0 {
                        break 'deepening;
                    }
                    break;
                }
                let pv = self.pv[0].clone();
                if pv.is_empty() {
                    break;
                }
                prev_scores[k] = score;
                self.root_excluded.push(pv[0]);
                let line = Line {
                    score: Score::from_internal(score),
                    pv_uci: pv.iter().map(|m| chesscrab_core::uci_of(*m)).collect(),
                    pv,
                };
                let elapsed = self.start.elapsed().as_millis() as u64;
                on_info(&Info {
                    depth,
                    seldepth: self.seldepth as u32,
                    multipv: k + 1,
                    score: line.score,
                    nodes: self.nodes,
                    nps: self.nodes * 1000 / elapsed.max(1),
                    time_ms: elapsed,
                    hashfull: self.tt.hashfull(),
                    pv: line.pv.clone(),
                });
                lines.push(line);
            }
            if lines.is_empty() {
                break;
            }
            // MultiPV satırlarını skora göre sırala
            lines.sort_by_key(|l| std::cmp::Reverse(l.score.to_cp()));
            result.best = lines[0].pv.first().copied();
            result.best_uci = result.best.map(chesscrab_core::uci_of);
            result.score = lines[0].score;
            result.depth = depth;
            result.seldepth = self.seldepth as u32;
            result.lines = lines;

            if self.stopped {
                break;
            }
            // Mat bulunduysa ve derinlik yeterliyse bitir
            if let Score::Mate(n) = result.score
                && limits.depth.is_none() && !limits.infinite && (n.unsigned_abs() * 2) < depth {
                    break;
                }
            if let Some(soft) = self.soft_deadline
                && Instant::now() >= soft {
                    break;
                }
        }
        result.nodes = self.nodes;
        result.time_ms = self.start.elapsed().as_millis() as u64;
        result
    }

    fn setup_time(&mut self, pos: &Chess, l: &Limits) {
        self.soft_deadline = None;
        self.hard_deadline = None;
        if l.infinite {
            return;
        }
        let now = Instant::now();
        if let Some(mt) = l.movetime_ms {
            let d = Duration::from_millis(mt.saturating_sub(5).max(1));
            self.soft_deadline = Some(now + d);
            self.hard_deadline = Some(now + d);
            return;
        }
        let (time, inc) = match pos.turn() {
            Color::White => (l.wtime, l.winc.unwrap_or(0)),
            Color::Black => (l.btime, l.binc.unwrap_or(0)),
        };
        if let Some(t) = time {
            let mtg = l.movestogo.unwrap_or(30).clamp(1, 50) as u64;
            let base = t / mtg + inc * 3 / 4;
            let soft = base.min(t / 3).max(1);
            let hard = (base * 3).min(t / 2).max(soft);
            self.soft_deadline = Some(now + Duration::from_millis(soft.saturating_sub(10).max(1)));
            self.hard_deadline = Some(now + Duration::from_millis(hard.saturating_sub(10).max(1)));
        }
    }

    #[inline]
    fn check_stop(&mut self) {
        if self.nodes & 1023 == 0 {
            if self.stop.load(Ordering::Relaxed) {
                self.stopped = true;
            }
            if let Some(h) = self.hard_deadline
                && Instant::now() >= h {
                    self.stopped = true;
                }
        }
        if let Some(n) = self.node_limit
            && self.nodes >= n {
                self.stopped = true;
            }
    }

    fn is_repetition(&self, halfmoves: u32) -> bool {
        let n = self.hashes.len();
        if n < 5 {
            return false;
        }
        let cur = self.hashes[n - 1];
        let limit = (halfmoves as usize).min(n - 1);
        let mut i = 2;
        while i <= limit {
            if self.hashes[n - 1 - i] == cur {
                return true;
            }
            i += 2;
        }
        false
    }

    #[inline]
    fn child(pos: &Chess, h: u64, m: Move) -> (Chess, u64) {
        let inc = pos.update_zobrist_hash::<Zobrist64>(Zobrist64(h), m, EnPassantMode::Legal);
        let mut c = pos.clone();
        c.play_unchecked(m);
        let nh = match inc {
            Some(z) => z.0,
            None => hash_of(&c),
        };
        (c, nh)
    }

    fn order_moves(&self, pos: &Chess, moves: &MoveList, tt_move: Option<Move>, ply: usize) -> Vec<(Move, i32)> {
        let us = pos.turn();
        moves
            .iter()
            .map(|&m| {
                let s = if Some(m) == tt_move {
                    10_000_000
                } else if is_tactical(&m) {
                    let victim = m.capture().map(piece_value).unwrap_or(0)
                        + m.promotion().map(piece_value).unwrap_or(0);
                    let attacker = piece_value(m.role()) / 10;
                    let base = victim * 10 - attacker;
                    if m.is_en_passant() || see(pos, m) >= 0 {
                        2_000_000 + base
                    } else {
                        -500_000 + base
                    }
                } else if self.killers[ply][0] == Some(m) {
                    1_000_000
                } else if self.killers[ply][1] == Some(m) {
                    900_000
                } else {
                    self.history[piece_index(us, m.role())][m.to() as usize]
                };
                (m, s)
            })
            .collect()
    }

    #[inline]
    fn pick(list: &mut [(Move, i32)], i: usize) -> Move {
        let mut best = i;
        for j in (i + 1)..list.len() {
            if list[j].1 > list[best].1 {
                best = j;
            }
        }
        list.swap(i, best);
        list[i].0
    }

    #[allow(clippy::too_many_arguments)]
    fn negamax(
        &mut self,
        pos: &Chess,
        hash: u64,
        mut depth: i32,
        mut alpha: i32,
        mut beta: i32,
        ply: usize,
        is_pv: bool,
        was_null: bool,
    ) -> i32 {
        self.pv[ply].clear();
        let root = ply == 0;

        if !root {
            self.check_stop();
            if self.stopped {
                return 0;
            }
            if pos.halfmoves() >= 100 || self.is_repetition(pos.halfmoves()) || pos.is_insufficient_material() {
                return 0;
            }
            // mat mesafesi budaması
            alpha = alpha.max(-MATE + ply as i32);
            beta = beta.min(MATE - ply as i32 - 1);
            if alpha >= beta {
                return alpha;
            }
            if ply >= MAX_PLY - 1 {
                return evaluate(pos);
            }
        }

        let in_check = pos.is_check();
        if in_check {
            depth += 1;
        }
        if depth <= 0 {
            return self.qsearch(pos, alpha, beta, ply);
        }
        self.nodes += 1;
        self.seldepth = self.seldepth.max(ply);

        // TT
        let tt = self.tt.probe(hash);
        let mut tt_move = None;
        if let Some(e) = tt {
            tt_move = e.mv;
            if !is_pv && !root && e.depth as i32 >= depth {
                let s = score_from_tt(e.score, ply);
                match e.bound {
                    Bound::Exact => return s,
                    Bound::Lower if s >= beta => return s,
                    Bound::Upper if s <= alpha => return s,
                    _ => {}
                }
            }
        }

        let static_eval = if in_check {
            -INF
        } else {
            tt.map(|e| e.eval).unwrap_or_else(|| evaluate(pos))
        };

        if !is_pv && !in_check {
            // Ters futility (statik null move)
            if depth <= 6 && static_eval - 85 * depth >= beta && beta.abs() < MATE_BOUND {
                return static_eval;
            }
            // Null move
            if !was_null && depth >= 3 && static_eval >= beta && has_non_pawn_material(pos)
                && let Ok(null_pos) = pos.clone().swap_turn() {
                    let r = 3 + depth / 4 + ((static_eval - beta) / 200).min(3);
                    let nh = hash_of(&null_pos);
                    self.hashes.push(nh);
                    let s = -self.negamax(&null_pos, nh, depth - 1 - r, -beta, -beta + 1, ply + 1, false, true);
                    self.hashes.pop();
                    if self.stopped {
                        return 0;
                    }
                    if s >= beta {
                        return if s >= MATE_BOUND { beta } else { s };
                    }
                }
        }

        // İç iteratif derinleşme yerine: TT hamlesi yoksa derinliği azalt
        if tt_move.is_none() && depth >= 6 && is_pv {
            depth -= 1;
        }

        let legal = pos.legal_moves();
        if legal.is_empty() {
            return if in_check { -MATE + ply as i32 } else { 0 };
        }

        let mut list = self.order_moves(pos, &legal, tt_move, ply);
        let mut best_score = -INF;
        let mut best_move: Option<Move> = None;
        let orig_alpha = alpha;
        let mut searched = 0usize;
        let mut quiets_tried: Vec<Move> = Vec::new();
        let us = pos.turn();

        for i in 0..list.len() {
            let m = Self::pick(&mut list, i);
            if root {
                if self.root_excluded.contains(&m) {
                    continue;
                }
                if !self.root_only.is_empty() && !self.root_only.contains(&m) {
                    continue;
                }
            }
            let tactical = is_tactical(&m);
            let is_killer = self.killers[ply].contains(&Some(m));

            if !root && !in_check && !tactical && best_score > -MATE_BOUND {
                // Geç hamle budaması
                if !is_pv && depth <= 4 && searched >= 4 + (depth * depth) as usize * 2 {
                    continue;
                }
                // Futility
                if !is_pv && depth <= 3 && static_eval + 120 + 100 * depth <= alpha && !is_killer {
                    continue;
                }
            }
            // Kötü alımları sığ derinlikte buda
            if !root && !is_pv && tactical && depth <= 3 && best_score > -MATE_BOUND && see(pos, m) < -100 * depth {
                continue;
            }

            let (child, ch) = Self::child(pos, hash, m);
            self.hashes.push(ch);
            let gives_check = child.is_check();

            let mut score;
            if searched == 0 {
                score = -self.negamax(&child, ch, depth - 1, -beta, -alpha, ply + 1, is_pv, false);
            } else {
                let mut r = 0;
                if depth >= 3 && !tactical && !in_check && !gives_check {
                    r = lmr(depth, searched);
                    if is_pv {
                        r -= 1;
                    }
                    if is_killer {
                        r -= 1;
                    }
                    let h = self.history[piece_index(us, m.role())][m.to() as usize];
                    r -= (h / 8000).clamp(-2, 2);
                    r = r.clamp(0, depth - 2);
                }
                score = -self.negamax(&child, ch, depth - 1 - r, -alpha - 1, -alpha, ply + 1, false, false);
                if score > alpha && r > 0 {
                    score = -self.negamax(&child, ch, depth - 1, -alpha - 1, -alpha, ply + 1, false, false);
                }
                if score > alpha && score < beta {
                    score = -self.negamax(&child, ch, depth - 1, -beta, -alpha, ply + 1, true, false);
                }
            }
            self.hashes.pop();
            searched += 1;
            if self.stopped {
                return 0;
            }

            if score > best_score {
                best_score = score;
                best_move = Some(m);
                if score > alpha {
                    alpha = score;
                    // PV güncelle
                    let (head, tail) = self.pv.split_at_mut(ply + 1);
                    let line = &mut head[ply];
                    line.clear();
                    line.push(m);
                    line.extend_from_slice(&tail[0]);
                    if score >= beta {
                        if !tactical {
                            let k = &mut self.killers[ply];
                            if k[0] != Some(m) {
                                k[1] = k[0];
                                k[0] = Some(m);
                            }
                            let bonus = (depth * depth).min(1200);
                            let hi = piece_index(us, m.role());
                            let e = &mut self.history[hi][m.to() as usize];
                            *e += bonus - *e * bonus / 16384;
                            for q in &quiets_tried {
                                let e = &mut self.history[piece_index(us, q.role())][q.to() as usize];
                                *e -= bonus + *e * bonus / 16384;
                            }
                        }
                        break;
                    }
                }
            }
            if !tactical {
                quiets_tried.push(m);
            }
        }

        if searched == 0 {
            // Kökte tüm hamleler dışlandı ya da hepsi budandı
            if root {
                return -INF;
            }
            return static_eval.max(alpha);
        }

        let bound = if best_score >= beta {
            Bound::Lower
        } else if best_score > orig_alpha {
            Bound::Exact
        } else {
            Bound::Upper
        };
        // MultiPV dışlamalı kök aramasını TT'ye yazma (diğer satırları bozar)
        if !(root && !self.root_excluded.is_empty()) {
            self.tt.store(
                hash,
                best_move,
                score_to_tt(best_score, ply),
                if in_check { 0 } else { static_eval },
                depth,
                bound,
            );
        }
        best_score
    }

    fn qsearch(&mut self, pos: &Chess, mut alpha: i32, beta: i32, ply: usize) -> i32 {
        self.pv[ply].clear();
        self.nodes += 1;
        self.check_stop();
        if self.stopped {
            return 0;
        }
        self.seldepth = self.seldepth.max(ply);
        if ply >= MAX_PLY - 1 {
            return evaluate(pos);
        }
        let in_check = pos.is_check();
        let moves = if in_check {
            let l = pos.legal_moves();
            if l.is_empty() {
                return -MATE + ply as i32;
            }
            l
        } else {
            let stand = evaluate(pos);
            if stand >= beta {
                return stand;
            }
            // Delta budaması
            if stand + QUEEN_DELTA < alpha {
                return alpha;
            }
            alpha = alpha.max(stand);
            let mut caps = pos.capture_moves();
            for p in pos.promotion_moves() {
                if !caps.contains(&p) {
                    caps.push(p);
                }
            }
            caps
        };

        let stand_for_cutoff = if in_check { -INF } else { alpha };
        let mut best = stand_for_cutoff;
        let mut list: Vec<(Move, i32)> = moves
            .iter()
            .map(|&m| {
                let victim = m.capture().map(piece_value).unwrap_or(0) + m.promotion().map(piece_value).unwrap_or(0);
                (m, victim * 10 - piece_value(m.role()) / 10)
            })
            .collect();

        for i in 0..list.len() {
            let m = Self::pick(&mut list, i);
            if !in_check && !m.is_en_passant() && see(pos, m) < 0 {
                continue;
            }
            let mut child = pos.clone();
            child.play_unchecked(m);
            let s = -self.qsearch(&child, -beta, -alpha, ply + 1);
            if self.stopped {
                return 0;
            }
            if s > best {
                best = s;
                if s > alpha {
                    alpha = s;
                    if s >= beta {
                        break;
                    }
                }
            }
        }
        best
    }
}

const QUEEN_DELTA: i32 = 1000;

#[cfg(test)]
mod tests {
    use super::*;
    use chesscrab_core::{parse_fen, parse_move};

    fn best(fen: &str, depth: u32) -> (String, Score) {
        let pos = parse_fen(fen).unwrap();
        let mut s = Searcher::new(16);
        let r = s.search(&pos, &[], &Limits::depth(depth), |_| {});
        (r.best_uci.unwrap(), r.score)
    }

    #[test]
    fn mate_in_one() {
        // Arka yatay matı
        let (m, sc) = best("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", 4);
        assert_eq!(m, "a1a8");
        assert_eq!(sc, Score::Mate(1));
    }

    #[test]
    fn mate_in_two() {
        // Klasik: 1.Qxh7+?? değil; 1. Qf6! ... (basit örnek: K+Q vs K)
        let (_m, sc) = best("k7/8/2K5/8/8/8/8/1Q6 w - - 0 1", 6);
        assert!(matches!(sc, Score::Mate(n) if n <= 2), "{sc:?}");
    }

    #[test]
    fn wins_hanging_queen() {
        let (m, _) = best("rnb1kbnr/pppp1ppp/8/4p1q1/4P3/3P4/PPP2PPP/RNBQKBNR w KQkq - 1 3", 5);
        assert_eq!(m, "c1g5");
    }

    #[test]
    fn knight_fork() {
        // Nc7+ çatal: şah ve kale
        let (m, _) = best("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", 6);
        assert_eq!(m, "b5c7");
    }

    #[test]
    fn avoids_stalemate_and_draw_detected() {
        let pos = parse_fen("7k/5Q2/5K2/8/8/8/8/8 w - - 0 1").unwrap();
        let mut s = Searcher::new(8);
        let r = s.search(&pos, &[], &Limits::depth(6), |_| {});
        // Mat bulmalı (Qg7#), pat değil
        assert_eq!(r.score, Score::Mate(1));
        let m = parse_move(&pos, "Qg7").unwrap();
        assert_eq!(r.best, Some(m));
    }

    #[test]
    fn multipv_returns_distinct_lines() {
        let pos = Chess::default();
        let mut s = Searcher::new(16);
        let l = Limits { depth: Some(6), multipv: 3, ..Default::default() };
        let r = s.search(&pos, &[], &l, |_| {});
        assert_eq!(r.lines.len(), 3);
        let firsts: Vec<_> = r.lines.iter().map(|l| l.pv_uci[0].clone()).collect();
        assert_ne!(firsts[0], firsts[1]);
        assert_ne!(firsts[1], firsts[2]);
    }

    #[test]
    fn incremental_hash_matches() {
        let mut pos = Chess::default();
        let mut h = hash_of(&pos);
        let line = "e4 Nf6 e5 d5 exd6 exd6 d4 Be7 Nf3 O-O Bd3 Nc6 O-O Bg4 c4 Qd7 Nc3 Rae8 Qc2 Kh8";
        for t in line.split_whitespace() {
            let m = parse_move(&pos, t).unwrap();
            let (c, nh) = Searcher::child(&pos, h, m);
            assert_eq!(nh, hash_of(&c), "hamle {t}");
            pos = c;
            h = nh;
        }
    }

    #[test]
    fn node_limit_respected() {
        let mut s = Searcher::new(8);
        let r = s.search(&Chess::default(), &[], &Limits::nodes(20_000), |_| {});
        assert!(r.nodes <= 25_000);
        assert!(r.best.is_some());
    }
}
