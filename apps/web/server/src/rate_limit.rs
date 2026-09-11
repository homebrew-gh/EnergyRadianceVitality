//! In-memory attempt limiter for nsec-proof recovery.
//!
//! Process-wide (single-operator companion). Does not log or return the
//! submitted secret.

use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

#[derive(Clone)]
pub struct AttemptLimiter {
    inner: Arc<Mutex<Vec<Instant>>>,
    max: usize,
    window: Duration,
}

impl AttemptLimiter {
    pub fn new(max: usize, window: Duration) -> Self {
        Self {
            inner: Arc::new(Mutex::new(Vec::new())),
            max,
            window,
        }
    }

    fn prune_locked(failures: &mut Vec<Instant>, now: Instant, window: Duration) {
        failures.retain(|t| now.duration_since(*t) <= window);
    }

    /// Whether another attempt may proceed. Does not record a failure.
    pub fn allow(&self) -> bool {
        let mut failures = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let now = Instant::now();
        Self::prune_locked(&mut failures, now, self.window);
        failures.len() < self.max
    }

    pub fn record_failure(&self) {
        let mut failures = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let now = Instant::now();
        Self::prune_locked(&mut failures, now, self.window);
        failures.push(now);
    }

    pub fn reset(&self) {
        self.inner.lock().unwrap_or_else(|e| e.into_inner()).clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allows_until_max_then_blocks() {
        let limiter = AttemptLimiter::new(3, Duration::from_secs(60));
        assert!(limiter.allow());
        limiter.record_failure();
        limiter.record_failure();
        assert!(limiter.allow());
        limiter.record_failure();
        assert!(!limiter.allow());
        limiter.reset();
        assert!(limiter.allow());
    }
}
