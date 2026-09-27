use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{span, text};
use super::{list_type, map_type, named_type, nullable_type, primitive_type, split_top_level, union_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], _: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    parse_text(text(node, source), node, source, diagnostics, owner, 0)
}

fn parse_text(raw: &str, node: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, depth: usize) -> TypeReference {
    let raw = raw.trim();
    if depth >= 64 { return unknown_type_with_message("python.type.depth", "Annotation nesting exceeds the static parser limit.".into(), node, source, diagnostics, owner); }
    if let Some(unquoted) = decode_string(raw) { return parse_text(&unquoted, node, source, diagnostics, owner, depth + 1); }
    let parts = split_top_level(raw, '|');
    if parts.len() > 1 { return parse_union(parts, node, source, diagnostics, owner, depth); }
    let primitive = match raw {
        "str" => Some(PrimitiveKind::String), "int" => Some(PrimitiveKind::Integer),
        "float" => Some(PrimitiveKind::Decimal), "bool" => Some(PrimitiveKind::Boolean), _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
    if matches!(raw, "Any" | "typing.Any" | "object" | "None" | "NoneType") {
        let unknown = unknown_type_with_message("python.type.unknown", format!("Python `{raw}` has no concrete non-null sample type."), node, source, diagnostics, owner);
        return if matches!(raw, "None" | "NoneType") { nullable_type(unknown, span(node)) } else { unknown };
    }
    if let Some(open) = raw.find('[') {
        if raw.ends_with(']') {
            let name = raw[..open].trim().strip_prefix("typing.").unwrap_or(raw[..open].trim());
            let args = split_top_level(&raw[open+1..raw.len()-1], ',');
            match (name, args.len()) {
                ("list" | "List" | "Sequence" | "set" | "Set" | "frozenset" | "FrozenSet", 1) => return list_type(parse_text(&args[0], node, source, diagnostics, owner, depth+1), span(node)),
                ("dict" | "Dict" | "Mapping", 2) => {
                    let key = parse_text(&args[0], node, source, diagnostics, owner, depth+1);
                    let value = parse_text(&args[1], node, source, diagnostics, owner, depth+1);
                    if !matches!(key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) { diagnostics.push(Diagnostic::warning("python.map.key", "JSON dictionary keys are strings; other annotated key types use string samples.", Some(span(node)), owner)); }
                    return map_type(key, value, span(node));
                }
                ("Optional", 1) => return nullable_type(parse_text(&args[0], node, source, diagnostics, owner, depth+1), span(node)),
                ("Required" | "NotRequired" | "ClassVar", 1) => return parse_text(&args[0], node, source, diagnostics, owner, depth+1),
                ("Union", _) if !args.is_empty() => return parse_union(args, node, source, diagnostics, owner, depth),
                _ => {}
            }
        }
        return unknown_type_with_message("python.generic.unsupported", format!("Unsupported static annotation `{raw}`."), node, source, diagnostics, owner);
    }
    if raw.split('.').all(|part| {
        let mut chars = part.chars();
        chars.next().is_some_and(|c| c.is_alphabetic() || c == '_') && chars.all(|c| c.is_alphanumeric() || c == '_')
    }) { return named_type(raw.into(), vec![], false, span(node)); }
    unknown_type_with_message("python.type.unsupported", format!("Dynamic or unsupported annotation `{raw}` is not evaluated."), node, source, diagnostics, owner)
}

fn parse_union(parts: Vec<String>, node: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, depth: usize) -> TypeReference {
    let is_nullable = parts.iter().any(|p| matches!(p.as_str(), "None" | "NoneType"));
    let members = parts.iter().filter(|p| !matches!(p.as_str(), "None" | "NoneType")).map(|p| parse_text(p, node, source, diagnostics, owner, depth+1)).collect::<Vec<_>>();
    if members.len() > 1 { diagnostics.push(Diagnostic::warning("python.union.sample_first", "JSON samples select the first supported union alternative.", Some(span(node)), owner)); }
    let union = union_type(members, span(node));
    if is_nullable { nullable_type(union, span(node)) } else { union }
}

pub(crate) fn wrapper(raw: &str) -> Option<String> {
    let decoded = decode_string(raw);
    let raw = decoded.as_deref().unwrap_or(raw).trim();
    raw.find('[').map(|index| raw[..index].trim().trim_start_matches("typing.").to_owned())
}

/// Decodes only literal annotation/key strings. Never executes Python or interpolates f-strings.
pub(crate) fn decode_string(raw: &str) -> Option<String> {
    let mut raw = raw.trim();
    let is_raw = raw.starts_with('r') || raw.starts_with('R');
    if is_raw || raw.starts_with('u') || raw.starts_with('U') { raw = &raw[1..]; }
    let quote = raw.chars().next()?;
    if quote != '\'' && quote != '"' { return None; }
    let width = if raw.starts_with(&quote.to_string().repeat(3)) { 3 } else { 1 };
    if raw.len() < width * 2 || !raw.ends_with(&quote.to_string().repeat(width)) { return None; }
    let content = &raw[width..raw.len()-width];
    if is_raw { return Some(content.to_owned()); }
    let mut output = String::new();
    let mut chars = content.chars();
    while let Some(c) = chars.next() {
        if c != '\\' { output.push(c); continue; }
        let escaped = chars.next()?;
        match escaped {
            '\\' | '\'' | '"' => output.push(escaped),
            'n' => output.push('\n'), 'r' => output.push('\r'), 't' => output.push('\t'),
            'b' => output.push('\u{8}'), 'f' => output.push('\u{c}'), 'v' => output.push('\u{b}'), 'a' => output.push('\u{7}'),
            '\n' => {},
            'u' | 'U' | 'x' => {
                let count = match escaped { 'u' => 4, 'U' => 8, _ => 2 };
                let digits = (0..count).map(|_| chars.next()).collect::<Option<String>>()?;
                output.push(char::from_u32(u32::from_str_radix(&digits, 16).ok()?)?);
            }
            _ => return None,
        }
    }
    Some(output)
}
