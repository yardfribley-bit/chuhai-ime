//! Android 共用内核会话；系统壳只传事件和展示 JSON 帧。

use qingjian_core::{Candidate, Engine, LearningTerm};
use qingjian_dictionary::{CodeTable, Dictionary};
use qingjian_learning::TranslationBook;
use serde_json::{Value, json};
use std::path::Path;

pub struct Session {
    engine: Engine,

    candidates: Vec<Candidate>,

    book: TranslationBook,

    data_dir: std::path::PathBuf,
}

impl Session {
    pub fn open(dir: &Path) -> Result<Self, String> {
        let dictionary = Dictionary::from_path(dir.join("dict.qj")).map_err(|e| e.to_string())?;
        let mut engine = Engine::new(dictionary);
        engine.set_code_table(Some(
            CodeTable::from_path(dir.join("wubi86.tsv")).map_err(|e| e.to_string())?,
        ));
        let book = TranslationBook::open(dir.join("translation-words.json")).unwrap_or_default();
        Ok(Self {
            engine,
            candidates: Vec::new(),
            book,
            data_dir: dir.to_owned(),
        })
    }

    pub fn call(&mut self, message: &str) -> Result<Value, String> {
        let message: Value = serde_json::from_str(message).map_err(|e| e.to_string())?;
        let mut commit = None;
        match message["op"].as_str().unwrap_or_default() {
            "push" => {
                for c in message["text"].as_str().unwrap_or_default().chars() {
                    self.engine.push(c);
                }
            }
            "delete" => {
                self.engine.backspace();
            }
            "clear" => self.engine.clear(),
            "raw" => {
                commit = Some(self.engine.composition().typed_text());
                self.engine.clear();
            }
            "accept" => {
                let index = message["index"].as_u64().unwrap_or(0) as usize;
                if let Some(candidate) = self.candidates.get(index) {
                    commit = Some(self.engine.commit(candidate));
                } else {
                    commit = Some(self.engine.take_raw());
                }
            }
            "mode" => {
                self.engine.clear();
                let mode = message["mode"].as_str().unwrap_or("mixed");
                self.engine.set_phonetic(mode != "wubi");
                self.engine.set_code_table(if mode == "pinyin" {
                    None
                } else {
                    Some(
                        CodeTable::from_path(self.data_dir.join("wubi86.tsv"))
                            .map_err(|e| e.to_string())?,
                    )
                });
            }
            "trigger" => {
                let before = message["before"].as_str().unwrap_or_default();
                return Ok(
                    match qingjian_core::translation_trigger::paragraph_before_slash(
                        before,
                        message["truncated"].as_bool().unwrap_or(false),
                    ) {
                        Some((source, length)) => {
                            json!({"source": source, "length": length, "system": qingjian_core::translation_trigger::TRANSLATE_SYSTEM_PROMPT})
                        }
                        None => json!({}),
                    },
                );
            }
            "record" => {
                self.book = TranslationBook::open(self.data_dir.join("translation-words.json"))
                    .map_err(|e| e.to_string())?;
                let source = message["source"].as_str().unwrap_or_default();
                let translation = message["translation"].as_str().unwrap_or_default();
                let terms: Vec<LearningTerm> =
                    serde_json::from_value(message["terms"].clone()).unwrap_or_default();
                let terms = qingjian_core::translation_trigger::validated_terms(terms, translation);
                self.book.record_on(
                    &jiff::Zoned::now().date().to_string(),
                    source,
                    translation,
                    &terms,
                );
                self.book.save().map_err(|e| e.to_string())?;
            }
            "today" => {
                self.book = TranslationBook::open(self.data_dir.join("translation-words.json"))
                    .map_err(|e| e.to_string())?;
                return serde_json::to_value(
                    self.book.today(&jiff::Zoned::now().date().to_string()),
                )
                .map_err(|e| e.to_string());
            }
            "mastered" => {
                self.book = TranslationBook::open(self.data_dir.join("translation-words.json"))
                    .map_err(|e| e.to_string())?;
                self.book
                    .mark_mastered(message["word"].as_str().unwrap_or_default(), true);
                self.book.save().map_err(|e| e.to_string())?;
            }
            "frame" => {}
            _ => return Err("unknown operation".into()),
        }
        self.candidates = self
            .engine
            .query()
            .map(|q| q.candidates.items)
            .unwrap_or_default();
        Ok(
            json!({"compose": self.engine.composition().typed_text(), "candidates": self.candidates.iter().take(20).map(|c| &c.text).collect::<Vec<_>>(), "commit": commit}),
        )
    }
}
