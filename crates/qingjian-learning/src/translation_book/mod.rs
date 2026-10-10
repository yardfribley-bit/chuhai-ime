//! 仅记录用户接受的整句译文中的词条；主动打开时查看今日首次遇到的词。

mod book;
mod entry;

pub use book::TranslationBook;
pub use entry::TranslationEntry;
