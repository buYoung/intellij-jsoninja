use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use super::{list_type, map_type, named_type, nullable_type, primitive_type, union_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    let raw = text(node, source).trim();
    let primitive = match raw {
        "bool" => Some(PrimitiveKind::Boolean),
        "char" | "wchar_t" | "char16_t" | "char32_t" | "std::string" | "std::wstring" | "string" => Some(PrimitiveKind::String),
        "float" | "double" | "long double" => Some(PrimitiveKind::Decimal),
        "int" | "short" | "short int" | "long" | "long int" | "long long" | "long long int" | "unsigned" | "unsigned int" | "unsigned long" | "unsigned long long" | "signed" | "signed int" | "size_t" | "std::size_t" | "int64_t" | "std::int64_t" | "std::int32_t" | "std::uint64_t" | "std::uint32_t" => Some(PrimitiveKind::Integer),
        _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
    if matches!(node.kind(), "struct_specifier" | "class_specifier" | "enum_specifier") {
        if let Some(name) = node.child_by_field_name("name") { return named_type(text_owned(name, source), vec![], false, span(node)); }
    }
    if node.kind() == "type_descriptor" {
        if let Some(ty) = node.child_by_field_name("type") {
            let base = parse(ty, source, parameters, diagnostics, owner);
            return node.child_by_field_name("declarator").map(|d| apply_declarator(d, base.clone(), source, diagnostics, owner)).unwrap_or(base);
        }
    }
    let template = if node.kind() == "template_type" { Some(node) } else if node.kind() == "qualified_identifier" {
        node.child_by_field_name("name").filter(|n| n.kind() == "template_type")
    } else { None };
    if let Some(template) = template {
        let name = template.child_by_field_name("name").map(|n| text(n, source)).unwrap_or("");
        let arguments = template.child_by_field_name("arguments").map(named_children).unwrap_or_default();
        let standard = node.kind() == "qualified_identifier" && node.child_by_field_name("scope").is_some_and(|s| text(s, source) == "std");
        if standard {
            let mut parse_arg = |index: usize| arguments.get(index).map(|n| parse(*n, source, parameters, diagnostics, owner));
            match name {
                "vector" | "list" | "deque" | "set" | "unordered_set" if !arguments.is_empty() => return list_type(parse_arg(0).unwrap(), span(node)),
                "array" if arguments.len() == 2 => {
                    let element = parse_arg(0).unwrap();
                    diagnostics.push(Diagnostic::warning("cpp.array.length_ignored", "Fixed C++ array length is not retained in JSON samples.", Some(span(node)), owner));
                    return list_type(element, span(node));
                }
                "map" | "unordered_map" if arguments.len() >= 2 => {
                    let key = parse_arg(0).unwrap(); let value = parse_arg(1).unwrap();
                    if !matches!(&key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) {
                        diagnostics.push(Diagnostic::warning("cpp.map.key", "JSON object keys are strings; C++ map key types are converted to sample strings.", Some(span(node)), owner));
                    }
                    return map_type(key, value, span(node));
                }
                "optional" if arguments.len() == 1 => return nullable_type(parse_arg(0).unwrap(), span(node)),
                "unique_ptr" | "shared_ptr" if !arguments.is_empty() => {
                    let value = parse_arg(0).unwrap();
                    diagnostics.push(Diagnostic::warning("cpp.ownership.ignored", "Pointer ownership is not represented; the pointee is treated as nullable.", Some(span(node)), owner));
                    return nullable_type(value, span(node));
                }
                "variant" if !arguments.is_empty() => {
                    let members = arguments.into_iter().map(|a| parse(a, source, parameters, diagnostics, owner)).collect();
                    diagnostics.push(Diagnostic::warning("cpp.variant.sample_first", "JSON samples select the first supported variant alternative.", Some(span(node)), owner));
                    return union_type(members, span(node));
                }
                _ => {}
            }
        }
        return unknown_type_with_message("cpp.template.unsupported", format!("Template `{raw}` is not instantiated or resolved."), node, source, diagnostics, owner);
    }
    if matches!(raw, "std::any" | "any" | "void" | "auto") {
        return unknown_type_with_message("cpp.type.unknown", format!("C++ `{raw}` has no known concrete JSON shape."), node, source, diagnostics, owner);
    }
    if matches!(node.kind(), "type_identifier" | "qualified_identifier" | "primitive_type" | "sized_type_specifier") {
        return named_type(raw.into(), vec![], parameters.contains(raw), span(node));
    }
    unknown_type_with_message("cpp.type.unsupported", format!("Unsupported C++ type `{raw}`."), node, source, diagnostics, owner)
}

pub(crate) fn declarator_name(node: Node<'_>, source: &[u8]) -> String {
    if matches!(node.kind(), "identifier" | "field_identifier" | "type_identifier") { return text_owned(node, source); }
    node.child_by_field_name("declarator").or_else(|| named_children(node).first().copied())
        .map(|n| declarator_name(n, source)).unwrap_or_default()
}

pub(crate) fn is_method(node: Node<'_>) -> bool {
    node.kind() == "function_declarator" && node.child_by_field_name("declarator").is_some_and(|n| matches!(n.kind(), "field_identifier" | "identifier" | "operator_name" | "destructor_name"))
}

pub(crate) fn apply_declarator(node: Node<'_>, base: TypeReference, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    let next = node.child_by_field_name("declarator").or_else(|| named_children(node).first().copied());
    let mapped = match node.kind() {
        "pointer_declarator" | "abstract_pointer_declarator" => {
            diagnostics.push(Diagnostic::warning("cpp.pointer.assumption", "Pointer length and ownership are unknown; the pointee is interpreted as nullable.", Some(span(node)), owner));
            nullable_type(base, span(node))
        }
        "reference_declarator" | "abstract_reference_declarator" => {
            diagnostics.push(Diagnostic::warning("cpp.reference.ignored", "Reference lifetime and identity are not represented in JSON.", Some(span(node)), owner)); base
        }
        "array_declarator" | "abstract_array_declarator" => {
            diagnostics.push(Diagnostic::warning("cpp.array.length_ignored", "Fixed array lengths are not represented in JSON.", Some(span(node)), owner)); list_type(base, span(node))
        }
        "function_declarator" | "abstract_function_declarator" => return unknown_type_with_message("cpp.function.unsupported", "Function pointer values cannot be represented as JSON.".into(), node, source, diagnostics, owner),
        _ => base,
    };
    next.map(|n| apply_declarator(n, mapped.clone(), source, diagnostics, owner)).unwrap_or(mapped)
}
