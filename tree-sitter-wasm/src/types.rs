use std::collections::HashMap;

use tree_sitter::Language;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[repr(i32)]
pub enum WasmErrorCode {
    InvalidLanguage = 1,
    InvalidHandle = 2,
    InvalidPointer = 3,
    InvalidUtf8 = 4,
    ParseFailed = 5,
    QueryFailed = 6,
    AllocationFailed = 7,
    InternalError = 8,
}

#[derive(Debug)]
pub struct WasmRuntimeError {
    pub code: WasmErrorCode,
    pub message: String,
}

pub type WasmResult<T> = Result<T, WasmRuntimeError>;

impl WasmRuntimeError {
    pub fn new(
        code: WasmErrorCode,
        message: impl Into<String>,
    ) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SupportedLanguage {
    Java,
    Kotlin,
    TypeScript,
    Go,
}

impl SupportedLanguage {
    pub fn from_id(language_id: i32) -> Option<Self> {
        match language_id {
            0 => Some(Self::Java),
            1 => Some(Self::Kotlin),
            2 => Some(Self::TypeScript),
            3 => Some(Self::Go),
            _ => None,
        }
    }

    pub fn all() -> [Self; 4] {
        [Self::Java, Self::Kotlin, Self::TypeScript, Self::Go]
    }

    pub fn as_json_name(self) -> &'static str {
        match self {
            Self::Java => "java",
            Self::Kotlin => "kotlin",
            Self::TypeScript => "typescript",
            Self::Go => "go",
        }
    }

    pub fn to_tree_sitter_language(self) -> WasmResult<Language> {
        #[cfg(feature = "language-grammars")]
        {
            let language = match self {
                Self::Java => tree_sitter_java::LANGUAGE.into(),
                Self::Kotlin => tree_sitter_kotlin_ng::LANGUAGE.into(),
                Self::TypeScript => tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into(),
                Self::Go => tree_sitter_go::LANGUAGE.into(),
            };
            Ok(language)
        }

        #[cfg(not(feature = "language-grammars"))]
        {
            let _ = self;
            Err(WasmRuntimeError::new(
                WasmErrorCode::InvalidLanguage,
                "Language grammars are not enabled in this build.",
            ))
        }
    }
}

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

pub struct HandleStore<T> {
    next_handle: i32,
    values_by_handle: HashMap<i32, T>,
}

impl<T> Default for HandleStore<T> {
    fn default() -> Self {
        Self {
            next_handle: 1,
            values_by_handle: HashMap::new(),
        }
    }
}

impl<T> HandleStore<T> {
    pub fn insert(
        &mut self,
        value: T,
    ) -> i32 {
        let handle = loop {
            let candidate_handle = self.next_handle;
            self.next_handle = self.next_handle.checked_add(1).unwrap_or(1);
            if candidate_handle > 0 && !self.values_by_handle.contains_key(&candidate_handle) {
                break candidate_handle;
            }
        };
        self.values_by_handle.insert(handle, value);
        handle
    }

    pub fn remove(
        &mut self,
        handle: i32,
    ) -> Option<T> {
        self.values_by_handle.remove(&handle)
    }

    pub fn get(
        &self,
        handle: i32,
    ) -> Option<&T> {
        self.values_by_handle.get(&handle)
    }

    pub fn get_mut(
        &mut self,
        handle: i32,
    ) -> Option<&mut T> {
        self.values_by_handle.get_mut(&handle)
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
