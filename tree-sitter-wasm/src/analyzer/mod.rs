mod go;
mod java;
mod kotlin;
mod typescript;

use tree_sitter::Node;

use crate::diagnostics::Diagnostic;
use crate::error::WasmResult;
use crate::ir::{AnalysisOutput, Annotation};
use crate::language::SupportedLanguage;
use crate::parser;
use crate::source::{children, named_children, span, text};

pub(crate) fn analyze_source(
    language: SupportedLanguage,
    source_code: &str,
) -> WasmResult<AnalysisOutput> {
    let tree = parser::parse_text(language, source_code)?;
    let source_bytes = source_code.as_bytes();
    let root_node = tree.root_node();
    let mut diagnostics = Vec::new();
    collect_syntax_diagnostics(root_node, &mut diagnostics);

    let declarations = match language {
        SupportedLanguage::Java => java::analyze(root_node, source_bytes, &mut diagnostics),
        SupportedLanguage::Kotlin => kotlin::analyze(root_node, source_bytes, &mut diagnostics),
        SupportedLanguage::TypeScript => {
            typescript::analyze(root_node, source_bytes, &mut diagnostics)
        }
        SupportedLanguage::Go => go::analyze(root_node, source_bytes, &mut diagnostics),
    };

    Ok(AnalysisOutput {
        language: language.as_json_name(),
        declarations,
        diagnostics,
    })
}

pub(crate) fn walk_nodes(
    node: Node<'_>,
    visitor: &mut impl FnMut(Node<'_>),
) {
    visitor(node);
    for child_node in children(node) {
        walk_nodes(child_node, visitor);
    }
}

pub(crate) fn collect_syntax_diagnostics(
    root_node: Node<'_>,
    diagnostics: &mut Vec<Diagnostic>,
) {
    walk_nodes(root_node, &mut |node| {
        if node.is_error() {
            diagnostics.push(Diagnostic::error(
                "syntax.error",
                format!("Syntax error near `{}`.", node.kind()),
                Some(span(node)),
                None,
            ));
        }
        if node.is_missing() {
            diagnostics.push(Diagnostic::error(
                "syntax.missing",
                format!("Missing syntax node `{}`.", node.kind()),
                Some(span(node)),
                None,
            ));
        }
    });
}

pub(crate) fn extract_at_annotations(
    node: Node<'_>,
    source_bytes: &[u8],
) -> Vec<Annotation> {
    let mut annotations = Vec::new();
    for child_node in children(node) {
        if child_node.kind() == "modifiers" {
            annotations.extend(extract_at_annotations(child_node, source_bytes));
            continue;
        }
        if child_node.kind() == "annotation" || child_node.kind() == "marker_annotation" {
            let raw_text = text(child_node, source_bytes);
            let annotation_name = raw_text
                .trim_start_matches('@')
                .split(['(', ' ', '\n'])
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            annotations.push(Annotation {
                name: annotation_name,
                text: raw_text.to_string(),
                span: span(child_node),
            });
        }
    }
    annotations
}

pub(crate) fn extract_decorator_annotations(
    node: Node<'_>,
    source_bytes: &[u8],
) -> Vec<Annotation> {
    named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == "decorator")
        .map(|decorator_node| {
            let decorator_text = text(decorator_node, source_bytes);
            let decorator_name = decorator_text
                .trim_start_matches('@')
                .split(['(', ' ', '\n'])
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            Annotation {
                name: decorator_name,
                text: decorator_text.to_string(),
                span: span(decorator_node),
            }
        })
        .collect()
}
