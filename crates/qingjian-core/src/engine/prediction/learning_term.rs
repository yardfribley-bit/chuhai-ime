//! 整句翻译中可供用户主动复习的词或短语。

#[derive(Debug, Clone, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct LearningTerm {
    pub word: String,

    pub meaning: String,
}
