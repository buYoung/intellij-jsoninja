use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{named_children, span, text};
use super::{list_type, map_type, named_type, nullable_type, primitive_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    parse_inner(node, source, parameters, diagnostics, owner, 0)
}
fn parse_inner(node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, depth: usize) -> TypeReference {
    if depth >= 64 { return unknown_type_with_message("rust.type.depth", "Type nesting exceeds the analysis limit.".into(), node, source, diagnostics, owner); }
    let raw = text(node, source);
    let mut parse_child = |child| parse_inner(child, source, parameters, diagnostics, owner, depth + 1);
    match node.kind() {
        "reference_type" => {
            let result = node.child_by_field_name("type").map(&mut parse_child);
            diagnostics.push(Diagnostic::warning("rust.reference.ignored", "Borrowing, lifetimes and mutability do not change JSON samples.", Some(span(node)), owner));
            if let Some(result) = result { return result; }
        }
        "array_type" => {
            let result = node.child_by_field_name("element").map(&mut parse_child);
            if node.child_by_field_name("length").is_some() { diagnostics.push(Diagnostic::warning("rust.array.length_ignored", "Rust array length is not retained by JSON sample generation.", Some(span(node)), owner)); }
            if let Some(result) = result { return list_type(result, span(node)); }
        }
        "generic_type" => {
            let name = node.child_by_field_name("type").map(|n| text(n, source)).unwrap_or("").trim_start_matches("::");
            let args = node.child_by_field_name("type_arguments").map(named_children).unwrap_or_default();
            if args.iter().any(|n| matches!(n.kind(), "lifetime" | "type_binding" | "integer_literal" | "block")) {
                return unknown_type_with_message("rust.generic.unsupported", format!("Unsupported lifetime, associated or const arguments in `{raw}`."), node, source, diagnostics, owner);
            }
            let args = args.into_iter().map(parse_child).collect::<Vec<_>>();
            let standard = !name.contains("::") || name.starts_with("std::") || name.starts_with("alloc::") || name.starts_with("core::");
            let base = name.rsplit("::").next().unwrap_or(name);
            if standard {
                match (base, args.as_slice()) {
                    ("Option", [value]) => return nullable_type(value.clone(), span(node)),
                    ("Vec" | "VecDeque" | "LinkedList" | "HashSet" | "BTreeSet", [value]) => return list_type(value.clone(), span(node)),
                    ("HashMap" | "BTreeMap", [key, value]) => {
                        if !matches!(key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) { diagnostics.push(Diagnostic::warning("rust.map.key", "JSON object keys are strings; other key types use string samples.", Some(span(node)), owner)); }
                        return map_type(key.clone(), value.clone(), span(node));
                    }
                    ("Box" | "Rc" | "Arc", [value]) => {
                        diagnostics.push(Diagnostic::warning("rust.ownership.ignored", "Ownership wrappers do not change JSON samples.", Some(span(node)), owner));
                        return value.clone();
                    }
                    _ => {}
                }
            }
            return named_type(name.into(), args, parameters.contains(name), span(node));
        }
        "primitive_type" | "type_identifier" | "scoped_type_identifier" => {
            let primitive = match raw {
                "String" | "str" | "char" | "std::string::String" | "alloc::string::String" => Some(PrimitiveKind::String),
                "i8" | "i16" | "i32" | "i64" | "i128" | "isize" | "u8" | "u16" | "u32" | "u64" | "u128" | "usize" => Some(PrimitiveKind::Integer),
                "f32" | "f64" => Some(PrimitiveKind::Decimal), "bool" => Some(PrimitiveKind::Boolean), _ => None,
            };
            if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
            return named_type(raw.trim_start_matches("r#").into(), vec![], parameters.contains(raw), span(node));
        }
        _ => {}
    }
    unknown_type_with_message("rust.type.unsupported", format!("Unsupported Rust type `{raw}`; no trait or macro evaluation is performed."), node, source, diagnostics, owner)
}
