//! 菜单栏等没有按键事件的显示路径读取当前 Caps Lock 状态。

use objc2_app_kit::{NSEvent, NSEventModifierFlags};

/// Caps Lock 亮着：视为英文模式，字母默认小写、按住 Shift 才大写、标点不转全角。
pub fn caps_lock_on() -> bool {
    NSEvent::modifierFlags_class().contains(NSEventModifierFlags::CapsLock)
}
