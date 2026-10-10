//! 「云服务」页：本地整句模型开关，云联想开关、云端词格数、接口地址 / 模型 / 密钥、测试连接。

use objc2::MainThreadMarker;
use objc2::rc::Retained;
use objc2_app_kit::{NSButton, NSComboBox, NSPopUpButton, NSSecureTextField, NSTextField};
use objc2_foundation::NSString;
use qingjian_platform::Config;

use crate::preferences::controls::{
    button, checkbox, combo_field, note, row_checkbox, row_control, row_popup, secure_field,
    select, set_checked, text_field,
};
use crate::preferences::layout::{Layout, PAGE_PADDING, ROW_HEIGHT};
use crate::preferences::setting::Setting;
use crate::preferences::target::PreferencesTarget;

/// 云端词槽位弹出菜单的上限（配置文件里可以填更大，菜单只列到这）。
const MAX_CLOUD_SLOTS: usize = 4;

pub struct CloudPage {
    /// 本地整句模型开关。
    local_model: Retained<NSButton>,

    /// 云联想开关。
    enabled: Retained<NSButton>,

    /// 云端词槽位数（0–4）。
    slots: Retained<NSPopUpButton>,

    /// 接口地址。
    base_url: Retained<NSTextField>,

    /// 模型名。
    model: Retained<NSComboBox>,

    provider: Retained<NSComboBox>,

    /// 密钥输入框，永远不回显已有值。
    api_key: Retained<NSSecureTextField>,

    /// 「测试连接」按钮。
    test: Retained<NSButton>,
}

impl CloudPage {
    pub fn build(layout: &mut Layout, mtm: MainThreadMarker, target: &PreferencesTarget) -> Self {
        note(
            layout,
            mtm,
            "翻译用法：先完成中文选词 → 段落末尾连续输入 // → 等待英文预览 → 回车替换；Esc 取消并保留原文。完整说明见“使用指南”。",
        );
        let local_model = checkbox(mtm, "本地整句模型", Setting::LocalModelEnabled, target);
        row_checkbox(layout, &local_model);
        note(
            layout,
            mtm,
            "随包的小模型在本机给整句候选重新排序，全程离线；停键后几十毫秒生效。关掉只用词库统计。",
        );
        let enabled = checkbox(
            mtm,
            "启用云服务（整句翻译与联想）",
            Setting::CloudEnabled,
            target,
        );
        row_checkbox(layout, &enabled);
        note(
            layout,
            mtm,
            "开启后组句时会把光标附近的几十个字发给下面的服务，让模型补全整句、联想下文；密码框里绝不发送。菜单栏图标旁会带一个云朵。",
        );
        let slot_titles: Vec<String> = (0..=MAX_CLOUD_SLOTS)
            .map(|n| match n {
                0 => "不要（只要整句补全）".to_owned(),
                n => format!("{n} 格"),
            })
            .collect();
        let slots = row_popup(
            layout,
            mtm,
            "云端词位置",
            &slot_titles,
            Setting::CloudSlots,
            target,
        );
        note(
            layout,
            mtm,
            "云端词到了只补进第一页末尾这几格（比如 2 就是 8、9），前面的本地候选不动；没到就什么都不变，翻页后全是本地候选。",
        );
        let provider = combo_field(mtm, Setting::CloudService, target);
        for service in qingjian_predict::CloudService::ALL {
            // SAFETY: NSString 提供控件要求的显示文本，控件保留对象。
            unsafe {
                provider.addItemWithObjectValue(&NSString::from_str(service.label()));
            }
        }
        provider.setPlaceholderString(Some(&NSString::from_str("输入 openrouter 或 deepseek")));
        row_control(layout, mtm, "接入服务", &provider);
        let fetch = button(mtm, "获取 / 刷新模型列表", Setting::LoadModels, target);
        layout.place(&fetch, PAGE_PADDING, 200.0, ROW_HEIGHT + 4.0);
        layout.next_row(ROW_HEIGHT + 4.0);
        let base_url = text_field(mtm, Setting::BaseUrl, target);
        row_control(layout, mtm, "中转地址", &base_url);
        let model = combo_field(mtm, Setting::Model, target);
        model.setPlaceholderString(Some(&NSString::from_str("搜索或选择模型；也可填完整 ID")));
        row_control(layout, mtm, "模型", &model);
        let api_key = secure_field(mtm, Setting::ApiKey, target);
        row_control(layout, mtm, "API 密钥", &api_key);
        note(
            layout,
            mtm,
            "选接入服务 → 获取并选择模型 → 粘贴密钥 → 连接并启用。DeepSeek 官方需先粘贴密钥获取列表；自定义中转填写一次地址。文本框回车确认。",
        );
        let test = button(mtm, "连接并启用", Setting::TestCloud, target);
        layout.place(&test, PAGE_PADDING, 160.0, ROW_HEIGHT + 4.0);
        layout.next_row(ROW_HEIGHT + 4.0);
        note(
            layout,
            mtm,
            "用上面填的地址、模型、密钥发一条最小请求，结果显示在窗口底部。输入法进程看不到终端里的代理变量，走不通时先查这个。",
        );
        Self {
            local_model,
            enabled,
            slots,
            base_url,
            model,
            provider,
            api_key,
            test,
        }
    }

    pub fn set_models(&self, models: &[qingjian_predict::ModelEntry]) {
        self.model.removeAllItems();
        for model in models {
            // SAFETY: 模型显示项是有效 NSString，控件保留对象。
            unsafe {
                self.model
                    .addItemWithObjectValue(&NSString::from_str(&model.label()));
            }
        }
    }

    /// `key_present` 是密钥已经有了（环境或配置里）；密钥框永远不回显值，只换占位文字。
    /// `model_present` 是包里或用户目录里有模型文件，没有就把本地模型的勾选灰掉；云联想关着时它下面的项全灰。
    pub fn sync(&self, config: &Config, key_present: bool, model_present: bool) {
        set_checked(&self.local_model, config.model.enabled && model_present);
        self.local_model.setEnabled(model_present);
        set_checked(&self.enabled, config.predict.enabled);
        let cloud = config.predict.enabled;
        self.slots.setEnabled(cloud);
        self.base_url.setEnabled(
            qingjian_predict::CloudService::for_url(&config.predict.base_url)
                == qingjian_predict::CloudService::Custom,
        );
        self.base_url.setHidden(
            qingjian_predict::CloudService::for_url(&config.predict.base_url)
                != qingjian_predict::CloudService::Custom,
        );
        self.provider.setStringValue(&NSString::from_str(
            qingjian_predict::CloudService::for_url(&config.predict.base_url).label(),
        ));
        self.model.setEnabled(true);
        self.api_key.setEnabled(true);
        self.test.setEnabled(true);
        select(&self.slots, Some(config.predict.slots.min(MAX_CLOUD_SLOTS)));
        self.base_url
            .setStringValue(&NSString::from_str(&config.predict.base_url));
        self.model
            .setStringValue(&NSString::from_str(&config.predict.model));
        self.api_key.setStringValue(&NSString::from_str(""));
        let hint = if key_present {
            "已设置，输入新值可替换"
        } else {
            "未设置"
        };
        self.api_key
            .setPlaceholderString(Some(&NSString::from_str(hint)));
    }
}
