use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Field, PrimitiveKind, TypeReference};
use crate::source::{clean_member_name, last_path_segment, named_children, named_children_of_kind, span, text, text_owned};

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
        "_type" | "type_elem" | "type_constraint" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        "type_identifier" | "qualified_type" => {
            parse_named_or_primitive(text(node, source_bytes), Vec::new(), type_parameter_names, span(node))
        }
        "generic_type" => {
            let base_type_node = node.child_by_field_name("type");
            let type_arguments_node = node.child_by_field_name("type_arguments");
            let type_arguments = type_arguments_node
                .map(|child_node| {
                    named_children(child_node)
                        .into_iter()
                        .map(|type_element_node| {
                            parse(
                                type_element_node,
                                source_bytes,
                                type_parameter_names,
                                diagnostics,
                                declaration_name,
                            )
                        })
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            let base_name = base_type_node
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_else(|| text_owned(node, source_bytes));
            named_type(
                last_path_segment(&base_name),
                type_arguments,
                type_parameter_names.contains(&last_path_segment(&base_name)),
                span(node),
            )
        }
        "pointer_type" => {
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
        "slice_type" | "array_type" => {
            let element_type_node = node.child_by_field_name("element")
                .or_else(|| named_children(node).into_iter().last());
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
        "map_type" => {
            let key_type = node.child_by_field_name("key")
                .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
                .unwrap_or_else(|| unknown_type(node, source_bytes));
            let value_type = node.child_by_field_name("value")
                .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
                .unwrap_or_else(|| unknown_type(node, source_bytes));
            map_type(key_type, value_type, span(node))
        }
        "struct_type" => parse_inline_struct(node, source_bytes, type_parameter_names, diagnostics, declaration_name),
        "interface_type" => parse_interface_type(node, source_bytes, type_parameter_names, diagnostics, declaration_name),
        "binary_expression" => parse_binary_expression(node, source_bytes, type_parameter_names, diagnostics, declaration_name),
        "negated_type" => named_children(node)
            .into_iter()
            .next()
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(node, source_bytes)),
        _ => unknown_type_with_message(
            "go.type.unsupported",
            format!("Unsupported Go type node `{}`.", node.kind()),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        ),
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
        "string" | "rune" => Some(PrimitiveKind::String),
        "bool" => Some(PrimitiveKind::Boolean),
        "int" | "int8" | "int16" | "int32" | "int64" | "uint" | "uint8" | "uint16" | "uint32" | "uint64" | "uintptr" | "byte" => {
            Some(PrimitiveKind::Integer)
        }
        "float32" | "float64" => Some(PrimitiveKind::Decimal),
        "complex64" | "complex128" => Some(PrimitiveKind::Number),
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

fn parse_inline_struct(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let field_nodes = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "field_declaration_list")
        .map(named_children)
        .unwrap_or_default();
    let mut fields = Vec::new();

    for field_declaration_node in field_nodes {
        if field_declaration_node.kind() != "field_declaration" {
            continue;
        }
        let tag_annotations = extract_go_tag_annotations(field_declaration_node, source_bytes);
        let type_node = field_declaration_node.child_by_field_name("type");
        let resolved_type_reference = type_node
            .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
            .unwrap_or_else(|| unknown_type(field_declaration_node, source_bytes));
        let field_name_nodes = named_children_of_kind(field_declaration_node, "field_identifier");

        if field_name_nodes.is_empty() {
            diagnostics.push(Diagnostic::warning(
                "go.inline_struct.embedded_field_ignored",
                "Embedded struct fields are ignored in inline object normalization.",
                Some(span(field_declaration_node)),
                declaration_name,
            ));
            continue;
        }

        for field_name_node in field_name_nodes {
            let field_source_name = text(field_name_node, source_bytes);
            fields.push(Field {
                name: clean_member_name(field_source_name),
                source_name: field_source_name.to_string(),
                optional: false,
                span: span(field_declaration_node),
                annotations: tag_annotations.clone(),
                type_reference: resolved_type_reference.clone(),
            });
        }
    }

    inline_object_type(fields, span(node))
}

fn parse_interface_type(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    let mut type_members = Vec::new();
    let mut contains_method_member = false;

    for child_node in named_children(node) {
        match child_node.kind() {
            "type_elem" => {
                type_members.push(parse(
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                ));
            }
            "method_elem" => {
                contains_method_member = true;
            }
            _ => {}
        }
    }

    if contains_method_member {
        return unknown_type_with_message(
            "go.interface.methods_unsupported",
            "Go interface methods are not normalized into the current IR type model.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        );
    }

    if type_members.is_empty() {
        return unknown_type(node, source_bytes);
    }

    union_type(type_members, span(node))
}

fn parse_binary_expression(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    if !text(node, source_bytes).contains('|') {
        return unknown_type_with_message(
            "go.binary_expression.unsupported",
            "Only union-style binary expressions are normalized inside Go type constraints.".to_string(),
            node,
            source_bytes,
            diagnostics,
            declaration_name,
        );
    }

    let left_type = node.child_by_field_name("left")
        .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
        .unwrap_or_else(|| unknown_type(node, source_bytes));
    let right_type = node.child_by_field_name("right")
        .map(|child_node| parse(child_node, source_bytes, type_parameter_names, diagnostics, declaration_name))
        .unwrap_or_else(|| unknown_type(node, source_bytes));

    union_type(vec![left_type, right_type], span(node))
}

pub(crate) fn extract_go_tag_annotations(
    node: Node<'_>,
    source_bytes: &[u8],
) -> Vec<crate::ir::Annotation> {
    let Some(tag_node) = node.child_by_field_name("tag") else {
        return Vec::new();
    };
    let tag_text = text(tag_node, source_bytes)
        .trim_matches('`')
        .trim_matches('"');
    let tag_span = span(tag_node);
    let mut annotations = Vec::new();

    for tag_part in tag_text.split_whitespace() {
        let annotation_name = tag_part.split(':').next().unwrap_or("tag").trim();
        annotations.push(crate::ir::Annotation {
            name: annotation_name.to_string(),
            text: tag_part.to_string(),
            span: tag_span.clone(),
        });
    }

    annotations
}
