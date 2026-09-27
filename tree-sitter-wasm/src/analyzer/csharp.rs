use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Annotation, Declaration, DeclarationKind, EnumValue, Field, TypeReference};
use crate::source::{has_token, named_children, span, text, text_owned};
use crate::type_parser::csharp::parse;

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut out = vec![];
    visit(root, "", source, diagnostics, &mut out);
    let known = out.iter().map(|d| d.name.clone()).collect::<BTreeSet<_>>();
    for declaration in &mut out {
        for field in &mut declaration.fields { resolve_names(&mut field.type_reference, &declaration.name, &known, diagnostics); }
        if let Some(alias) = &mut declaration.aliased_type { resolve_names(alias, &declaration.name, &known, diagnostics); }
    }
    out
}

fn empty(node: Node<'_>, name: String, kind: DeclarationKind) -> Declaration {
    Declaration { name, kind, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None }
}
fn qualify(prefix: &str, name: &str) -> String { if prefix.is_empty() { name.into() } else { format!("{prefix}.{name}") } }
fn clean(name: &str) -> String { name.trim_start_matches('@').to_owned() }
fn warning(code: &str, message: impl Into<String>, node: Node<'_>, owner: Option<&str>, diagnostics: &mut Vec<Diagnostic>) {
    diagnostics.push(Diagnostic::warning(code, message, Some(span(node)), owner));
}

fn visit(node: Node<'_>, prefix: &str, source: &[u8], diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>) {
    match node.kind() {
        "compilation_unit" | "declaration_list" => {
            let mut scope = prefix.to_owned();
            for child in named_children(node) {
                if child.kind() == "file_scoped_namespace_declaration" {
                    if let Some(name) = child.child_by_field_name("name") { scope = qualify(prefix, text(name, source)); }
                } else { visit(child, &scope, source, diagnostics, out); }
            }
        }
        "namespace_declaration" => {
            if let (Some(name), Some(body)) = (node.child_by_field_name("name"), node.child_by_field_name("body")) {
                visit(body, &qualify(prefix, text(name, source)), source, diagnostics, out);
            }
        }
        "class_declaration" | "struct_declaration" | "record_declaration" | "enum_declaration" => {
            let Some(name_node) = node.child_by_field_name("name") else { return; };
            let name = qualify(prefix, &clean(text(name_node, source)));
            out.push(parse_record(node, name.clone(), source, diagnostics));
            if let Some(body) = node.child_by_field_name("body") {
                for child in named_children(body).into_iter().filter(|c| matches!(c.kind(), "class_declaration" | "struct_declaration" | "record_declaration" | "enum_declaration")) {
                    visit(child, &name, source, diagnostics, out);
                }
            }
        }
        "using_directive" => {
            if let Some(name_node) = node.child_by_field_name("name") {
                if let Some(ty) = named_children(node).into_iter().find(|c| c.id() != name_node.id()) {
                    let name = qualify(prefix, text(name_node, source));
                    let mut declaration = empty(node, name.clone(), DeclarationKind::TypeAlias);
                    declaration.aliased_type = Some(parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name)));
                    out.push(declaration);
                }
            }
        }
        kind if kind.starts_with("preproc_if") => warning("csharp.conditional.unsupported", "Conditional declarations are not selected or evaluated.", node, None, diagnostics),
        _ => {}
    }
}

fn parse_record(node: Node<'_>, name: String, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Declaration {
    let kind = match node.kind() { "struct_declaration" => DeclarationKind::Struct, "record_declaration" => DeclarationKind::Record, "enum_declaration" => DeclarationKind::Enum, _ => DeclarationKind::Class };
    let mut declaration = empty(node, name.clone(), kind);
    if named_children(node).iter().any(|n| n.kind() == "base_list") { warning("csharp.base.unsupported", "External/base type members are not merged without a resolved declaration mapping.", node, Some(&name), diagnostics); }
    if named_children(node).iter().any(|n| n.kind() == "type_parameter_list") { warning("csharp.generic.declaration", "Generic declarations are not instantiated; unresolved parameters produce unknown samples.", node, Some(&name), diagnostics); }
    if node.kind() == "record_declaration" {
        if let Some(parameters) = named_children(node).into_iter().find(|n| n.kind() == "parameter_list") {
            for parameter in named_children(parameters).into_iter().filter(|n| n.kind() == "parameter") {
                if let (Some(key), Some(ty)) = (parameter.child_by_field_name("name"), parameter.child_by_field_name("type")) {
                    declaration.fields.push(parse_field(parameter, key, ty, true, source, diagnostics, &name));
                }
            }
        }
    }
    if let Some(body) = node.child_by_field_name("body") {
        for member in named_children(body) {
            if member.kind() == "enum_member_declaration" {
                if let Some(key) = member.child_by_field_name("name") {
                    declaration.enum_values.push(EnumValue { name: clean(text(key, source)), value_text: member.child_by_field_name("value").map(|v| text_owned(v, source)), span: span(member) });
                }
                continue;
            }
            if member.kind().starts_with("preproc_if") { warning("csharp.conditional.unsupported", "Conditional members are not evaluated.", member, Some(&name), diagnostics); continue; }
            if !matches!(member.kind(), "field_declaration" | "property_declaration") { continue; }
            let modifiers = named_children(member).into_iter().filter(|n| n.kind() == "modifier").map(|n| text(n, source)).collect::<Vec<_>>();
            if !modifiers.contains(&"public") || modifiers.contains(&"static") || modifiers.contains(&"const") { continue; }
            if member.kind() == "property_declaration" {
                if let (Some(key), Some(ty)) = (member.child_by_field_name("name"), member.child_by_field_name("type")) {
                    declaration.fields.push(parse_field(member, key, ty, false, source, diagnostics, &name));
                }
            } else if let Some(variables) = named_children(member).into_iter().find(|n| n.kind() == "variable_declaration") {
                if let Some(ty) = variables.child_by_field_name("type") {
                    for variable in named_children(variables).into_iter().filter(|n| n.kind() == "variable_declarator") {
                        if let Some(key) = variable.child_by_field_name("name") { declaration.fields.push(parse_field(member, key, ty, false, source, diagnostics, &name)); }
                    }
                }
            }
        }
    }
    declaration
}

fn parse_field(node: Node<'_>, key: Node<'_>, ty: Node<'_>, is_record_parameter: bool, source: &[u8], diagnostics: &mut Vec<Diagnostic>, owner: &str) -> Field {
    let name = clean(text(key, source));
    let mut source_name = name.clone();
    let mut annotations = vec![];
    for list in named_children(node).into_iter().filter(|n| n.kind() == "attribute_list") {
        let target = named_children(list).into_iter().find(|n| n.kind() == "attribute_target_specifier").map(|n| text(n, source).trim_end_matches(':').trim());
        for attribute in named_children(list).into_iter().filter(|n| n.kind() == "attribute") {
            let attribute_name = attribute.child_by_field_name("name").map(|n| text(n, source)).unwrap_or("");
            annotations.push(Annotation { name: attribute_name.to_owned(), text: text_owned(attribute, source), span: span(attribute) });
            if matches!(attribute_name, "JsonPropertyName" | "JsonPropertyNameAttribute" | "System.Text.Json.Serialization.JsonPropertyName" | "System.Text.Json.Serialization.JsonPropertyNameAttribute") {
                if is_record_parameter && target != Some("property") || target.is_some_and(|t| !matches!(t, "property" | "field")) {
                    warning("csharp.attribute.target", "JsonPropertyName must target the generated record property, not its constructor parameter.", attribute, Some(owner), diagnostics); continue;
                }
                let args = named_children(attribute).into_iter().find(|n| n.kind() == "attribute_argument_list").map(named_children).unwrap_or_default();
                let literal = if args.len() == 1 && args[0].child_by_field_name("name").is_none() { named_children(args[0]).into_iter().next() } else { None };
                let decoded = literal.and_then(|literal| decode_string(text(literal, source)));
                if let Some(value) = decoded { source_name = value; }
                else { warning("csharp.attribute.value", "JsonPropertyName requires a supported literal string; attribute expressions are not evaluated.", attribute, Some(owner), diagnostics); }
            } else {
                warning("csharp.attribute.unsupported", format!("Attribute `{attribute_name}` is retained but its serialization behavior is not evaluated."), attribute, Some(owner), diagnostics);
            }
        }
    }
    let optional = is_record_parameter && has_token(node, "=") || node.prev_named_sibling().is_some_and(|n| n.kind() == "comment" && text(n, source).trim() == "// JSONinja optional v1");
    Field { name, source_name, optional, span: span(node), annotations, type_reference: parse(ty, source, &BTreeSet::new(), diagnostics, Some(owner)) }
}

fn decode_string(raw: &str) -> Option<String> {
    if raw.starts_with("@\"") && raw.ends_with('"') { return Some(raw[2..raw.len()-1].replace("\"\"", "\"")); }
    if raw.starts_with('"') { return serde_json::from_str::<String>(raw).ok(); }
    None
}

fn resolve_names(reference: &mut TypeReference, owner: &str, known: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, .. } => {
            if known.contains(name) { return; }
            let mut prefix = owner;
            loop {
                let scoped = qualify(prefix, name);
                if known.contains(&scoped) { *name = scoped; return; }
                let Some((parent, _)) = prefix.rsplit_once('.') else { break; };
                prefix = parent;
            }
            diagnostics.push(Diagnostic::warning("csharp.type.unresolved", format!("C# type `{name}` is not declared in this source."), Some(span.clone()), Some(owner)));
        }
        TypeReference::List { element_type, .. } => resolve_names(element_type, owner, known, diagnostics),
        TypeReference::Nullable { wrapped_type, .. } => resolve_names(wrapped_type, owner, known, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { resolve_names(key_type, owner, known, diagnostics); resolve_names(value_type, owner, known, diagnostics); }
        _ => {}
    }
}
