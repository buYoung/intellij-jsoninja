use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::TypeReference;
use crate::source::{span, text};

pub(crate) fn parse(node: Node<'_>, source: &[u8], _: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    super::jsdoc_expression::parse(text(node, source), &span(node), diagnostics, owner)
}
