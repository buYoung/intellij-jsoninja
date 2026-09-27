use crate::diagnostics::Diagnostic;
use crate::ir::{Field, PrimitiveKind, Span, TypeReference};
use super::{inline_object_type, list_type, map_type, named_type, nullable_type, primitive_type, union_type};

pub(crate) fn parse(raw: &str, location: &Span, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    parse_inner(raw, location, diagnostics, owner, 0)
}
fn parse_inner(raw: &str, location: &Span, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, depth: usize) -> TypeReference {
    let raw = raw.trim();
    if depth >= 64 { return unknown(raw, location, diagnostics, owner, "jsdoc.type.depth", "Type expression nesting exceeds the analysis limit."); }
    let Some(parts) = split_balanced(raw, '|') else {
        diagnostics.push(Diagnostic::error("jsdoc.type.malformed", "Unbalanced JSDoc type expression.", Some(location.clone()), owner));
        return TypeReference::Unknown { raw_text: raw.into(), span: location.clone() };
    };
    if parts.len() > 1 {
        let is_nullable = parts.iter().any(|p| matches!(p.trim(), "null" | "undefined"));
        let members = parts.iter().filter(|p| !matches!(p.trim(), "null" | "undefined")).map(|p| parse_inner(p, location, diagnostics, owner, depth+1)).collect::<Vec<_>>();
        if members.len() > 1 { diagnostics.push(Diagnostic::warning("jsdoc.union.sample_first", "JSON samples select the first supported union alternative.", Some(location.clone()), owner)); }
        let result = union_type(members, location.clone());
        return if is_nullable { nullable_type(result, location.clone()) } else { result };
    }
    if let Some((inner, rest)) = take_group(raw, '(', ')') {
        if rest.trim().is_empty() { return parse_inner(inner, location, diagnostics, owner, depth+1); }
    }
    if raw.len() > 1 && raw.starts_with('?') { return nullable_type(parse_inner(&raw[1..], location, diagnostics, owner, depth+1), location.clone()); }
    if let Some(inner) = raw.strip_prefix('!') {
        return match parse_inner(inner, location, diagnostics, owner, depth+1) { TypeReference::Nullable { wrapped_type, .. } => *wrapped_type, other => other };
    }
    if let Some(inner) = raw.strip_suffix('=') { return parse_inner(inner, location, diagnostics, owner, depth+1); }
    if let Some(inner) = raw.strip_suffix("[]") { return list_type(parse_inner(inner, location, diagnostics, owner, depth+1), location.clone()); }
    if let Some(inner) = raw.strip_suffix('?').filter(|_| raw.len() > 1) { return nullable_type(parse_inner(inner, location, diagnostics, owner, depth+1), location.clone()); }
    let primitive = match raw {
        "string" | "String" => Some(PrimitiveKind::String), "number" | "Number" => Some(PrimitiveKind::Number),
        "boolean" | "Boolean" => Some(PrimitiveKind::Boolean), _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, location.clone()); }
    if matches!(raw, "null" | "undefined") { return nullable_type(TypeReference::Unknown { raw_text: raw.into(), span: location.clone() }, location.clone()); }
    if matches!(raw, "Object" | "object") { return inline_object_type(vec![], location.clone()); }
    if let Some((record, rest)) = take_group(raw, '{', '}') {
        if rest.trim().is_empty() {
            let mut fields = vec![];
            for item in split_balanced(record, ',').unwrap_or_default().into_iter().filter(|p| !p.trim().is_empty()) {
                let pair = split_balanced(item, ':').unwrap_or_default();
                let Some(key) = pair.first() else { continue; };
                let optional = key.trim().ends_with('?');
                let raw_key = key.trim().trim_end_matches('?');
                let Some(name) = property_key(raw_key) else { return unknown(raw, location, diagnostics, owner, "jsdoc.record.key", "Unsupported record key."); };
                let reference = if pair.len() == 2 { parse_inner(pair[1], location, diagnostics, owner, depth+1) } else { unknown(item, location, diagnostics, owner, "jsdoc.record.type", "Record field type is missing or unsupported.") };
                fields.push(Field { name: name.clone(), source_name: name, optional, span: location.clone(), annotations: vec![], type_reference: reference });
            }
            return inline_object_type(fields, location.clone());
        }
    }
    if let Some(open) = raw.find('<') {
        if let Some((inner, rest)) = take_group(&raw[open..], '<', '>') {
            if rest.trim().is_empty() {
                let name = raw[..open].trim().trim_end_matches('.');
                let args = split_balanced(inner, ',').unwrap_or_default().iter().map(|a| parse_inner(a, location, diagnostics, owner, depth+1)).collect::<Vec<_>>();
                match (name, args.as_slice()) {
                    ("Array", [item]) => return list_type(item.clone(), location.clone()),
                    ("Object" | "object", [key, value]) => {
                        if !matches!(key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) { diagnostics.push(Diagnostic::warning("jsdoc.map.key", "JSON object keys are strings; other map keys use string samples.", Some(location.clone()), owner)); }
                        return map_type(key.clone(), value.clone(), location.clone());
                    }
                    _ => return unknown(raw, location, diagnostics, owner, "jsdoc.generic.unsupported", "Unsupported JSDoc generic application."),
                }
            }
        }
    }
    if is_name(raw) && !matches!(raw, "any" | "unknown" | "void" | "function" | "Function") { return named_type(raw.into(), vec![], false, location.clone()); }
    unknown(raw, location, diagnostics, owner, "jsdoc.type.unknown", "Unsupported or unconstrained JSDoc type generates a null sample.")
}
fn unknown(raw: &str, location: &Span, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, code: &str, message: &str) -> TypeReference {
    diagnostics.push(Diagnostic::warning(code, message, Some(location.clone()), owner));
    TypeReference::Unknown { raw_text: raw.into(), span: location.clone() }
}
pub(crate) fn is_name(raw: &str) -> bool {
    let mut chars = raw.chars();
    chars.next().is_some_and(|c| c.is_alphabetic() || matches!(c, '_' | '$')) && chars.all(|c| c.is_alphanumeric() || matches!(c, '_' | '$' | '.' | '#' | '~'))
}
pub(crate) fn property_key(raw: &str) -> Option<String> {
    if raw.starts_with('"') { return serde_json::from_str(raw).ok(); }
    if raw.starts_with('\'') && raw.ends_with('\'') && raw.len() >= 2 {
        let mut result = String::new(); let mut chars = raw[1..raw.len()-1].chars();
        while let Some(c) = chars.next() {
            if c != '\\' { result.push(c); continue; }
            match chars.next()? { '\'' => result.push('\''), '"' => result.push('"'), '\\' => result.push('\\'), 'n' => result.push('\n'), 'r' => result.push('\r'), 't' => result.push('\t'), _ => return None }
        }
        return Some(result);
    }
    if !raw.is_empty() && raw.chars().all(|c| c.is_alphanumeric() || matches!(c, '_' | '$' | '-')) { Some(raw.into()) } else { None }
}

/// Balanced type/name tokenization; delimiters inside quoted keys are ordinary data.
pub(crate) fn split_balanced(raw: &str, delimiter: char) -> Option<Vec<&str>> {
    let mut parts = vec![]; let mut start = 0; let mut stack = vec![]; let mut quote = None; let mut escaped = false;
    for (index, c) in raw.char_indices() {
        if let Some(q) = quote { if escaped { escaped = false; } else if c == '\\' { escaped = true; } else if c == q { quote = None; } continue; }
        if matches!(c, '\'' | '"') { quote = Some(c); continue; }
        if c == delimiter && stack.is_empty() { parts.push(raw[start..index].trim()); start = index + c.len_utf8(); continue; }
        match c {
            '(' => stack.push(')'), '[' => stack.push(']'), '{' => stack.push('}'), '<' => stack.push('>'),
            ')' | ']' | '}' | '>' => if stack.pop() != Some(c) { return None; }, _ => {}
        }
    }
    if !stack.is_empty() || quote.is_some() { return None; }
    parts.push(raw[start..].trim()); Some(parts)
}
pub(crate) fn take_group(raw: &str, open: char, close: char) -> Option<(&str, &str)> {
    if !raw.starts_with(open) { return None; }
    let mut depth = 0; let mut quote = None; let mut escaped = false;
    for (index, c) in raw.char_indices() {
        if let Some(q) = quote { if escaped { escaped = false; } else if c == '\\' { escaped = true; } else if c == q { quote = None; } continue; }
        if matches!(c, '\'' | '"') { quote = Some(c); continue; }
        if c == open { depth += 1; }
        if c == close { depth -= 1; if depth == 0 { return Some((&raw[open.len_utf8()..index], &raw[index + close.len_utf8()..])); } }
    }
    None
}
