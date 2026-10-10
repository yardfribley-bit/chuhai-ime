//! 一次已完成输入中的英文提示；保留发生时刻，延迟保存不会跨日误计。
use crate::LearningTerm;

#[derive(Debug, Clone)]
pub struct LearningEncounter {
    pub term: LearningTerm,

    pub at: std::time::SystemTime,
}
