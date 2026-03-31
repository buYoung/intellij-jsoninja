#[derive(Clone, Debug)]
pub struct QueryCaptureResult {
    pub name: String,
    pub text: String,
    pub start_row: usize,
    pub start_col: usize,
    pub end_row: usize,
    pub end_col: usize,
}

pub struct QueryExecutionResult {
    pub captures: Vec<QueryCaptureResult>,
    pub error_message: Option<String>,
}

impl QueryExecutionResult {
    pub fn to_json_string(&self) -> String {
        let captures_text = self
            .captures
            .iter()
            .map(|capture| {
                format!(
                    "{{\"name\":\"{}\",\"text\":\"{}\",\"start_row\":{},\"start_col\":{},\"end_row\":{},\"end_col\":{}}}",
                    escape_json_string(&capture.name),
                    escape_json_string(&capture.text),
                    capture.start_row,
                    capture.start_col,
                    capture.end_row,
                    capture.end_col,
                )
            })
            .collect::<Vec<_>>()
            .join(",");

        let error_text = match &self.error_message {
            Some(message) => format!("\"{}\"", escape_json_string(message)),
            None => "null".to_string(),
        };

        format!("{{\"captures\":[{}],\"error\":{}}}", captures_text, error_text)
    }
}

fn escape_json_string(value: &str) -> String {
    let mut escaped_value = String::with_capacity(value.len());
    for character in value.chars() {
        match character {
            '"' => escaped_value.push_str("\\\""),
            '\\' => escaped_value.push_str("\\\\"),
            '\n' => escaped_value.push_str("\\n"),
            '\r' => escaped_value.push_str("\\r"),
            '\t' => escaped_value.push_str("\\t"),
            '\u{08}' => escaped_value.push_str("\\b"),
            '\u{0C}' => escaped_value.push_str("\\f"),
            _ if character.is_control() => {
                escaped_value.push_str(&format!("\\u{:04x}", character as u32))
            }
            _ => escaped_value.push(character),
        }
    }
    escaped_value
}
