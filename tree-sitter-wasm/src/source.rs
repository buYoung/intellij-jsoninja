use tree_sitter::Node;

use crate::ir::Span;

pub fn text<'a>(
    node: Node<'_>,
    source_bytes: &'a [u8],
) -> &'a str {
    node.utf8_text(source_bytes).unwrap_or_default()
}

pub fn text_owned(
    node: Node<'_>,
    source_bytes: &[u8],
) -> String {
    text(node, source_bytes).to_string()
}

pub fn span(node: Node<'_>) -> Span {
    let start_position = node.start_position();
    let end_position = node.end_position();
    Span {
        start_row: start_position.row,
        start_col: start_position.column,
        end_row: end_position.row,
        end_col: end_position.column,
    }
}

pub fn named_children(node: Node<'_>) -> Vec<Node<'_>> {
    let mut cursor = node.walk();
    node.named_children(&mut cursor).collect()
}

pub fn children(node: Node<'_>) -> Vec<Node<'_>> {
    let mut cursor = node.walk();
    node.children(&mut cursor).collect()
}

pub fn first_named_child_of_kind<'a>(
    node: Node<'a>,
    kind: &str,
) -> Option<Node<'a>> {
    named_children(node)
        .into_iter()
        .find(|child_node| child_node.kind() == kind)
}

pub fn named_children_of_kind<'a>(
    node: Node<'a>,
    kind: &str,
) -> Vec<Node<'a>> {
    named_children(node)
        .into_iter()
        .filter(|child_node| child_node.kind() == kind)
        .collect()
}

pub fn has_token(
    node: Node<'_>,
    token: &str,
) -> bool {
    children(node)
        .into_iter()
        .any(|child_node| !child_node.is_named() && child_node.kind() == token)
}

pub fn clean_member_name(raw_text: &str) -> String {
    raw_text
        .trim()
        .trim_matches('"')
        .trim_matches('\'')
        .to_string()
}

pub fn last_path_segment(raw_text: &str) -> String {
    raw_text
        .split('.')
        .last()
        .unwrap_or(raw_text)
        .trim()
        .to_string()
}
