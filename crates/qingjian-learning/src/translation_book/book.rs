//! JSON 词本；读取失败不覆盖旧数据，保存采用原子写入。

use super::TranslationEntry;
use qingjian_core::{LearningTerm, storage::write_atomic_str};
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

#[derive(Debug, Default)]
pub struct TranslationBook {
    entries: BTreeMap<String, TranslationEntry>,

    path: Option<PathBuf>,
}

impl TranslationBook {
    pub fn open(path: impl Into<PathBuf>) -> Result<Self, std::io::Error> {
        let path = path.into();
        let entries = match std::fs::read_to_string(&path) {
            Ok(text) => serde_json::from_str(&text).map_err(std::io::Error::other)?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => BTreeMap::new(),
            Err(error) => return Err(error),
        };
        Ok(Self {
            entries,
            path: Some(path),
        })
    }

    pub fn record_on(
        &mut self,
        date: &str,
        source: &str,
        translation: &str,
        terms: &[LearningTerm],
    ) {
        self.record_terms_on(date, source, translation, terms, "整句翻译");
    }

    pub fn record_encounters(&mut self, encounters: &[qingjian_core::LearningEncounter]) {
        for encounter in encounters {
            if let Ok(timestamp) = jiff::Timestamp::try_from(encounter.at) {
                let date = timestamp
                    .to_zoned(jiff::tz::TimeZone::system())
                    .date()
                    .to_string();
                self.record_candidates_on(&date, std::slice::from_ref(&encounter.term));
            }
        }
    }

    pub fn record_candidates_on(&mut self, date: &str, terms: &[LearningTerm]) {
        // 不跨输入片段去重：同一词在新的已完成片段中再次出现，应再加一次。
        for term in terms {
            self.record_terms_on(
                date,
                &term.meaning,
                "",
                std::slice::from_ref(term),
                "候选提示",
            );
        }
    }

    fn record_terms_on(
        &mut self,
        date: &str,
        source: &str,
        translation: &str,
        terms: &[LearningTerm],
        origin: &str,
    ) {
        let mut recorded = std::collections::BTreeSet::new();
        for term in terms {
            let key = term.word.trim().to_lowercase();
            if key.is_empty() || !recorded.insert(key.clone()) {
                continue;
            }
            let count = if origin == "整句翻译" {
                occurrences(translation, &key).max(1)
            } else {
                1
            };
            let entry = self.entries.entry(key).or_insert_with(|| TranslationEntry {
                word: term.word.trim().to_owned(),
                meaning: term.meaning.clone(),
                source: source.to_owned(),
                translation: translation.to_owned(),
                first_date: date.to_owned(),
                mastered: false,
                daily_counts: BTreeMap::new(),
                origins: std::collections::BTreeSet::new(),
            });
            let daily = entry.daily_counts.entry(date.to_owned()).or_default();
            *daily = daily.saturating_add(count);
            entry.origins.insert(origin.to_owned());
            if entry.meaning.is_empty() {
                entry.meaning.clone_from(&term.meaning);
            }
            if entry.translation.is_empty() && !translation.is_empty() {
                entry.source = source.to_owned();
                entry.translation = translation.to_owned();
            }
        }
    }

    pub fn today(&self, date: &str) -> Vec<&TranslationEntry> {
        self.entries
            .values()
            .filter(|entry| {
                !entry.mastered
                    && (entry.daily_counts.get(date).is_some_and(|count| *count > 0)
                        || entry.first_date == date)
            })
            .collect()
    }

    pub fn mark_mastered(&mut self, word: &str, mastered: bool) {
        if let Some(entry) = self.entries.get_mut(&word.to_lowercase()) {
            entry.mastered = mastered;
        }
    }

    pub fn save(&self) -> Result<(), std::io::Error> {
        if let Some(path) = &self.path {
            let text =
                serde_json::to_string_pretty(&self.entries).map_err(std::io::Error::other)?;
            write_atomic_str(path, &text)?;
        }
        Ok(())
    }

    /// 生成可由系统浏览器打开的静态复习页，不载入外部资源。
    pub fn write_today_page(&self, date: &str, path: &Path) -> Result<(), std::io::Error> {
        let mut page = String::from(
            "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>出海舰 · 今日学词</title><style>body{font:18px system-ui;max-width:800px;margin:40px auto;padding:20px;background:#f5f7fa;color:#182233}article{background:white;border-radius:12px;padding:20px;margin:16px 0}blockquote{margin:10px 0;color:#526176}</style><h1>今日学词</h1><p>来自已完成输入中的候选提示和已接受的英文译文。今日次数按有效输入片段及译文中的实际出现次数统计；重绘不重复计数。</p>",
        );
        let entries = self.today(date);
        if entries.is_empty() {
            page.push_str(
                "<p>今天还没有词条。完成中文输入或接受整句英文译文后，再来这里复习。</p>",
            );
        }
        for entry in entries {
            page.push_str(&format!(
                "<article><h2>{}</h2><p>{}</p><p>今日出现：{} · 来源：{}</p><blockquote>{}</blockquote><details><summary>查看英文用法</summary><p>{}</p></details></article>",
                escape(&entry.word),
                escape(&entry.meaning),
                entry.daily_counts.get(date).map(|count| format!("{count} 次")).unwrap_or_else(|| "旧记录未统计".to_owned()),
                escape(&entry.origins.iter().cloned().collect::<Vec<_>>().join("、")),
                escape(&entry.source),
                escape(&entry.translation)
            ));
        }
        write_atomic_str(path, &page)
    }
}

fn occurrences(text: &str, word: &str) -> u32 {
    let text = text.to_lowercase();
    text.match_indices(word)
        .filter(|(start, _)| {
            let end = start + word.len();
            let is_word = |c: char| c.is_alphanumeric() || c == '_';
            !text[..*start].chars().next_back().is_some_and(is_word)
                && !text[end..].chars().next().is_some_and(is_word)
        })
        .count()
        .try_into()
        .unwrap_or(u32::MAX)
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

#[cfg(test)]
mod tests {
    use super::TranslationBook;
    use qingjian_core::LearningTerm;

    #[test]
    fn records_only_first_day_and_hides_mastered_terms() {
        let mut book = TranslationBook::default();
        let terms = vec![LearningTerm {
            word: "Lateral movement".into(),
            meaning: "横向移动".into(),
        }];
        book.record_on(
            "2026-10-10",
            "检测横向移动",
            "Detect lateral movement",
            &terms,
        );
        book.record_on("2026-10-11", "横向移动", "Lateral movement", &terms);
        assert_eq!(book.today("2026-10-10").len(), 1);
        assert_eq!(book.today("2026-10-11").len(), 1);
        assert_eq!(book.today("2026-10-10")[0].daily_counts["2026-10-10"], 1);
        assert_eq!(book.today("2026-10-11")[0].daily_counts["2026-10-11"], 1);
        book.mark_mastered("lateral movement", true);
        assert!(book.today("2026-10-10").is_empty());
    }

    #[test]
    fn combines_sources_counts_real_occurrences_and_preserves_old_json() {
        let mut book = TranslationBook::default();
        let term = LearningTerm {
            word: "Vulnerability".into(),
            meaning: "漏洞".into(),
        };
        book.record_candidates_on("2026-10-10", std::slice::from_ref(&term));
        book.record_on(
            "2026-10-10",
            "漏洞",
            "Vulnerability and vulnerability; vulnerabilities differ.",
            &[term.clone(), term],
        );
        let entry = book.today("2026-10-10")[0];
        assert_eq!(entry.daily_counts["2026-10-10"], 3);
        assert_eq!(entry.origins.len(), 2);
        let old: super::TranslationEntry = serde_json::from_str(r#"{"word":"word","meaning":"词","source":"词","translation":"word","first_date":"2026-10-10","mastered":false}"#).unwrap();
        assert!(old.daily_counts.is_empty());
        assert_eq!(
            super::occurrences(
                "privilege escalation and Privilege Escalation",
                "privilege escalation"
            ),
            2
        );
    }

    #[test]
    fn escapes_untrusted_model_text() {
        assert_eq!(super::escape("<script>&\""), "&lt;script&gt;&amp;&quot;");
    }
}
