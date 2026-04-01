use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeParameter};
use crate::language::SupportedLanguage;
use crate::source::{first_named_child_of_kind, named_children, named_children_of_kind, span, text_owned};
use crate::type_parser;

use super::{extract_at_annotations, walk_nodes};

pub(crate) fn analyze(
    root_node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<Declaration> {
    let mut declarations = Vec::new();
    walk_nodes(root_node, &mut |node| match node.kind() {
        "class_declaration" => declarations.push(parse_class_declaration(node, source_bytes, diagnostics)),
        "interface_declaration" => declarations.push(parse_interface_declaration(node, source_bytes, diagnostics)),
        "record_declaration" => declarations.push(parse_record_declaration(node, source_bytes, diagnostics)),
        "enum_declaration" => declarations.push(parse_enum_declaration(node, source_bytes, diagnostics)),
        _ => {}
    });
    declarations
}

fn parse_class_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    parse_declaration_with_kind(
        node,
        DeclarationKind::Class,
        source_bytes,
        diagnostics,
        false,
    )
}

fn parse_interface_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    parse_declaration_with_kind(
        node,
        DeclarationKind::Interface,
        source_bytes,
        diagnostics,
        false,
    )
}

fn parse_record_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    parse_declaration_with_kind(
        node,
        DeclarationKind::Record,
        source_bytes,
        diagnostics,
        true,
    )
}

fn parse_enum_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    let name = node
        .child_by_field_name("name")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();
    let annotations = extract_at_annotations(node, source_bytes);
    let body_node = node.child_by_field_name("body");
    let enum_values = body_node
        .map(|child_node| {
            named_children_of_kind(child_node, "enum_constant")
                .into_iter()
                .map(|enum_constant_node| EnumValue {
                    name: enum_constant_node
                        .child_by_field_name("name")
                        .map(|child_node| text_owned(child_node, source_bytes))
                        .unwrap_or_default(),
                    value_text: enum_constant_node
                        .child_by_field_name("arguments")
                        .map(|child_node| text_owned(child_node, source_bytes)),
                    span: span(enum_constant_node),
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    Declaration {
        name,
        kind: DeclarationKind::Enum,
        span: span(node),
        annotations,
        type_parameters: Vec::new(),
        super_types: parse_super_types(node, source_bytes, &BTreeSet::new(), diagnostics, Some("enum")),
        fields: Vec::new(),
        enum_values,
        aliased_type: None,
    }
}

fn parse_declaration_with_kind(
    node: Node<'_>,
    kind: DeclarationKind,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    uses_record_components: bool,
) -> Declaration {
    let name = node
        .child_by_field_name("name")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();
    let type_parameters = parse_type_parameters(node, source_bytes, diagnostics, Some(name.as_str()));
    let type_parameter_names = type_parameters
        .iter()
        .map(|type_parameter| type_parameter.name.clone())
        .collect::<BTreeSet<_>>();
    let fields = if uses_record_components {
        parse_record_fields(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        )
    } else {
        parse_body_fields(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        )
    };

    Declaration {
        name: name.clone(),
        kind,
        span: span(node),
        annotations: extract_at_annotations(node, source_bytes),
        type_parameters,
        super_types: parse_super_types(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        fields,
        enum_values: Vec::new(),
        aliased_type: None,
    }
}

fn parse_type_parameters(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<TypeParameter> {
    let Some(type_parameters_node) = node.child_by_field_name("type_parameters") else {
        return Vec::new();
    };

    named_children_of_kind(type_parameters_node, "type_parameter")
        .into_iter()
        .map(|type_parameter_node| {
            let parameter_name = named_children(type_parameter_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "type_identifier")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let constraints = named_children(type_parameter_node)
                .into_iter()
                .filter(|child_node| child_node.kind() == "type_bound")
                .flat_map(|type_bound_node| named_children(type_bound_node))
                .map(|constraint_node| {
                    type_parser::parse_type_reference(
                        SupportedLanguage::Java,
                        constraint_node,
                        source_bytes,
                        &BTreeSet::new(),
                        diagnostics,
                        declaration_name,
                    )
                })
                .collect::<Vec<_>>();

            TypeParameter {
                name: parameter_name,
                constraints,
                span: span(type_parameter_node),
            }
        })
        .collect()
}

fn parse_body_fields(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<Field> {
    let Some(body_node) = node.child_by_field_name("body") else {
        return Vec::new();
    };

    let mut fields = Vec::new();
    for field_declaration_node in named_children_of_kind(body_node, "field_declaration") {
        let type_node = field_declaration_node.child_by_field_name("type");
        let resolved_type_reference = type_node
            .map(|child_node| {
                type_parser::parse_type_reference(
                    SupportedLanguage::Java,
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            })
            .unwrap_or_else(|| type_parser::unknown_type(field_declaration_node, source_bytes));
        let annotations = extract_at_annotations(field_declaration_node, source_bytes);

        for variable_declarator_node in named_children_of_kind(field_declaration_node, "variable_declarator") {
            let source_name = variable_declarator_node
                .child_by_field_name("name")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            fields.push(Field {
                name: source_name.clone(),
                source_name,
                optional: false,
                span: span(variable_declarator_node),
                annotations: annotations.clone(),
                type_reference: resolved_type_reference.clone(),
            });
        }
    }
    fields
}

fn parse_record_fields(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<Field> {
    let Some(parameters_node) = node.child_by_field_name("parameters") else {
        return Vec::new();
    };

    named_children_of_kind(parameters_node, "formal_parameter")
        .into_iter()
        .map(|formal_parameter_node| {
            let source_name = formal_parameter_node
                .child_by_field_name("name")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let type_reference = formal_parameter_node
                .child_by_field_name("type")
                .map(|child_node| {
                    type_parser::parse_type_reference(
                        SupportedLanguage::Java,
                        child_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                })
                .unwrap_or_else(|| type_parser::unknown_type(formal_parameter_node, source_bytes));

            Field {
                name: source_name.clone(),
                source_name,
                optional: false,
                span: span(formal_parameter_node),
                annotations: extract_at_annotations(formal_parameter_node, source_bytes),
                type_reference,
            }
        })
        .collect()
}

fn parse_super_types(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<crate::ir::TypeReference> {
    let mut super_types = Vec::new();

    if let Some(superclass_node) = node.child_by_field_name("superclass") {
        if let Some(type_node) = named_children(superclass_node).into_iter().next() {
            super_types.push(type_parser::parse_type_reference(
                SupportedLanguage::Java,
                type_node,
                source_bytes,
                type_parameter_names,
                diagnostics,
                declaration_name,
            ));
        }
    }

    if let Some(super_interfaces_node) = node.child_by_field_name("interfaces") {
        let type_list_node = first_named_child_of_kind(super_interfaces_node, "type_list")
            .unwrap_or(super_interfaces_node);
        for type_node in named_children(type_list_node) {
            super_types.push(type_parser::parse_type_reference(
                SupportedLanguage::Java,
                type_node,
                source_bytes,
                type_parameter_names,
                diagnostics,
                declaration_name,
            ));
        }
    }

    super_types
}
