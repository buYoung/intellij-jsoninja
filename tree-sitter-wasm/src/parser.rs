use std::sync::{Mutex, OnceLock};

use tree_sitter::{Parser, Tree};

use crate::types::{HandleStore, SupportedLanguage, WasmErrorCode, WasmResult, WasmRuntimeError};

struct ParserHandleState {
    parser: Parser,
    language: SupportedLanguage,
}

#[derive(Clone)]
pub struct TreeHandleState {
    pub tree: Tree,
    pub language: SupportedLanguage,
}

static PARSER_HANDLES: OnceLock<Mutex<HandleStore<ParserHandleState>>> = OnceLock::new();
static TREE_HANDLES: OnceLock<Mutex<HandleStore<TreeHandleState>>> = OnceLock::new();

pub fn parser_create(language_id: i32) -> WasmResult<i32> {
    let language = SupportedLanguage::from_id(language_id).ok_or_else(|| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidLanguage,
            format!("Unsupported language id: {language_id}"),
        )
    })?;

    let runtime_language = language.to_tree_sitter_language()?;
    let mut parser = Parser::new();
    parser
        .set_language(&runtime_language)
        .map_err(|language_error| {
            WasmRuntimeError::new(
                WasmErrorCode::InvalidLanguage,
                format!("Failed to set parser language: {language_error}"),
            )
        })?;

    let mut parser_handles = parser_handles().lock().expect("parser handles lock poisoned");
    Ok(parser_handles.insert(ParserHandleState { parser, language }))
}

pub fn parser_destroy(handle: i32) -> WasmResult<()> {
    let mut parser_handles = parser_handles().lock().expect("parser handles lock poisoned");
    parser_handles.remove(handle).map(|_| ()).ok_or_else(|| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidHandle,
            format!("Unknown parser handle: {handle}"),
        )
    })
}

pub fn parse_source(
    parser_handle: i32,
    source_code: &str,
) -> WasmResult<i32> {
    let mut parser_handles = parser_handles().lock().expect("parser handles lock poisoned");
    let parser_handle_state = parser_handles.get_mut(parser_handle).ok_or_else(|| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidHandle,
            format!("Unknown parser handle: {parser_handle}"),
        )
    })?;

    let tree = parser_handle_state
        .parser
        .parse(source_code, None)
        .ok_or_else(|| {
            WasmRuntimeError::new(
                WasmErrorCode::ParseFailed,
                "tree-sitter parser returned no syntax tree.",
            )
        })?;
    let language = parser_handle_state.language;
    drop(parser_handles);

    let mut tree_handles = tree_handles().lock().expect("tree handles lock poisoned");
    Ok(tree_handles.insert(TreeHandleState { tree, language }))
}

pub fn tree_destroy(handle: i32) -> WasmResult<()> {
    let mut tree_handles = tree_handles().lock().expect("tree handles lock poisoned");
    tree_handles.remove(handle).map(|_| ()).ok_or_else(|| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidHandle,
            format!("Unknown tree handle: {handle}"),
        )
    })
}

pub fn tree_handle_state(handle: i32) -> WasmResult<TreeHandleState> {
    let tree_handles = tree_handles().lock().expect("tree handles lock poisoned");
    tree_handles.get(handle).cloned().ok_or_else(|| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidHandle,
            format!("Unknown tree handle: {handle}"),
        )
    })
}

pub fn supported_languages_json() -> String {
    let languages = SupportedLanguage::all()
        .iter()
        .map(|language| format!("\"{}\"", language.as_json_name()))
        .collect::<Vec<_>>()
        .join(",");
    format!("[{languages}]")
}

fn parser_handles() -> &'static Mutex<HandleStore<ParserHandleState>> {
    PARSER_HANDLES.get_or_init(|| Mutex::new(HandleStore::default()))
}

fn tree_handles() -> &'static Mutex<HandleStore<TreeHandleState>> {
    TREE_HANDLES.get_or_init(|| Mutex::new(HandleStore::default()))
}
