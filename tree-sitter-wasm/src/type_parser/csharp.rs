use std::collections::BTreeSet;
use tree_sitter::Node;
use crate::diagnostics::Diagnostic;
use crate::ir::{PrimitiveKind, TypeReference};
use crate::source::{named_children, span, text};
use super::{list_type, map_type, named_type, nullable_type, primitive_type, unknown_type_with_message};

pub(crate) fn parse(node: Node<'_>, source: &[u8], parameters: &BTreeSet<String>, diagnostics: &mut Vec<Diagnostic>, owner: Option<&str>) -> TypeReference {
    let raw = text(node, source).trim();
    let primitive = match raw {
        "string" | "char" | "String" | "Char" | "System.String" | "System.Char" => Some(PrimitiveKind::String),
        "bool" | "Boolean" | "System.Boolean" => Some(PrimitiveKind::Boolean),
        "byte" | "sbyte" | "short" | "ushort" | "int" | "uint" | "long" | "ulong" | "nint" | "nuint" | "Int16" | "Int32" | "Int64" | "System.Int16" | "System.Int32" | "System.Int64" | "System.UInt16" | "System.UInt32" | "System.UInt64" => Some(PrimitiveKind::Integer),
        "float" | "double" | "decimal" | "Double" | "Single" | "Decimal" | "System.Double" | "System.Single" | "System.Decimal" => Some(PrimitiveKind::Decimal),
        _ => None,
    };
    if let Some(kind) = primitive { return primitive_type(kind, span(node)); }
    if matches!(node.kind(), "nullable_type" | "array_type") {
        if let Some(inner) = node.child_by_field_name("type") {
            let parsed = parse(inner, source, parameters, diagnostics, owner);
            if node.kind() == "nullable_type" { return nullable_type(parsed, span(node)); }
            if node.child_by_field_name("rank").is_some_and(|rank| text(rank, source).contains(',')) {
                return unknown_type_with_message("csharp.array.rank", "Multidimensional C# arrays need an explicit JSON shape mapping.".into(), node, source, diagnostics, owner);
            }
            return list_type(parsed, span(node));
        }
    }
    let generic = if node.kind() == "generic_name" { Some(node) } else { node.child_by_field_name("name").filter(|n| n.kind() == "generic_name") };
    if let Some(generic) = generic {
        let children = named_children(generic);
        let name = children.iter().find(|n| n.kind() == "identifier").map(|n| text(*n, source)).unwrap_or("");
        if node.kind() == "qualified_name" {
            let qualifier = node.child_by_field_name("qualifier").map(|n| text(n, source)).unwrap_or("");
            if qualifier != "System.Collections.Generic" && !(qualifier == "System" && name == "Nullable") {
                return unknown_type_with_message("csharp.generic.unsupported", format!("External generic `{raw}` is not resolved."), node, source, diagnostics, owner);
            }
        }
        let args = children.iter().find(|n| n.kind() == "type_argument_list").map(|n| named_children(*n)).unwrap_or_default();
        let mut parsed = args.iter().map(|n| parse(*n, source, parameters, diagnostics, owner)).collect::<Vec<_>>();
        match (name, parsed.len()) {
            ("List" | "IList" | "IEnumerable" | "ICollection" | "IReadOnlyList" | "IReadOnlyCollection" | "HashSet", 1) => return list_type(parsed.remove(0), span(node)),
            ("Nullable", 1) => return nullable_type(parsed.remove(0), span(node)),
            ("Dictionary" | "IDictionary" | "IReadOnlyDictionary", 2) => {
                let key = parsed.remove(0); let value = parsed.remove(0);
                if !matches!(key, TypeReference::Primitive { primitive: PrimitiveKind::String, .. }) { diagnostics.push(Diagnostic::warning("csharp.map.key", "JSON keys are strings; other dictionary key types use string samples.", Some(span(node)), owner)); }
                return map_type(key, value, span(node));
            }
            _ => return unknown_type_with_message("csharp.generic.unsupported", format!("Generic type `{raw}` is not resolved."), node, source, diagnostics, owner),
        }
    }
    if matches!(raw, "object" | "dynamic" | "Object" | "System.Object") {
        return unknown_type_with_message("csharp.type.unknown", "Object/dynamic values have no concrete JSON shape.".into(), node, source, diagnostics, owner);
    }
    if matches!(node.kind(), "identifier" | "qualified_name" | "alias_qualified_name") {
        return named_type(raw.trim_start_matches('@').trim_start_matches("global::").into(), vec![], parameters.contains(raw), span(node));
    }
    unknown_type_with_message("csharp.type.unsupported", format!("Unsupported C# type `{raw}`."), node, source, diagnostics, owner)
}
