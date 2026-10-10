//! 接入服务预设与异步模型目录，供各平台配置界面共用。
mod model;
mod request;
mod service;
pub use model::ModelEntry;
pub use request::ModelRequest;
pub use service::CloudService;
