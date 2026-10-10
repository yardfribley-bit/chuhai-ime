//! 请求超时必须给壳终止回执，否则翻译窗口会一直等待。

use std::net::TcpListener;
use std::sync::mpsc;
use std::time::Duration;

use qingjian_core::{PredictionKind, PredictionRequest};

use super::Worker;
use crate::chat_client::ChatClient;
use crate::config::PredictConfig;

#[test]
fn timed_out_translation_returns_an_empty_reply_with_the_same_sequence() {
    // 接受 TCP 连接但不提供 HTTP 响应，确定触发真实请求超时。
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let config = PredictConfig {
        base_url: format!("http://{}", listener.local_addr().unwrap()),
        timeout_ms: 50,
        ..PredictConfig::default()
    };
    let (requests, request_rx) = mpsc::channel();
    let (response_tx, responses) = mpsc::channel();
    let worker = Worker::new(
        request_rx,
        response_tx,
        ChatClient::new(&config, "test-only-key".to_owned()),
        Duration::from_millis(1),
    );
    let thread = std::thread::spawn(move || worker.run());
    requests
        .send(PredictionRequest {
            sequence: 42,
            kind: PredictionKind::Translate,
            before: String::new(),
            after: String::new(),
            pinyin: String::new(),
            letters: String::new(),
            syllables: 0,
            candidates: Vec::new(),
            guess: String::new(),
            max_items: 1,
            want_sentence: true,
            text: "测试".to_owned(),
            target_language: "en".to_owned(),
        })
        .unwrap();
    let reply = responses.recv_timeout(Duration::from_secs(3)).unwrap();
    assert_eq!(reply.sequence, 42);
    assert!(reply.sentence.is_none());
    assert!(reply.words.is_empty());
    drop(requests);
    thread.join().unwrap().unwrap();
}
