//! 接受译文后的本地词条记录，以及菜单主动打开复习页。

use super::{Host, diagnostics::open_with_system};
use crate::app::paths;
use qingjian_core::LearningTerm;
use qingjian_learning::TranslationBook;

impl Host {
    pub fn record_translation(&mut self, source: &str, translation: &str, terms: &[LearningTerm]) {
        let Some(dir) = paths::user_data_dir() else {
            return;
        };
        let result = (|| -> Result<(), std::io::Error> {
            let mut book = TranslationBook::open(dir.join("translation-words.json"))?;
            book.record_on(
                &jiff::Zoned::now().date().to_string(),
                source,
                translation,
                terms,
            );
            book.save()
        })();
        if let Err(error) = result {
            tracing::warn!(%error, "今日词条保存失败");
        }
    }

    pub fn record_learning_encounters(&mut self) {
        let terms = self.engine.take_encountered_terms();
        if terms.is_empty() {
            return;
        }
        let Some(dir) = paths::user_data_dir() else {
            return;
        };
        let result = (|| -> Result<(), std::io::Error> {
            let mut book = TranslationBook::open(dir.join("translation-words.json"))?;
            book.record_encounters(&terms);
            book.save()
        })();
        if let Err(error) = result {
            tracing::warn!(%error, "候选词条保存失败");
        }
    }

    pub fn open_today_words(&mut self) {
        self.record_learning_encounters();
        let Some(dir) = paths::user_data_dir() else {
            return;
        };
        let page = dir.join("today-words.html");
        let result = (|| -> Result<(), std::io::Error> {
            let book = TranslationBook::open(dir.join("translation-words.json"))?;
            book.write_today_page(&jiff::Zoned::now().date().to_string(), &page)
        })();
        match result {
            Ok(()) => open_with_system(&[&page.to_string_lossy()]),
            Err(error) => self.show_notice(&format!("今日学词读取失败：{error}"), self.anchor),
        }
    }
}
