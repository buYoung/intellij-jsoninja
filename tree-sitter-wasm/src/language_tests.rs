use super::*;

fn without_spans(mut value: Value) -> Value {
    match &mut value {
        Value::Object(object) => {
            object.remove("span");
            for value in object.values_mut() { *value = without_spans(value.take()); }
        }
        Value::Array(values) => for value in values { *value = without_spans(value.take()); },
        _ => {}
    }
    value
}

fn semantic_analysis(analysis: &Value) -> Value {
    serde_json::json!({
        "language": analysis["language"],
        "declarations": analysis["declarations"].as_array().unwrap().iter().map(|d| serde_json::json!({
            "name": d["name"], "kind": d["kind"], "alias": without_spans(d["aliased_type"].clone()),
            "enum_values": without_spans(d["enum_values"].clone()),
            "fields": d["fields"].as_array().unwrap().iter().map(|f| serde_json::json!({
                "name": f["name"], "source_name": f["source_name"], "optional": f["optional"], "type": without_spans(f["type"].clone())
            })).collect::<Vec<_>>()
        })).collect::<Vec<_>>(),
        "diagnostics": analysis["diagnostics"].as_array().unwrap().iter().map(|d| d["code"].clone()).collect::<Vec<_>>()
    })
}

#[test]
fn c_analysis_matches_reviewed_golden() {
    let source = include_str!("../tests/fixtures/c/analysis.c");
    let actual = decode_analyze_source(4, source);
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/c/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_eq!(actual["declarations"][1]["fields"][0]["span"]["start_row"], 2);
}

#[test]
fn c_query_matches_reviewed_capture_golden() {
    let source = guest_memory_from_str(include_str!("../tests/fixtures/c/analysis.c"));
    let query = guest_memory_from_str(include_str!("../queries/c/type-declarations.scm"));
    let parser = parser_create(4);
    assert!(parser > 0);
    let tree = tree_parse(parser, source.pointer, source.length);
    assert!(tree > 0);
    let result = decode_tree_query(tree, &source, &query);
    tree_destroy(tree);
    parser_destroy(parser);
    assert_eq!(result["has_syntax_errors"], false, "{result}");
    let mut actual = result["captures"].as_array().unwrap().iter().filter(|c| {
        matches!(c["name"].as_str(), Some("declaration.name" | "alias.name" | "field.declarator"))
    }).map(|c| format!("{}: {}", c["name"].as_str().unwrap(), c["text"].as_str().unwrap())).collect::<Vec<_>>();
    let mut expected = include_str!("../tests/fixtures/c/expected-captures.txt").lines().map(str::to_owned).collect::<Vec<_>>();
    actual.sort(); expected.sort();
    assert_eq!(actual, expected);
}

#[test]
fn c_generated_array_requires_both_marker_and_exact_layout() {
    let source = "/* JSONinja collection: array v1 */\ntypedef struct JsoninjaArray1 { size_t length; int *data; } JsoninjaArray1;\ntypedef struct Ordinary { size_t length; int *data; } Ordinary;\ntypedef struct JsoninjaArray2 { size_t length; int *data; } JsoninjaArray2; typedef struct Root { JsoninjaArray1 values; } Root;";
    let analysis = decode_analyze_source(4, source);
    let d = analysis["declarations"].as_array().unwrap();
    assert_eq!(d.len(), 3);
    assert_eq!(d[0]["name"], "Ordinary");
    assert_eq!(d[0]["kind"], "struct");
    assert_eq!(d[0]["fields"].as_array().unwrap().len(), 2);
    assert_eq!(d[1]["name"], "JsoninjaArray2");
    assert_eq!(d[1]["kind"], "struct");
    assert_eq!(d[2]["name"], "Root");
    assert_eq!(d[2]["fields"][0]["type"]["kind"], "list");
    assert_eq!(d[2]["fields"][0]["type"]["element_type"]["primitive"], "integer");
    let extra = decode_analyze_source(4, "/* JSONinja collection: array v1 */\ntypedef struct JsoninjaArray3 { size_t length; int *data, other; } JsoninjaArray3;");
    assert_eq!(extra["declarations"][0]["kind"], "struct");
    assert_eq!(extra["declarations"][0]["fields"].as_array().unwrap().len(), 3);
}

#[test]
fn c_unresolved_header_types_are_visible() {
    let analysis = decode_analyze_source(4, "struct Root { ExternalType value; };");
    assert_diagnostic_code_present(&analysis, "c.type.unresolved");
}

#[test]
fn c_pointer_array_binding_and_unsupported_forms_are_explicit() {
    let analysis = decode_analyze_source(4, "struct Root { int *pointers[3]; int (*matrix)[3]; int (*callback)(int); unsigned flags:3; };\n#if CONFIG\nstruct Conditional { int value; };\n#endif");
    let fields = &analysis["declarations"][0]["fields"];
    assert_eq!(fields[0]["type"]["kind"], "list", "{analysis}");
    assert_eq!(fields[0]["type"]["element_type"]["kind"], "nullable", "{analysis}");
    assert_eq!(fields[1]["type"]["kind"], "nullable", "{analysis}");
    assert_eq!(fields[1]["type"]["wrapped_type"]["kind"], "list", "{analysis}");
    assert_diagnostic_code_present(&analysis, "c.function_pointer.unsupported");
    assert_diagnostic_code_present(&analysis, "c.bitfield.unsupported");
    assert_diagnostic_code_present(&analysis, "c.preprocessor.unsupported");
}

#[test]
fn c_malformed_source_reports_errors_and_keeps_partial_declarations() {
    let analysis = decode_analyze_source(4, "struct Root { int valid; char *name;");
    assert!(analysis["diagnostics"].as_array().unwrap().iter().any(|d| d["severity"] == "error"));
}

#[test]
fn cpp_public_members_nested_templates_and_namespaces_match_golden() {
    let actual = decode_analyze_source(5, include_str!("../tests/fixtures/cpp/analysis.cpp"));
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/cpp/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_query_captures_expected_names(5, include_str!("../tests/fixtures/cpp/analysis.cpp"), include_str!("../queries/cpp/type-declarations.scm"), &["declaration.name", "field.declarator", "alias.name"]);
}

#[test]
fn cpp_lossy_containers_and_unsupported_templates_report_diagnostics() {
    let analysis = decode_analyze_source(5, "struct Root { std::array<float,4> values; std::variant<int,std::string> choice; std::any anything; Custom<T> dependent; int *pointer; int &reference; };");
    let fields = &analysis["declarations"][0]["fields"];
    assert_eq!(fields[0]["type"]["kind"], "list");
    assert_eq!(fields[1]["type"]["kind"], "union");
    assert_eq!(fields[2]["type"]["kind"], "unknown");
    assert_diagnostic_code_present(&analysis, "cpp.array.length_ignored");
    assert_diagnostic_code_present(&analysis, "cpp.variant.sample_first");
    assert_diagnostic_code_present(&analysis, "cpp.template.unsupported");
    assert_diagnostic_code_present(&analysis, "cpp.pointer.assumption");
    assert_diagnostic_code_present(&analysis, "cpp.reference.ignored");
}

#[test]
fn cpp_typedef_records_keep_names_and_fields() {
    let analysis = decode_analyze_source(5, "typedef struct { int id; } User; typedef User Alias; struct Root { Alias user; };");
    assert_eq!(analysis["declarations"][0]["name"], "User");
    assert_eq!(analysis["declarations"][0]["fields"][0]["name"], "id");
    assert_eq!(analysis["declarations"][1]["aliased_type"]["name"], "User");
    assert!(analysis["diagnostics"].as_array().unwrap().is_empty(), "{analysis}");
}

#[test]
fn csharp_records_property_attributes_and_public_members_match_golden() {
    let actual = decode_analyze_source(6, include_str!("../tests/fixtures/csharp/analysis.cs"));
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/csharp/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_eq!(actual["declarations"][1]["fields"][0]["annotations"][0]["name"], "JsonPropertyName");
    assert_query_captures_expected_names(6, include_str!("../tests/fixtures/csharp/analysis.cs"), include_str!("../queries/csharp/type-declarations.scm"), &["declaration.name", "field.name", "parameter.name", "alias.name"]);
}

#[test]
fn csharp_attribute_expressions_and_unconstrained_types_are_explicit() {
    let analysis = decode_analyze_source(6, "public record Person([param: JsonPropertyName(\"bad\")] string Name); public class Root { [JsonPropertyName(nameof(Name))] public string Name {get;set;} public object Value {get;set;} public int[,] Matrix {get;set;} }");
    assert_diagnostic_code_present(&analysis, "csharp.attribute.target");
    assert_diagnostic_code_present(&analysis, "csharp.attribute.value");
    assert_diagnostic_code_present(&analysis, "csharp.type.unknown");
    assert_diagnostic_code_present(&analysis, "csharp.array.rank");
    assert_eq!(analysis["declarations"][0]["fields"][0]["source_name"], "Name");
}

#[test]
fn python_typeddict_optional_keys_forward_references_and_aliases_match_golden() {
    let actual = decode_analyze_source(7, include_str!("../tests/fixtures/python/analysis.py"));
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/python/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_eq!(actual["declarations"][1]["super_types"][0]["name"], "Base");
    assert_query_captures_expected_names(7, include_str!("../tests/fixtures/python/analysis.py"), include_str!("../queries/python/type-declarations.scm"), &["declaration.name", "field.name", "alias.name"]);
}

#[test]
fn python_dynamic_annotations_remain_data_and_unicode_spans_use_bytes() {
    let actual = decode_analyze_source(7, "class Root:\n    이름: str\n    value: make_type()\n    missing: External\nBad = TypedDict('Bad', field_factory())\n");
    assert_eq!(actual["declarations"][0]["fields"][0]["name"], "이름");
    assert_eq!(actual["declarations"][0]["fields"][0]["span"]["end_col"], 15);
    assert_diagnostic_code_present(&actual, "python.type.unsupported");
    assert_diagnostic_code_present(&actual, "python.type.unresolved");
    assert_diagnostic_code_present(&actual, "python.typeddict.dynamic");
}

#[test]
fn python_inherited_typeddict_totality_does_not_change_base_fields() {
    let actual = decode_analyze_source(7, "class Base(TypedDict):\n    required: int\nclass Child(Base, total=False):\n    optional: str\n    explicit: Required[bool]\n");
    assert_eq!(actual["declarations"][0]["fields"][0]["optional"], false);
    assert_eq!(actual["declarations"][1]["fields"][0]["optional"], true);
    assert_eq!(actual["declarations"][1]["fields"][1]["optional"], false);
}

#[test]
fn rust_owned_fields_serde_names_newtypes_and_enums_match_golden() {
    let source = include_str!("../tests/fixtures/rust/analysis.rs");
    let actual = decode_analyze_source(8, source);
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/rust/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_query_captures_expected_names(8, source, include_str!("../queries/rust/type-declarations.scm"), &["declaration.name", "field.name", "alias.name", "enum.value"]);
}

#[test]
fn rust_unsupported_shapes_are_diagnostics_not_invented_values() {
    let actual = decode_analyze_source(8, "struct Root<'a> { reference: &'a str, fixed: [i32; 3], external: Other, #[serde(flatten)] flat: Other } enum Data { Text(String), Empty } struct Pair(i32, bool); struct Unit;");
    assert_diagnostic_code_present(&actual, "rust.reference.ignored");
    assert_diagnostic_code_present(&actual, "rust.array.length_ignored");
    assert_diagnostic_code_present(&actual, "rust.type.unresolved");
    assert_diagnostic_code_present(&actual, "rust.attribute.unsupported");
    assert_diagnostic_code_present(&actual, "rust.enum.payload");
    assert_diagnostic_code_present(&actual, "rust.tuple.unsupported");
    assert_diagnostic_code_present(&actual, "rust.unit.unsupported");
    assert_eq!(actual["declarations"][1]["aliased_type"]["kind"], "unknown");
    assert!(actual["declarations"][1]["enum_values"].as_array().unwrap().is_empty());
}

#[test]
fn rust_modules_preserve_distinct_local_names_and_rename_rules() {
    let actual = decode_analyze_source(8, "mod a { struct Item { id: i64 } struct Root { item: Item } } mod b { struct Item { label: String } } #[serde(rename_all = \"kebab-case\")] struct Root { first_name: String, #[serde(skip_serializing)] secret: bool, #[serde(skip_deserializing)] keep: bool }");
    assert_eq!(actual["declarations"][1]["fields"][0]["type"]["name"], "a::Item");
    assert_eq!(actual["declarations"][3]["fields"][0]["source_name"], "first-name");
    assert_eq!(actual["declarations"][3]["fields"].as_array().unwrap().len(), 2);
    assert!(actual["diagnostics"].as_array().unwrap().is_empty(), "{actual}");
}

#[test]
fn scala_case_fields_local_scopes_and_simple_enums_match_golden() {
    let source = include_str!("../tests/fixtures/scala/analysis.scala");
    let actual = decode_analyze_source(9, source);
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/scala/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_eq!(actual["declarations"][1]["super_types"][0]["name"], "Base");
    assert_query_captures_expected_names(9, source, include_str!("../queries/scala/type-declarations.scm"), &["declaration.name", "field.name", "alias.name", "enum.value"]);
}

#[test]
fn scala_constructor_defaults_contextual_parameters_and_unsupported_types_are_distinct() {
    let actual = decode_analyze_source(9, "case class Root(required: Int = 1, nullable: String | Null)(using context: String) { val computed = build(); val refined: AnyRef { def x: Int } = null }\nopaque type Secret = String\nenum Data:\n  case Text(value: String)\n");
    assert_eq!(actual["declarations"][0]["fields"][0]["optional"], false);
    assert_eq!(actual["declarations"][0]["fields"][1]["type"]["kind"], "nullable");
    assert_eq!(actual["declarations"][0]["fields"].as_array().unwrap().len(), 4);
    assert_diagnostic_code_present(&actual, "scala.default.ignored");
    assert_diagnostic_code_present(&actual, "scala.field.inferred");
    assert_diagnostic_code_present(&actual, "scala.type.unsupported");
    assert_diagnostic_code_present(&actual, "scala.opaque.unsupported");
    assert_diagnostic_code_present(&actual, "scala.enum.payload");
}

#[test]
fn scala_generated_alias_scope_and_optional_marker_require_exact_markers() {
    let actual = decode_analyze_source(9, "// JSONinja alias scope v1\nobject JsoninjaTypes { case class Item(/* JSONinja optional v1 */ name: Option[String]); type Root = List[Item] }\nobject Ordinary { type Alias = String; val value: Int = 1 }");
    assert_eq!(actual["declarations"][0]["name"], "Item");
    assert_eq!(actual["declarations"][0]["fields"][0]["optional"], true);
    assert_eq!(actual["declarations"][1]["name"], "Root");
    assert_eq!(actual["declarations"][2]["name"], "Ordinary.Alias");
    assert_eq!(actual["declarations"].as_array().unwrap().len(), 3);
    assert!(actual["diagnostics"].as_array().unwrap().is_empty(), "{actual}");
}

#[test]
fn jsdoc_real_comments_nested_properties_aliases_and_quoted_keys_match_golden() {
    let source = include_str!("../tests/fixtures/jsdoc/analysis.js");
    let actual = decode_analyze_source(10, source);
    let expected: Value = serde_json::from_str(include_str!("../tests/fixtures/jsdoc/expected-analysis.json")).unwrap();
    assert_eq!(semantic_analysis(&actual), expected);
    assert_eq!(actual["declarations"][0]["fields"][0]["span"]["start_row"], 4);
    assert_eq!(actual["declarations"][0]["fields"][0]["span"]["start_col"], 3);
    assert_query_captures_expected_names(10, source, include_str!("../queries/jsdoc/type-declarations.scm"), &["documentation"]);
    let source = guest_memory_from_str(source);
    let query = guest_memory_from_str(include_str!("../queries/jsdoc/type-declarations.scm"));
    let parser = parser_create(10); let tree = tree_parse(parser, source.pointer, source.length);
    let captures = decode_tree_query(tree, &source, &query);
    tree_destroy(tree); parser_destroy(parser);
    assert_eq!(captures["captures"].as_array().unwrap().len(), 3, "{captures}");
    assert!(captures["captures"].as_array().unwrap().iter().all(|c| !c["text"].as_str().unwrap().contains("Fake")));
}

#[test]
fn jsdoc_union_record_paths_and_original_unicode_spans_are_bounded() {
    let source = "/**\n * @typedef {Object} Root\n * @property {Array<Object>} users\n * @property {(string|number)} users[].name\n * @property {boolean} [\"a.b\"]\n * @property {External} 이름\n */";
    let actual = decode_analyze_source(10, source);
    let fields = &actual["declarations"][0]["fields"];
    assert_eq!(fields[0]["type"]["element_type"]["fields"][0]["type"]["kind"], "union");
    assert_eq!(fields[1]["source_name"], "a.b");
    assert_eq!(fields[1]["optional"], true);
    assert_eq!(fields[2]["span"]["start_row"], 5);
    assert_eq!(fields[2]["span"]["end_col"], 30);
    assert_diagnostic_code_present(&actual, "jsdoc.union.sample_first");
    assert_diagnostic_code_present(&actual, "jsdoc.type.unresolved");
}

#[test]
fn jsdoc_malformed_types_and_duplicate_typedefs_report_errors() {
    for source in ["/** @typedef {Array<string} Broken */", "/** @typedef {string} Same */\n/** @typedef {number} Same */"] {
        let actual = decode_analyze_source(10, source);
        assert!(actual["diagnostics"].as_array().unwrap().iter().any(|d| d["severity"] == "error"), "{actual}");
    }
    let actual = decode_analyze_source(10, "const sample = `/** @typedef {string} Fake */`;\n/** @typedef {function(string):boolean} Callback */");
    assert_eq!(actual["declarations"].as_array().unwrap().len(), 1);
    assert_eq!(actual["declarations"][0]["aliased_type"]["kind"], "unknown");
    assert_diagnostic_code_present(&actual, "jsdoc.type.unknown");
}

#[test]
fn jsdoc_empty_comments_and_excessive_property_paths_do_not_panic() {
    let actual = decode_analyze_source(10, "/**/\n/***/\n/** @typedef {string} Root */");
    assert_eq!(actual["declarations"].as_array().unwrap().len(), 1);
    assert!(actual["diagnostics"].as_array().unwrap().is_empty(), "{actual}");
    let source = format!("/**\n * @typedef {{Object}} Root\n * @property {{number}} {}value\n */", "nested.".repeat(1000));
    let actual = decode_analyze_source(10, &source);
    assert_diagnostic_code_present(&actual, "jsdoc.tag.malformed");
}

#[test]
fn c_generated_collection_cycles_are_bounded_and_do_not_become_root_candidates() {
    let actual = decode_analyze_source(4, "/* JSONinja collection: array v1 */\ntypedef struct JsoninjaArray1 { size_t length; struct JsoninjaArray1 *data; } JsoninjaArray1; typedef struct Root { JsoninjaArray1 values; } Root;");
    assert_eq!(actual["declarations"].as_array().unwrap().len(), 1);
    assert_eq!(actual["declarations"][0]["name"], "Root");
    assert_diagnostic_code_present(&actual, "c.collection.recursive");
}
