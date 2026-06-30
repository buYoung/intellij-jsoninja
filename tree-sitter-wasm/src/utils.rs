#[cfg(target_arch = "wasm32")]
use std::alloc::Layout;

#[cfg(target_arch = "wasm32")]
pub fn create_allocation_layout(size: i32) -> Option<Layout> {
    if size <= 0 {
        return None;
    }

    Layout::from_size_align(size as usize, 1).ok()
}
