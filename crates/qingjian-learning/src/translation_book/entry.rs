//! 一个词条及其首次使用的上下文。

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct TranslationEntry {
    pub word: String,

    pub meaning: String,

    pub source: String,

    pub translation: String,

    pub first_date: String,

    pub mastered: bool,
}
