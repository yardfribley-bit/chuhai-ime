//! 翻译选中文字的方向：纯中文译成学习语言；夹了英文或假名就译成中文。

use crate::candidate::Language;

/// 选中的文字该译成什么语言。纯汉字 → 学习语言（学习语言是中文时退到英文）。
/// 里面有拉丁字母或假名，或者根本没有汉字 → 中文。中英混排不再按谁字多来猜方向。
pub fn translation_target(text: &str, learning: Language) -> Language {
    let mut han = 0usize;
    let mut kana = 0usize;
    let mut latin = 0usize;
    for c in text.chars() {
        if is_kana(c) {
            kana += 1;
        } else if is_han(c) {
            han += 1;
        } else if c.is_ascii_alphabetic() || is_latin_extended(c) {
            latin += 1;
        }
    }
    if han == 0 || kana > 0 || latin > 0 {
        return Language::Chinese;
    }
    match learning {
        Language::Chinese => Language::English,
        other => other,
    }
}

fn is_han(c: char) -> bool {
    matches!(c as u32,
        0x3400..=0x4DBF | 0x4E00..=0x9FFF | 0xF900..=0xFAFF | 0x20000..=0x323AF | 0x3005 | 0x3007)
}

fn is_kana(c: char) -> bool {
    matches!(c as u32, 0x3041..=0x309F | 0x30A0..=0x30FF | 0x31F0..=0x31FF | 0xFF66..=0xFF9F)
}

/// 带变音符的拉丁字母（café、naïve）也算字母。
fn is_latin_extended(c: char) -> bool {
    matches!(c as u32, 0x00C0..=0x024F) && c.is_alphabetic()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn chinese_text_goes_to_the_learning_language() {
        assert_eq!(
            translation_target("我想去吃饭", Language::English),
            Language::English
        );
        assert_eq!(
            translation_target("我想去吃饭。", Language::Japanese),
            Language::Japanese
        );
        // 没接释义表时学习语言是中文，译成英文
        assert_eq!(
            translation_target("我想去吃饭", Language::Chinese),
            Language::English
        );
    }

    #[test]
    fn latin_or_kana_text_goes_back_to_chinese() {
        assert_eq!(
            translation_target("I want to eat.", Language::English),
            Language::Chinese
        );
        assert_eq!(
            translation_target("café au lait", Language::English),
            Language::Chinese
        );
        assert_eq!(
            translation_target("ご飯を食べたい", Language::Japanese),
            Language::Chinese
        );
        assert_eq!(
            translation_target("東京へ行きます", Language::English),
            Language::Chinese
        );
    }

    #[test]
    fn mixed_text_goes_to_chinese() {
        assert_eq!(
            translation_target("用 Rust 写输入法", Language::English),
            Language::Chinese
        );
        assert_eq!(
            translation_target("我们正在 fix 这个漏洞", Language::Japanese),
            Language::Chinese
        );
        assert_eq!(
            translation_target("Rust 的 borrow checker", Language::English),
            Language::Chinese
        );
        // 纯标点 / 数字：没有字母也没有汉字，按「不是中文」算，译成中文（模型会原样返回）
        assert_eq!(
            translation_target("2026-09-05", Language::English),
            Language::Chinese
        );
    }
}
