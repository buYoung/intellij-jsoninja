use std::ptr;
use std::slice;
use std::sync::{Mutex, OnceLock};

use crate::types::{WasmErrorCode, WasmResult, WasmRuntimeError};

// Host -> guest:
// 1. The host calls alloc(size) and writes bytes into the returned linear-memory pointer.
// 2. The guest reads the bytes directly from (ptr, len).
//
// Guest -> host:
// 1. The guest allocates result bytes in linear memory and returns them as a packed i64.
// 2. High 32 bits = ptr, low 32 bits = len.
// 3. The host reads the bytes, then calls dealloc(ptr, len).
static LAST_ERROR_MESSAGE: OnceLock<Mutex<String>> = OnceLock::new();

pub fn clear_last_error_message() {
    let mut last_error_message = last_error_message().lock().expect("last error lock poisoned");
    last_error_message.clear();
}

pub fn set_last_error_message(message: impl Into<String>) {
    let mut last_error_message = last_error_message().lock().expect("last error lock poisoned");
    *last_error_message = message.into();
}

pub fn read_utf8_string(
    pointer: i32,
    length: i32,
) -> WasmResult<String> {
    let bytes = read_bytes(pointer, length)?;
    String::from_utf8(bytes).map_err(|utf8_error| {
        WasmRuntimeError::new(
            WasmErrorCode::InvalidUtf8,
            format!("Input bytes are not valid UTF-8: {utf8_error}"),
        )
    })
}

pub fn write_utf8_string(value: &str) -> WasmResult<i64> {
    let bytes = value.as_bytes();
    if bytes.is_empty() {
        return Ok(0);
    }

    let pointer = crate::alloc(bytes.len() as i32);
    if pointer <= 0 {
        return Err(WasmRuntimeError::new(
            WasmErrorCode::AllocationFailed,
            "Failed to allocate WASM memory for result bytes.",
        ));
    }

    unsafe {
        ptr::copy_nonoverlapping(bytes.as_ptr(), pointer as *mut u8, bytes.len());
    }

    Ok(pack_ptr_len(pointer, bytes.len() as i32))
}

pub fn last_error_ptr_len() -> i64 {
    let message = {
        let last_error_message = last_error_message().lock().expect("last error lock poisoned");
        last_error_message.clone()
    };

    if message.is_empty() {
        0
    } else {
        write_utf8_string(&message).unwrap_or(0)
    }
}

pub fn pack_ptr_len(
    pointer: i32,
    length: i32,
) -> i64 {
    ((pointer as i64) << 32) | (length as u32 as i64)
}

pub fn pack_error_code(error_code: WasmErrorCode) -> i64 {
    error_code as i32 as u32 as i64
}

fn read_bytes(
    pointer: i32,
    length: i32,
) -> WasmResult<Vec<u8>> {
    if length < 0 {
        return Err(WasmRuntimeError::new(
            WasmErrorCode::InvalidPointer,
            "Negative buffer length is not allowed.",
        ));
    }

    if length == 0 {
        return Ok(Vec::new());
    }

    if pointer <= 0 {
        return Err(WasmRuntimeError::new(
            WasmErrorCode::InvalidPointer,
            "A non-zero buffer length requires a positive pointer.",
        ));
    }

    let bytes = unsafe { slice::from_raw_parts(pointer as *const u8, length as usize) };
    Ok(bytes.to_vec())
}

fn last_error_message() -> &'static Mutex<String> {
    LAST_ERROR_MESSAGE.get_or_init(|| Mutex::new(String::new()))
}
