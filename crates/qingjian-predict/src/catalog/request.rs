//! 有限大小、不跟随跳转的模型目录请求，后台线程返回结果。
use super::ModelEntry;
use crate::PredictConfig;
use std::sync::mpsc;

pub struct ModelRequest {
    pub base_url: String,

    result: mpsc::Receiver<Result<Vec<ModelEntry>, String>>,
}
impl ModelRequest {
    pub fn start(config: &PredictConfig) -> Result<Self, String> {
        let url = models_url(&config.base_url)?;
        let base_url = config.base_url.clone();
        let key = config.resolve_api_key();
        let (sender, result) = mpsc::channel();
        std::thread::Builder::new()
            .name("cloud-models".into())
            .spawn(move || {
                let outcome = tokio::runtime::Builder::new_current_thread()
                    .enable_all()
                    .build()
                    .map_err(|_| "无法启动网络请求".to_owned())
                    .and_then(|runtime| runtime.block_on(fetch(url, key)));
                let _ = sender.send(outcome);
            })
            .map_err(|_| "无法启动网络请求".to_owned())?;
        Ok(Self { base_url, result })
    }
    pub fn poll(&self) -> Option<Result<Vec<ModelEntry>, String>> {
        self.result.try_recv().ok()
    }
}
fn models_url(base: &str) -> Result<reqwest::Url, String> {
    let base = base.trim().trim_end_matches('/');
    let base = base.strip_suffix("/chat/completions").unwrap_or(base);
    let url =
        reqwest::Url::parse(&format!("{base}/models")).map_err(|_| "接口地址无效".to_owned())?;
    if url.scheme() != "https"
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err("请使用不含账户信息的 HTTPS 接口地址".into());
    }
    Ok(url)
}
async fn fetch(url: reqwest::Url, key: Option<String>) -> Result<Vec<ModelEntry>, String> {
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(std::time::Duration::from_secs(25))
        .build()
        .map_err(|_| "网络初始化失败".to_owned())?;
    let mut request = client.get(url);
    if let Some(key) = key.filter(|s| !s.is_empty()) {
        request = request.bearer_auth(key);
    }
    let mut response = request
        .send()
        .await
        .map_err(|_| "网络连接失败，请检查网络后重试".to_owned())?;
    if !response.status().is_success() {
        return Err(match response.status().as_u16() {
            401 => "请先粘贴此服务的有效密钥",
            402 => "账户余额不足",
            403 => "此密钥没有访问权限",
            404 => "服务不提供模型列表，可手动填写模型 ID",
            429 => "请求太频繁，请稍后重试",
            _ => "模型列表获取失败，请重试或手动填写模型 ID",
        }
        .into());
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|_| "读取模型列表失败".to_owned())?
    {
        if bytes.len() + chunk.len() > 8 * 1024 * 1024 {
            return Err("模型列表过大".into());
        }
        bytes.extend_from_slice(&chunk);
    }
    let value: serde_json::Value =
        serde_json::from_slice(&bytes).map_err(|_| "模型列表格式不受支持".to_owned())?;
    parse(&value)
}
fn parse(value: &serde_json::Value) -> Result<Vec<ModelEntry>, String> {
    let data = value
        .get("data")
        .and_then(|v| v.as_array())
        .ok_or("服务不提供兼容的模型列表")?;
    let mut models = Vec::new();
    for m in data {
        if let Some(outputs) = m
            .pointer("/architecture/output_modalities")
            .and_then(|v| v.as_array())
            && !outputs.iter().any(|v| v.as_str() == Some("text"))
        {
            continue;
        }
        let Some(id) = m
            .get("id")
            .and_then(|v| v.as_str())
            .filter(|s| !s.is_empty())
        else {
            continue;
        };
        models.push(ModelEntry {
            id: id.to_owned(),
            name: m
                .get("name")
                .and_then(|v| v.as_str())
                .unwrap_or(id)
                .to_owned(),
        });
    }
    models.sort_by_key(|a| a.name.to_lowercase());
    models.dedup_by(|a, b| a.id == b.id);
    Ok(models)
}
#[cfg(test)]
mod tests {
    use super::{models_url, parse};
    #[test]
    fn safe_endpoint_normalization() {
        assert_eq!(
            models_url("https://openrouter.ai/api/v1/chat/completions/")
                .unwrap()
                .as_str(),
            "https://openrouter.ai/api/v1/models"
        );
        for url in [
            "http://relay.example/v1",
            "https://user:secret@relay.example/v1",
            "https://relay.example/v1?key=secret",
        ] {
            assert!(models_url(url).is_err());
        }
    }
    #[test]
    fn display_names_preserve_model_ids_and_filter_image_models() {
        let list=parse(&serde_json::json!({"data":[{"id":"deepseek/x","name":"DeepSeek X"},{"id":"image","architecture":{"output_modalities":["image"]}},{"name":"missing"}]})).unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].id, "deepseek/x");
        assert!(list[0].matches("DeepSeek"));
    }
}
