use std::cell::RefCell;
#[cfg(not(target_arch = "wasm32"))]
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

use tree_sitter::{Parser, Tree};

use crate::handle_store::HandleStore;
use crate::language::SupportedLanguage;

thread_local! {
    static LAST_ERROR_MESSAGE: RefCell<String> = RefCell::new(String::new());
}

pub(crate) struct ParserHandleState {
    pub(crate) parser: Parser,
    pub(crate) language: SupportedLanguage,
}

#[derive(Clone)]
pub(crate) struct TreeHandleState {
    pub(crate) tree: Tree,
    pub(crate) language: SupportedLanguage,
}

pub(crate) struct RuntimeState {
    parser_handles: Mutex<HandleStore<ParserHandleState>>,
    tree_handles: Mutex<HandleStore<TreeHandleState>>,
    #[cfg(not(target_arch = "wasm32"))]
    host_memory: Mutex<HostMemoryStore>,
}

static RUNTIME_STATE: OnceLock<RuntimeState> = OnceLock::new();

pub(crate) fn runtime_state() -> &'static RuntimeState {
    RUNTIME_STATE.get_or_init(RuntimeState::default)
}

impl Default for RuntimeState {
    fn default() -> Self {
        Self {
            parser_handles: Mutex::new(HandleStore::default()),
            tree_handles: Mutex::new(HandleStore::default()),
            #[cfg(not(target_arch = "wasm32"))]
            host_memory: Mutex::new(HostMemoryStore::default()),
        }
    }
}

impl RuntimeState {
    pub(crate) fn parser_handles(&self) -> &Mutex<HandleStore<ParserHandleState>> {
        &self.parser_handles
    }

    pub(crate) fn tree_handles(&self) -> &Mutex<HandleStore<TreeHandleState>> {
        &self.tree_handles
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub(crate) fn host_memory(&self) -> &Mutex<HostMemoryStore> {
        &self.host_memory
    }
}

pub(crate) fn clear_last_error_message() {
    LAST_ERROR_MESSAGE.with(|last_error_message| last_error_message.borrow_mut().clear());
}

pub(crate) fn set_last_error_message(message: impl Into<String>) {
    let message = message.into();
    LAST_ERROR_MESSAGE.with(|last_error_message| {
        *last_error_message.borrow_mut() = message;
    });
}

pub(crate) fn get_last_error_message() -> String {
    LAST_ERROR_MESSAGE.with(|last_error_message| last_error_message.borrow().clone())
}

#[cfg(not(target_arch = "wasm32"))]
pub(crate) struct HostMemoryStore {
    pub(crate) next_pointer: i32,
    pub(crate) allocations_by_pointer: HashMap<i32, Vec<u8>>,
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
