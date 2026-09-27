// Warning: Rust field 'zip_code' cannot retain JSON key 'zip-code' without a serializer mapping.

#[derive(Debug, Clone)]
pub struct RootAddres {
    pub zip_code: String,
}

#[derive(Debug, Clone)]
pub struct Root {
    pub active: bool,
    pub address: RootAddres,
    pub id: i64,
    pub name: String,
    pub scores: Vec<i64>,
}
