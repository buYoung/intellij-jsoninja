#[cfg(target_arch = "wasm32")]
use std::ptr;
#[cfg(target_arch = "wasm32")]
use std::slice;
#[cfg(not(target_arch = "wasm32"))]
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

use crate::types::{WasmErrorCode, WasmResult, WasmRuntimeError};

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
static LAST_ERROR_MESSAGE: OnceLock<Mutex<String>> = OnceLock::new();
#[cfg(not(target_arch = "wasm32"))]
static HOST_MEMORY: OnceLock<Mutex<HostMemoryStore>> = OnceLock::new();

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

    let (pointer, length) = write_bytes(bytes)?;
    Ok(pack_ptr_len(pointer, length))
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

    #[cfg(not(target_arch = "wasm32"))]
    {
        let host_memory = host_memory().lock().expect("host memory lock poisoned");
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

fn last_error_message() -> &'static Mutex<String> {
    LAST_ERROR_MESSAGE.get_or_init(|| Mutex::new(String::new()))
}

pub fn write_bytes(bytes: &[u8]) -> WasmResult<(i32, i32)> {
    let pointer = crate::alloc(bytes.len() as i32);
    if pointer <= 0 {
        return Err(WasmRuntimeError::new(
            WasmErrorCode::AllocationFailed,
            "Failed to allocate WASM memory for result bytes.",
        ));
    }

    write_bytes_to_pointer(pointer, bytes)?;
    Ok((pointer, bytes.len() as i32))
}

#[cfg(not(target_arch = "wasm32"))]
pub fn allocate_host_buffer(size: i32) -> i32 {
    if size <= 0 {
        return 0;
    }

    let mut host_memory = host_memory().lock().expect("host memory lock poisoned");
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

    let mut host_memory = host_memory().lock().expect("host memory lock poisoned");
    host_memory.allocations_by_pointer.remove(&pointer);
}

fn write_bytes_to_pointer(
    pointer: i32,
    bytes: &[u8],
) -> WasmResult<()> {
    #[cfg(not(target_arch = "wasm32"))]
    {
        let mut host_memory = host_memory().lock().expect("host memory lock poisoned");
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

#[cfg(not(target_arch = "wasm32"))]
fn host_memory() -> &'static Mutex<HostMemoryStore> {
    HOST_MEMORY.get_or_init(|| Mutex::new(HostMemoryStore::default()))
}

#[cfg(not(target_arch = "wasm32"))]
struct HostMemoryStore {
    next_pointer: i32,
    allocations_by_pointer: HashMap<i32, Vec<u8>>,
}

#[cfg(not(target_arch = "wasm32"))]
impl Default for HostMemoryStore {
    fn default() -> Self {
        Self {
            next_pointer: 1,
            allocations_by_pointer: HashMap::new(),
        }
    }
}
