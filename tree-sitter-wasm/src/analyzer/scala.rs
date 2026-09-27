use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeParameter, TypeReference};
use crate::source::{children, named_children, span, text};
use crate::type_parser::scala::parse;

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut out = vec![];
    visit(root, "", source, diagnostics, &mut out);
    let known = out.iter().map(|d| d.name.clone()).collect::<BTreeSet<_>>();
    for declaration in &mut out {
        for field in &mut declaration.fields { resolve(&mut field.type_reference, &known, &declaration.name, diagnostics); }
        for base in &mut declaration.super_types { resolve(base, &known, &declaration.name, diagnostics); }
        if let Some(alias) = &mut declaration.aliased_type { resolve(alias, &known, &declaration.name, diagnostics); }
    }
    out
}
fn empty(node: Node<'_>, name: String, kind: DeclarationKind) -> Declaration {
    Declaration { name, kind, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None }
}
fn visit(node: Node<'_>, scope: &str, source: &[u8], diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>) {
    match node.kind() {
        "compilation_unit" | "template_body" => {
            let mut active_scope = scope.to_owned();
            for child in named_children(node) {
                if child.kind() == "package_clause" && child.child_by_field_name("body").is_none() {
                    if let Some(name) = child.child_by_field_name("name") { active_scope = qualified(&active_scope, text(name, source)); }
                } else { visit(child, &active_scope, source, diagnostics, out); }
            }
        }
        "package_clause" => if let (Some(name), Some(body)) = (node.child_by_field_name("name"), node.child_by_field_name("body")) { visit(body, &qualified(scope, text(name, source)), source, diagnostics, out); },
        "object_definition" => if let (Some(name), Some(body)) = (node.child_by_field_name("name"), node.child_by_field_name("body")) {
            let is_generated_scope = text(name, source).starts_with("JsoninjaTypes") && has_marker(node, source, "// JSONinja alias scope v1") && named_children(body).iter().all(|n| matches!(n.kind(), "class_definition" | "type_definition" | "comment" | "block_comment")) && named_children(body).iter().any(|n| n.kind() == "type_definition");
            let next_scope = if is_generated_scope { scope.into() } else { qualified(scope, &unquote(text(name, source))) };
            visit(body, &next_scope, source, diagnostics, out);
        },
        "class_definition" | "trait_definition" | "enum_definition" | "type_definition" => {
            let Some(key) = node.child_by_field_name("name") else { return; };
            let name = qualified(scope, &unquote(text(key, source)));
            let kind = match node.kind() { "trait_definition" => DeclarationKind::Interface, "enum_definition" => DeclarationKind::Enum, "type_definition" => DeclarationKind::TypeAlias, _ => DeclarationKind::Class };
            let mut declaration = empty(node, name.clone(), kind);
            let mut parameters = BTreeSet::new();
            if let Some(generic) = node.child_by_field_name("type_parameters") {
                for parameter in named_children(generic) {
                    let key = if parameter.kind() == "identifier" { Some(parameter) } else { parameter.child_by_field_name("name").or_else(|| named_children(parameter).into_iter().find(|n| n.kind() == "identifier")) };
                    if let Some(key) = key { let key = unquote(text(key, source)); parameters.insert(key.clone()); declaration.type_parameters.push(TypeParameter { name: key, constraints: vec![], span: span(parameter) }); }
                }
            }
            for annotation in named_children(node).into_iter().filter(|n| n.kind() == "annotation") { warning("scala.annotation.unsupported", "Annotation/serializer behavior is not executed.", annotation, &name, diagnostics); }
            if node.kind() == "type_definition" {
                if named_children(node).iter().any(|n| n.kind() == "opaque_modifier") {
                    declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("scala.opaque.unsupported", "Opaque representation is not exposed as a JSON shape.".into(), node, source, diagnostics, Some(&name)));
                } else { declaration.aliased_type = node.child_by_field_name("type").map(|ty| parse(ty, source, &parameters, diagnostics, Some(&name))); }
            } else if node.kind() == "enum_definition" {
                let body = node.child_by_field_name("body").map(named_children).unwrap_or_default();
                let cases = body.into_iter().filter(|n| n.kind() == "enum_case_definitions").flat_map(named_children).filter(|n| matches!(n.kind(), "simple_enum_case" | "full_enum_case")).collect::<Vec<_>>();
                if cases.iter().any(|c| c.kind() == "full_enum_case" || c.child_by_field_name("extend").is_some()) || node.child_by_field_name("class_parameters").is_some() {
                    declaration.kind = DeclarationKind::TypeAlias;
                    declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("scala.enum.payload", "Data-carrying enum cases require a serializer-specific representation.".into(), node, source, diagnostics, Some(&name)));
                } else {
                    declaration.enum_values = cases.iter().filter_map(|case| case.child_by_field_name("name").map(|key| EnumValue { name: unquote(text(key, source)), value_text: None, span: span(*case) })).collect();
                }
            } else {
                let is_case = children(node).iter().any(|n| n.kind() == "case");
                let groups = named_children(node).into_iter().filter(|n| n.kind() == "class_parameters").collect::<Vec<_>>();
                for (index, group) in groups.iter().enumerate() {
                    let is_contextual = children(*group).iter().any(|n| matches!(n.kind(), "using" | "implicit"));
                    for parameter in named_children(*group).into_iter().filter(|n| n.kind() == "class_parameter") {
                        let has_storage = children(parameter).iter().any(|n| matches!(n.kind(), "val" | "var"));
                        if is_contextual || !(has_storage || is_case && index == 0) || is_private(parameter, source) { continue; }
                        add_field(parameter, parameter.child_by_field_name("name"), source, &parameters, &name, diagnostics, &mut declaration.fields);
                        if parameter.child_by_field_name("default_value").is_some() { warning("scala.default.ignored", "Constructor defaults are not optional JSON keys and are not evaluated.", parameter, &name, diagnostics); }
                    }
                }
                if let Some(base) = node.child_by_field_name("extend") {
                    for ty in named_children(base) {
                        if matches!(ty.kind(), "type_identifier" | "stable_type_identifier" | "generic_type") { declaration.super_types.push(parse(ty, source, &parameters, diagnostics, Some(&name))); }
                        else if ty.kind() == "arguments" { warning("scala.base.arguments", "Base constructor arguments are not evaluated.", ty, &name, diagnostics); }
                    }
                }
                if let Some(body) = node.child_by_field_name("body") {
                    for member in named_children(body) {
                        if matches!(member.kind(), "val_definition" | "var_definition" | "val_declaration" | "var_declaration") && !is_private(member, source) {
                            let key = member.child_by_field_name("pattern").or_else(|| member.child_by_field_name("name"));
                            if let Some(keys) = key.filter(|n| n.kind() == "identifiers") {
                                for key in named_children(keys) { add_field(member, Some(key), source, &parameters, &name, diagnostics, &mut declaration.fields); }
                            } else { add_field(member, key, source, &parameters, &name, diagnostics, &mut declaration.fields); }
                        }
                    }
                    visit(body, &name, source, diagnostics, out);
                }
            }
            out.push(declaration);
        }
        _ => {}
    }
}
fn add_field(node: Node<'_>, key: Option<Node<'_>>, source: &[u8], parameters: &BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>, fields: &mut Vec<Field>) {
    let Some(key) = key.filter(|n| matches!(n.kind(), "identifier" | "operator_identifier")) else { return; };
    let name = unquote(text(key, source));
    let reference = node.child_by_field_name("type").map(|ty| parse(ty, source, parameters, diagnostics, Some(owner))).unwrap_or_else(|| crate::type_parser::unknown_type_with_message("scala.field.inferred", "Fields without explicit types are not evaluated.".into(), node, source, diagnostics, Some(owner)));
    let field = Field { name: name.clone(), source_name: name.clone(), optional: has_marker(node, source, "/* JSONinja optional v1 */"), span: span(node), annotations: vec![], type_reference: reference };
    if let Some(index) = fields.iter().position(|f| f.name == name) { fields[index] = field; } else { fields.push(field); }
}
fn is_private(node: Node<'_>, source: &[u8]) -> bool { named_children(node).iter().filter(|n| matches!(n.kind(), "modifiers" | "access_modifier")).any(|n| text(*n, source).split(|c: char| !c.is_alphanumeric()).any(|p| matches!(p, "private" | "protected"))) }
fn has_marker(node: Node<'_>, source: &[u8], marker: &str) -> bool { node.prev_named_sibling().is_some_and(|p| matches!(p.kind(), "comment" | "block_comment") && text(p, source).trim() == marker) }
fn unquote(value: &str) -> String { value.trim_matches('`').into() }
fn qualified(scope: &str, name: &str) -> String { if scope.is_empty() { name.into() } else { format!("{scope}.{name}") } }
fn warning(code: &str, message: &str, node: Node<'_>, owner: &str, diagnostics: &mut Vec<Diagnostic>) { diagnostics.push(Diagnostic::warning(code, message, Some(span(node)), Some(owner))); }
fn resolve(reference: &mut TypeReference, known: &BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, type_arguments, is_type_parameter, span } => {
            for argument in type_arguments { resolve(argument, known, owner, diagnostics); }
            if *is_type_parameter { return; }
            let mut scope = owner;
            loop {
                let candidate = qualified(scope, name);
                if known.contains(&candidate) { *name = candidate; return; }
                if scope.is_empty() { break; }
                scope = scope.rsplit_once('.').map(|p| p.0).unwrap_or("");
            }
            diagnostics.push(Diagnostic::warning("scala.type.unresolved", format!("Scala type `{name}` is not declared in this source; imports and implicit search are not resolved."), Some(span.clone()), Some(owner)));
        }
        TypeReference::Nullable { wrapped_type, .. } => resolve(wrapped_type, known, owner, diagnostics),
        TypeReference::List { element_type, .. } => resolve(element_type, known, owner, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { resolve(key_type, known, owner, diagnostics); resolve(value_type, known, owner, diagnostics); }
        TypeReference::Union { members, .. } => for member in members { resolve(member, known, owner, diagnostics); },
        _ => {}
    }
}
