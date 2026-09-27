use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{span, text};
use super::{list_type, map_type, named_type, nullable_type, primitive_type, split_top_level, union_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    parse_text(text(node, source), node, source, parameters, diagnostics, owner, 0)
}
fn parse_text(raw: &str, node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, depth: usize) -> TypeReference {
    let raw = raw.trim();
    if depth >= 64 { return unknown_type_with_message("scala.type.depth", "Type nesting exceeds the analysis limit.".into(), node, source, diagnostics, owner); }
    let parts = split_top_level(raw, '|');
    if parts.len() > 1 {
        let nullable = parts.iter().any(|p| matches!(p.as_str(), "Null" | "scala.Null"));
        let members = parts.iter().filter(|p| !matches!(p.as_str(), "Null" | "scala.Null")).map(|p| parse_text(p, node, source, parameters, diagnostics, owner, depth+1)).collect::<Vec<_>>();
        if members.len() > 1 { diagnostics.push(Diagnostic::warning("scala.union.sample_first", "JSON samples select the first supported union alternative.", Some(span(node)), owner)); }
        let result = union_type(members, span(node));
        return if nullable { nullable_type(result, span(node)) } else { result };
    }
    if raw.starts_with('(') && raw.ends_with(')') && split_top_level(&raw[1..raw.len()-1], ',').len() == 1 { return parse_text(&raw[1..raw.len()-1], node, source, parameters, diagnostics, owner, depth+1); }
    let primitive = match raw.strip_prefix("scala.").unwrap_or(raw) {
        "String" | "Char" | "java.lang.String" | "Predef.String" => Some(PrimitiveKind::String),
        "Byte" | "Short" | "Int" | "Long" | "BigInt" | "math.BigInt" => Some(PrimitiveKind::Integer),
        "Float" | "Double" | "BigDecimal" | "math.BigDecimal" => Some(PrimitiveKind::Decimal), "Boolean" => Some(PrimitiveKind::Boolean), _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
    if matches!(raw, "Any" | "AnyRef" | "AnyVal" | "Object" | "Null" | "Nothing" | "Unit" | "scala.Any" | "scala.Null") {
        return unknown_type_with_message("scala.type.unknown", format!("Scala `{raw}` has no concrete JSON sample shape."), node, source, diagnostics, owner);
    }
    if let Some(open) = raw.find('[') {
        if raw.ends_with(']') {
            let name = raw[..open].trim();
            let args = split_top_level(&raw[open+1..raw.len()-1], ',').iter().map(|a| parse_text(a, node, source, parameters, diagnostics, owner, depth+1)).collect::<Vec<_>>();
            let base = name.rsplit('.').next().unwrap_or(name);
            let standard = !name.contains('.') || matches!(name.rsplit_once('.').map(|p| p.0), Some("scala" | "scala.collection" | "scala.collection.immutable" | "scala.collection.mutable" | "scala.Predef"));
            if standard {
                match (base, args.as_slice()) {
                    ("Option", [value]) => return nullable_type(value.clone(), span(node)),
                    ("List" | "Seq" | "Vector" | "Array" | "Set" | "IndexedSeq" | "Iterable", [value]) => return list_type(value.clone(), span(node)),
                    ("Map" | "HashMap" | "TreeMap", [key, value]) => {
                        if !matches!(key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) { diagnostics.push(Diagnostic::warning("scala.map.key", "JSON object keys are strings; non-string map keys use string samples.", Some(span(node)), owner)); }
                        return map_type(key.clone(), value.clone(), span(node));
                    }
                    _ => {}
                }
            }
            if is_name(name) { return named_type(name.replace('`', ""), args, parameters.contains(name), span(node)); }
        }
    } else if is_name(raw) { return named_type(raw.replace('`', ""), vec![], parameters.contains(raw), span(node)); }
    unknown_type_with_message("scala.type.unsupported", format!("Unsupported Scala type `{raw}`; type-level computation is not evaluated."), node, source, diagnostics, owner)
}
fn is_name(raw: &str) -> bool {
    !raw.is_empty() && raw.split('.').all(|part| {
        if part.starts_with('`') && part.ends_with('`') && part.len() > 2 { return true; }
        let mut chars = part.chars(); chars.next().is_some_and(|c| c.is_alphabetic() || c == '_') && chars.all(|c| c.is_alphanumeric() || c == '_')
    })
}
