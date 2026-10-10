//! 用户可直接选择的输入预设；内核仍分别使用中文方案与英文布尔状态。
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum InputMode {
    Wubi,

    Pinyin,

    English,
}

impl InputMode {
    pub const ALL: [Self; 3] = [Self::Wubi, Self::Pinyin, Self::English];

    pub const fn key(self) -> &'static str {
        match self {
            Self::Wubi => "wubi",
            Self::Pinyin => "pinyin",
            Self::English => "english",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::Wubi => "五笔",
            Self::Pinyin => "拼音",
            Self::English => "英文",
        }
    }

    pub const fn next(self) -> Self {
        match self {
            Self::Wubi => Self::Pinyin,
            Self::Pinyin => Self::English,
            Self::English => Self::Wubi,
        }
    }
}
