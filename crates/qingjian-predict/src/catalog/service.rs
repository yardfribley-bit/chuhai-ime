//! 已知接入服务的固定地址；模型名称不能决定密钥的接入渠道。
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CloudService {
    OpenRouter,
    DeepSeek,
    Custom,
}
impl CloudService {
    pub const ALL: [Self; 3] = [Self::OpenRouter, Self::DeepSeek, Self::Custom];
    pub fn label(self) -> &'static str {
        match self {
            Self::OpenRouter => "OpenRouter",
            Self::DeepSeek => "DeepSeek 官方",
            Self::Custom => "自定义中转",
        }
    }
    pub fn base_url(self) -> Option<&'static str> {
        match self {
            Self::OpenRouter => Some("https://openrouter.ai/api/v1"),
            Self::DeepSeek => Some("https://api.deepseek.com"),
            Self::Custom => None,
        }
    }
    pub fn for_url(value: &str) -> Self {
        let Ok(url) = reqwest::Url::parse(value) else {
            return Self::Custom;
        };
        match url.host_str() {
            Some("openrouter.ai") => Self::OpenRouter,
            Some("api.deepseek.com") => Self::DeepSeek,
            _ => Self::Custom,
        }
    }
    pub fn search(query: &str) -> Option<Self> {
        let q = query.trim().to_lowercase();
        Self::ALL
            .into_iter()
            .find(|s| s.label().to_lowercase() == q || *s == Self::DeepSeek && q == "deepseek")
    }
}
#[cfg(test)]
mod tests {
    use super::CloudService;
    #[test]
    fn does_not_guess_a_relay_from_a_model_or_path() {
        assert_eq!(
            CloudService::for_url("https://relay.example/openrouter.ai"),
            CloudService::Custom
        );
        assert_eq!(
            CloudService::for_url("https://openrouter.ai.evil.example/v1"),
            CloudService::Custom
        );
        assert_eq!(
            CloudService::search("deepseek"),
            Some(CloudService::DeepSeek)
        );
    }
}
