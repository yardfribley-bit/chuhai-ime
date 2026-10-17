//! AgentReins 快速使用指南；与 docs/user/getting-started/quick-start.md 同步。

use objc2::MainThreadMarker;
use objc2_app_kit::NSFont;

use crate::preferences::controls::{GROUP_GAP, caption, note_full};
use crate::preferences::layout::{Layout, PAGE_PADDING, ROW_HEIGHT};

pub fn build(layout: &mut Layout, mtm: MainThreadMarker) {
    for (title, text) in [
        (
            "1. 输入中文",
            "选择 AgentReins，菜单栏显示“中”。用五笔86或拼音输入，按空格或数字选择中文候选。五笔输入 inim 对应“漏洞”。请先完成选词，再触发翻译。",
        ),
        (
            "2. 设置翻译服务",
            "在“云服务”页启用服务，填写接口地址、模型和 API 密钥，每个输入框按回车保存，再点击“测试连接”。整句翻译需要联网，费用由你选择的服务商收取。",
        ),
        (
            "3. 段落末尾输入 //",
            "把光标放在当前段落末尾，连续输入两个半角斜杠 //。中文段落译成「通用 → 整句翻译」所选的英语、日语或西班牙语。复制来的英文同样在末尾输入 //，直接译成中文。等待预览后按回车替换。例如：We are fixing a vulnerability.//",
        ),
        (
            "4. 把看到的中英文译成中文",
            "在网页或聊天里选中那段中英文，按 ⌃⌥T（可在「快捷键」里改）。候选窗口出现中文后按回车替换原文，Esc 保留。纯中文会译成「通用」里的学习语言。要先在「云服务」启用翻译，一次最多 500 字。",
        ),
        (
            "5. 取消与中英文切换",
            "按 Esc 取消翻译；失败或超时会保留原文。不要在等待时移动光标或切换应用。从菜单选择“五笔／拼音／英文”。Caps Lock 默认切换中英文，也可在通用设置中选择依次切换三个模式；大写英文请按住 Shift。",
        ),
        (
            "6. 有空再学词",
            "从输入法菜单点击“今日学词…”，查看已完成输入中的英文候选提示和已接受译文里的词、含义及今日出现次数。不会在打字时弹出复习提醒；取消的译文不记入词本。",
        ),
    ] {
        let heading = caption(mtm, title);
        heading.setFont(Some(&NSFont::boldSystemFontOfSize(14.0)));
        layout.place(&heading, PAGE_PADDING, layout.inner_width(), ROW_HEIGHT);
        layout.next_row(ROW_HEIGHT);
        note_full(layout, mtm, text);
        layout.space(GROUP_GAP);
    }
}
