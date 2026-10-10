//! 从系统读取的光标前文本中识别整句翻译触发范围，供各平台共用。

pub const TRANSLATE_SYSTEM_PROMPT: &str = "\
你是一个输入法的翻译助手。用户在应用里选中了一段文字并按了翻译快捷键，你会收到 JSON：\
text（选中的原文）、target_language（目标语言代码：zh 中文、en 英语、ja 日语）。\
把 text 完整、自然地译成目标语言，保留原文的语气、换行与标点习惯；\
原文已经是目标语言时：目标不是中文就改译成中文，目标是中文就原样返回。\
网络安全文本使用专业术语：横向移动 lateral movement、权限提升 privilege escalation、远程代码执行 remote code execution。保留 CVE、IP、域名、命令、代码和产品名；不得添加原文没有的事实或提高确定程度。\
不要解释、不要加引号、不要加「译文：」之类的前缀。\
输出 JSON：{\"sentence\": \"译文\", \"terms\": [{\"word\": \"译文中的英语单词或短语\", \"meaning\": \"对应中文含义\"}]}。目标为英语时最多选 8 个值得学习的词或短语，其他语言 terms 给空数组。";

/// 第二个 `/` 尚未写入应用时，返回原文及替换范围的 UTF-16 长度。
/// `truncated` 表示系统读取窗口没有从文档开头开始。
pub fn paragraph_before_slash(before: &str, truncated: bool) -> Option<(&str, usize)> {
    let paragraph = before.rsplit(['\n', '\r']).next()?;
    let text = paragraph.strip_suffix('/')?;
    if (truncated && !before.contains(['\n', '\r']))
        || text.trim().is_empty()
        || !text.chars().any(|c| ('\u{4e00}'..='\u{9fff}').contains(&c))
    {
        return None;
    }
    Some((text, paragraph.encode_utf16().count()))
}

/// 模型词条必须出现在译文中，去重并限制数量，避免记录幻觉词条。
pub fn validated_terms(
    terms: Vec<crate::LearningTerm>,
    sentence: &str,
) -> Vec<crate::LearningTerm> {
    let sentence = sentence.to_lowercase();
    let mut result: Vec<crate::LearningTerm> = Vec::new();
    for mut term in terms {
        term.word = term.word.trim().to_owned();
        term.meaning = term.meaning.trim().to_owned();
        let key = term.word.to_lowercase();
        let occurs = sentence.match_indices(&key).any(|(offset, _)| {
            let end = offset + key.len();
            !sentence[..offset]
                .chars()
                .next_back()
                .is_some_and(|c| c.is_alphanumeric())
                && !sentence[end..]
                    .chars()
                    .next()
                    .is_some_and(|c| c.is_alphanumeric())
        });
        if key.is_empty()
            || key.len() > 100
            || term.meaning.is_empty()
            || term.meaning.len() > 300
            || !key.chars().any(|c| c.is_ascii_alphabetic())
            || !occurs
            || result
                .iter()
                .any(|t| t.word.eq_ignore_ascii_case(&term.word))
        {
            continue;
        }
        result.push(term);
        if result.len() == 8 {
            break;
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::{paragraph_before_slash, validated_terms};
    use crate::LearningTerm;

    #[test]
    fn terms_must_appear_as_words_and_be_unique() {
        let terms = [
            " lateral movement ",
            "LATERAL MOVEMENT",
            "movement",
            "move",
            "imaginary",
            "",
        ]
        .into_iter()
        .map(|word| LearningTerm {
            word: word.into(),
            meaning: "释义".into(),
        })
        .collect();
        let valid = validated_terms(terms, "Detect lateral movement.");
        assert_eq!(
            valid.iter().map(|t| t.word.as_str()).collect::<Vec<_>>(),
            vec!["lateral movement", "movement"]
        );
    }

    #[test]
    fn extracts_only_current_paragraph_and_counts_utf16() {
        assert_eq!(
            paragraph_before_slash("上一段\r\n安全测试😀/", true),
            Some(("安全测试😀", 7))
        );
    }

    #[test]
    fn refuses_truncated_paragraph_and_non_chinese_input() {
        assert_eq!(paragraph_before_slash("未读完的中文/", true), None);
        assert_eq!(paragraph_before_slash("https://example.com/", false), None);
        assert_eq!(paragraph_before_slash("//", false), None);
        assert_eq!(paragraph_before_slash("中文", false), None);
    }
}
