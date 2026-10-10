//! JNI C 薄桥的 C ABI；panic 不跨越 FFI 边界。

mod session;

use session::Session;
use std::ffi::{CStr, CString, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};

/// # Safety
/// `directory` 必须指向有效、以零结尾的 UTF-8 字符串。
#[unsafe(no_mangle)]
pub unsafe extern "C" fn chuhai_create(directory: *const c_char) -> *mut Session {
    catch_unwind(|| {
        if directory.is_null() {
            return None;
        }
        let directory = unsafe { CStr::from_ptr(directory) }.to_str().ok()?;
        Session::open(std::path::Path::new(directory))
            .ok()
            .map(|s| Box::into_raw(Box::new(s)))
    })
    .ok()
    .flatten()
    .unwrap_or(std::ptr::null_mut())
}

/// # Safety
/// `session` 是当前线程独占的有效会话；`message` 是零结尾 UTF-8。
#[unsafe(no_mangle)]
pub unsafe extern "C" fn chuhai_call(session: *mut Session, message: *const c_char) -> *mut c_char {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<serde_json::Value, String> {
        if session.is_null() || message.is_null() {
            return Err("invalid session".into());
        }
        let message = unsafe { CStr::from_ptr(message) }
            .to_str()
            .map_err(|e| e.to_string())?;
        unsafe { &mut *session }.call(message)
    }));
    let value = match result {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => serde_json::json!({"error":error}),
        Err(_) => serde_json::json!({"error":"native engine failed"}),
    };
    CString::new(value.to_string())
        .unwrap_or_default()
        .into_raw()
}

/// # Safety
/// 只传入本库返回的字符串，每个指针释放一次。
#[unsafe(no_mangle)]
pub unsafe extern "C" fn chuhai_free(text: *mut c_char) {
    if !text.is_null() {
        drop(unsafe { CString::from_raw(text) });
    }
}

/// # Safety
/// 只传入 create 返回的会话；释放后不可再调用。
#[unsafe(no_mangle)]
pub unsafe extern "C" fn chuhai_destroy(session: *mut Session) {
    if !session.is_null() {
        drop(unsafe { Box::from_raw(session) });
    }
}
