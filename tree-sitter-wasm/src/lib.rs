mod analyzer;
mod diagnostics;
mod error;
mod handle_store;
mod ir;
mod language;
mod memory;
mod parser;
mod query;
mod query_result;
mod runtime_state;
mod source;
mod type_parser;
mod utils;

#[cfg(test)]
mod tests;

use std::panic::{catch_unwind, AssertUnwindSafe};

use error::{WasmErrorCode, WasmResult, WasmRuntimeError};
use runtime_state::{clear_last_error_message, set_last_error_message};

#[no_mangle]
pub extern "C" fn alloc(size: i32) -> i32 {
    memory::allocate_buffer(size)
}

#[no_mangle]
pub extern "C" fn dealloc(ptr: i32, size: i32) {
    memory::deallocate_buffer(ptr, size)
}

#[no_mangle]
pub extern "C" fn parser_create(language_id: i32) -> i32 {
    execute_i32(|| parser::parser_create(language_id))
}

#[no_mangle]
pub extern "C" fn parser_destroy(handle: i32) {
    execute_void(|| parser::parser_destroy(handle))
}

#[no_mangle]
pub extern "C" fn tree_parse(
    parser_handle: i32,
    source_ptr: i32,
    source_len: i32,
) -> i32 {
    execute_i32(|| {
        let source_code = memory::read_utf8_string(source_ptr, source_len)?;
        parser::parse_source(parser_handle, &source_code)
    })
}

#[no_mangle]
pub extern "C" fn parse(
    parser_handle: i32,
    source_ptr: i32,
    source_len: i32,
) -> i32 {
    tree_parse(parser_handle, source_ptr, source_len)
}

#[no_mangle]
pub extern "C" fn tree_query(
    tree_handle: i32,
    source_ptr: i32,
    source_len: i32,
    query_ptr: i32,
    query_len: i32,
) -> i64 {
    execute_i64(|| {
        let source_code = memory::read_utf8_string(source_ptr, source_len)?;
        let query_source = memory::read_utf8_string(query_ptr, query_len)?;
        query::tree_query(tree_handle, &source_code, &query_source)
    })
}

#[no_mangle]
pub extern "C" fn query_execute(
    tree_handle: i32,
    query_ptr: i32,
    query_len: i32,
    source_ptr: i32,
    source_len: i32,
) -> i64 {
    tree_query(tree_handle, source_ptr, source_len, query_ptr, query_len)
}

#[no_mangle]
pub extern "C" fn analyze_source(
    language_id: i32,
    source_ptr: i32,
    source_len: i32,
) -> i64 {
    execute_i64(|| {
        let source_code = memory::read_utf8_string(source_ptr, source_len)?;
        let language = language::SupportedLanguage::from_id(language_id).ok_or_else(|| {
            WasmRuntimeError::new(
                WasmErrorCode::InvalidLanguage,
                format!("Unsupported language id: {language_id}"),
            )
        })?;
        let analysis_output = analyzer::analyze_source(language, &source_code)?;
        memory::write_json(&analysis_output)
    })
}

#[no_mangle]
pub extern "C" fn tree_destroy(handle: i32) {
    execute_void(|| parser::tree_destroy(handle))
}

#[no_mangle]
pub extern "C" fn get_supported_languages() -> i64 {
    execute_i64(|| memory::write_json(&language::supported_languages()))
}

#[no_mangle]
pub extern "C" fn get_last_error() -> i64 {
    match catch_unwind(AssertUnwindSafe(memory::last_error_ptr_len)) {
        Ok(result) => result,
        Err(_) => 0,
    }
}

fn execute_i32(operation: impl FnOnce() -> WasmResult<i32>) -> i32 {
    clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(result_value)) => result_value,
        Ok(Err(runtime_error)) => {
            set_last_error_message(runtime_error.message);
            -1
        }
        Err(_) => {
            set_last_error_message("Unhandled panic in tree-sitter WASM module.");
            -1
        }
    }
}

fn execute_i64(operation: impl FnOnce() -> WasmResult<i64>) -> i64 {
    clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(result_value)) => result_value,
        Ok(Err(runtime_error)) => {
            set_last_error_message(runtime_error.message);
            memory::pack_error_code(runtime_error.code)
        }
        Err(_) => {
            set_last_error_message("Unhandled panic in tree-sitter WASM module.");
            memory::pack_error_code(WasmErrorCode::InternalError)
        }
    }
}

fn execute_void(operation: impl FnOnce() -> WasmResult<()>) {
    clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(())) => {}
        Ok(Err(runtime_error)) => {
            set_last_error_message(runtime_error.message);
        }
        Err(_) => {
            set_last_error_message("Unhandled panic in tree-sitter WASM module.");
        }
    }
}
