use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, Field, Span, TypeReference};
use crate::source::{span, text};
use crate::type_parser::{inline_object_type, list_type};
use crate::type_parser::jsdoc_expression::{is_name, parse};
use super::jsdoc_tags::{extract, name_token, property_path, typed_value, Tag};

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut out = vec![];
    super::walk_nodes(root, &mut |node| {
        if node.kind() != "comment" || !text(node, source).starts_with("/**") { return; }
        let mut current: Option<Declaration> = None;
        for tag in extract(node, source) {
            match tag.name.as_str() {
                "typedef" => {
                    if let Some(declaration) = current.take() { out.push(declaration); }
                    let Some((ty, rest)) = typed_value(&tag.value) else { malformed(&tag, diagnostics, "Unclosed typedef type expression."); continue; };
                    let Some(name) = name_token(rest).filter(|name| is_name(name)) else { malformed(&tag, diagnostics, "A typedef requires a supported namepath."); continue; };
                    let reference = parse(ty.unwrap_or("Object"), &tag.span, diagnostics, Some(name));
                    let mut declaration = Declaration { name: name.into(), kind: DeclarationKind::TypeAlias, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None };
                    set_type(&mut declaration, reference);
                    current = Some(declaration);
                }
                "type" if current.is_some() => {
                    let declaration = current.as_mut().unwrap();
                    if let Some((Some(ty), _)) = typed_value(&tag.value) {
                        let reference = parse(ty, &tag.span, diagnostics, Some(&declaration.name));
                        if declaration.fields.is_empty() { set_type(declaration, reference); }
                        else { warning("jsdoc.type.conflict", "Type tag after property tags is ignored; declare the type before its properties.", &tag.span, Some(&declaration.name), diagnostics); }
                    } else { malformed(&tag, diagnostics, "A type tag requires a balanced type expression."); }
                }
                "property" | "prop" => {
                    let Some(declaration) = current.as_mut() else { warning("jsdoc.property.orphan", "Property tag has no preceding typedef in this documentation block.", &tag.span, None, diagnostics); continue; };
                    let Some((ty, rest)) = typed_value(&tag.value) else { malformed(&tag, diagnostics, "Unclosed property type expression."); continue; };
                    let Some(path) = name_token(rest).and_then(property_path) else { malformed(&tag, diagnostics, "Property name/path is missing or malformed."); continue; };
                    if declaration.kind != DeclarationKind::Class { warning("jsdoc.property.conflict", "Properties on a non-object typedef are not applied.", &tag.span, Some(&declaration.name), diagnostics); continue; }
                    let reference = parse(ty.unwrap_or("*"), &tag.span, diagnostics, Some(&declaration.name));
                    let (key, is_array) = path.parts.last().unwrap();
                    let reference = if *is_array { list_type(reference, tag.span.clone()) } else { reference };
                    let field = Field { name: key.clone(), source_name: key.clone(), optional: path.is_optional || ty.is_some_and(|t| t.trim().ends_with('=')), span: tag.span.clone(), annotations: vec![], type_reference: reference };
                    insert_path(&mut declaration.fields, &path.parts, field, &declaration.name, diagnostics);
                }
                "enum" | "callback" | "template" => warning("jsdoc.tag.unsupported", "Runtime enums, callbacks and template declarations are not inferred or executed.", &tag.span, current.as_ref().map(|d| d.name.as_str()), diagnostics),
                _ => {}
            }
        }
        if let Some(declaration) = current { out.push(declaration); }
    });
    let mut known = BTreeSet::new();
    for declaration in &out {
        if !known.insert(declaration.name.clone()) { diagnostics.push(Diagnostic::error("jsdoc.typedef.duplicate", format!("Duplicate typedef `{}`.", declaration.name), Some(declaration.span.clone()), Some(&declaration.name))); }
    }
    for declaration in &out {
        for field in &declaration.fields { check_names(&field.type_reference, &known, &declaration.name, diagnostics); }
        if let Some(alias) = &declaration.aliased_type { check_names(alias, &known, &declaration.name, diagnostics); }
    }
    out
}
fn set_type(declaration: &mut Declaration, reference: TypeReference) {
    match reference {
        TypeReference::InlineObject { fields, .. } => { declaration.kind = DeclarationKind::Class; declaration.fields = fields; declaration.aliased_type = None; }
        other => { declaration.kind = DeclarationKind::TypeAlias; declaration.fields.clear(); declaration.aliased_type = Some(other); }
    }
}
fn insert_path(fields: &mut Vec<Field>, path: &[(String, bool)], field: Field, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    let (name, is_array) = &path[0];
    if path.len() == 1 {
        if let Some(existing) = fields.iter_mut().find(|f| f.source_name == *name) {
            if is_empty_object(&field.type_reference) && object_fields(&mut existing.type_reference).is_some() { existing.optional = field.optional; }
            else { warning("jsdoc.property.duplicate", "Repeated or conflicting property type uses the last declaration.", &field.span, Some(owner), diagnostics); *existing = field; }
        } else { fields.push(field); }
        return;
    }
    let index = fields.iter().position(|f| f.source_name == *name).unwrap_or_else(|| {
        let reference = inline_object_type(vec![], field.span.clone());
        fields.push(Field { name: name.clone(), source_name: name.clone(), optional: false, span: field.span.clone(), annotations: vec![], type_reference: if *is_array { list_type(reference, field.span.clone()) } else { reference } });
        fields.len()-1
    });
    let parent = &mut fields[index].type_reference;
    if *is_array && !matches!(parent, TypeReference::List { .. }) { warning("jsdoc.property.path", "Array property path conflicts with its parent type.", &field.span, Some(owner), diagnostics); return; }
    if let Some(nested) = object_fields(parent) { insert_path(nested, &path[1..], field, owner, diagnostics); }
    else { warning("jsdoc.property.path", "Nested property path conflicts with its parent type or named reference.", &field.span, Some(owner), diagnostics); }
}
fn object_fields(reference: &mut TypeReference) -> Option<&mut Vec<Field>> {
    match reference {
        TypeReference::InlineObject { fields, .. } => Some(fields),
        TypeReference::List { element_type, .. } => object_fields(element_type),
        TypeReference::Nullable { wrapped_type, .. } => object_fields(wrapped_type),
        _ => None,
    }
}
fn is_empty_object(reference: &TypeReference) -> bool { match reference { TypeReference::InlineObject { fields, .. } => fields.is_empty(), TypeReference::List { element_type, .. } => is_empty_object(element_type), _ => false } }
fn malformed(tag: &Tag, diagnostics: &mut Vec<Diagnostic>, message: &str) { diagnostics.push(Diagnostic::error("jsdoc.tag.malformed", message, Some(tag.span.clone()), None)); }
fn warning(code: &str, message: &str, location: &Span, owner: Option<&str>, diagnostics: &mut Vec<Diagnostic>) { diagnostics.push(Diagnostic::warning(code, message, Some(location.clone()), owner)); }
fn check_names(reference: &TypeReference, known: &BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, .. } if !known.contains(name) => warning("jsdoc.type.unresolved", &format!("JSDoc type `{name}` has no typedef in this source."), span, Some(owner), diagnostics),
        TypeReference::List { element_type, .. } => check_names(element_type, known, owner, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { check_names(key_type, known, owner, diagnostics); check_names(value_type, known, owner, diagnostics); }
        TypeReference::Nullable { wrapped_type, .. } => check_names(wrapped_type, known, owner, diagnostics),
        TypeReference::Union { members, .. } => for member in members { check_names(member, known, owner, diagnostics); },
        TypeReference::InlineObject { fields, .. } => for field in fields { check_names(&field.type_reference, known, owner, diagnostics); },
        _ => {}
    }
}
