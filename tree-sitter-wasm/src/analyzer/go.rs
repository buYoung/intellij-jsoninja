use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, Field, TypeParameter};
use crate::language::SupportedLanguage;
use crate::source::{clean_member_name, named_children, named_children_of_kind, span, text, text_owned};
use crate::type_parser;

use super::walk_nodes;

pub(crate) fn analyze(
    root_node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<Declaration> {
    let mut declarations = Vec::new();
    walk_nodes(root_node, &mut |node| match node.kind() {
        "type_spec" => declarations.push(parse_type_spec(node, source_bytes, diagnostics)),
        "type_alias" => declarations.push(parse_type_alias(node, source_bytes, diagnostics)),
        _ => {}
    });
    declarations
}

fn parse_type_spec(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
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
    let type_node = node.child_by_field_name("type");
    let declaration_kind = type_node.map(|child_node| match child_node.kind() {
        "struct_type" => DeclarationKind::Struct,
        "interface_type" => DeclarationKind::Interface,
        _ => DeclarationKind::TypeAlias,
    }).unwrap_or(DeclarationKind::TypeAlias);

    let (fields, super_types, aliased_type) = match type_node {
        Some(type_node) if type_node.kind() == "struct_type" => parse_struct_members(
            type_node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        Some(type_node) if type_node.kind() == "interface_type" => (
            Vec::new(),
            parse_interface_embeds(
                type_node,
                source_bytes,
                &type_parameter_names,
                diagnostics,
                Some(name.as_str()),
            ),
            None,
        ),
        Some(type_node) => (
            Vec::new(),
            Vec::new(),
            Some(type_parser::parse_type_reference(
                SupportedLanguage::Go,
                type_node,
                source_bytes,
                &type_parameter_names,
                diagnostics,
                Some(name.as_str()),
            )),
        ),
        None => (Vec::new(), Vec::new(), None),
    };

    Declaration {
        name,
        kind: declaration_kind,
        span: span(node),
        annotations: Vec::new(),
        type_parameters,
        super_types,
        fields,
        enum_values: Vec::new(),
        aliased_type,
    }
}

fn parse_type_alias(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
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

    Declaration {
        name: name.clone(),
        kind: DeclarationKind::TypeAlias,
        span: span(node),
        annotations: Vec::new(),
        type_parameters,
        super_types: Vec::new(),
        fields: Vec::new(),
        enum_values: Vec::new(),
        aliased_type: node.child_by_field_name("type").map(|child_node| {
            type_parser::parse_type_reference(
                SupportedLanguage::Go,
                child_node,
                source_bytes,
                &type_parameter_names,
                diagnostics,
                Some(name.as_str()),
            )
        }),
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

    named_children_of_kind(type_parameters_node, "type_parameter_declaration")
        .into_iter()
        .map(|type_parameter_node| {
            let parameter_name = named_children(type_parameter_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "identifier")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let constraints = type_parameter_node
                .child_by_field_name("type")
                .map(|child_node| {
                    vec![type_parser::parse_type_reference(
                        SupportedLanguage::Go,
                        child_node,
                        source_bytes,
                        &BTreeSet::new(),
                        diagnostics,
                        declaration_name,
                    )]
                })
                .unwrap_or_default();

            TypeParameter {
                name: parameter_name,
                constraints,
                span: span(type_parameter_node),
            }
        })
        .collect()
}

fn parse_struct_members(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> (Vec<Field>, Vec<crate::ir::TypeReference>, Option<crate::ir::TypeReference>) {
    let mut fields = Vec::new();
    let mut super_types = Vec::new();
    let field_declaration_nodes = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "field_declaration_list")
        .map(named_children)
        .unwrap_or_default();

    for field_declaration_node in field_declaration_nodes {
        if field_declaration_node.kind() != "field_declaration" {
            continue;
        }
        let type_reference = field_declaration_node
            .child_by_field_name("type")
            .map(|child_node| {
                type_parser::parse_type_reference(
                    SupportedLanguage::Go,
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            })
            .unwrap_or_else(|| type_parser::unknown_type(field_declaration_node, source_bytes));
        let tag_annotations = crate::type_parser::extract_go_tag_annotations(field_declaration_node, source_bytes);
        let field_name_nodes = named_children_of_kind(field_declaration_node, "field_identifier");

        if field_name_nodes.is_empty() {
            super_types.push(type_reference);
            continue;
        }

        for field_name_node in field_name_nodes {
            let source_name = text(field_name_node, source_bytes);
            fields.push(Field {
                name: clean_member_name(source_name),
                source_name: source_name.to_string(),
                optional: false,
                span: span(field_declaration_node),
                annotations: tag_annotations.clone(),
                type_reference: type_reference.clone(),
            });
        }
    }

    (fields, super_types, None)
}

fn parse_interface_embeds(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<crate::ir::TypeReference> {
    let mut super_types = Vec::new();

    for child_node in named_children(node) {
        match child_node.kind() {
            "type_elem" => super_types.push(type_parser::parse_type_reference(
                SupportedLanguage::Go,
                child_node,
                source_bytes,
                type_parameter_names,
                diagnostics,
                declaration_name,
            )),
            "method_elem" => diagnostics.push(Diagnostic::warning(
                "go.interface.method_ignored",
                "Go interface methods are not represented in the declaration IR.",
                Some(span(child_node)),
                declaration_name,
            )),
            _ => {}
        }
    }

    super_types
}
