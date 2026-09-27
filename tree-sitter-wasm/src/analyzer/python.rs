use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, Field, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use crate::type_parser::python::{parse, decode_string, wrapper};

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut out = vec![];
    let mut typed_dict_names = BTreeSet::new();
    let mut class_bases = vec![];
    super::walk_nodes(root, &mut |node| {
        if node.kind() == "class_definition" {
            let name = node.child_by_field_name("name").map(|n| text_owned(n, source)).unwrap_or_default();
            let bases = node.child_by_field_name("superclasses").map(named_children).unwrap_or_default().iter().map(|n| text_owned(*n, source)).collect::<Vec<_>>();
            class_bases.push((name, bases));
        }
        if node.kind() == "assignment" && node.child_by_field_name("right").is_some_and(|r| r.kind() == "call" && r.child_by_field_name("function").is_some_and(|f| matches!(text(f, source), "TypedDict" | "typing.TypedDict"))) {
            if let Some(key) = node.child_by_field_name("left") { typed_dict_names.insert(text_owned(key, source)); }
        }
    });
    loop {
        let before = typed_dict_names.len();
        for (name, bases) in &class_bases {
            if bases.iter().any(|b| matches!(b.as_str(), "TypedDict" | "typing.TypedDict") || typed_dict_names.contains(b)) { typed_dict_names.insert(name.clone()); }
        }
        if before == typed_dict_names.len() { break; }
    }
    visit(root, source, &typed_dict_names, diagnostics, &mut out);
    let known = out.iter().map(|d| d.name.clone()).collect::<BTreeSet<_>>();
    for declaration in &out {
        for field in &declaration.fields { check_names(&field.type_reference, &known, &declaration.name, diagnostics); }
        if let Some(alias) = &declaration.aliased_type { check_names(alias, &known, &declaration.name, diagnostics); }
        for base in &declaration.super_types { check_names(base, &known, &declaration.name, diagnostics); }
    }
    out
}
fn empty(node: Node<'_>, name: String, kind: DeclarationKind) -> Declaration {
    Declaration { name, kind, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None }
}
fn warning(code: &str, message: impl Into<String>, node: Node<'_>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    diagnostics.push(Diagnostic::warning(code, message, Some(span(node)), Some(owner)));
}
fn visit(node: Node<'_>, source: &[u8], typed_dict_names: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>) {
    match node.kind() {
        "module" | "expression_statement" => for child in named_children(node) { visit(child, source, typed_dict_names, diagnostics, out); },
        "decorated_definition" => if let Some(definition) = node.child_by_field_name("definition") {
            if definition.kind() == "class_definition" {
                for decorator in named_children(node).into_iter().filter(|n| n.kind() == "decorator") {
                    let raw = text(decorator, source).trim_start_matches('@');
                    if !matches!(raw.split('(').next(), Some("dataclass" | "dataclasses.dataclass")) {
                        warning("python.decorator.unsupported", "Decorator behavior is not executed or used to invent fields.", decorator, text(definition.child_by_field_name("name").unwrap(), source), diagnostics);
                    }
                }
                visit(definition, source, typed_dict_names, diagnostics, out);
            }
        },
        "class_definition" => {
            let name = node.child_by_field_name("name").map(|n| text_owned(n, source)).unwrap_or_default();
            let bases = node.child_by_field_name("superclasses").map(named_children).unwrap_or_default();
            let mut declaration = empty(node, name.clone(), DeclarationKind::Class);
            let is_typed_dict = typed_dict_names.contains(&name);
            let is_total = totality(&bases, source, diagnostics, node, &name);
            if bases.iter().any(|b| matches!(text(*b, source), "Enum" | "IntEnum" | "enum.Enum" | "enum.IntEnum")) {
                declaration.kind = DeclarationKind::TypeAlias;
                declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("python.enum.unsupported", "Python Enum runtime values are not evaluated.".into(), node, source, diagnostics, Some(&name)));
                out.push(declaration); return;
            }
            for base in &bases {
                let raw = text(*base, source);
                if base.kind() == "keyword_argument" || matches!(raw, "TypedDict" | "typing.TypedDict" | "object") { continue; }
                declaration.super_types.push(parse(*base, source, &BTreeSet::new(), diagnostics, Some(&name)));
            }
            if let Some(body) = node.child_by_field_name("body") {
                for statement in named_children(body).into_iter().filter(|n| n.kind() == "expression_statement") {
                    for assignment in named_children(statement).into_iter().filter(|n| n.kind() == "assignment") {
                        if let (Some(key), Some(ty)) = (assignment.child_by_field_name("left"), assignment.child_by_field_name("type")) {
                            if key.kind() != "identifier" { continue; }
                            let raw_type = text(ty, source);
                            if wrapper(raw_type).as_deref() == Some("ClassVar") { continue; }
                            let optional = match wrapper(raw_type).as_deref() {
                                Some("Required") => false, Some("NotRequired") => true,
                                _ => if is_typed_dict { !is_total } else { assignment.child_by_field_name("right").is_some() },
                            };
                            let field_name = text_owned(key, source);
                            declaration.fields.push(Field { name: field_name.clone(), source_name: field_name, optional, span: span(assignment), annotations: vec![], type_reference: parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name)) });
                        }
                    }
                }
            }
            out.push(declaration);
        }
        "assignment" => {
            let (Some(key), Some(right)) = (node.child_by_field_name("left"), node.child_by_field_name("right")) else { return; };
            if key.kind() != "identifier" { return; }
            let name = text_owned(key, source);
            if right.kind() == "call" && right.child_by_field_name("function").is_some_and(|f| matches!(text(f, source), "TypedDict" | "typing.TypedDict")) {
                out.push(functional_typed_dict(node, right, name, source, diagnostics));
            } else if matches!(right.kind(), "subscript" | "binary_operator" | "identifier" | "attribute" | "none") || node.child_by_field_name("type").is_some_and(|t| matches!(text(t, source), "TypeAlias" | "typing.TypeAlias")) {
                let mut declaration = empty(node, name.clone(), DeclarationKind::TypeAlias);
                declaration.aliased_type = Some(parse(right, source, &BTreeSet::new(), diagnostics, Some(&name)));
                out.push(declaration);
            }
        }
        _ => {}
    }
}
fn totality(arguments: &[Node<'_>], source: &[u8], diagnostics: &mut Vec<Diagnostic>, node: Node<'_>, owner: &str) -> bool {
    for argument in arguments {
        if argument.kind() == "keyword_argument" && argument.child_by_field_name("name").is_some_and(|n| text(n, source) == "total") {
            return match argument.child_by_field_name("value").map(|n| text(n, source)) {
                Some("False") => false, Some("True") => true,
                _ => { warning("python.typeddict.total", "TypedDict totality must be a literal bool; defaulting to required keys.", node, owner, diagnostics); true }
            };
        }
    }
    true
}
fn functional_typed_dict(node: Node<'_>, call: Node<'_>, name: String, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Declaration {
    let mut declaration = empty(node, name.clone(), DeclarationKind::Class);
    let args = call.child_by_field_name("arguments").map(named_children).unwrap_or_default();
    let is_total = totality(&args, source, diagnostics, node, &name);
    let Some(dictionary) = args.get(1).filter(|n| n.kind() == "dictionary") else {
        declaration.kind = DeclarationKind::TypeAlias;
        declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("python.typeddict.dynamic", "Functional TypedDict fields must be a literal dictionary.".into(), call, source, diagnostics, Some(&name)));
        return declaration;
    };
    for pair in named_children(*dictionary) {
        let (Some(key), Some(value)) = (pair.child_by_field_name("key"), pair.child_by_field_name("value")) else {
            warning("python.typeddict.dynamic", "Dynamic TypedDict dictionary entries are omitted.", pair, &name, diagnostics); continue;
        };
        let Some(field_name) = (if key.kind() == "string" { decode_string(text(key, source)) } else { None }) else {
            warning("python.typeddict.key", "TypedDict keys must be supported literal strings.", key, &name, diagnostics); continue;
        };
        let optional = match wrapper(text(value, source)).as_deref() { Some("Required") => false, Some("NotRequired") => true, _ => !is_total };
        declaration.fields.push(Field { name: field_name.clone(), source_name: field_name, optional, span: span(pair), annotations: vec![], type_reference: parse(value, source, &BTreeSet::new(), diagnostics, Some(&name)) });
    }
    declaration
}
fn check_names(reference: &TypeReference, known: &BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, .. } if !known.contains(name) => diagnostics.push(Diagnostic::warning("python.type.unresolved", format!("Python type `{name}` is not declared in this source; modules are not imported."), Some(span.clone()), Some(owner))),
        TypeReference::Nullable { wrapped_type, .. } => check_names(wrapped_type, known, owner, diagnostics),
        TypeReference::List { element_type, .. } => check_names(element_type, known, owner, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { check_names(key_type, known, owner, diagnostics); check_names(value_type, known, owner, diagnostics); }
        TypeReference::Union { members, .. } => for m in members { check_names(m, known, owner, diagnostics); },
        _ => {}
    }
}
