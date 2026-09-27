use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::Annotation;
use crate::source::{named_children, span, text};

#[derive(Default)]
pub(super) struct Attributes {
    pub annotations: Vec<Annotation>,
    pub rename: Option<String>,
    pub rename_all: Option<String>,
    pub is_skipped: bool,
    pub is_optional: bool,
    pub has_unsupported_shape: bool,
}

pub(super) fn before(node: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: &str) -> Attributes {
    let mut siblings = vec![];
    let mut previous = node.prev_named_sibling();
    while let Some(sibling) = previous {
        if !matches!(sibling.kind(), "attribute_item" | "line_comment" | "block_comment") { break; }
        siblings.push(sibling); previous = sibling.prev_named_sibling();
    }
    siblings.reverse();
    let mut result = Attributes::default();
    for sibling in siblings {
        let raw = text(sibling, source).trim();
        if matches!(raw, "// JSONinja optional v1" | "/* JSONinja optional v1 */") { result.is_optional = true; }
        if sibling.kind() != "attribute_item" { continue; }
        let Some(attribute) = named_children(sibling).into_iter().find(|n| n.kind() == "attribute") else { continue; };
        let name = named_children(attribute).into_iter().find(|n| matches!(n.kind(), "identifier" | "scoped_identifier")).map(|n| text(n, source)).unwrap_or("");
        result.annotations.push(Annotation { name: name.into(), text: raw.into(), span: span(sibling) });
        if name != "serde" {
            if !matches!(name, "derive" | "doc" | "allow" | "warn" | "repr") { warn(diagnostics, sibling, owner, "rust.attribute.unsupported", "Attribute behavior is not expanded; conditional/custom fields may differ."); }
            continue;
        }
        let Some(arguments) = attribute.child_by_field_name("arguments") else { continue; };
        for part in split_metadata(text(arguments, source).trim_start_matches('(').trim_end_matches(')')) {
            let pair = part.split_once('=');
            let key = pair.map(|p| p.0.trim()).unwrap_or(part.trim());
            let value = pair.and_then(|p| string_literal(p.1.trim()));
            match key {
                "rename" if value.is_some() => result.rename = value,
                "rename_all" if value.as_deref().is_some_and(|v| CASES.contains(&v)) => result.rename_all = value,
                "skip" | "skip_serializing" => result.is_skipped = true,
                "default" => result.is_optional = true,
                "skip_serializing_if" if value.is_some() => {
                    result.is_optional = true;
                    if value.as_deref() != Some("Option::is_none") { warn(diagnostics, sibling, owner, "rust.attribute.condition", "Custom skip condition is not executed; field is treated as optional."); }
                }
                "skip_deserializing" | "alias" | "deny_unknown_fields" => {},
                _ => {
                    if matches!(key, "flatten" | "tag" | "content" | "untagged" | "transparent" | "with" | "serialize_with" | "into") { result.has_unsupported_shape = true; }
                    warn(diagnostics, sibling, owner, "rust.attribute.unsupported", "Unsupported Serde metadata is not executed; JSON shape may differ.");
                }
            }
        }
    }
    result
}
const CASES: [&str; 8] = ["lowercase", "UPPERCASE", "PascalCase", "camelCase", "snake_case", "SCREAMING_SNAKE_CASE", "kebab-case", "SCREAMING-KEBAB-CASE"];

pub(super) fn rename(value: &str, case: Option<&str>, is_variant: bool) -> String {
    let Some(case) = case else { return value.into(); };
    if is_variant {
        let mut snake = String::new();
        for (index, c) in value.chars().enumerate() { if c.is_uppercase() && index > 0 { snake.push('_'); } snake.extend(c.to_lowercase()); }
        return match case {
            "lowercase" => value.to_lowercase(), "UPPERCASE" => value.to_uppercase(), "PascalCase" => value.into(),
            "camelCase" => lower_first(value), "snake_case" => snake, "SCREAMING_SNAKE_CASE" => snake.to_uppercase(),
            "kebab-case" => snake.replace('_', "-"), "SCREAMING-KEBAB-CASE" => snake.to_uppercase().replace('_', "-"), _ => value.into(),
        };
    }
    let pascal = || value.split('_').filter(|p| !p.is_empty()).map(|part| {
        let mut chars = part.chars(); chars.next().unwrap().to_uppercase().collect::<String>() + chars.as_str()
    }).collect::<String>();
    match case {
        "lowercase" | "snake_case" => value.into(), "UPPERCASE" | "SCREAMING_SNAKE_CASE" => value.to_uppercase(),
        "PascalCase" => pascal(), "camelCase" => lower_first(&pascal()), "kebab-case" => value.replace('_', "-"),
        "SCREAMING-KEBAB-CASE" => value.to_uppercase().replace('_', "-"), _ => value.into(),
    }
}
fn lower_first(value: &str) -> String { let mut chars = value.chars(); chars.next().map(|c| c.to_lowercase().collect::<String>() + chars.as_str()).unwrap_or_default() }
fn warn(diagnostics: &mut Vec<Diagnostic>, node: Node<'_>, owner: &str, code: &str, message: &str) { diagnostics.push(Diagnostic::warning(code, message, Some(span(node)), Some(owner))); }

fn split_metadata(raw: &str) -> Vec<&str> {
    let mut result = vec![]; let mut start = 0; let mut depth: usize = 0; let mut quoted = false; let mut escaped = false;
    for (index, c) in raw.char_indices() {
        if quoted { if escaped { escaped = false; } else if c == '\\' { escaped = true; } else if c == '"' { quoted = false; } continue; }
        match c { '"' => quoted = true, '(' | '[' | '{' => depth += 1, ')' | ']' | '}' => depth = depth.saturating_sub(1), ',' if depth == 0 => { result.push(&raw[start..index]); start = index + 1; }, _ => {} }
    }
    if !raw[start..].trim().is_empty() { result.push(&raw[start..]); } result
}
fn string_literal(raw: &str) -> Option<String> {
    if raw.starts_with('r') {
        let opening = raw.find('"')?; let hashes = &raw[1..opening];
        if !hashes.chars().all(|c| c == '#') || !raw.ends_with(&format!("\"{hashes}")) { return None; }
        return raw.get(opening + 1..raw.len() - hashes.len() - 1).map(str::to_owned);
    }
    // JSON escapes form the conservative common subset; unsupported Rust escapes are diagnosed.
    serde_json::from_str::<String>(raw).ok()
}
