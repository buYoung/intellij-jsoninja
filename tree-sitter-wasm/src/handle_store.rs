use std::collections::HashMap;

pub struct HandleStore<T> {
    next_handle: i32,
    values_by_handle: HashMap<i32, T>,
}

impl<T> Default for HandleStore<T> {
    fn default() -> Self {
        Self {
            next_handle: 1,
            values_by_handle: HashMap::new(),
        }
    }
}

impl<T> HandleStore<T> {
    pub fn insert(
        &mut self,
        value: T,
    ) -> i32 {
        let handle = loop {
            let candidate_handle = self.next_handle;
            self.next_handle = self.next_handle.checked_add(1).unwrap_or(1);
            if candidate_handle > 0 && !self.values_by_handle.contains_key(&candidate_handle) {
                break candidate_handle;
            }
        };
        self.values_by_handle.insert(handle, value);
        handle
    }

    pub fn remove(
        &mut self,
        handle: i32,
    ) -> Option<T> {
        self.values_by_handle.remove(&handle)
    }

    pub fn get(
        &self,
        handle: i32,
    ) -> Option<&T> {
        self.values_by_handle.get(&handle)
    }

    pub fn get_mut(
        &mut self,
        handle: i32,
    ) -> Option<&mut T> {
        self.values_by_handle.get_mut(&handle)
    }
}
