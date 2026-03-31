use streaming_iterator::StreamingIterator;
use tree_sitter::{Language, Query, QueryCursor};

use crate::error::{WasmErrorCode, WasmResult, WasmRuntimeError};
use crate::memory;
use crate::parser;
use crate::query_result::{QueryCaptureResult, QueryExecutionResult};

pub fn query_execute(
    tree_handle: i32,
    query_source: &str,
    source_code: &str,
) -> WasmResult<i64> {
    let tree_handle_state = parser::tree_handle_state(tree_handle)?;
    let language = tree_handle_state.language.to_tree_sitter_language()?;
    let query = compile_query(&language, query_source)?;
    let capture_results =
        collect_capture_results(&query, tree_handle_state.tree.root_node(), source_code.as_bytes());

    let query_result = QueryExecutionResult {
        captures: capture_results,
        error_message: None,
    };
    memory::write_utf8_string(&query_result.to_json_string())
}

fn compile_query(
    language: &Language,
    query_source: &str,
) -> WasmResult<Query> {
    Query::new(language, query_source).map_err(|query_error| {
        WasmRuntimeError::new(
            WasmErrorCode::QueryFailed,
            format!(
                "Failed to compile query at row {}, column {}: {}",
                query_error.row,
                query_error.column,
                query_error.message,
            ),
        )
    })
}

fn collect_capture_results(
    query: &Query,
    root_node: tree_sitter::Node<'_>,
    source_bytes: &[u8],
) -> Vec<QueryCaptureResult> {
    let mut query_cursor = QueryCursor::new();
    let mut query_captures = query_cursor.captures(query, root_node, source_bytes);
    let mut capture_results = Vec::new();

    loop {
        query_captures.advance();
        let Some((query_match, capture_index)) = query_captures.get() else {
            break;
        };

        let query_capture = query_match.captures[*capture_index];
        capture_results.push(build_capture_result(query, query_capture, source_bytes));
    }

    capture_results
}

fn build_capture_result(
    query: &Query,
    query_capture: tree_sitter::QueryCapture<'_>,
    source_bytes: &[u8],
) -> QueryCaptureResult {
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

    QueryCaptureResult {
        name: capture_name,
        text: captured_text,
        start_row: start_position.row as usize,
        start_col: start_position.column as usize,
        end_row: end_position.row as usize,
        end_col: end_position.column as usize,
    }
}
