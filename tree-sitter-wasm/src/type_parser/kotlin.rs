use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::PrimitiveKind;
use crate::source::{first_named_child_of_kind, last_path_segment, named_children, span, text_owned};

use super::{
    list_type, map_type, named_type, nullable_type, primitive_type, split_top_level, unknown_type,
    unknown_type_with_message,
};

pub(crate) fn parse(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> crate::ir::TypeReference {
    let raw_text = text_owned(node, source_bytes).replace(' ', "");
    if raw_text.ends_with('?') {
        let base_name = raw_text.trim_end_matches('?');
        return nullable_type(
            parse_text_fallback(base_name, type_parameter_names, span(node)),
            span(node),
        );
    }

    match node.kind() {
        "type" => named_children(node)
            .into_iter()
            .find(|child_node| child_node.kind() != "type_modifiers")
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| parse_text_fallback(&raw_text, type_parameter_names, span(node))),
        "nullable_type" => {
            let inner_type_node = first_named_child_of_kind(node, "type")
                .or_else(|| named_children(node).into_iter().find(|child_node| child_node.kind() != "type_modifiers"));
            inner_type_node.map(|child_node| {
                nullable_type(
                    parse(
                        child_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    ),
                    span(node),
                )
            }).unwrap_or_else(|| parse_text_fallback(&raw_text, type_parameter_names, span(node)))
        }
        "parenthesized_type" => {
            let inner_type_node = first_named_child_of_kind(node, "type");
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
        "user_type" | "non_nullable_type" => parse_user_type(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        "type_projection" => named_children(node)
            .into_iter()
            .find(|child_node| child_node.kind() == "type")
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "function_type" => unknown_type_with_message(
            "kotlin.type.function",
            "Function types are not normalized in the current IR.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
        _ => unknown_type_with_message(
            "kotlin.type.unsupported",
            format!("Unsupported Kotlin type node `{}`.", node.kind()),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
    }
}

fn parse_user_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> crate::ir::TypeReference {
    let full_name = text_owned(node, source_bytes).replace(' ', "");
    let simple_name = last_path_segment(&full_name);
    let type_arguments = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "type_arguments")
        .map(|type_arguments_node| {
            named_children(type_arguments_node)
                .into_iter()
                .filter(|child_node| child_node.kind() == "type_projection")
                .map(|type_projection_node| {
                    parse(
                        type_projection_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    match simple_name.as_str() {
        "Array" | "List" | "MutableList" | "Set" | "MutableSet" | "Collection" | "Iterable" | "Sequence" => {
            let element_type = type_arguments.into_iter().next().unwrap_or_else(|| unknown_type(node, source_bytes));
            list_type(element_type, span(node))
        }
        "Map" | "MutableMap" => {
            let mut type_argument_iter = type_arguments.into_iter();
            let key_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            let value_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            map_type(key_type, value_type, span(node))
        }
        _ => parse_named_or_primitive(
            &simple_name,
            type_arguments,
            type_parameter_names,
            span(node),
        ),
    }
}

fn parse_named_or_primitive(
    raw_name: &str,
    type_arguments: Vec<crate::ir::TypeReference>,
    type_parameter_names: &BTreeSet<String>,
    type_span: crate::ir::Span,
) -> crate::ir::TypeReference {
    let simple_name = last_path_segment(raw_name);
    let primitive_kind = match simple_name.as_str() {
        "String" | "Char" => Some(PrimitiveKind::String),
        "Boolean" => Some(PrimitiveKind::Boolean),
        "Byte" | "Short" | "Int" | "Long" | "UByte" | "UShort" | "UInt" | "ULong" => {
            Some(PrimitiveKind::Integer)
        }
        "Float" | "Double" => Some(PrimitiveKind::Decimal),
        "Number" => Some(PrimitiveKind::Number),
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

fn parse_text_fallback(
    raw_text: &str,
    type_parameter_names: &BTreeSet<String>,
    type_span: crate::ir::Span,
) -> crate::ir::TypeReference {
    let normalized_text = raw_text.trim();
    if normalized_text.ends_with('?') {
        return nullable_type(
            parse_text_fallback(normalized_text.trim_end_matches('?'), type_parameter_names, type_span.clone()),
            type_span,
        );
    }

    if normalized_text.ends_with('>') && normalized_text.contains('<') {
        let generic_start_index = normalized_text.find('<').unwrap_or(0);
        let raw_name = &normalized_text[..generic_start_index];
        let generic_arguments_text = &normalized_text[generic_start_index + 1..normalized_text.len() - 1];
        let generic_arguments = split_top_level(generic_arguments_text, ',')
            .into_iter()
            .map(|argument_text| parse_text_fallback(&argument_text, type_parameter_names, type_span.clone()))
            .collect::<Vec<_>>();

        match last_path_segment(raw_name).as_str() {
            "Array" | "List" | "MutableList" | "Set" | "MutableSet" | "Collection" | "Iterable" | "Sequence" => {
                return list_type(
                    generic_arguments.into_iter().next().unwrap_or_else(|| {
                        parse_named_or_primitive("Any", Vec::new(), type_parameter_names, type_span.clone())
                    }),
                    type_span,
                );
            }
            "Map" | "MutableMap" => {
                let mut generic_argument_iter = generic_arguments.into_iter();
                return map_type(
                    generic_argument_iter.next().unwrap_or_else(|| {
                        parse_named_or_primitive("Any", Vec::new(), type_parameter_names, type_span.clone())
                    }),
                    generic_argument_iter.next().unwrap_or_else(|| {
                        parse_named_or_primitive("Any", Vec::new(), type_parameter_names, type_span.clone())
                    }),
                    type_span,
                );
            }
            _ => {
                return parse_named_or_primitive(
                    raw_name,
                    generic_arguments,
                    type_parameter_names,
                    type_span,
                );
            }
        }
    }

    parse_named_or_primitive(normalized_text, Vec::new(), type_parameter_names, type_span)
}
