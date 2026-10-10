//! 记录已完成输入中的英文候选提示和已接受译文中的词条，按本地日期累计出现次数。

mod book;
mod entry;

pub use book::TranslationBook;
pub use entry::TranslationEntry;
