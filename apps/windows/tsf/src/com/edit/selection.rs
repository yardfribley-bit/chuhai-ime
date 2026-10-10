//! 「翻译选中文字」的读选区会话：Server 收到快捷键后请 DLL 读当前选区，这里在异步只读会话里取文本与屏幕矩形回给 Server。
//! 读到非空选区才置 [`Shared::set_translating`]。

use std::rc::Rc;

use windows::Win32::UI::TextServices::{
    ITfContext, ITfEditSession, ITfEditSession_Impl, ITfRange, TF_ES_READ,
};
use windows::core::{Result, implement};

use qingjian_platform::protocol::ScreenRect;

use super::anchor::{anchor_rect, mouse_screen_rect, selection_range};
use crate::com::composition::Shared;
use crate::com::log::log;
use crate::com::service::SharedClient;

/// 原文字符上限（与 macOS 壳一致），超过就不译，避免整篇误触。
const MAX_TRANSLATE_CHARS: usize = 500;

#[implement(ITfEditSession)]
pub(crate) struct SelectionSession {
    /// 目标文档上下文。
    context: ITfContext,

    /// 引擎层：把选区文本发给 Server。
    engine: SharedClient,

    /// 读到非空选区就置「翻译评审进行中」。
    shared: Rc<Shared>,

    /// 请求标识，回给 Server 对上是哪一次 `RequestSelection`。
    request: u64,

    paragraph: bool,

    client_id: u32,
}

impl ITfEditSession_Impl for SelectionSession_Impl {
    fn DoEditSession(&self, ec: u32) -> Result<()> {
        let (text, rect) = read_selection(
            &self.context,
            ec,
            self.paragraph,
            &self.shared,
            self.request,
        );
        let has_text = !text.trim().is_empty();
        let response = if let Ok(mut guard) = self.engine.try_borrow_mut()
            && let Some(client) = guard.as_mut()
        {
            Some(client.selection(self.request, text, rect))
        } else {
            None
        };
        match response {
            Some(Ok(response)) if response.commit.is_some() => {
                // 不满足触发条件时把第二个斜杠写回，不能吞掉 URL 或代码里的 //。
                self.shared.set_translation_range(None);
                super::update::request_update(
                    &self.context,
                    self.client_id,
                    self.engine.clone(),
                    self.shared.clone(),
                    response.commit,
                    String::new(),
                )?;
            }
            Some(Ok(_)) if has_text => self.shared.set_translating(true),
            Some(Err(error)) => log(&format!("回选区给 Server 失败: {error}")),
            _ => {}
        }
        Ok(())
    }
}

/// 请求一个异步只读会话读选区。`Ok` 只说明已受理。
pub(crate) fn request_selection(
    context: &ITfContext,
    client_id: u32,
    engine: SharedClient,
    shared: Rc<Shared>,
    request: u64,
    paragraph: bool,
) -> Result<()> {
    let session = SelectionSession {
        context: context.clone(),
        engine,
        shared,
        request,
        paragraph,
        client_id,
    };
    super::update::request(context, client_id, session.into(), TF_ES_READ)
}

/// 没有选区 / 读不到 / 过长时文本为空串（矩形退到鼠标处）。
fn read_selection(
    context: &ITfContext,
    ec: u32,
    paragraph: bool,
    shared: &Shared,
    request: u64,
) -> (String, ScreenRect) {
    shared.set_translation_range(None);
    let Some(range) = selection_range(context, ec) else {
        return (String::new(), mouse_screen_rect());
    };
    let rect = anchor_rect(context, ec, &range);
    if super::surrounding::private_input(context, ec, &range) {
        return (String::new(), rect);
    }
    if paragraph {
        if !unsafe { range.IsEmpty(ec) }.is_ok_and(|empty| empty.as_bool()) {
            return (String::new(), rect);
        }
        let mut shifted = 0;
        if unsafe {
            range.ShiftStart(
                ec,
                -(MAX_TRANSLATE_CHARS as i32),
                &mut shifted,
                std::ptr::null(),
            )
        }
        .is_err()
        {
            return (String::new(), rect);
        }
        let before = range_text(&range, ec);
        let Some((source, length)) = qingjian_core::translation_trigger::paragraph_before_slash(
            &before,
            shifted <= -(MAX_TRANSLATE_CHARS as i32),
        ) else {
            return (String::new(), rect);
        };
        let original = format!("{source}/");
        // 折回段尾后，只扩展需要替换的当前段落，避免动到上一段。
        if unsafe { range.Collapse(ec, windows::Win32::UI::TextServices::TF_ANCHOR_END) }.is_err() {
            return (String::new(), rect);
        }
        if unsafe { range.ShiftStart(ec, -(length as i32), &mut shifted, std::ptr::null()) }
            .is_err()
        {
            return (String::new(), rect);
        }
        shared.set_translation_range(Some((range, original, request)));
        (source.to_owned(), rect)
    } else {
        let text = range_text(&range, ec);
        if !text.is_empty() {
            shared.set_translation_range(Some((range, text.clone(), request)));
        }
        (text, rect)
    }
}

pub(super) fn range_text(range: &ITfRange, ec: u32) -> String {
    let mut buf = [0u16; MAX_TRANSLATE_CHARS + 1];
    let mut fetched = 0u32;
    if unsafe { range.GetText(ec, 0, &mut buf, &mut fetched) }.is_err() {
        return String::new();
    }
    let count = fetched as usize;
    if count == 0 || count > MAX_TRANSLATE_CHARS {
        return String::new();
    }
    String::from_utf16_lossy(&buf[..count])
}
