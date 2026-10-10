//! 段落翻译协议回归：斜杠不丢失、等待期间回车不结束评审。

use crate::dispatch::{Router, RouterConfig};
use qingjian_core::{Engine, Prediction, PredictionPolicy, PredictionRequest, Predictor};
use qingjian_dictionary::Dictionary;
use qingjian_platform::protocol::{
    ClientMessage, KeyEvent, PROTOCOL_VERSION, ServerMessage, SessionId,
};

struct PendingPredictor;

impl Predictor for PendingPredictor {
    fn policy(&self) -> PredictionPolicy {
        PredictionPolicy::default()
    }
    fn submit(&mut self, _: PredictionRequest) {}
    fn poll(&mut self) -> Option<Prediction> {
        None
    }
}

fn router() -> Router {
    let engine = Engine::new(Dictionary::parse("我\two\t100\n").unwrap())
        .with_predictor(Box::new(PendingPredictor));
    let mut router = Router::new(engine, RouterConfig::default());
    router.handle(ClientMessage::OpenSession {
        session: SessionId(1),
        app: None,
        protocol: PROTOCOL_VERSION,
    });
    router
}

fn trigger(router: &mut Router) -> u64 {
    let message = || ClientMessage::Key {
        session: SessionId(1),
        event: KeyEvent::new(0xBF, Some('/'), Default::default()),
    };
    router.handle(message());
    match router.handle(message()).unwrap() {
        ServerMessage::RequestSelection {
            request,
            paragraph: true,
            ..
        } => request,
        other => panic!("expected paragraph request: {other:?}"),
    }
}

#[test]
fn restores_second_slash_when_no_translatable_paragraph_exists() {
    let mut router = router();
    let request = trigger(&mut router);
    let result = router.handle(ClientMessage::Selection {
        session: SessionId(1),
        request,
        text: String::new(),
        rect: qingjian_platform::protocol::ScreenRect {
            left: 0,
            top: 0,
            right: 0,
            bottom: 0,
        },
    });
    assert!(
        matches!(result, Some(ServerMessage::KeyResult { commit: Some(text), .. }) if text == "/")
    );
    assert!(router.translation.is_none());
}

#[test]
fn enter_keeps_pending_translation_alive() {
    let mut router = router();
    let request = trigger(&mut router);
    router.handle(ClientMessage::Selection {
        session: SessionId(1),
        request,
        text: "检测横向移动".into(),
        rect: qingjian_platform::protocol::ScreenRect {
            left: 0,
            top: 0,
            right: 0,
            bottom: 0,
        },
    });
    let response = router.handle(ClientMessage::Key {
        session: SessionId(1),
        event: KeyEvent::new(0x0D, None, Default::default()),
    });
    assert!(matches!(
        response,
        Some(ServerMessage::KeyResult { commit: None, .. })
    ));
    assert!(router.translation.is_some());
}
