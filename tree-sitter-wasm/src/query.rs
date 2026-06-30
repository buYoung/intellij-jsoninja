use streaming_iterator::StreamingIterator;
use tree_sitter::{Language, Query, QueryCursor};

use crate::diagnostics::Diagnostic;
use crate::error::{WasmErrorCode, WasmResult, WasmRuntimeError};
use crate::memory;
use crate::parser;
use crate::query_result::{QueryCaptureResult, QueryExecutionResult};
use crate::source::span;

pub fn tree_query(
    tree_handle: i32,
    source_code: &str,
    query_source: &str,
) -> WasmResult<i64> {
    let tree_handle_state = parser::tree_handle_state(tree_handle)?;
    let language = tree_handle_state.language.to_tree_sitter_language()?;
    let query = compile_query(&language, query_source)?;
    let capture_results =
        collect_capture_results(&query, tree_handle_state.tree.root_node(), source_code.as_bytes());

    let query_result = QueryExecutionResult {
        captures: capture_results,
        diagnostics: collect_syntax_diagnostics(tree_handle_state.tree.root_node()),
        has_syntax_errors: tree_handle_state.tree.root_node().has_error(),
    };
    memory::write_json(&query_result)
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
        span: span(node),
    }
}

fn collect_syntax_diagnostics(root_node: tree_sitter::Node<'_>) -> Vec<Diagnostic> {
    let mut diagnostics = Vec::new();
    let mut cursor = root_node.walk();
    let mut nodes = vec![root_node];

    while let Some(node) = nodes.pop() {
        if node.is_error() {
            diagnostics.push(Diagnostic::error(
                "syntax.error",
                format!("Syntax error near `{}`.", node.kind()),
                Some(span(node)),
                None,
            ));
        }
        if node.is_missing() {
            diagnostics.push(Diagnostic::error(
                "syntax.missing",
                format!("Missing syntax node `{}`.", node.kind()),
                Some(span(node)),
                None,
            ));
        }

        nodes.extend(node.children(&mut cursor));
    }

    diagnostics
}
