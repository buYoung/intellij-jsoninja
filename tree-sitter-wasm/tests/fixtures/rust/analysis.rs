use std::collections::HashMap;
#[serde(rename_all = "camelCase")]
pub struct Root {
    pub user_id: i64,
    #[serde(rename = "display-name")]
    pub display_name: String,
    #[serde(default)]
    pub nickname: Option<String>,
    pub items: Vec<Item>,
    pub flags: HashMap<String, bool>,
    #[serde(skip)]
    internal: String,
}
pub struct Item { pub r#type: String }
pub struct Count(pub i64);
pub type Users = Vec<Root>;
#[serde(rename_all = "snake_case")]
pub enum Status { ReadyNow, Done }
