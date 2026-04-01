#[cfg(target_arch = "wasm32")]
use std::alloc::{alloc as allocate_memory, dealloc as deallocate_memory};
#[cfg(target_arch = "wasm32")]
use std::ptr;
#[cfg(target_arch = "wasm32")]
use std::slice;

use serde::Serialize;

use crate::error::{WasmErrorCode, WasmResult, WasmRuntimeError};
use crate::runtime_state::get_last_error_message;
#[cfg(not(target_arch = "wasm32"))]
use crate::runtime_state::runtime_state;
#[cfg(target_arch = "wasm32")]
use crate::utils;

// Host -> guest:
// 1. The host calls alloc(size) and writes bytes into the returned linear-memory pointer.
// 2. The guest receives the pointer and length as separate i32 arguments.
//
// Guest -> host:
// 1. The guest allocates result bytes in linear memory and returns a packed i64 value.
// 2. The high 32 bits contain the pointer and the low 32 bits contain the byte length.
// 3. The host reads the bytes from WASM linear memory and then calls dealloc(ptr, len).
//
// Error reporting:
// 1. i32-returning functions signal failure with -1 and expose the detailed message via get_last_error().
// 2. i64-returning functions signal failure with (0 << 32) | error_code and expose the detailed message
//    via get_last_error().

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

    let (pointer, length) = write_bytes(bytes)?;
    Ok(pack_ptr_len(pointer, length))
}

pub fn write_json<T: Serialize>(value: &T) -> WasmResult<i64> {
    let json_text = serde_json::to_string(value).map_err(|serialization_error| {
        WasmRuntimeError::new(
            WasmErrorCode::InternalError,
            format!("Failed to serialize JSON result: {serialization_error}"),
        )
    })?;
    write_utf8_string(&json_text)
}

pub fn last_error_ptr_len() -> i64 {
    let message = get_last_error_message();
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

    #[cfg(not(target_arch = "wasm32"))]
    {
        let host_memory = runtime_state()
            .host_memory()
            .lock()
            .expect("host memory lock poisoned");
        let bytes = host_memory.allocations_by_pointer.get(&pointer).ok_or_else(|| {
            WasmRuntimeError::new(
                WasmErrorCode::InvalidPointer,
                format!("Unknown host buffer pointer: {pointer}"),
            )
        })?;
        if bytes.len() < length as usize {
            return Err(WasmRuntimeError::new(
                WasmErrorCode::InvalidPointer,
                format!("Host buffer at pointer {pointer} is shorter than {length} bytes."),
            ));
        }
        return Ok(bytes[..length as usize].to_vec());
    }

    #[cfg(target_arch = "wasm32")]
    {
    let bytes = unsafe { slice::from_raw_parts(pointer as *const u8, length as usize) };
    Ok(bytes.to_vec())
    }
}

pub fn write_bytes(bytes: &[u8]) -> WasmResult<(i32, i32)> {
    let pointer = allocate_buffer(bytes.len() as i32);
    if pointer <= 0 {
        return Err(WasmRuntimeError::new(
            WasmErrorCode::AllocationFailed,
            "Failed to allocate WASM memory for result bytes.",
        ));
    }

    write_bytes_to_pointer(pointer, bytes)?;
    Ok((pointer, bytes.len() as i32))
}

pub fn allocate_buffer(size: i32) -> i32 {
    #[cfg(not(target_arch = "wasm32"))]
    {
        return allocate_host_buffer(size);
    }

    #[cfg(target_arch = "wasm32")]
    {
        let layout = match utils::create_allocation_layout(size) {
            Some(layout) => layout,
            None => return 0,
        };

        unsafe { allocate_memory(layout) as i32 }
    }
}

pub fn deallocate_buffer(
    pointer: i32,
    size: i32,
) {
    #[cfg(not(target_arch = "wasm32"))]
    {
        deallocate_host_buffer(pointer, size);
        return;
    }

    #[cfg(target_arch = "wasm32")]
    {
        if pointer == 0 {
            return;
        }

        let layout = match utils::create_allocation_layout(size) {
            Some(layout) => layout,
            None => return,
        };

        unsafe {
            deallocate_memory(pointer as *mut u8, layout);
        }
    }
}

#[cfg(not(target_arch = "wasm32"))]
pub fn allocate_host_buffer(size: i32) -> i32 {
    if size <= 0 {
        return 0;
    }

    let mut host_memory = runtime_state()
        .host_memory()
        .lock()
        .expect("host memory lock poisoned");
    let pointer = host_memory.next_pointer;
    host_memory.next_pointer = host_memory
        .next_pointer
        .checked_add(size.max(1))
        .unwrap_or(1);
    host_memory.allocations_by_pointer.insert(pointer, vec![0; size as usize]);
    pointer
}

#[cfg(not(target_arch = "wasm32"))]
pub fn deallocate_host_buffer(
    pointer: i32,
    _size: i32,
) {
    if pointer <= 0 {
        return;
    }

    let mut host_memory = runtime_state()
        .host_memory()
        .lock()
        .expect("host memory lock poisoned");
    host_memory.allocations_by_pointer.remove(&pointer);
}

fn write_bytes_to_pointer(
    pointer: i32,
    bytes: &[u8],
) -> WasmResult<()> {
    #[cfg(not(target_arch = "wasm32"))]
    {
        let mut host_memory = runtime_state()
            .host_memory()
            .lock()
            .expect("host memory lock poisoned");
        let allocation = host_memory.allocations_by_pointer.get_mut(&pointer).ok_or_else(|| {
            WasmRuntimeError::new(
                WasmErrorCode::InvalidPointer,
                format!("Unknown host buffer pointer: {pointer}"),
            )
        })?;
        if allocation.len() < bytes.len() {
            return Err(WasmRuntimeError::new(
                WasmErrorCode::InvalidPointer,
                format!("Host buffer at pointer {pointer} is shorter than {} bytes.", bytes.len()),
            ));
        }
        allocation[..bytes.len()].copy_from_slice(bytes);
        return Ok(());
    }

    #[cfg(target_arch = "wasm32")]
    {
        unsafe {
            ptr::copy_nonoverlapping(bytes.as_ptr(), pointer as *mut u8, bytes.len());
        }
        Ok(())
    }
}
