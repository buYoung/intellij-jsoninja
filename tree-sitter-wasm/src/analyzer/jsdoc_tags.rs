use tree_sitter::Node;
use crate::ir::Span;
use crate::source::text;
use crate::type_parser::jsdoc_expression::{property_key, split_balanced, take_group};

pub(super) struct Tag { pub name: String, pub value: String, pub span: Span }

pub(super) fn extract(node: Node<'_>, source: &[u8]) -> Vec<Tag> {
    let raw = text(node, source);
    if raw.len() < 5 || !raw.starts_with("/**") || !raw.ends_with("*/") { return vec![]; }
    let mut tags: Vec<Tag> = vec![];
    let start = node.start_position();
    for (index, line) in raw[3..raw.len()-2].split('\n').enumerate() {
        let mut trimmed = line.trim_start();
        if trimmed.starts_with('*') { trimmed = trimmed[1..].trim_start(); }
        let offset = line.len() - trimmed.len();
        let start_col = if index == 0 { start.column + 3 } else { 0 };
        let location = Span { start_row: start.row + index, start_col: start_col + offset, end_row: start.row + index, end_col: start_col + line.trim_end().len() };
        let trimmed = trimmed.trim_end();
        if let Some(tag) = trimmed.strip_prefix('@') {
            let end = tag.find(|c: char| !c.is_ascii_alphabetic()).unwrap_or(tag.len());
            tags.push(Tag { name: tag[..end].into(), value: tag[end..].trim().into(), span: location });
        } else if let Some(tag) = tags.last_mut().filter(|_| !trimmed.is_empty()) {
            tag.value.push(' '); tag.value.push_str(trimmed); tag.span.end_row = location.end_row; tag.span.end_col = location.end_col;
        }
    }
    tags
}

pub(super) fn typed_value(value: &str) -> Option<(Option<&str>, &str)> {
    let value = value.trim();
    if value.starts_with('{') { let (ty, rest) = take_group(value, '{', '}')?; Some((Some(ty), rest.trim())) } else { Some((None, value)) }
}

pub(super) fn name_token(value: &str) -> Option<&str> {
    let value = value.trim();
    if value.is_empty() { return None; }
    let mut quote = None; let mut escaped = false; let mut depth: usize = 0;
    for (index, c) in value.char_indices() {
        if let Some(q) = quote { if escaped { escaped = false; } else if c == '\\' { escaped = true; } else if c == q { quote = None; } continue; }
        match c { '\'' | '"' => quote = Some(c), '[' => depth += 1, ']' => depth = depth.checked_sub(1)?, _ if c.is_whitespace() && depth == 0 => return Some(&value[..index]), _ => {} }
    }
    if quote.is_some() || depth != 0 { None } else { Some(value) }
}

pub(super) struct PropertyPath { pub parts: Vec<(String, bool)>, pub is_optional: bool }
pub(super) fn property_path(raw: &str) -> Option<PropertyPath> {
    let (name, is_optional) = if raw.starts_with('[') {
        let (inner, rest) = take_group(raw, '[', ']')?;
        if !rest.is_empty() { return None; }
        (*split_balanced(inner, '=')?.first()?, true)
    } else { (raw, false) };
    let mut parts = vec![];
    for part in split_balanced(name, '.')? {
        if parts.len() >= 64 { return None; }
        let is_array = part.ends_with("[]");
        let key = if is_array { &part[..part.len()-2] } else { part };
        parts.push((property_key(key)?, is_array));
    }
    if parts.is_empty() { None } else { Some(PropertyPath { parts, is_optional }) }
}
