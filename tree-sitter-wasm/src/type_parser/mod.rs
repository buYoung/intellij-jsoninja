mod go;
mod java;
mod kotlin;
mod typescript;

use std::collections::BTreeSet;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, Span, TypeReference};
use crate::language::SupportedLanguage;
use crate::source::{span, text_owned};

pub(crate) fn parse_type_reference(
    language: SupportedLanguage,
    node: Node<'_>,
    source_bytes: &[u8],
    type_parameter_names: &BTreeSet<String>,
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    match language {
        SupportedLanguage::Java => java::parse(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        SupportedLanguage::Kotlin => kotlin::parse(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        SupportedLanguage::TypeScript => typescript::parse(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
        SupportedLanguage::Go => go::parse(
            node,
            source_bytes,
            type_parameter_names,
            diagnostics,
            declaration_name,
        ),
    }
}

pub(crate) use go::extract_go_tag_annotations;

pub(crate) fn unknown_type(
    node: Node<'_>,
    source_bytes: &[u8],
) -> TypeReference {
    TypeReference::Unknown {
        raw_text: text_owned(node, source_bytes),
        span: span(node),
    }
}

pub(crate) fn unknown_type_with_message(
    code: &str,
    message: String,
    node: Node<'_>,
    source_bytes: &[u8],
    diagnostics: &mut Vec<Diagnostic>,
    declaration_name: Option<&str>,
) -> TypeReference {
    diagnostics.push(Diagnostic::warning(
        code,
        message,
        Some(span(node)),
        declaration_name,
    ));
    unknown_type(node, source_bytes)
}

pub(crate) fn primitive_type(
    primitive: PrimitiveKind,
    span: Span,
) -> TypeReference {
    TypeReference::Primitive { primitive, span }
}

pub(crate) fn named_type(
    name: String,
    type_arguments: Vec<TypeReference>,
    is_type_parameter: bool,
    span: Span,
) -> TypeReference {
    TypeReference::Named {
        name,
        type_arguments,
        is_type_parameter,
        span,
    }
}

pub(crate) fn list_type(
    element_type: TypeReference,
    span: Span,
) -> TypeReference {
    TypeReference::List {
        element_type: Box::new(element_type),
        span,
    }
}

pub(crate) fn map_type(
    key_type: TypeReference,
    value_type: TypeReference,
    span: Span,
) -> TypeReference {
    TypeReference::Map {
        key_type: Box::new(key_type),
        value_type: Box::new(value_type),
        span,
    }
}

pub(crate) fn nullable_type(
    wrapped_type: TypeReference,
    span: Span,
) -> TypeReference {
    TypeReference::Nullable {
        wrapped_type: Box::new(wrapped_type),
        span,
    }
}

pub(crate) fn inline_object_type(
    fields: Vec<crate::ir::Field>,
    span: Span,
) -> TypeReference {
    TypeReference::InlineObject { fields, span }
}

pub(crate) fn union_type(
    members: Vec<TypeReference>,
    span: Span,
) -> TypeReference {
    let flattened_members = members
        .into_iter()
        .flat_map(|member_type_reference| match member_type_reference {
            TypeReference::Union {
                members: union_members,
                ..
            } => union_members,
            other_type_reference => vec![other_type_reference],
        })
        .collect::<Vec<_>>();

    match flattened_members.len() {
        0 => TypeReference::Unknown {
            raw_text: String::new(),
            span,
        },
        1 => flattened_members.into_iter().next().unwrap_or(TypeReference::Unknown {
            raw_text: String::new(),
            span,
        }),
        _ => TypeReference::Union {
            members: flattened_members,
            span,
        },
    }
}

pub(crate) fn split_top_level(
    text: &str,
    delimiter: char,
) -> Vec<String> {
    let mut parts = Vec::new();
    let mut start_index = 0;
    let mut angle_depth: usize = 0;
    let mut paren_depth: usize = 0;
    let mut brace_depth: usize = 0;
    let mut bracket_depth: usize = 0;

    for (index, character) in text.char_indices() {
        match character {
            '<' => angle_depth += 1,
            '>' => angle_depth = angle_depth.saturating_sub(1),
            '(' => paren_depth += 1,
            ')' => paren_depth = paren_depth.saturating_sub(1),
            '{' => brace_depth += 1,
            '}' => brace_depth = brace_depth.saturating_sub(1),
            '[' => bracket_depth += 1,
            ']' => bracket_depth = bracket_depth.saturating_sub(1),
            _ => {}
        }

        if character == delimiter
            && angle_depth == 0
            && paren_depth == 0
            && brace_depth == 0
            && bracket_depth == 0
        {
            parts.push(text[start_index..index].trim().to_string());
            start_index = index + character.len_utf8();
        }
    }

    parts.push(text[start_index..].trim().to_string());
    parts.into_iter().filter(|part| !part.is_empty()).collect()
}
