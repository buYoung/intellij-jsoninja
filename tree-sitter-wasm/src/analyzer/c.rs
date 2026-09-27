use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{Declaration, DeclarationKind, EnumValue, Field, TypeReference};
use crate::source::{named_children, span, text, text_owned};
use crate::type_parser::{self, c::{apply_declarator, declarator_name}};

pub(crate) fn analyze(root: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>) -> Vec<Declaration> {
    let mut declarations = Vec::new();
    let mut entries = BTreeSet::new();
    let mut collections = BTreeSet::new();
    visit(root, source, diagnostics, &mut declarations, &mut entries, &mut collections);
    // Only renderer-marked entry structs participate in map normalization.
    let entry_types = declarations.iter().filter(|d| entries.contains(&d.name)).map(|d| (d.name.clone(), d.fields.clone())).collect::<std::collections::BTreeMap<_, _>>();
    for declaration in &mut declarations {
        if let Some(TypeReference::Map { value_type, key_type, .. }) = &mut declaration.aliased_type {
            if let TypeReference::Named { name, .. } = &**value_type {
                if let Some(fields) = entry_types.get(name) {
                    **key_type = fields[0].type_reference.clone();
                    **value_type = fields[1].type_reference.clone();
                } else {
                    diagnostics.push(Diagnostic::warning("c.map.invalid_entry", "The marked map has no matching generated key/value entry; shape is not inferred.", Some(declaration.span.clone()), Some(&declaration.name)));
                }
            }
        }
    }
    let collection_types = declarations.iter().filter(|d| collections.contains(&d.name)).filter_map(|d| d.aliased_type.clone().map(|ty| (d.name.clone(), ty))).collect::<std::collections::BTreeMap<_, _>>();
    // Helpers must not become root candidates in the shared document builder.
    declarations.retain(|d| !entries.contains(&d.name) && !collections.contains(&d.name));
    for declaration in &mut declarations {
        for field in &mut declaration.fields { expand_collections(&mut field.type_reference, &collection_types, &mut BTreeSet::new(), &declaration.name, diagnostics); }
        if let Some(alias) = &mut declaration.aliased_type { expand_collections(alias, &collection_types, &mut BTreeSet::new(), &declaration.name, diagnostics); }
    }
    let known = declarations.iter().map(|d| d.name.clone()).collect::<BTreeSet<_>>();
    for declaration in &declarations {
        let mut referenced = BTreeSet::new();
        for field in &declaration.fields { referenced_names(&field.type_reference, &mut referenced); }
        if let Some(alias) = &declaration.aliased_type { referenced_names(alias, &mut referenced); }
        for unresolved in referenced.difference(&known) {
            diagnostics.push(Diagnostic::warning("c.type.unresolved", format!("C type `{unresolved}` is not declared in this source; headers and macros are not resolved."), Some(declaration.span.clone()), Some(&declaration.name)));
        }
    }
    declarations
}

fn referenced_names(reference: &TypeReference, names: &mut BTreeSet<String>) {
    match reference {
        TypeReference::Named { name, .. } => { names.insert(name.clone()); }
        TypeReference::Nullable { wrapped_type, .. } => referenced_names(wrapped_type, names),
        TypeReference::List { element_type, .. } => referenced_names(element_type, names),
        TypeReference::Map { key_type, value_type, .. } => { referenced_names(key_type, names); referenced_names(value_type, names); }
        _ => {}
    }
}

fn has_single_declarator(node: Node<'_>) -> bool {
    let mut cursor = node.walk();
    node.children_by_field_name("declarator", &mut cursor).count() == 1
}

fn marker(node: Node<'_>, source: &[u8], expected: &str) -> bool {
    node.prev_named_sibling().is_some_and(|previous| previous.kind() == "comment" && text(previous, source).trim() == expected)
}

fn empty_declaration(node: Node<'_>, name: String, kind: DeclarationKind) -> Declaration {
    Declaration { name, kind, span: span(node), annotations: vec![], type_parameters: vec![], super_types: vec![], fields: vec![], enum_values: vec![], aliased_type: None }
}

fn visit(node: Node<'_>, source: &[u8], diagnostics: &mut Vec<Diagnostic>, out: &mut Vec<Declaration>, entries: &mut BTreeSet<String>, collections: &mut BTreeSet<String>) {
    match node.kind() {
        "type_definition" => {
            let Some(ty) = node.child_by_field_name("type") else { return; };
            let mut cursor = node.walk();
            for declarator in node.children_by_field_name("declarator", &mut cursor) {
                let name = declarator_name(declarator, source);
                if name.is_empty() { continue; }
                if matches!(ty.kind(), "struct_specifier" | "enum_specifier") && ty.child_by_field_name("body").is_some() && declarator.kind() == "type_identifier" {
                    let declaration = parse_record(ty, node, name.clone(), source, diagnostics, entries, collections);
                    if let Some(tag) = ty.child_by_field_name("name") {
                        let tag_name = text_owned(tag, source);
                        if tag_name != name {
                            let mut alias = empty_declaration(node, tag_name, DeclarationKind::TypeAlias);
                            alias.aliased_type = Some(type_parser::named_type(name.clone(), vec![], false, span(ty)));
                            out.push(alias);
                        }
                    }
                    out.push(declaration);
                } else {
                    let mut declaration = empty_declaration(node, name.clone(), DeclarationKind::TypeAlias);
                    let base = type_parser::c::parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name));
                    declaration.aliased_type = Some(apply_declarator(declarator, base, text(ty, source) == "char", source, diagnostics, Some(&name), false));
                    out.push(declaration);
                }
            }
        }
        "struct_specifier" | "enum_specifier" if node.child_by_field_name("body").is_some() => {
            if let Some(name) = node.child_by_field_name("name") {
                out.push(parse_record(node, node, text_owned(name, source), source, diagnostics, entries, collections));
            } else {
                diagnostics.push(Diagnostic::warning("c.anonymous.unsupported", "Anonymous declarations require a named typedef to be converted.", Some(span(node)), None));
            }
        }
        "preproc_if" | "preproc_ifdef" => diagnostics.push(Diagnostic::warning("c.preprocessor.unsupported", "Conditional declarations are not selected or evaluated; their fields are omitted.", Some(span(node)), None)),
        "union_specifier" => diagnostics.push(Diagnostic::warning("c.union.unsupported", "C unions have no unambiguous JSON object representation.", Some(span(node)), None)),
        "translation_unit" | "declaration" => for child in named_children(node) { visit(child, source, diagnostics, out, entries, collections); },
        _ => {}
    }
}

fn parse_record(node: Node<'_>, outer: Node<'_>, name: String, source: &[u8], diagnostics: &mut Vec<Diagnostic>, entries: &mut BTreeSet<String>, collections: &mut BTreeSet<String>) -> Declaration {
    let is_enum = node.kind() == "enum_specifier";
    let mut declaration = empty_declaration(outer, name.clone(), if is_enum { DeclarationKind::Enum } else { DeclarationKind::Struct });
    let Some(body) = node.child_by_field_name("body") else { return declaration; };
    if is_enum {
        for member in named_children(body).into_iter().filter(|child| child.kind() == "enumerator") {
            if let Some(key) = member.child_by_field_name("name") {
                let value = member.child_by_field_name("value").map(|v| text_owned(v, source));
                if value.as_ref().is_some_and(|v| v.parse::<i64>().is_err()) {
                    diagnostics.push(Diagnostic::warning("c.enum.expression", "Enum expressions are not evaluated.", Some(span(member)), Some(&name)));
                }
                declaration.enum_values.push(EnumValue { name: text_owned(key, source), value_text: value, span: span(member) });
            }
        }
        return declaration;
    }
    let members = named_children(body);
    let fields = members.iter().copied().filter(|n| n.kind() == "field_declaration").collect::<Vec<_>>();
    let has_exact_members = fields.len() == members.iter().filter(|n| n.kind() != "comment").count();
    let mut storage = None;
    if has_exact_members && fields.len() == 2 && fields.iter().all(|n| has_single_declarator(*n)) && (name.starts_with("JsoninjaArray") || name.starts_with("JsoninjaMap")) {
        let length = fields[0];
        let data = fields[1];
        if length.child_by_field_name("type").is_some_and(|n| text(n, source) == "size_t")
            && length.child_by_field_name("declarator").is_some_and(|n| n.kind() == "field_identifier" && text(n, source) == "length")
            && data.child_by_field_name("declarator").is_some_and(|n| n.kind() == "pointer_declarator" && declarator_name(n, source) == "data")
            && (marker(outer, source, "/* JSONinja collection: array v1 */") || marker(outer, source, "/* JSONinja collection: map v1 */")) {
            storage = Some(data);
        }
    }
    if let Some(data) = storage {
        collections.insert(name.clone());
        let ty = data.child_by_field_name("type").unwrap();
        let pointer = data.child_by_field_name("declarator").unwrap();
        let inner = pointer.child_by_field_name("declarator").unwrap();
        let base = type_parser::c::parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name));
        let element = apply_declarator(inner, base, text(ty, source) == "char", source, diagnostics, Some(&name), true);
        declaration.kind = DeclarationKind::TypeAlias;
        declaration.aliased_type = Some(if marker(outer, source, "/* JSONinja collection: array v1 */") {
            type_parser::list_type(element, span(node))
        } else {
            type_parser::map_type(type_parser::primitive_type(crate::ir::PrimitiveKind::String, span(node)), element, span(node))
        });
        return declaration;
    }
    if has_exact_members && fields.len() == 1 && marker(outer, source, "/* JSONinja empty object v1 */") && text(fields[0], source).trim() == "unsigned char jsoninja_empty;" {
        return declaration;
    }
    for field in members {
        if field.kind() == "comment" { continue; }
        if field.kind() != "field_declaration" {
            diagnostics.push(Diagnostic::warning("c.member.unsupported", "Conditional or anonymous C members are omitted without preprocessing.", Some(span(field)), Some(&name)));
            continue;
        }
        let Some(ty) = field.child_by_field_name("type") else { continue; };
        let is_bitfield = named_children(field).iter().any(|n| n.kind() == "bitfield_clause");
        let mut cursor = field.walk();
        let declarators = field.children_by_field_name("declarator", &mut cursor).collect::<Vec<_>>();
        if declarators.is_empty() {
            diagnostics.push(Diagnostic::warning("c.anonymous.unsupported", "Anonymous C fields cannot be represented without a named member.", Some(span(field)), Some(&name)));
        }
        for declarator in declarators {
            let field_name = declarator_name(declarator, source);
            if field_name.is_empty() { continue; }
            let base = type_parser::c::parse(ty, source, &BTreeSet::new(), diagnostics, Some(&name));
            let reference = if is_bitfield {
                type_parser::unknown_type_with_message("c.bitfield.unsupported", "Bit-field width and storage are not represented.".into(), field, source, diagnostics, Some(&name))
            } else { apply_declarator(declarator, base, text(ty, source) == "char", source, diagnostics, Some(&name), false) };
            let optional = marker(field, source, "/* JSONinja optional v1 */");
            if optional { diagnostics.push(Diagnostic::warning("c.optional.marker", "Optionality is retained from a JSONinja comment; C itself has no absent members.", Some(span(field)), Some(&name))); }
            declaration.fields.push(Field { name: field_name.clone(), source_name: field_name, optional, span: span(field), annotations: vec![], type_reference: reference });
        }
    }
    if name.starts_with("JsoninjaEntry") && marker(outer, source, "/* JSONinja collection: entry v1 */") && declaration.fields.len() == 2
        && declaration.fields[0].name == "key" && declaration.fields[1].name == "value" && has_exact_members {
        entries.insert(name);
    }
    declaration
}

fn expand_collections(reference: &mut TypeReference, collections: &std::collections::BTreeMap<String, TypeReference>, active: &mut BTreeSet<String>, owner: &str, diagnostics: &mut Vec<Diagnostic>) {
    match reference {
        TypeReference::Named { name, span, .. } if collections.contains_key(name) => {
            if active.len() >= 64 || !active.insert(name.clone()) {
                diagnostics.push(Diagnostic::warning("c.collection.recursive", "Recursive or excessively nested collection helpers cannot retain a finite sample shape.", Some(span.clone()), Some(owner)));
                *reference = TypeReference::Unknown { raw_text: name.clone(), span: span.clone() };
                return;
            }
            let key = name.clone();
            let mut replacement = collections[&key].clone();
            expand_collections(&mut replacement, collections, active, owner, diagnostics);
            active.remove(&key);
            *reference = replacement;
        }
        TypeReference::List { element_type, .. } => expand_collections(element_type, collections, active, owner, diagnostics),
        TypeReference::Nullable { wrapped_type, .. } => expand_collections(wrapped_type, collections, active, owner, diagnostics),
        TypeReference::Map { key_type, value_type, .. } => { expand_collections(key_type, collections, active, owner, diagnostics); expand_collections(value_type, collections, active, owner, diagnostics); }
        _ => {}
    }
}
