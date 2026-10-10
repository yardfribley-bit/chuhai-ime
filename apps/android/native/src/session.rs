//! Android 共用内核会话；系统壳只传事件和展示 JSON 帧。

use qingjian_core::{Candidate, Engine, Language, LearningTerm};
use qingjian_dictionary::{CodeTable, Dictionary};
use qingjian_learning::TranslationBook;
use qingjian_translate::Glossary;
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
        engine.set_translator(Box::new(
            Glossary::from_path(Language::English, dir.join("glossary-en.qj"))
                .map_err(|e| e.to_string())?,
        ));
        let book =
            TranslationBook::open(dir.join("translation-words.json")).map_err(|e| e.to_string())?;
        Ok(Self {
            engine,
            candidates: Vec::new(),
            book,
            data_dir: dir.to_owned(),
        })
    }

    fn flush_words(&mut self) -> Result<(), String> {
        self.book
            .record_encounters(&self.engine.take_encountered_terms());
        self.book.save().map_err(|e| e.to_string())
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
            "visible" => {
                let indices = message["indices"].as_array().cloned().unwrap_or_default();
                self.engine.note_displayed(
                    indices
                        .iter()
                        .filter_map(|index| index.as_u64())
                        .filter_map(|index| self.candidates.get(index as usize)),
                );
                return Ok(json!({}));
            }
            "privacy" => self
                .engine
                .set_private(message["private"].as_bool().unwrap_or(true)),
            "clear" => {
                self.engine.clear();
                self.flush_words()?;
            }
            "raw" => {
                commit = Some(self.engine.take_raw());
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
                self.engine.set_english_mode(mode == "english");
                self.engine.set_phonetic(mode != "wubi");
                self.engine
                    .set_code_table(if mode == "pinyin" || mode == "english" {
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
                self.flush_words()?;
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
                self.flush_words()?;
                self.book = TranslationBook::open(self.data_dir.join("translation-words.json"))
                    .map_err(|e| e.to_string())?;
                return serde_json::to_value(
                    self.book.today(&jiff::Zoned::now().date().to_string()),
                )
                .map_err(|e| e.to_string());
            }
            "mastered" => {
                self.flush_words()?;
                self.book = TranslationBook::open(self.data_dir.join("translation-words.json"))
                    .map_err(|e| e.to_string())?;
                self.book
                    .mark_mastered(message["word"].as_str().unwrap_or_default(), true);
                self.book.save().map_err(|e| e.to_string())?;
            }
            "frame" => {}
            _ => return Err("unknown operation".into()),
        }
        self.book
            .record_encounters(&self.engine.take_encountered_terms());
        self.candidates = match self.engine.query() {
            Ok(mut query) => {
                self.engine.annotate(&mut query.candidates);
                query.candidates.items
            }
            Err(_) => Vec::new(),
        };

        Ok(
            json!({"compose": self.engine.composition().typed_text(), "candidates": self.candidates.iter().take(20).map(|c| &c.text).collect::<Vec<_>>(), "annotations": self.candidates.iter().take(20).map(|candidate| candidate.translation.as_ref().map(|translation| translation.senses().iter().map(|sense| sense.text.clone()).collect::<Vec<_>>().join("; ")).unwrap_or_default()).collect::<Vec<_>>(), "commit": commit}),
        )
    }
}

impl Drop for Session {
    fn drop(&mut self) {
        let _ = self.flush_words();
    }
}
