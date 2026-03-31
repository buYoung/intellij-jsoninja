use std::alloc::Layout;

pub fn create_allocation_layout(size: i32) -> Option<Layout> {
    if size <= 0 {
        return None;
    }

    Layout::from_size_align(size as usize, 1).ok()
}
