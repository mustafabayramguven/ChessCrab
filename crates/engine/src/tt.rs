//! Transpozisyon tablosu.

use shakmaty::Move;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Bound {
    Exact,
    Lower,
    Upper,
}

#[derive(Clone, Copy, Debug)]
pub struct Entry {
    pub key: u64,
    pub mv: Option<Move>,
    pub score: i32,
    pub eval: i32,
    pub depth: i16,
    pub bound: Bound,
    pub age: u8,
}

pub struct TranspositionTable {
    entries: Vec<Option<Entry>>,
    mask: usize,
    age: u8,
}

impl TranspositionTable {
    /// `mb` megabaytlık tablo (2'nin kuvvetine yuvarlanır).
    pub fn new(mb: usize) -> Self {
        let entry_size = std::mem::size_of::<Option<Entry>>().max(1);
        let want = (mb.max(1) * 1024 * 1024) / entry_size;
        let mut n = 1usize;
        while n * 2 <= want {
            n *= 2;
        }
        Self { entries: vec![None; n], mask: n - 1, age: 0 }
    }

    pub fn clear(&mut self) {
        self.entries.iter_mut().for_each(|e| *e = None);
        self.age = 0;
    }

    pub fn new_search(&mut self) {
        self.age = self.age.wrapping_add(1);
    }

    #[inline]
    pub fn probe(&self, key: u64) -> Option<Entry> {
        match self.entries[key as usize & self.mask] {
            Some(e) if e.key == key => Some(e),
            _ => None,
        }
    }

    #[inline]
    pub fn store(&mut self, key: u64, mv: Option<Move>, score: i32, eval: i32, depth: i32, bound: Bound) {
        let slot = &mut self.entries[key as usize & self.mask];
        let replace = match slot {
            None => true,
            Some(old) => old.key == key || old.age != self.age || depth as i16 >= old.depth - 2,
        };
        if replace {
            let mv = mv.or_else(|| slot.filter(|o| o.key == key).and_then(|o| o.mv));
            *slot = Some(Entry { key, mv, score, eval, depth: depth as i16, bound, age: self.age });
        }
    }

    /// Doluluk (binde), UCI hashfull için.
    pub fn hashfull(&self) -> u32 {
        let n = self.entries.len().min(1000);
        let used = self.entries[..n].iter().filter(|e| matches!(e, Some(x) if x.age == self.age)).count();
        (used * 1000 / n.max(1)) as u32
    }
}
