//! 「云服务」页：本地整句模型开关（`[model]`）、`[predict]` 各项与「测试连接」（后台线程跑）。

use qingjian_predict::{ConnectionTest, PredictConfig};
use windows_reactor::*;

use crate::panel::cloud_status::CloudStatus;
use crate::panel::controls::{field, labeled, note, page};
use crate::panel::{Message, Settings};

/// 后台跑一次连通性测试，轮询到有结果或被取消。
pub(crate) fn run_test(
    config: &PredictConfig,
    cancel: &CancellationToken,
) -> Result<String, String> {
    let test = ConnectionTest::start(config).map_err(|error| error.to_string())?;
    loop {
        if cancel.is_cancelled() {
            return Err("已取消".to_owned());
        }
        if let Some(result) = test.poll() {
            return result
                .map(|report| {
                    format!(
                        "连接成功：模型 {}，耗时 {} ms",
                        report.model,
                        report.elapsed.as_millis()
                    )
                })
                .map_err(|error| error.to_string());
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
}

pub(crate) fn view(settings: &Settings, context: &mut ViewContext<Settings>) -> View {
    let p = &settings.config.predict;
    let status = match &settings.cloud_status {
        CloudStatus::Idle => String::new(),
        CloudStatus::Testing => "测试中…".to_owned(),
        CloudStatus::Ok(message) => message.clone(),
        CloudStatus::Failed(message) => format!("失败：{message}"),
    };
    let provider = qingjian_predict::CloudService::for_url(&p.base_url);
    let service_query = settings
        .cloud_provider_query
        .clone()
        .unwrap_or_else(|| provider.label().to_owned());
    let model_query = settings
        .cloud_model_query
        .clone()
        .unwrap_or_else(|| p.model.clone());
    let suggestions: Vec<String> = settings
        .cloud_models
        .iter()
        .filter(|m| m.matches(&model_query))
        .map(|m| m.label())
        .collect();
    let service_suggestions: Vec<String> = qingjian_predict::CloudService::ALL
        .into_iter()
        .filter(|s| {
            s.label()
                .to_lowercase()
                .contains(&service_query.to_lowercase())
        })
        .map(|s| s.label().to_owned())
        .collect();
    let rows = [
        field(
            "本地整句模型",
            "随包的小模型在本机给整句候选重新排序，全程离线；停键后几十毫秒生效。关掉只用词库统计。",
            ToggleSwitch::new()
                .is_on(settings.config.model.enabled)
                .on_toggled(context.callback(Message::LocalModel)),
        ),
        field(
            "启用云联想",
            "开启后组句时会把光标附近的几十个字发给下面的服务，让模型补全整句、联想下文；密钥框里的内容不发送。",
            ToggleSwitch::new()
                .is_on(p.enabled)
                .on_toggled(context.callback(Message::CloudEnabled)),
        ),
        field(
            "云端词格数",
            "云端词到了只补进第一页末尾这几格，前面的本地候选不动；没到就什么都不变。0 = 只要整句补全。",
            NumberBox::new()
                .minimum(0.0)
                .maximum(9.0)
                .value(p.slots as f64)
                .on_value_changed(context.callback(Message::CloudSlots)),
        ),
        field(
            "整句补全",
            "preedit 右侧给出整句补全，按 Tab 采用。",
            ToggleSwitch::new()
                .is_on(p.sentence)
                .on_toggled(context.callback(Message::CloudSentence)),
        ),
        field(
            "接入服务",
            "输入 openrouter、deepseek，或选择自定义中转。选择服务后自动配置地址。",
            AutoSuggestBox::new()
                .text(service_query)
                .items_source(service_suggestions)
                .on_text_changed(context.callback(Message::CloudProviderQuery))
                .on_suggestion_chosen(context.callback(Message::CloudProvider)),
        ),
        field(
            "选择模型",
            "按名称搜索并选择；DeepSeek 官方需先粘贴密钥，再获取列表。",
            AutoSuggestBox::new()
                .text(model_query)
                .items_source(suggestions)
                .on_text_changed(context.callback(Message::CloudModelQuery))
                .on_suggestion_chosen(context.callback(Message::CloudModelSelected)),
        ),
        labeled(
            "",
            Button::new()
                .on_click(context.message(Message::LoadCloudModels))
                .content("获取 / 刷新模型列表"),
        ),
        field(
            "高级：中转地址",
            "官方服务地址自动填写；自定义中转只需填写一次地址。更改地址后需重新粘贴密钥。",
            TextBox::new()
                .text(p.base_url.clone())
                .is_enabled(provider == qingjian_predict::CloudService::Custom)
                .on_text_changed(context.callback(Message::CloudBaseUrl)),
        ),
        field(
            "高级：模型 ID",
            "列表不可用时可手动填写完整 ID。",
            TextBox::new()
                .text(p.model.clone())
                .on_text_changed(context.callback(Message::CloudModel)),
        ),
        field(
            "API 密钥",
            "只保存在这台电脑上，不会随配置文件导出，也不显示已填的值。留空则读环境变量 QINGJIAN_API_KEY。",
            PasswordBox::new()
                .password(p.api_key.clone().unwrap_or_default())
                .placeholder_text("留空则读环境变量 QINGJIAN_API_KEY")
                .on_password_changed(context.callback(Message::CloudApiKey)),
        ),
        labeled(
            "",
            StackPanel::new()
                .orientation(Orientation::Horizontal)
                .spacing(12.0)
                .children((
                    Button::new()
                        .on_click(context.message(Message::TestConnection))
                        .content("连接并启用"),
                    TextBlock::new().text(status),
                )),
        ),
        note(
            "用上面填的地址、模型、密钥发一条最小请求。走不通时先查这里；Server 进程看不到终端里的代理变量。",
        ),
    ];
    page("云服务", StackPanel::new().spacing(16.0).children(rows))
}
