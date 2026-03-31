mod memory;
mod parser;
mod query;
mod types;
mod utils;

use std::alloc::{alloc as allocate_memory, dealloc as deallocate_memory};
use std::panic::{catch_unwind, AssertUnwindSafe};
use types::WasmResult;

#[no_mangle]
pub extern "C" fn alloc(size: i32) -> i32 {
    let layout = match utils::create_allocation_layout(size) {
        Some(layout) => layout,
        None => return 0,
    };

    unsafe { allocate_memory(layout) as i32 }
}

#[no_mangle]
pub extern "C" fn dealloc(ptr: i32, size: i32) {
    if ptr == 0 {
        return;
    }

    let layout = match utils::create_allocation_layout(size) {
        Some(layout) => layout,
        None => return,
    };

    unsafe {
        deallocate_memory(ptr as *mut u8, layout);
    }
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
pub extern "C" fn parse(
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
pub extern "C" fn query_execute(
    tree_handle: i32,
    query_ptr: i32,
    query_len: i32,
    source_ptr: i32,
    source_len: i32,
) -> i64 {
    execute_i64(|| {
        let query_source = memory::read_utf8_string(query_ptr, query_len)?;
        let source_code = memory::read_utf8_string(source_ptr, source_len)?;
        query::query_execute(tree_handle, &query_source, &source_code)
    })
}

#[no_mangle]
pub extern "C" fn tree_destroy(handle: i32) {
    execute_void(|| parser::tree_destroy(handle))
}

#[no_mangle]
pub extern "C" fn get_supported_languages() -> i64 {
    execute_i64(|| memory::write_utf8_string(&parser::supported_languages_json()))
}

#[no_mangle]
pub extern "C" fn get_last_error() -> i64 {
    match catch_unwind(AssertUnwindSafe(memory::last_error_ptr_len)) {
        Ok(result) => result,
        Err(_) => 0,
    }
}

fn execute_i32(operation: impl FnOnce() -> WasmResult<i32>) -> i32 {
    memory::clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(result_value)) => result_value,
        Ok(Err(runtime_error)) => {
            memory::set_last_error_message(runtime_error.message);
            -1
        }
        Err(_) => {
            memory::set_last_error_message("Unhandled panic in tree-sitter WASM module.");
            -1
        }
    }
}

fn execute_i64(operation: impl FnOnce() -> WasmResult<i64>) -> i64 {
    memory::clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(result_value)) => result_value,
        Ok(Err(runtime_error)) => {
            memory::set_last_error_message(runtime_error.message);
            memory::pack_error_code(runtime_error.code)
        }
        Err(_) => {
            memory::set_last_error_message("Unhandled panic in tree-sitter WASM module.");
            memory::pack_error_code(types::WasmErrorCode::InternalError)
        }
    }
}

fn execute_void(operation: impl FnOnce() -> WasmResult<()>) {
    memory::clear_last_error_message();
    match catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(())) => {}
        Ok(Err(runtime_error)) => {
            memory::set_last_error_message(runtime_error.message);
        }
        Err(_) => {
            memory::set_last_error_message("Unhandled panic in tree-sitter WASM module.");
        }
    }
}
