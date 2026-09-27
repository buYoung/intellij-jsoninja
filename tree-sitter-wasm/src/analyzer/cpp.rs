use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use crate::type_parser::cpp::{parse, apply_declarator, declarator_name, is_method};

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

fn qualify(prefix: &str, name: &str) -> String { if prefix.is_empty() { name.into() } else { format!("{prefix}::{name}") } }

fn visit(node: Node<'_>, prefix: &str, source: &[u8], diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>) {
    match node.kind() {
        "namespace_definition" => {
            let name = node.child_by_field_name("name").map(|n| text(n, source)).unwrap_or("");
            if let Some(body) = node.child_by_field_name("body") { visit(body, &qualify(prefix, name), source, diagnostics, out); }
        }
        "struct_specifier" | "class_specifier" | "enum_specifier" if node.child_by_field_name("body").is_some() => {
            let Some(name) = node.child_by_field_name("name") else {
                diagnostics.push(Diagnostic::warning("cpp.anonymous.unsupported", "Anonymous C++ declarations need an explicit named type.", Some(span(node)), None)); return;
            };
            let name = qualify(prefix, text(name, source));
            out.push(parse_record(node, name, source, diagnostics));
        }
        "alias_declaration" => {
            if let (Some(name), Some(ty)) = (node.child_by_field_name("name"), node.child_by_field_name("type")) {
                let name = qualify(prefix, text(name, source));
                let mut declaration = empty(node, name.clone(), DeclarationKind::TypeAlias);
                declaration.aliased_type = Some(parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name)));
                out.push(declaration);
            }
        }
        "type_definition" => {
            if let Some(ty) = node.child_by_field_name("type") {
                let mut cursor = node.walk();
                for d in node.children_by_field_name("declarator", &mut cursor) {
                    let name = qualify(prefix, &declarator_name(d, source));
                    if matches!(ty.kind(), "struct_specifier" | "class_specifier" | "enum_specifier") && ty.child_by_field_name("body").is_some() && d.kind() == "type_identifier" {
                        if let Some(tag) = ty.child_by_field_name("name") {
                            let tag_name = qualify(prefix, text(tag, source));
                            if tag_name != name {
                                let mut alias = empty(node, tag_name, DeclarationKind::TypeAlias);
                                alias.aliased_type = Some(crate::type_parser::named_type(name.clone(), vec![], false, span(ty)));
                                out.push(alias);
                            }
                        }
                        out.push(parse_record(ty, name, source, diagnostics));
                        continue;
                    }
                    let mut declaration = empty(node, name.clone(), DeclarationKind::TypeAlias);
                    let base = parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name));
                    declaration.aliased_type = Some(apply_declarator(d, base, source, diagnostics, Some(&name)));
                    out.push(declaration);
                }
            }
        }
        "template_declaration" | "preproc_if" | "preproc_ifdef" | "union_specifier" => diagnostics.push(Diagnostic::warning("cpp.declaration.unsupported", "Dependent templates, conditional declarations and unions are not evaluated.", Some(span(node)), None)),
        "translation_unit" | "declaration_list" | "declaration" => for child in named_children(node) { visit(child, prefix, source, diagnostics, out); },
        _ => {}
    }
}

fn parse_record(node: Node<'_>, name: String, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Declaration {
    let kind = match node.kind() { "class_specifier" => DeclarationKind::Class, "enum_specifier" => DeclarationKind::Enum, _ => DeclarationKind::Struct };
    let mut declaration = empty(node, name.clone(), kind);
    let body = node.child_by_field_name("body").unwrap();
    if node.kind() == "enum_specifier" {
        for member in named_children(body).into_iter().filter(|n| n.kind() == "enumerator") {
            if let Some(key) = member.child_by_field_name("name") {
                declaration.enum_values.push(EnumValue { name: text_owned(key, source), value_text: member.child_by_field_name("value").map(|n| text_owned(n, source)), span: span(member) });
            }
        }
        return declaration;
    }
    if named_children(node).iter().any(|n| n.kind() == "base_class_clause") {
        diagnostics.push(Diagnostic::warning("cpp.base.unsupported", "Base class members are not merged without an explicit public-data mapping.", Some(span(node)), Some(&name)));
    }
    let mut is_public = node.kind() == "struct_specifier";
    for member in named_children(body) {
        if member.kind() == "access_specifier" { is_public = text(member, source) == "public"; continue; }
        if member.kind() == "comment" || member.kind() == "function_definition" { continue; }
        if member.kind() != "field_declaration" {
            diagnostics.push(Diagnostic::warning("cpp.member.unsupported", "Unsupported C++ member declaration is omitted.", Some(span(member)), Some(&name))); continue;
        }
        if !is_public || named_children(member).iter().any(|n| n.kind() == "storage_class_specifier" && text(*n, source) == "static") { continue; }
        let Some(ty) = member.child_by_field_name("type") else { continue; };
        let mut cursor = member.walk();
        for d in member.children_by_field_name("declarator", &mut cursor) {
            if is_method(d) { continue; }
            let field_name = declarator_name(d, source);
            if field_name.is_empty() { continue; }
            let base = parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name));
            let reference = if named_children(member).iter().any(|n| n.kind() == "bitfield_clause") {
                crate::type_parser::unknown_type_with_message("cpp.bitfield.unsupported", "Bit-field width is not represented in JSON.".into(), member, source, diagnostics, Some(&name))
            } else { apply_declarator(d, base, source, diagnostics, Some(&name)) };
            let optional = member.prev_named_sibling().is_some_and(|n| n.kind() == "comment" && text(n, source).trim() == "// JSONinja optional v1");
            declaration.fields.push(Field { name: field_name.clone(), source_name: field_name, optional, span: span(member), annotations: vec![], type_reference: reference });
        }
    }
    declaration
}

fn resolve_names(reference: &mut TypeReference, owner: &str, known: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, .. } => {
            if known.contains(name) { return; }
            if let Some((prefix, _)) = owner.rsplit_once("::") {
                let scoped = qualify(prefix, name);
                if known.contains(&scoped) { *name = scoped; return; }
            }
            diagnostics.push(Diagnostic::warning("cpp.type.unresolved", format!("C++ type `{name}` is not declared in this source."), Some(span.clone()), Some(owner)));
        }
        TypeReference::List { element_type, .. } => resolve_names(element_type, owner, known, diagnostics),
        TypeReference::Nullable { wrapped_type, .. } => resolve_names(wrapped_type, owner, known, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { resolve_names(key_type, owner, known, diagnostics); resolve_names(value_type, owner, known, diagnostics); }
        TypeReference::Union { members, .. } => for member in members { resolve_names(member, owner, known, diagnostics); },
        _ => {}
    }
}
