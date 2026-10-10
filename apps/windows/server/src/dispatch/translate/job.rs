//! 「翻译选中文字」的进行态。

/// 一次「翻译选中文字」的进行态。
#[derive(Clone)]
pub(crate) struct Translation {
    pub(crate) request: u64,

    /// 云端译文；`None` 表示还在等（候选窗显示「翻译中…」）。
    pub(crate) result: Option<String>,

    pub(crate) source: String,

    pub(crate) terms: Vec<qingjian_core::LearningTerm>,
}
