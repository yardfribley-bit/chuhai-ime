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
        for term in terms {
            self.entries
                .entry(term.word.to_lowercase())
                .or_insert_with(|| TranslationEntry {
                    word: term.word.clone(),
                    meaning: term.meaning.clone(),
                    source: source.to_owned(),
                    translation: translation.to_owned(),
                    first_date: date.to_owned(),
                    mastered: false,
                });
        }
    }

    pub fn today(&self, date: &str) -> Vec<&TranslationEntry> {
        self.entries
            .values()
            .filter(|entry| entry.first_date == date && !entry.mastered)
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
            "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>出海舰 · 今日学词</title><style>body{font:18px system-ui;max-width:800px;margin:40px auto;padding:20px;background:#f5f7fa;color:#182233}article{background:white;border-radius:12px;padding:20px;margin:16px 0}blockquote{margin:10px 0;color:#526176}</style><h1>今日学词</h1><p>来自你今天接受的英文译文；中文含义由翻译服务生成。</p>",
        );
        let entries = self.today(date);
        if entries.is_empty() {
            page.push_str("<p>今天还没有新增词条。接受整句英文译文后，再来这里复习。</p>");
        }
        for entry in entries {
            page.push_str(&format!(
                "<article><h2>{}</h2><p>{}</p><blockquote>{}</blockquote><p>{}</p></article>",
                escape(&entry.word),
                escape(&entry.meaning),
                escape(&entry.source),
                escape(&entry.translation)
            ));
        }
        write_atomic_str(path, &page)
    }
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
        assert!(book.today("2026-10-11").is_empty());
        book.mark_mastered("lateral movement", true);
        assert!(book.today("2026-10-10").is_empty());
    }

    #[test]
    fn escapes_untrusted_model_text() {
        assert_eq!(super::escape("<script>&\""), "&lt;script&gt;&amp;&quot;");
    }
}
