use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{first_named_child_of_kind, last_path_segment, named_children, span, text, text_owned};

use super::{
    list_type, map_type, named_type, nullable_type, primitive_type, unknown_type,
    unknown_type_with_message,
};

pub(crate) fn parse(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    match node.kind() {
        "annotated_type" => {
            let inner_type_node = first_named_child_of_kind(node, "_unannotated_type")
                .or_else(|| named_children(node).into_iter().find(|child_node| child_node.kind() != "annotation" && child_node.kind() != "marker_annotation"));
            inner_type_node.map(|child_node| {
                parse(
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            }).unwrap_or_else(|| unknown_type(node, source_bytes))
        }
        "array_type" => {
            let element_type_node = node.child_by_field_name("element");
            element_type_node.map(|child_node| {
                list_type(
                    parse(
                        child_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    ),
                    span(node),
                )
            }).unwrap_or_else(|| unknown_type(node, source_bytes))
        }
        "generic_type" => parse_generic_type(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        "type_identifier" | "scoped_type_identifier" => {
            parse_named_or_primitive(text(node, source_bytes), Vec::new(), type_parameter_names, span(node))
        }
        "integral_type" => primitive_type(PrimitiveKind::Integer, span(node)),
        "floating_point_type" => primitive_type(PrimitiveKind::Decimal, span(node)),
        "boolean_type" => primitive_type(PrimitiveKind::Boolean, span(node)),
        "void_type" => unknown_type_with_message(
            "java.type.void",
            "Void cannot be normalized to a value type.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
        "wildcard" => {
            let bounded_type_node = named_children(node)
                .into_iter()
                .find(|child_node| child_node.kind() != "annotation" && child_node.kind() != "marker_annotation");
            bounded_type_node.map(|child_node| {
                parse(
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            }).unwrap_or_else(|| unknown_type(node, source_bytes))
        }
        _ => unknown_type_with_message(
            "java.type.unsupported",
            format!("Unsupported Java type node `{}`.", node.kind()),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
    }
}

fn parse_generic_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let mut named_children_iter = named_children(node).into_iter();
    let name_node = named_children_iter
        .next()
        .filter(|child_node| child_node.kind() != "type_arguments");
    let type_arguments_node = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "type_arguments");

    let type_arguments = type_arguments_node
        .map(|child_node| {
            named_children(child_node)
                .into_iter()
                .map(|type_argument_node| {
                    parse(
                        type_argument_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    let name = name_node
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_else(|| text_owned(node, source_bytes));

    match last_path_segment(&name).as_str() {
        "List" | "Set" | "Collection" | "Iterable" | "ArrayList" | "LinkedHashSet" => {
            let element_type = type_arguments.into_iter().next().unwrap_or_else(|| unknown_type(node, source_bytes));
            list_type(element_type, span(node))
        }
        "Map" | "HashMap" | "LinkedHashMap" => {
            let mut type_argument_iter = type_arguments.into_iter();
            let key_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            let value_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            map_type(key_type, value_type, span(node))
        }
        "Optional" => {
            let wrapped_type = type_arguments.into_iter().next().unwrap_or_else(|| unknown_type(node, source_bytes));
            nullable_type(wrapped_type, span(node))
        }
        _ => parse_named_or_primitive(&name, type_arguments, type_parameter_names, span(node)),
    }
}

fn parse_named_or_primitive(
    raw_name: &str,
    type_arguments: Vec<TypeReference>,
    type_parameter_names: &BTreeSet<String>,
    type_span: crate::ir::Span,
) -> TypeReference {
    let simple_name = last_path_segment(raw_name);
    let primitive_kind = match simple_name.as_str() {
        "String" | "CharSequence" | "Character" | "char" => Some(PrimitiveKind::String),
        "boolean" | "Boolean" => Some(PrimitiveKind::Boolean),
        "byte" | "short" | "int" | "long" | "Byte" | "Short" | "Integer" | "Long" => {
            Some(PrimitiveKind::Integer)
        }
        "float" | "double" | "Float" | "Double" | "BigDecimal" => Some(PrimitiveKind::Decimal),
        "Number" | "BigInteger" => Some(PrimitiveKind::Number),
        _ => None,
    };

    primitive_kind
        .map(|resolved_primitive_kind| primitive_type(resolved_primitive_kind, type_span.clone()))
        .unwrap_or_else(|| {
            named_type(
                simple_name.clone(),
                type_arguments,
                type_parameter_names.contains(&simple_name),
                type_span,
            )
        })
}
