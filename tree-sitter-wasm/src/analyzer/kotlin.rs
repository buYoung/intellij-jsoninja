use std::collections::{BTreeMap, BTreeSet};

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeParameter};
use crate::language::SupportedLanguage;
use crate::source::{has_token, named_children, named_children_of_kind, span, text, text_owned};
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
        "object_declaration" => declarations.push(parse_object_declaration(node, source_bytes, diagnostics)),
        "type_alias" => declarations.push(parse_type_alias(node, source_bytes, diagnostics)),
        _ => {}
    });
    declarations
}

fn parse_class_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    let kind = if named_children(node)
        .into_iter()
        .any(|child_node| child_node.kind() == "enum_class_body")
    {
        DeclarationKind::Enum
    } else if has_token(node, "interface") {
        DeclarationKind::Interface
    } else {
        DeclarationKind::Class
    };
    let name = node
        .child_by_field_name("name")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();
    let type_parameters = parse_type_parameters(node, source_bytes, diagnostics, Some(name.as_str()));
    let type_parameter_names = type_parameters
        .iter()
        .map(|type_parameter| type_parameter.name.clone())
        .collect::<BTreeSet<_>>();
    let enum_values = if kind == DeclarationKind::Enum {
        parse_enum_values(node, source_bytes)
    } else {
        Vec::new()
    };

    let declaration = Declaration {
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
        fields: parse_fields(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        enum_values,
        aliased_type: None,
    };

    normalize_container_alias(declaration)
}

fn parse_object_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    let name = node
        .child_by_field_name("name")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();

    let declaration = Declaration {
        name: name.clone(),
        kind: DeclarationKind::Object,
        span: span(node),
        annotations: extract_at_annotations(node, source_bytes),
        type_parameters: Vec::new(),
        super_types: parse_super_types(
            node,
            source_bytes,
            &BTreeSet::new(),
            diagnostics,
            Some(name.as_str()),
        ),
        fields: parse_fields(
            node,
            source_bytes,
            &BTreeSet::new(),
            diagnostics,
            Some(name.as_str()),
        ),
        enum_values: Vec::new(),
        aliased_type: None,
    };

    normalize_container_alias(declaration)
}

fn parse_type_alias(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Declaration {
    let alias_name = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "identifier")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();
    let type_parameters = parse_type_parameters(node, source_bytes, diagnostics, Some(alias_name.as_str()));
    let type_parameter_names = type_parameters
        .iter()
        .map(|type_parameter| type_parameter.name.clone())
        .collect::<BTreeSet<_>>();
    let aliased_type_node = named_children(node)
        .into_iter()
        .rev()
        .find(|child_node| {
            matches!(
                child_node.kind(),
                "type" | "user_type" | "nullable_type" | "parenthesized_type" | "function_type" | "non_nullable_type"
            )
        });

    Declaration {
        name: alias_name.clone(),
        kind: DeclarationKind::TypeAlias,
        span: span(node),
        annotations: extract_at_annotations(node, source_bytes),
        type_parameters,
        super_types: Vec::new(),
        fields: Vec::new(),
        enum_values: Vec::new(),
        aliased_type: aliased_type_node.map(|child_node| {
            type_parser::parse_type_reference(
                SupportedLanguage::Kotlin,
                child_node,
                source_bytes,
                &type_parameter_names,
                diagnostics,
                Some(alias_name.as_str()),
            )
        }),
    }
}

fn normalize_container_alias(declaration: Declaration) -> Declaration {
    if !declaration.fields.is_empty() || declaration.super_types.len() != 1 {
        return declaration;
    }

    let Some(aliased_type_reference) = declaration.super_types.first().cloned() else {
        return declaration;
    };

    if !matches!(
        aliased_type_reference,
        crate::ir::TypeReference::List { .. } | crate::ir::TypeReference::Map { .. }
    ) {
        return declaration;
    }

    Declaration {
        kind: DeclarationKind::TypeAlias,
        super_types: Vec::new(),
        fields: Vec::new(),
        enum_values: Vec::new(),
        aliased_type: Some(aliased_type_reference),
        ..declaration
    }
}

fn parse_type_parameters(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<TypeParameter> {
    let type_parameter_constraints = collect_type_constraints(node, source_bytes, diagnostics, declaration_name);
    let Some(type_parameters_node) = named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == "type_parameters")
    else {
        return Vec::new();
    };

    named_children_of_kind(type_parameters_node, "type_parameter")
        .into_iter()
        .map(|type_parameter_node| {
            let parameter_name = named_children(type_parameter_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "identifier")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let mut constraints = named_children(type_parameter_node)
                .into_iter()
                .filter(|child_node| child_node.kind() == "type")
                .map(|constraint_node| {
                    type_parser::parse_type_reference(
                        SupportedLanguage::Kotlin,
                        constraint_node,
                        source_bytes,
                        &BTreeSet::new(),
                        diagnostics,
                        declaration_name,
                    )
                })
                .collect::<Vec<_>>();
            if let Some(extra_constraints) = type_parameter_constraints.get(&parameter_name) {
                constraints.extend(extra_constraints.clone());
            }

            TypeParameter {
                name: parameter_name,
                constraints,
                span: span(type_parameter_node),
            }
        })
        .collect()
}

fn collect_type_constraints(
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> BTreeMap<String, Vec<crate::ir::TypeReference>> {
    let mut constraints_by_parameter_name = BTreeMap::new();

    for type_constraints_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "type_constraints")
    {
        for type_constraint_node in named_children_of_kind(type_constraints_node, "type_constraint") {
            let parameter_name = named_children(type_constraint_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "identifier")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let constraint_type_node = named_children(type_constraint_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "type");
            if let Some(type_node) = constraint_type_node {
                constraints_by_parameter_name
                    .entry(parameter_name)
                    .or_insert_with(Vec::new)
                    .push(type_parser::parse_type_reference(
                        SupportedLanguage::Kotlin,
                        type_node,
                        source_bytes,
                        &BTreeSet::new(),
                        diagnostics,
                        declaration_name,
                    ));
            }
        }
    }

    constraints_by_parameter_name
}

fn parse_super_types(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<crate::ir::TypeReference> {
    let mut super_types = Vec::new();
    for delegation_specifiers_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "delegation_specifiers")
    {
        for delegation_specifier_node in named_children_of_kind(delegation_specifiers_node, "delegation_specifier") {
            let target_node = named_children(delegation_specifier_node)
                .into_iter()
                .find(|child_node| {
                    matches!(
                        child_node.kind(),
                        "type" | "constructor_invocation" | "explicit_delegation" | "user_type" | "nullable_type"
                    )
                });
            if let Some(type_node) = target_node {
                let parsed_super_type = if type_node.kind() == "constructor_invocation" {
                    named_children(type_node)
                        .into_iter()
                        .find(|child_node| child_node.kind() == "user_type")
                        .map(|child_node| {
                            type_parser::parse_type_reference(
                                SupportedLanguage::Kotlin,
                                child_node,
                                source_bytes,
                                type_parameter_names,
                                diagnostics,
                                declaration_name,
                            )
                        })
                        .unwrap_or_else(|| type_parser::unknown_type(type_node, source_bytes))
                } else {
                    type_parser::parse_type_reference(
                        SupportedLanguage::Kotlin,
                        type_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                };
                super_types.push(parsed_super_type);
            }
        }
    }
    super_types
}

fn parse_fields(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<Field> {
    let mut fields = Vec::new();

    for primary_constructor_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "primary_constructor")
    {
        for class_parameters_node in named_children(primary_constructor_node)
            .into_iter()
            .filter(|child_node| child_node.kind() == "class_parameters")
        {
            for class_parameter_node in named_children_of_kind(class_parameters_node, "class_parameter") {
                let parameter_text = text(class_parameter_node, source_bytes).trim_start();
                if !parameter_text.starts_with("val ") && !parameter_text.starts_with("var ")
                    && !parameter_text.contains(" val ") && !parameter_text.contains(" var ")
                {
                    continue;
                }

                let source_name = named_children(class_parameter_node)
                    .into_iter()
                    .find(|child_node| child_node.kind() == "identifier")
                    .map(|child_node| text_owned(child_node, source_bytes))
                    .unwrap_or_default();
                let type_node = named_children(class_parameter_node)
                    .into_iter()
                    .find(|child_node| {
                        matches!(
                            child_node.kind(),
                            "type" | "user_type" | "nullable_type" | "parenthesized_type" | "function_type" | "non_nullable_type"
                        )
                    });
                let type_reference = type_node
                    .map(|child_node| {
                        type_parser::parse_type_reference(
                            SupportedLanguage::Kotlin,
                            child_node,
                            source_bytes,
                            type_parameter_names,
                            diagnostics,
                            declaration_name,
                        )
                    })
                    .unwrap_or_else(|| type_parser::unknown_type(class_parameter_node, source_bytes));

                fields.push(Field {
                    name: source_name.clone(),
                    source_name,
                    optional: false,
                    span: span(class_parameter_node),
                    annotations: extract_at_annotations(class_parameter_node, source_bytes),
                    type_reference,
                });
            }
        }
    }

    for class_body_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "class_body")
    {
        for property_declaration_node in named_children_of_kind(class_body_node, "property_declaration") {
            let variable_declaration_node = named_children(property_declaration_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "variable_declaration");
            let Some(variable_node) = variable_declaration_node else {
                continue;
            };
            let source_name = named_children(variable_node)
                .into_iter()
                .find(|child_node| child_node.kind() == "identifier")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let type_node = named_children(property_declaration_node)
                .into_iter()
                .find(|child_node| {
                    matches!(
                        child_node.kind(),
                        "type" | "user_type" | "nullable_type" | "parenthesized_type" | "function_type" | "non_nullable_type"
                    )
                });
            let type_reference = type_node
                .map(|child_node| {
                    type_parser::parse_type_reference(
                        SupportedLanguage::Kotlin,
                        child_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                })
                .unwrap_or_else(|| type_parser::unknown_type(property_declaration_node, source_bytes));
            fields.push(Field {
                name: source_name.clone(),
                source_name,
                optional: false,
                span: span(property_declaration_node),
                annotations: extract_at_annotations(property_declaration_node, source_bytes),
                type_reference,
            });
        }
    }

    fields
}

fn parse_enum_values(
    node: Node<'_>,
    source_bytes: &[u8],
) -> Vec<EnumValue> {
    let mut enum_values = Vec::new();
    for enum_class_body_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "enum_class_body")
    {
        walk_nodes(enum_class_body_node, &mut |child_node| {
            if child_node.kind() == "enum_entry" {
                let name = named_children(child_node)
                    .into_iter()
                    .find(|named_child_node| named_child_node.kind() == "identifier")
                    .map(|named_child_node| text_owned(named_child_node, source_bytes))
                    .unwrap_or_default();
                let value_text = named_children(child_node)
                    .into_iter()
                    .find(|named_child_node| named_child_node.kind() == "value_arguments")
                    .map(|named_child_node| text_owned(named_child_node, source_bytes));
                enum_values.push(EnumValue {
                    name,
                    value_text,
                    span: span(child_node),
                });
            }
        });
    }
    enum_values
}
