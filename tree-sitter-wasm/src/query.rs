use streaming_iterator::StreamingIterator;
use tree_sitter::{Query, QueryCursor};

use crate::memory;
use crate::parser;
use crate::types::{QueryCaptureResult, QueryExecutionResult, WasmErrorCode, WasmResult, WasmRuntimeError};

pub fn query_execute(
    tree_handle: i32,
    query_source: &str,
    source_code: &str,
) -> WasmResult<i64> {
    let tree_handle_state = parser::tree_handle_state(tree_handle)?;
    let language = tree_handle_state.language.to_tree_sitter_language()?;
    let query = Query::new(&language, query_source).map_err(|query_error| {
        WasmRuntimeError::new(
            WasmErrorCode::QueryFailed,
            format!(
                "Failed to compile query at row {}, column {}: {}",
                query_error.row,
                query_error.column,
                query_error.message,
            ),
        )
    })?;

    let mut query_cursor = QueryCursor::new();
    let source_bytes = source_code.as_bytes();
    let mut query_captures = query_cursor.captures(&query, tree_handle_state.tree.root_node(), source_bytes);
    let mut capture_results = Vec::new();

    loop {
        query_captures.advance();
        let Some((query_match, capture_index)) = query_captures.get() else {
            break;
        };
        let query_capture = query_match.captures[*capture_index];
        let node = query_capture.node;
        let start_position = node.start_position();
        let end_position = node.end_position();
        let capture_name = query
            .capture_names()
            .get(query_capture.index as usize)
            .copied()
            .unwrap_or_default()
            .to_string();
        let captured_text = node.utf8_text(source_bytes).unwrap_or_default().to_string();

        capture_results.push(QueryCaptureResult {
            name: capture_name,
            text: captured_text,
            start_row: start_position.row as usize,
            start_col: start_position.column as usize,
            end_row: end_position.row as usize,
            end_col: end_position.column as usize,
        });
    }

    let query_result = QueryExecutionResult {
        captures: capture_results,
        error_message: None,
    };
    memory::write_utf8_string(&query_result.to_json_string())
}
