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
