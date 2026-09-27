use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use super::{list_type, named_type, nullable_type, primitive_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], _: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    let raw = text(node, source).trim();
    let primitive = match raw {
        "bool" | "_Bool" => Some(PrimitiveKind::Boolean),
        "float" | "double" | "long double" => Some(PrimitiveKind::Decimal),
        "char" | "signed char" | "unsigned char" | "short" | "short int" | "unsigned short" | "unsigned short int" |
        "int" | "signed" | "signed int" | "unsigned" | "unsigned int" | "long" | "long int" | "unsigned long" | "unsigned long int" |
        "long long" | "long long int" | "unsigned long long" | "unsigned long long int" | "size_t" | "ptrdiff_t" |
        "int8_t" | "int16_t" | "int32_t" | "int64_t" | "uint8_t" | "uint16_t" | "uint32_t" | "uint64_t" => Some(PrimitiveKind::Integer),
        _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
    match node.kind() {
        "struct_specifier" | "enum_specifier" => {
            if let Some(name) = node.child_by_field_name("name") {
                return named_type(text_owned(name, source), vec![], false, span(node));
            }
        }
        "type_identifier" => return named_type(raw.to_owned(), vec![], false, span(node)),
        _ => {}
    }
    unknown_type_with_message("c.type.unsupported", format!("C type `{raw}` is not represented in JSON."), node, source, diagnostics, owner)
}

pub(crate) fn declarator_name(node: Node<'_>, source: &[u8]) -> String {
    match node.kind() {
        "identifier" | "field_identifier" | "type_identifier" => text_owned(node, source),
        _ => node.child_by_field_name("declarator")
            .or_else(|| named_children(node).into_iter().find(|child| child.kind() != "type_qualifier"))
            .map(|child| declarator_name(child, source)).unwrap_or_default(),
    }
}

pub(crate) fn apply_declarator(node: Node<'_>, base: TypeReference, is_char: bool, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>, is_generated: bool) -> TypeReference {
    let next = node.child_by_field_name("declarator");
    match node.kind() {
        "pointer_declarator" => {
            if !is_generated {
                diagnostics.push(Diagnostic::warning("c.pointer.assumption", if is_char {
                    "A char pointer is interpreted as a string; encoding, length and ownership are not inferred."
                } else { "A C pointer is interpreted as a nullable value; allocation and array length are not inferred." }, Some(span(node)), owner));
            }
            let wrapped = if is_char { primitive_type(PrimitiveKind::String, span(node)) } else { nullable_type(base, span(node)) };
            next.map(|child| apply_declarator(child, wrapped.clone(), false, source, diagnostics, owner, is_generated)).unwrap_or(wrapped)
        }
        "array_declarator" => {
            diagnostics.push(Diagnostic::warning("c.array.length_ignored", "C array length is not represented in the declaration IR.", Some(span(node)), owner));
            let wrapped = list_type(base, span(node));
            next.map(|child| apply_declarator(child, wrapped.clone(), false, source, diagnostics, owner, is_generated)).unwrap_or(wrapped)
        }
        "parenthesized_declarator" => named_children(node).first().map(|child|
            apply_declarator(*child, base.clone(), is_char, source, diagnostics, owner, is_generated)).unwrap_or(base),
        "function_declarator" => unknown_type_with_message("c.function_pointer.unsupported", "Function types cannot be represented as JSON values.".into(), node, source, diagnostics, owner),
        _ => base,
    }
}
