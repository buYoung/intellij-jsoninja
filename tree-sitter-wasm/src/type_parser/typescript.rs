use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Field, PrimitiveKind, TypeReference};
use crate::source::{clean_member_name, last_path_segment, named_children, named_children_of_kind, span, text};

use super::{
    inline_object_type, list_type, map_type, named_type, nullable_type, primitive_type, union_type,
    unknown_type, unknown_type_with_message,
};

pub(crate) fn parse(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    match node.kind() {
        "type" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "type_annotation" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "parenthesized_type" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "readonly_type" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "predefined_type" => parse_predefined_type(node, source_bytes),
        "type_identifier" | "nested_type_identifier" => {
            named_type(
                clean_member_name(text(node, source_bytes)),
                Vec::new(),
                type_parameter_names.contains(text(node, source_bytes)),
                span(node),
            )
        }
        "generic_type" => parse_generic_type(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        "array_type" => {
            let element_type_node = named_children(node).into_iter().next();
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
        "union_type" => parse_union_type(node, source_bytes, type_parameter_names, diagnostics, declaration_name),
        "object_type" => parse_object_type(node, source_bytes, type_parameter_names, diagnostics, declaration_name),
        "literal_type" => parse_literal_type(node, source_bytes, diagnostics, declaration_name),
        "intersection_type" => unknown_type_with_message(
            "typescript.type.intersection",
            "Intersection types are not normalized in the current IR.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
        "tuple_type" => unknown_type_with_message(
            "typescript.type.tuple",
            "Tuple types are not normalized in the current IR.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
        "flow_maybe_type" => {
            let inner_type_node = named_children(node).into_iter().next();
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
            }).unwrap_or_else(|| unknown_type(node, source_bytes))
        }
        _ => unknown_type_with_message(
            "typescript.type.unsupported",
            format!("Unsupported TypeScript type node `{}`.", node.kind()),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
    }
}

fn parse_predefined_type(
    node: Node<'_>,
    source_bytes: &[u8],
) -> TypeReference {
    let raw_text = text(node, source_bytes);
    let resolved_primitive_kind = match raw_text {
        "string" => Some(PrimitiveKind::String),
        "number" => Some(PrimitiveKind::Number),
        "boolean" => Some(PrimitiveKind::Boolean),
        _ => None,
    };

    resolved_primitive_kind
        .map(|primitive_kind| primitive_type(primitive_kind, span(node)))
        .unwrap_or(TypeReference::Unknown {
            raw_text: raw_text.to_string(),
            span: span(node),
        })
}

fn parse_generic_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let name_node = node.child_by_field_name("name");
    let type_arguments_node = node.child_by_field_name("type_arguments");
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
        .map(|child_node| clean_member_name(text(child_node, source_bytes)))
        .unwrap_or_else(|| clean_member_name(text(node, source_bytes)));
    let simple_name = last_path_segment(&name);

    match simple_name.as_str() {
        "Array" | "ReadonlyArray" | "Set" => {
            let element_type = type_arguments.into_iter().next().unwrap_or_else(|| unknown_type(node, source_bytes));
            list_type(element_type, span(node))
        }
        "Record" | "Map" => {
            let mut type_argument_iter = type_arguments.into_iter();
            let key_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            let value_type = type_argument_iter.next().unwrap_or_else(|| unknown_type(node, source_bytes));
            map_type(key_type, value_type, span(node))
        }
        _ => named_type(
            simple_name.clone(),
            type_arguments,
            type_parameter_names.contains(&simple_name),
            span(node),
        ),
    }
}

fn parse_union_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let mut contains_nullable_member = false;
    let members = named_children(node)
        .into_iter()
        .filter_map(|member_node| {
            let member_text = text(member_node, source_bytes).trim();
            if member_text == "null" || member_text == "undefined" {
                contains_nullable_member = true;
                None
            } else {
                Some(parse(
                    member_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                ))
            }
        })
        .collect::<Vec<_>>();

    let normalized_union = union_type(members, span(node));
    if contains_nullable_member {
        nullable_type(normalized_union, span(node))
    } else {
        normalized_union
    }
}

fn parse_object_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let mut fields = Vec::new();

    for property_signature_node in named_children_of_kind(node, "property_signature") {
        let name_node = property_signature_node.child_by_field_name("name");
        let type_annotation_node = property_signature_node.child_by_field_name("type");
        let Some(property_name_node) = name_node else {
            diagnostics.push(Diagnostic::warning(
                "typescript.object_type.property_name_missing",
                "Object type property is missing a name.",
                Some(span(property_signature_node)),
                declaration_name,
            ));
            continue;
        };

        let property_source_name = text(property_name_node, source_bytes);
        let property_type_reference = type_annotation_node
            .and_then(|child_node| named_children(child_node).into_iter().next())
            .map(|child_node| {
                parse(
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            })
            .unwrap_or_else(|| unknown_type(property_signature_node, source_bytes));

        fields.push(Field {
            name: clean_member_name(property_source_name),
            source_name: property_source_name.to_string(),
            optional: text(property_signature_node, source_bytes).contains('?'),
            span: span(property_signature_node),
            annotations: Vec::new(),
            type_reference: property_type_reference,
        });
    }

    for unsupported_member_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() != "property_signature")
    {
        diagnostics.push(Diagnostic::warning(
            "typescript.object_type.member_unsupported",
            format!(
                "Unsupported object type member `{}` was ignored.",
                unsupported_member_node.kind()
            ),
            Some(span(unsupported_member_node)),
            declaration_name,
        ));
    }

    inline_object_type(fields, span(node))
}

fn parse_literal_type(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    match text(node, source_bytes).trim() {
        "true" | "false" => primitive_type(PrimitiveKind::Boolean, span(node)),
        "null" | "undefined" => unknown_type_with_message(
            "typescript.literal_type.nullish",
            "Nullish literal types are only normalized inside union types.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
        literal_text if literal_text.starts_with('"') || literal_text.starts_with('\'') => {
            primitive_type(PrimitiveKind::String, span(node))
        }
        literal_text if literal_text.parse::<i64>().is_ok() => primitive_type(PrimitiveKind::Integer, span(node)),
        literal_text if literal_text.parse::<f64>().is_ok() => primitive_type(PrimitiveKind::Number, span(node)),
        _ => unknown_type(node, source_bytes),
    }
}
