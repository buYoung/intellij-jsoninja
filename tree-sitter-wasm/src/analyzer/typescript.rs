use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeParameter};
use crate::language::SupportedLanguage;
use crate::source::{children, clean_member_name, named_children, named_children_of_kind, span, text, text_owned};
use crate::type_parser;

use super::{extract_decorator_annotations, walk_nodes};

pub(crate) fn analyze(
    root_node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<Declaration> {
    let mut declarations = Vec::new();
    walk_nodes(root_node, &mut |node| match node.kind() {
        "interface_declaration" => declarations.push(parse_interface_declaration(node, source_bytes, diagnostics)),
        "type_alias_declaration" => declarations.push(parse_type_alias_declaration(node, source_bytes, diagnostics)),
        "class_declaration" | "abstract_class_declaration" => {
            declarations.push(parse_class_declaration(node, source_bytes, diagnostics))
        }
        "enum_declaration" => declarations.push(parse_enum_declaration(node, source_bytes)),
        _ => {}
    });
    declarations
}

fn parse_interface_declaration(
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
        kind: DeclarationKind::Interface,
        span: span(node),
        annotations: extract_decorator_annotations(node, source_bytes),
        type_parameters,
        super_types: parse_interface_super_types(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        fields: parse_interface_fields(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        enum_values: Vec::new(),
        aliased_type: None,
    }
}

fn parse_type_alias_declaration(
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
        annotations: extract_decorator_annotations(node, source_bytes),
        type_parameters,
        super_types: Vec::new(),
        fields: Vec::new(),
        enum_values: Vec::new(),
        aliased_type: node.child_by_field_name("value").map(|child_node| {
            type_parser::parse_type_reference(
                SupportedLanguage::TypeScript,
                child_node,
                source_bytes,
                &type_parameter_names,
                diagnostics,
                Some(name.as_str()),
            )
        }),
    }
}

fn parse_class_declaration(
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
        kind: DeclarationKind::Class,
        span: span(node),
        annotations: extract_decorator_annotations(node, source_bytes),
        type_parameters,
        super_types: parse_class_heritage(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        fields: parse_class_fields(
            node,
            source_bytes,
            &type_parameter_names,
            diagnostics,
            Some(name.as_str()),
        ),
        enum_values: Vec::new(),
        aliased_type: None,
    }
}

fn parse_enum_declaration(
    node: Node<'_>,
    source_bytes: &[u8],
) -> Declaration {
    let name = node
        .child_by_field_name("name")
        .map(|child_node| text_owned(child_node, source_bytes))
        .unwrap_or_default();
    let enum_values = node.child_by_field_name("body")
        .map(|body_node| {
            named_children_of_kind(body_node, "enum_assignment")
                .into_iter()
                .map(|enum_assignment_node| EnumValue {
                    name: enum_assignment_node
                        .child_by_field_name("name")
                        .map(|child_node| clean_member_name(text(child_node, source_bytes)))
                        .unwrap_or_default(),
                    value_text: enum_assignment_node
                        .child_by_field_name("value")
                        .map(|child_node| text_owned(child_node, source_bytes)),
                    span: span(enum_assignment_node),
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    Declaration {
        name,
        kind: DeclarationKind::Enum,
        span: span(node),
        annotations: extract_decorator_annotations(node, source_bytes),
        type_parameters: Vec::new(),
        super_types: Vec::new(),
        fields: Vec::new(),
        enum_values,
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
            let parameter_name = type_parameter_node
                .child_by_field_name("name")
                .map(|child_node| text_owned(child_node, source_bytes))
                .unwrap_or_default();
            let constraints = type_parameter_node
                .child_by_field_name("constraint")
                .map(|constraint_node| {
                    named_children(constraint_node)
                        .into_iter()
                        .map(|constraint_type_node| {
                            type_parser::parse_type_reference(
                                SupportedLanguage::TypeScript,
                                constraint_type_node,
                                source_bytes,
                                &BTreeSet::new(),
                                diagnostics,
                                declaration_name,
                            )
                        })
                        .collect::<Vec<_>>()
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

fn parse_interface_super_types(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<crate::ir::TypeReference> {
    named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "extends_type_clause")
        .flat_map(|extends_type_clause_node| {
            named_children(extends_type_clause_node)
                .into_iter()
                .map(|type_node| {
                    type_parser::parse_type_reference(
                        SupportedLanguage::TypeScript,
                        type_node,
                        source_bytes,
                        type_parameter_names,
                        diagnostics,
                        declaration_name,
                    )
                })
                .collect::<Vec<_>>()
        })
        .collect()
}

fn parse_class_heritage(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<crate::ir::TypeReference> {
    let mut super_types = Vec::new();

    for class_heritage_node in named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "class_heritage")
    {
        for heritage_member_node in named_children(class_heritage_node) {
            match heritage_member_node.kind() {
                "extends_clause" => {
                    if let Some(value_node) = heritage_member_node.child_by_field_name("value") {
                        super_types.push(type_parser::parse_type_reference(
                            SupportedLanguage::TypeScript,
                            value_node,
                            source_bytes,
                            type_parameter_names,
                            diagnostics,
                            declaration_name,
                        ));
                    }
                }
                "implements_clause" => {
                    for type_node in named_children(heritage_member_node) {
                        super_types.push(type_parser::parse_type_reference(
                            SupportedLanguage::TypeScript,
                            type_node,
                            source_bytes,
                            type_parameter_names,
                            diagnostics,
                            declaration_name,
                        ));
                    }
                }
                _ => {}
            }
        }
    }

    super_types
}

fn parse_interface_fields(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<Field> {
    let Some(body_node) = node.child_by_field_name("body") else {
        return Vec::new();
    };

    parse_property_signature_fields(
        body_node,
        source_bytes,
        type_parameter_names,
        diagnostics,
        declaration_name,
    )
}

fn parse_class_fields(
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
    for field_node in named_children_of_kind(body_node, "public_field_definition") {
        let source_name = field_node
            .child_by_field_name("name")
            .map(|child_node| clean_member_name(text(child_node, source_bytes)))
            .unwrap_or_default();
        let type_reference = field_node
            .child_by_field_name("type")
            .and_then(|child_node| named_children(child_node).into_iter().next())
            .map(|child_node| {
                type_parser::parse_type_reference(
                    SupportedLanguage::TypeScript,
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            })
            .unwrap_or_else(|| type_parser::unknown_type(field_node, source_bytes));
        fields.push(Field {
            name: source_name.clone(),
            source_name,
            optional: text(field_node, source_bytes).contains('?'),
            span: span(field_node),
            annotations: extract_decorator_annotations(field_node, source_bytes),
            type_reference,
        });
    }
    fields
}

fn parse_property_signature_fields(
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> Vec<Field> {
    let mut fields = Vec::new();

    for property_signature_node in named_children_of_kind(node, "property_signature") {
        let source_name = property_signature_node
            .child_by_field_name("name")
            .map(|child_node| clean_member_name(text(child_node, source_bytes)))
            .unwrap_or_default();
        let type_reference = property_signature_node
            .child_by_field_name("type")
            .and_then(|child_node| named_children(child_node).into_iter().next())
            .map(|child_node| {
                type_parser::parse_type_reference(
                    SupportedLanguage::TypeScript,
                    child_node,
                    source_bytes,
                    type_parameter_names,
                    diagnostics,
                    declaration_name,
                )
            })
            .unwrap_or_else(|| type_parser::unknown_type(property_signature_node, source_bytes));
        fields.push(Field {
            name: source_name.clone(),
            source_name,
            optional: text(property_signature_node, source_bytes).contains('?'),
            span: span(property_signature_node),
            annotations: extract_decorator_annotations(property_signature_node, source_bytes),
            type_reference,
        });
    }

    for unsupported_member_node in children(node)
        .into_iter()
        .filter(|child_node| child_node.is_named() && child_node.kind() != "property_signature")
    {
        diagnostics.push(Diagnostic::warning(
            "typescript.interface.member_ignored",
            format!(
                "Unsupported interface member `{}` was ignored.",
                unsupported_member_node.kind()
            ),
            Some(span(unsupported_member_node)),
            declaration_name,
        ));
    }

    fields
}
