//! 一个词条及其首次使用的上下文。

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct TranslationEntry {
    pub word: String,

    pub meaning: String,

    pub source: String,

    pub translation: String,

    pub first_date: String,

    pub mastered: bool,

    /// 按日期保存有效出现次数；旧记录没有历史次数，不猜测补数。
    #[serde(default)]
    pub daily_counts: std::collections::BTreeMap<String, u32>,

    /// 候选提示、整句翻译等来源。
    #[serde(default)]
    pub origins: std::collections::BTreeSet<String>,
}
