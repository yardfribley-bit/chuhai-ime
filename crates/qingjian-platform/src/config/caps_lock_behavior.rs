//! Mac 大写锁定键的切换策略；默认保持中英文两态，可选择三预设循环。
use super::InputMode;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CapsLockBehavior {
    #[default]
    ChineseEnglish,

    ThreeModes,
}

impl CapsLockBehavior {
    pub const ALL: [Self; 2] = [Self::ChineseEnglish, Self::ThreeModes];

    pub const fn key(self) -> &'static str {
        match self {
            Self::ChineseEnglish => "chinese_english",
            Self::ThreeModes => "three_modes",
        }
    }

    pub fn next(self, current: InputMode, chinese: InputMode) -> InputMode {
        match self {
            Self::ThreeModes => current.next(),
            Self::ChineseEnglish if current == InputMode::English => chinese,
            Self::ChineseEnglish => InputMode::English,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preserves_chinese_scheme_or_cycles_all_three() {
        for chinese in [InputMode::Wubi, InputMode::Pinyin] {
            assert_eq!(
                CapsLockBehavior::ChineseEnglish.next(chinese, chinese),
                InputMode::English
            );
            assert_eq!(
                CapsLockBehavior::ChineseEnglish.next(InputMode::English, chinese),
                chinese
            );
        }
        let mut current = InputMode::Wubi;
        for expected in [InputMode::Pinyin, InputMode::English, InputMode::Wubi] {
            current = CapsLockBehavior::ThreeModes.next(current, InputMode::Wubi);
            assert_eq!(current, expected);
        }
    }
}
