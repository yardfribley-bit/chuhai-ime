//! 编辑会话：TSF 不允许直接改文档，要经 `RequestEditSession` 申请，在回调里拿着 edit cookie 写。
//!
//! 必须用异步会话（不带 `TF_ES_SYNC`）：沉浸式应用（Win11 新记事本等）的文本存区隔着进程边界，
//! 同步读写会让 `textinputframework.dll` 访问违例把宿主整个搞崩。回调可能在 `OnKeyDown` 返回之后才跑。

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::rc::Rc;

use windows::Win32::Foundation::E_FAIL;
use windows::Win32::UI::TextServices::{
    ITfContext, ITfEditSession, ITfEditSession_Impl, TF_CONTEXT_EDIT_CONTEXT_FLAGS, TF_ES_READWRITE,
};
use windows::core::{Error, Result, implement};

use crate::com::composition::{Shared, apply};
use crate::com::log::log;
use crate::com::service::SharedClient;

/// 一次性的读写会话：把本次按键的组句更新写进 `context`。
#[implement(ITfEditSession)]
pub(crate) struct UpdateSession {
    /// 目标文档上下文。
    context: ITfContext,

    /// 引擎层：量到光标矩形后报给 Server 摆候选窗口。
    engine: SharedClient,

    /// 组句状态。
    shared: Rc<Shared>,

    /// 本次要落定上屏的文本。
    commit: Option<String>,

    /// 本次组句拼音行；空串表示收起组句。
    preedit: String,

    translation_range: Option<(windows::Win32::UI::TextServices::ITfRange, String, u64)>,
}

impl ITfEditSession_Impl for UpdateSession_Impl {
    fn DoEditSession(&self, ec: u32) -> Result<()> {
        // 从框架的 C++ 调进来：panic 不能越过 FFI。
        let result = catch_unwind(AssertUnwindSafe(|| {
            if let Some((range, original, request)) = &self.translation_range {
                let Some(current) = super::anchor::selection_range(&self.context, ec) else {
                    return Ok(());
                };
                // 段落翻译时光标在原文末尾；选区翻译时光标范围与原文完全相同。
                let same_end = unsafe {
                    current.CompareEnd(ec, range, windows::Win32::UI::TextServices::TF_ANCHOR_END)
                }
                .is_ok_and(|value| value == 0);
                let same_start = unsafe {
                    current.CompareStart(
                        ec,
                        range,
                        windows::Win32::UI::TextServices::TF_ANCHOR_START,
                    )
                }
                .is_ok_and(|value| value == 0);
                let caret_at_end =
                    unsafe { current.IsEmpty(ec) }.is_ok_and(|empty| empty.as_bool());
                if !same_end
                    || !(same_start || caret_at_end)
                    || super::selection::range_text(range, ec) != *original
                {
                    log("翻译原文或输入位置已变化，保留原文");
                    return Ok(());
                }
                if let Some(text) = &self.commit {
                    unsafe {
                        range.SetText(ec, 0, &text.encode_utf16().collect::<Vec<_>>())?;
                    }
                    crate::com::composition::move_selection_to_end(&self.context, ec, range)?;
                    if let Ok(mut guard) = self.engine.try_borrow_mut()
                        && let Some(client) = guard.as_mut()
                    {
                        let _ = client.translation_accepted(*request);
                    }
                }
                return Ok(());
            }
            apply(
                &self.shared,
                &self.engine,
                &self.context,
                ec,
                self.commit.as_deref(),
                &self.preedit,
            )
        }));
        match result {
            Ok(Ok(())) => Ok(()),
            Ok(Err(error)) => {
                log(&format!("组句更新失败: {error}"));
                Err(error)
            }
            Err(_) => {
                log("组句更新回调 panic（已兜住）");
                Err(Error::from(E_FAIL))
            }
        }
    }
}

/// 请求一个异步读写会话。`Ok` 只说明已受理，写入结果在回调里记日志。
pub(crate) fn request_update(
    context: &ITfContext,
    client_id: u32,
    engine: SharedClient,
    shared: Rc<Shared>,
    commit: Option<String>,
    preedit: String,
) -> Result<()> {
    let translation_range = if commit.is_some() {
        shared.take_translation_range()
    } else {
        None
    };
    let session = UpdateSession {
        context: context.clone(),
        engine,
        shared,
        commit,
        preedit,
        translation_range,
    };
    request(context, client_id, session.into(), TF_ES_READWRITE)
}

/// 异步提交一个编辑会话；会话对象由框架持有到回调跑完。
pub(super) fn request(
    context: &ITfContext,
    client_id: u32,
    session: ITfEditSession,
    flags: TF_CONTEXT_EDIT_CONTEXT_FLAGS,
) -> Result<()> {
    unsafe { context.RequestEditSession(client_id, &session, flags)? }.ok()
}
