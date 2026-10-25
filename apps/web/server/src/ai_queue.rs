//! Single in-flight AI request. A second call is rejected until the first finishes or is cancelled.

use std::sync::Arc;

use tokio::sync::{watch, Mutex};

#[derive(Clone, Default)]
pub struct AiQueue {
    inner: Arc<Mutex<Inner>>,
}

#[derive(Default)]
struct Inner {
    busy: bool,
    generation: u64,
    cancel: Option<watch::Sender<bool>>,
    last_error: Option<String>,
}

pub struct Flight {
    pub generation: u64,
    cancel: watch::Receiver<bool>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AiQueueStatus {
    pub busy: bool,
    pub queued: u32,
    pub last_error: Option<String>,
}

impl AiQueue {
    pub fn new() -> Self {
        Self::default()
    }

    pub async fn try_begin(&self) -> Result<Flight, ()> {
        let mut inner = self.inner.lock().await;
        if inner.busy {
            return Err(());
        }
        inner.busy = true;
        inner.generation = inner.generation.wrapping_add(1);
        let (tx, rx) = watch::channel(false);
        inner.cancel = Some(tx);
        Ok(Flight {
            generation: inner.generation,
            cancel: rx,
        })
    }

    pub async fn finish(&self, generation: u64, last_error: Option<String>) {
        let mut inner = self.inner.lock().await;
        if inner.generation != generation {
            return;
        }
        inner.busy = false;
        inner.cancel = None;
        inner.last_error = last_error;
    }

    pub async fn cancel(&self) -> bool {
        let inner = self.inner.lock().await;
        if !inner.busy {
            return false;
        }
        if let Some(tx) = &inner.cancel {
            let _ = tx.send(true);
        }
        true
    }

    pub async fn status(&self) -> AiQueueStatus {
        let inner = self.inner.lock().await;
        AiQueueStatus {
            busy: inner.busy,
            queued: 0,
            last_error: inner.last_error.clone(),
        }
    }
}

impl Flight {
    pub fn is_cancelled(&self) -> bool {
        *self.cancel.borrow()
    }

    pub async fn cancelled(&mut self) {
        if self.is_cancelled() {
            return;
        }
        while self.cancel.changed().await.is_ok() {
            if self.is_cancelled() {
                return;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn second_begin_is_rejected_until_finish() {
        let queue = AiQueue::new();
        let first = queue.try_begin().await.expect("first");
        assert!(queue.try_begin().await.is_err());
        let status = queue.status().await;
        assert!(status.busy);
        assert_eq!(status.queued, 0);
        queue.finish(first.generation, None).await;
        assert!(!queue.status().await.busy);
        assert!(queue.try_begin().await.is_ok());
    }

    #[tokio::test]
    async fn cancel_marks_the_flight() {
        let queue = AiQueue::new();
        let mut flight = queue.try_begin().await.expect("flight");
        assert!(queue.cancel().await);
        flight.cancelled().await;
        assert!(flight.is_cancelled());
        queue
            .finish(flight.generation, Some("cancelled".into()))
            .await;
        assert_eq!(
            queue.status().await.last_error.as_deref(),
            Some("cancelled")
        );
    }
}
