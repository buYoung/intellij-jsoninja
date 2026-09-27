use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeParameter, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use crate::type_parser::rust::parse;
use super::rust_attributes as attributes;

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut out = vec![]; let mut helpers = BTreeSet::new();
    visit(root, "", source, diagnostics, &mut out, &mut helpers);
    let known = out.iter().map(|d| d.name.clone()).collect::<BTreeSet<_>>();
    for declaration in &mut out {
        for field in &mut declaration.fields { resolve(&mut field.type_reference, &known, &helpers, &declaration.name, diagnostics); }
        if let Some(alias) = &mut declaration.aliased_type { resolve(alias, &known, &helpers, &declaration.name, diagnostics); }
    }
    out
}
fn empty(node: Node<'_>, name: String, kind: DeclarationKind) -> Declaration {
    Declaration { name, kind, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None }
}
fn visit(node: Node<'_>, scope: &str, source: &[u8], diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>, helpers: &mut BTreeSet<String>) {
    match node.kind() {
        "source_file" | "declaration_list" => for child in named_children(node) { visit(child, scope, source, diagnostics, out, helpers); },
        "mod_item" => if let Some(body) = node.child_by_field_name("body") {
            let local = node.child_by_field_name("name").map(|n| text(n, source)).unwrap_or("");
            visit(body, &qualified(scope, local), source, diagnostics, out, helpers);
        },
        "macro_invocation" | "union_item" => diagnostics.push(Diagnostic::warning("rust.declaration.unsupported", "Macros and unions are not evaluated as JSON declarations.", Some(span(node)), None)),
        "struct_item" | "enum_item" | "type_item" => {
            let Some(name_node) = node.child_by_field_name("name") else { return; };
            let local = text(name_node, source).trim_start_matches("r#");
            let name = qualified(scope, local);
            let attrs = attributes::before(node, source, diagnostics, &name);
            let kind = match node.kind() { "enum_item" => DeclarationKind::Enum, "type_item" => DeclarationKind::TypeAlias, _ => DeclarationKind::Struct };
            let mut declaration = empty(node, name.clone(), kind);
            declaration.annotations = attrs.annotations;
            let mut parameters = BTreeSet::new();
            if let Some(type_parameters) = node.child_by_field_name("type_parameters") {
                for parameter in named_children(type_parameters) {
                    if parameter.kind() == "type_parameter" {
                        if let Some(key) = parameter.child_by_field_name("name") {
                            let key = text_owned(key, source); parameters.insert(key.clone());
                            declaration.type_parameters.push(TypeParameter { name: key, constraints: vec![], span: span(parameter) });
                        }
                    } else if parameter.kind() == "const_parameter" { diagnostics.push(Diagnostic::warning("rust.generic.const", "Const generic values are not evaluated.", Some(span(parameter)), Some(&name))); }
                }
            }
            if attrs.has_unsupported_shape {
                declaration.kind = DeclarationKind::TypeAlias;
                declaration.aliased_type = Some(crate::type_parser::unknown_type(node, source));
            } else if node.kind() == "type_item" {
                declaration.aliased_type = node.child_by_field_name("type").map(|n| parse(n, source, &parameters, diagnostics, Some(&name)));
            } else if let Some(body) = node.child_by_field_name("body") {
                if node.kind() == "enum_item" {
                    if is_generated_value(node, body, local, source) { helpers.insert(name); return; }
                    let variants = named_children(body).into_iter().filter(|n| n.kind() == "enum_variant").collect::<Vec<_>>();
                    if variants.iter().any(|v| v.child_by_field_name("body").is_some()) {
                        declaration.kind = DeclarationKind::TypeAlias;
                        declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("rust.enum.payload", "Data-carrying enums require serializer-specific tagging and are not flattened into unit values.".into(), node, source, diagnostics, Some(&name)));
                    } else {
                        for variant in variants {
                            let metadata = attributes::before(variant, source, diagnostics, &name);
                            if metadata.is_skipped { continue; }
                            if let Some(key) = variant.child_by_field_name("name") {
                                let key = text(key, source).trim_start_matches("r#");
                                let key = metadata.rename.unwrap_or_else(|| attributes::rename(key, attrs.rename_all.as_deref(), true));
                                declaration.enum_values.push(EnumValue { name: key, value_text: None, span: span(variant) });
                                if variant.child_by_field_name("value").is_some() { diagnostics.push(Diagnostic::warning("rust.enum.discriminant", "Rust discriminants are not JSON values; unit variants use their string names.", Some(span(variant)), Some(&name))); }
                            }
                        }
                    }
                } else if body.kind() == "ordered_field_declaration_list" {
                    let mut cursor = body.walk();
                    let types = body.children_by_field_name("type", &mut cursor).collect::<Vec<_>>();
                    declaration.kind = DeclarationKind::TypeAlias;
                    declaration.aliased_type = Some(if types.len() == 1 { parse(types[0], source, &parameters, diagnostics, Some(&name)) } else {
                        crate::type_parser::unknown_type_with_message("rust.tuple.unsupported", "Multi-field tuple structs do not have an object field mapping.".into(), node, source, diagnostics, Some(&name))
                    });
                } else {
                    for field in named_children(body).into_iter().filter(|n| n.kind() == "field_declaration") {
                        let (Some(key), Some(ty)) = (field.child_by_field_name("name"), field.child_by_field_name("type")) else { continue; };
                        let metadata = attributes::before(field, source, diagnostics, &name);
                        if metadata.is_skipped { continue; }
                        let key = text(key, source).trim_start_matches("r#").to_owned();
                        let source_name = metadata.rename.unwrap_or_else(|| attributes::rename(&key, attrs.rename_all.as_deref(), false));
                        let reference = if metadata.has_unsupported_shape { crate::type_parser::unknown_type(ty, source) } else { parse(ty, source, &parameters, diagnostics, Some(&name)) };
                        declaration.fields.push(Field { name: key, source_name, optional: attrs.is_optional || metadata.is_optional, span: span(field), annotations: metadata.annotations, type_reference: reference });
                    }
                }
            } else {
                declaration.kind = DeclarationKind::TypeAlias;
                declaration.aliased_type = Some(crate::type_parser::unknown_type_with_message("rust.unit.unsupported", "Unit structs have no concrete object fields; a null sample is generated.".into(), node, source, diagnostics, Some(&name)));
            }
            out.push(declaration);
        }
        _ => {}
    }
}
fn qualified(scope: &str, local: &str) -> String { if scope.is_empty() { local.into() } else { format!("{scope}::{local}") } }
fn is_generated_value(node: Node<'_>, body: Node<'_>, name: &str, source: &[u8]) -> bool {
    if !name.starts_with("JsoninjaValue") { return false; }
    let mut previous = node.prev_named_sibling(); let mut has_marker = false;
    while let Some(sibling) = previous {
        if !matches!(sibling.kind(), "attribute_item" | "line_comment") { break; }
        if text(sibling, source).trim() == "// JSONinja value v1" { has_marker = true; }
        previous = sibling.prev_named_sibling();
    }
    let compact = text(body, source).chars().filter(|c| !c.is_whitespace()).collect::<String>();
    has_marker && compact == format!("{{Null,Bool(bool),Integer(i64),Number(f64),String(String),Array(Vec<{name}>),Object(HashMap<String,{name}>),}}")
}
fn resolve(reference: &mut TypeReference, known: &BTreeSet<String>, helpers: &BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, type_arguments, is_type_parameter } => {
            for argument in type_arguments { resolve(argument, known, helpers, owner, diagnostics); }
            let scope = owner.rsplit_once("::").map(|p| p.0).unwrap_or("");
            let candidate = qualified(scope, name.trim_start_matches("self::"));
            if helpers.contains(name) || helpers.contains(&candidate) {
                diagnostics.push(Diagnostic::warning("rust.value.unknown", "JSONinjaValue stores arbitrary JSON; its original value shape is unavailable to sample generation.", Some(span.clone()), Some(owner)));
                *reference = TypeReference::Unknown { raw_text: name.clone(), span: span.clone() };
            } else if *is_type_parameter { } else if known.contains(&candidate) { *name = candidate; }
            else if name.starts_with("crate::") && known.contains(name.trim_start_matches("crate::")) { *name = name.trim_start_matches("crate::").into(); }
            else if !known.contains(name) { diagnostics.push(Diagnostic::warning("rust.type.unresolved", format!("Rust type `{name}` is not declared in this source; imports and traits are not resolved."), Some(span.clone()), Some(owner))); }
        }
        TypeReference::Nullable { wrapped_type, .. } => resolve(wrapped_type, known, helpers, owner, diagnostics),
        TypeReference::List { element_type, .. } => resolve(element_type, known, helpers, owner, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { resolve(key_type, known, helpers, owner, diagnostics); resolve(value_type, known, helpers, owner, diagnostics); }
        _ => {}
    }
}
