//! 模型的易读名称及用于请求的完整标识。
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ModelEntry {
    pub id: String,

    pub name: String,
}
impl ModelEntry {
    pub fn matches(&self, query: &str) -> bool {
        let query = query.trim().to_lowercase();
        self.id.to_lowercase().contains(&query) || self.name.to_lowercase().contains(&query)
    }
    pub fn label(&self) -> String {
        if self.name == self.id {
            self.id.clone()
        } else {
            format!("{} · {}", self.name, self.id)
        }
    }
}
