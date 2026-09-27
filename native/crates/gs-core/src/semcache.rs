//! Semantic response cache.
//!
//! Near-identical queries get the previous answer. The embedding reuses the
//! CLIP text tower that is already built and linked, so this costs no new model
//! and no new download.
//!
//! The risk with a semantic cache is not the storage, it is the threshold. Too
//! low and "how do I cancel my order" is answered with the refund text. Too
//! high and the cache never hits and the whole mechanism is dead weight. This
//! module makes the threshold an explicit parameter and the hit rate a
//! measured output, not an assumption.

use std::collections::BTreeMap;

/// One stored exchange.
#[derive(Debug, Clone)]
pub struct Entry {
    pub query: String,
    pub response: String,
    pub embedding: Vec<f32>,
}

/// A cosine-similarity response cache over a fixed number of entries.
///
/// Bounded on purpose: an unbounded semantic cache is a memory leak with extra
/// steps, and the oldest entries are the least likely to be paraphrases of
/// anything current.
#[derive(Debug, Default)]
pub struct SemanticCache {
    entries: Vec<Entry>,
    capacity: usize,
    hits: u64,
    misses: u64,
}

#[derive(Debug, Clone)]
pub struct Lookup {
    pub hit: bool,
    pub response: Option<String>,
    pub matched_query: Option<String>,
    pub similarity: f32,
    pub embedder: &'static str,
}

impl SemanticCache {
    pub fn new(capacity: usize) -> Self {
        Self { entries: Vec::new(), capacity: capacity.max(1), hits: 0, misses: 0 }
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    pub fn stats(&self) -> (u64, u64) {
        (self.hits, self.misses)
    }

    /// Hit rate over all lookups. Zero lookups is 0.0, not NaN.
    pub fn hit_rate(&self) -> f64 {
        let total = self.hits + self.misses;
        if total == 0 {
            0.0
        } else {
            self.hits as f64 / total as f64
        }
    }

    /// Store an exchange.
    pub fn put(&mut self, query: &str, response: &str, embedding: Vec<f32>) {
        if self.entries.len() >= self.capacity {
            // Evict the entry least similar to the newcomer: it is the one
            // least likely to be part of a paraphrase family still in use.
            let new_sim = |e: &Entry| cosine(&embedding, &e.embedding);
            let mut worst = 0usize;
            let mut worst_sim = f32::INFINITY;
            for (i, e) in self.entries.iter().enumerate() {
                let s = new_sim(e);
                if s < worst_sim {
                    worst_sim = s;
                    worst = i;
                }
            }
            self.entries.remove(worst);
        }
        self.entries.push(Entry {
            query: query.to_string(),
            response: response.to_string(),
            embedding,
        });
    }

    /// Find the nearest stored query above `threshold`.
    pub fn get(&mut self, query: &str, embedding: &[f32], threshold: f32) -> Lookup {
        let mut nearest: Option<(usize, f32)> = None;
        let mut above: Option<(usize, f32)> = None;
        for (i, e) in self.entries.iter().enumerate() {
            let s = cosine(embedding, &e.embedding);
            if nearest.map_or(true, |(_, bs)| s > bs) {
                nearest = Some((i, s));
            }
            if s >= threshold && above.map_or(true, |(_, bs)| s > bs) {
                above = Some((i, s));
            }
        }
        // A miss reports the nearest similarity it saw, not 0.0. Reporting zero
        // hides how far below the threshold the miss fell, which is the number
        // needed to choose a threshold at all.
        let nearest_sim = nearest.map(|(_, s)| s).unwrap_or(0.0);
        let best = above;
        match best {
            Some((i, s)) => {
                self.hits += 1;
                Lookup {
                    hit: true,
                    response: Some(self.entries[i].response.clone()),
                    matched_query: Some(self.entries[i].query.clone()),
                    similarity: s,
                    embedder: "clip-text",
                }
            }
            None => {
                self.misses += 1;
                Lookup {
                    hit: false,
                    response: None,
                    matched_query: None,
                    similarity: nearest_sim,
                    embedder: "clip-text",
                }
            }
        }
    }

    /// Entries as a map, for serialisation or inspection.
    pub fn snapshot(&self) -> BTreeMap<String, String> {
        self.entries.iter().map(|e| (e.query.clone(), e.response.clone())).collect()
    }
}

pub fn cosine(a: &[f32], b: &[f32]) -> f32 {
    let n = a.len().min(b.len());
    if n == 0 {
        return 0.0;
    }
    let (mut dot, mut na, mut nb) = (0f32, 0f32, 0f32);
    for i in 0..n {
        dot += a[i] * b[i];
        na += a[i] * a[i];
        nb += b[i] * b[i];
    }
    let d = (na.sqrt() * nb.sqrt()).max(1e-12);
    dot / d
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(x: f32) -> Vec<f32> {
        vec![x, 1.0 - x, 0.1]
    }

    #[test]
    fn an_empty_cache_always_misses() {
        let mut c = SemanticCache::new(8);
        let l = c.get("q", &v(0.5), 0.95);
        assert!(!l.hit);
        assert_eq!(c.hit_rate(), 0.0, "no lookups must not divide by zero");
    }

    #[test]
    fn a_near_duplicate_hits_and_a_distant_one_misses() {
        let mut c = SemanticCache::new(8);
        c.put("how do I reset my password", "Open Settings, choose Security.", v(0.50));
        let near = c.get("how do I reset my passwd", &v(0.51), 0.95);
        assert!(near.hit, "similarity {} should clear 0.95", near.similarity);
        let far = c.get("what is the weather in Oslo", &v(0.05), 0.95);
        assert!(!far.hit);
    }

    #[test]
    fn the_threshold_actually_gates() {
        let mut c = SemanticCache::new(8);
        c.put("a", "A", v(0.50));
        // 0.50 vs 0.70 is a real cosine distance; a strict threshold must refuse.
        assert!(!c.get("b", &v(0.70), 0.95).hit);
        assert!(c.get("b", &v(0.70), 0.10).hit, "a loose threshold must admit");
    }

    #[test]
    fn capacity_is_bounded_and_evicts() {
        let mut c = SemanticCache::new(3);
        for i in 0..10 {
            c.put(&format!("q{i}"), &format!("r{i}"), v(i as f32 / 10.0));
        }
        assert_eq!(c.len(), 3, "cache must not grow past capacity");
    }

    #[test]
    fn hit_rate_is_hits_over_lookups() {
        let mut c = SemanticCache::new(8);
        c.put("x", "X", v(0.5));
        c.get("x", &v(0.5), 0.95);
        c.get("y", &v(0.0), 0.95);
        let (h, m) = c.stats();
        assert_eq!((h, m), (1, 1));
        assert!((c.hit_rate() - 0.5).abs() < 1e-9);
    }
}
