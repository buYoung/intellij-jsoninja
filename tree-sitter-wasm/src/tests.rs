use serde_json::Value;

use crate::error::WasmErrorCode;
use crate::memory;
use crate::{
    analyze_source, dealloc, get_last_error, get_supported_languages, parser_create, parser_destroy,
    tree_destroy, tree_parse, tree_query,
};

const JAVA_QUERY: &str = include_str!("../queries/java/type-declarations.scm");
const KOTLIN_QUERY: &str = include_str!("../queries/kotlin/type-declarations.scm");
const TYPESCRIPT_QUERY: &str = include_str!("../queries/typescript/type-declarations.scm");
const GO_QUERY: &str = include_str!("../queries/go/type-declarations.scm");

const JAVA_SOURCE: &str = include_str!("../tests/fixtures/java/sample.java");
const JAVA_EXPECTED_ANALYSIS: &str = include_str!("../tests/fixtures/java/expected-analysis.json");
const KOTLIN_SOURCE: &str = include_str!("../tests/fixtures/kotlin/sample.kt");
const KOTLIN_EXPECTED_ANALYSIS: &str = include_str!("../tests/fixtures/kotlin/expected-analysis.json");
const TYPESCRIPT_SOURCE: &str = include_str!("../tests/fixtures/typescript/sample.ts");
const TYPESCRIPT_ANALYSIS_SOURCE: &str = include_str!("../tests/fixtures/typescript/analysis.ts");
const TYPESCRIPT_EXPECTED_ANALYSIS: &str = include_str!("../tests/fixtures/typescript/expected-analysis.json");
const GO_SOURCE: &str = include_str!("../tests/fixtures/go/sample.go");
const GO_ANALYSIS_SOURCE: &str = include_str!("../tests/fixtures/go/analysis.go");
const GO_EXPECTED_ANALYSIS: &str = include_str!("../tests/fixtures/go/expected-analysis.json");

#[test]
fn get_supported_languages_returns_language_descriptors() {
    let languages = decode_json_result(get_supported_languages());
    assert_eq!(
        languages,
        serde_json::json!([
            { "id": 0, "name": "java" },
            { "id": 1, "name": "kotlin" },
            { "id": 2, "name": "typescript" },
            { "id": 3, "name": "go" }
        ])
    );
}

#[test]
fn parser_and_tree_handles_have_independent_lifetimes() {
    assert_query_captures_expected_names(0, JAVA_SOURCE, JAVA_QUERY, &["type.name", "field.name"]);
    assert_query_captures_expected_names(1, KOTLIN_SOURCE, KOTLIN_QUERY, &["type.name", "field.name"]);
    assert_query_captures_expected_names(2, TYPESCRIPT_SOURCE, TYPESCRIPT_QUERY, &["type.name", "field.name"]);
    assert_query_captures_expected_names(3, GO_SOURCE, GO_QUERY, &["type.name", "field.name"]);
}

#[test]
fn analyze_source_matches_fixture_regressions() {
    assert_analysis_fixture(0, JAVA_SOURCE, JAVA_EXPECTED_ANALYSIS);
    assert_analysis_fixture(1, KOTLIN_SOURCE, KOTLIN_EXPECTED_ANALYSIS);
    assert_analysis_fixture(2, TYPESCRIPT_ANALYSIS_SOURCE, TYPESCRIPT_EXPECTED_ANALYSIS);
    assert_analysis_fixture(3, GO_ANALYSIS_SOURCE, GO_EXPECTED_ANALYSIS);
}

#[test]
fn analyze_source_preserves_partial_results_and_diagnostics_on_syntax_errors() {
    let analysis = decode_analyze_source(
        0,
        r#"
        class Broken {
          String name;
        "#,
    );

    assert!(analysis["diagnostics"].as_array().is_some_and(|diagnostic_entries| !diagnostic_entries.is_empty()));
    assert_eq!(analysis["declarations"][0]["name"], "Broken");
}

#[test]
fn tree_query_reports_syntax_errors_and_diagnostics() {
    let parser_handle = parser_create(0);
    assert!(parser_handle > 0);

    let malformed_source_memory = guest_memory_from_str(
        r#"
        class Broken {
            String name;
        "#,
    );
    let tree_handle = tree_parse(parser_handle, malformed_source_memory.pointer, malformed_source_memory.length);
    assert!(tree_handle > 0);
    assert_eq!(parser_destroy(parser_handle), ());

    let query_memory = guest_memory_from_str(JAVA_QUERY);
    let query_result = decode_tree_query(tree_handle, &malformed_source_memory, &query_memory);

    assert_eq!(query_result["has_syntax_errors"], true);
    assert!(query_result["diagnostics"].as_array().is_some_and(|diagnostic_entries| !diagnostic_entries.is_empty()));
    assert_eq!(tree_destroy(tree_handle), ());
}

#[test]
fn analyze_source_rejects_invalid_utf8_without_panicking() {
    let invalid_memory = guest_memory_from_bytes(&[0xff, 0xfe, 0xfd]);
    let packed_error = analyze_source(0, invalid_memory.pointer, invalid_memory.length);
    assert_eq!(unpack_ptr_len(packed_error), (0, WasmErrorCode::InvalidUtf8 as i32));
    assert_last_error_contains("Input bytes are not valid UTF-8");
}

#[test]
fn analyze_source_extracts_java_record_and_enum_details() {
    let analysis = decode_analyze_source(
        0,
        r#"record AuditRecord(String name, List<Integer> values) implements Serializable {}
enum Status { READY("ready"), DONE }"#,
    );

    assert_eq!(analysis["declarations"][0]["kind"], "record");
    assert_eq!(analysis["declarations"][0]["fields"][1]["type"]["kind"], "list");
    assert_eq!(analysis["declarations"][0]["super_types"][0]["name"], "Serializable");
    assert_eq!(analysis["declarations"][1]["kind"], "enum");
    assert_eq!(analysis["declarations"][1]["enum_values"][0]["name"], "READY");
    assert_eq!(analysis["declarations"][1]["enum_values"][1]["name"], "DONE");
}

#[test]
fn analyze_source_extracts_kotlin_type_alias_and_enum_class() {
    let analysis = decode_analyze_source(
        1,
        r#"typealias Identifier = String
enum class Status {
    READY,
    DONE,
}"#,
    );

    assert_eq!(analysis["declarations"][0]["kind"], "type_alias");
    assert_eq!(analysis["declarations"][0]["aliased_type"]["kind"], "primitive");
    assert_eq!(analysis["declarations"][0]["aliased_type"]["primitive"], "string");
    assert_eq!(analysis["declarations"][1]["kind"], "enum");
    assert_eq!(analysis["declarations"][1]["enum_values"][0]["name"], "READY");
    assert_eq!(analysis["declarations"][1]["enum_values"][1]["name"], "DONE");
}

#[test]
fn analyze_source_typescript_unsupported_types_emit_unknown_and_diagnostics() {
    let analysis = decode_analyze_source(
        2,
        r#"type Pair = [string, number];
type Combined = Foo & Bar;"#,
    );

    assert_eq!(analysis["declarations"][0]["aliased_type"]["kind"], "unknown");
    assert_eq!(analysis["declarations"][1]["aliased_type"]["kind"], "unknown");
    assert_diagnostic_code_present(&analysis, "typescript.type.tuple");
    assert_diagnostic_code_present(&analysis, "typescript.type.intersection");
}

#[test]
fn analyze_source_go_interface_methods_and_embedded_fields_emit_expected_ir() {
    let analysis = decode_analyze_source(
        3,
        r#"type Reader interface {
    io.Reader
    Read(p []byte) error
}

type User struct {
    Base
    Name string
}"#,
    );

    assert_eq!(analysis["declarations"][0]["kind"], "interface");
    assert_eq!(analysis["declarations"][0]["super_types"][0]["name"], "Reader");
    assert_eq!(analysis["declarations"][1]["kind"], "struct");
    assert_eq!(analysis["declarations"][1]["super_types"][0]["name"], "Base");
    assert_eq!(analysis["declarations"][1]["fields"][0]["name"], "Name");
    assert_diagnostic_code_present(&analysis, "go.interface.method_ignored");
}

#[test]
fn invalid_handles_and_pointers_return_error_codes_without_panicking() {
    let source_memory = guest_memory_from_str(JAVA_SOURCE);
    let invalid_parse_handle = tree_parse(-1, source_memory.pointer, source_memory.length);
    assert_eq!(invalid_parse_handle, -1);
    assert_last_error_contains("Unknown parser handle: -1");
    drop(source_memory);

    let parser_handle = parser_create(0);
    assert!(parser_handle > 0);

    let invalid_pointer_parse_handle = tree_parse(parser_handle, 0, 4);
    assert_eq!(invalid_pointer_parse_handle, -1);
    assert_last_error_contains("A non-zero buffer length requires a positive pointer.");

    let query_memory = guest_memory_from_str(JAVA_QUERY);
    let source_memory = guest_memory_from_str(JAVA_SOURCE);
    let packed_error = tree_query(-1, source_memory.pointer, source_memory.length, query_memory.pointer, query_memory.length);
    assert_eq!(unpack_ptr_len(packed_error), (0, WasmErrorCode::InvalidHandle as i32));
    assert_last_error_contains("Unknown tree handle: -1");

    drop(source_memory);
    drop(query_memory);
    assert_eq!(parser_destroy(parser_handle), ());
}

#[test]
fn successful_calls_clear_previous_error_messages() {
    let invalid_result = parser_create(99);
    assert_eq!(invalid_result, -1);
    assert_last_error_contains("Unsupported language id: 99");

    let parser_handle = parser_create(0);
    assert!(parser_handle > 0);
    assert_eq!(get_last_error(), 0);
    assert_eq!(parser_destroy(parser_handle), ());
}

fn assert_query_captures_expected_names(
    language_id: i32,
    source_code: &str,
    query_source: &str,
    expected_capture_names: &[&str],
) {
    let parser_handle = parser_create(language_id);
    assert!(parser_handle > 0, "parser handle should be positive for language id {language_id}");

    let source_memory = guest_memory_from_str(source_code);
    let tree_handle = tree_parse(parser_handle, source_memory.pointer, source_memory.length);
    assert!(tree_handle > 0, "tree handle should be positive for language id {language_id}");

    assert_eq!(parser_destroy(parser_handle), ());

    let query_memory = guest_memory_from_str(query_source);
    let query_result = decode_tree_query(tree_handle, &source_memory, &query_memory);
    let captures = query_result["captures"]
        .as_array()
        .expect("captures should be a JSON array");

    for expected_capture_name in expected_capture_names {
        assert!(
            captures.iter().any(|capture_entry| capture_entry["name"] == *expected_capture_name),
            "expected capture `{expected_capture_name}` for language id {language_id}"
        );
    }
    assert_eq!(query_result["has_syntax_errors"], false);

    assert_eq!(tree_destroy(tree_handle), ());
}

fn decode_tree_query(
    tree_handle: i32,
    source_memory: &GuestMemoryBuffer,
    query_memory: &GuestMemoryBuffer,
) -> Value {
    decode_json_result(tree_query(
        tree_handle,
        source_memory.pointer,
        source_memory.length,
        query_memory.pointer,
        query_memory.length,
    ))
}

fn assert_analysis_fixture(
    language_id: i32,
    source_code: &str,
    expected_json: &str,
) {
    let actual_analysis = decode_analyze_source(language_id, source_code);
    let expected_analysis: Value = serde_json::from_str(expected_json).expect("expected analysis JSON should parse");
    assert_eq!(actual_analysis, expected_analysis);
}

fn decode_analyze_source(
    language_id: i32,
    source_code: &str,
) -> Value {
    let source_memory = guest_memory_from_str(source_code);
    let packed_result = analyze_source(language_id, source_memory.pointer, source_memory.length);
    drop(source_memory);
    decode_json_result(packed_result)
}

fn assert_diagnostic_code_present(
    analysis: &Value,
    expected_code: &str,
) {
    let diagnostics = analysis["diagnostics"]
        .as_array()
        .expect("diagnostics should be a JSON array");
    assert!(
        diagnostics.iter().any(|diagnostic_entry| diagnostic_entry["code"] == expected_code),
        "expected diagnostic code `{expected_code}`, actual diagnostics={diagnostics:?}"
    );
}

fn decode_json_result(packed_result: i64) -> Value {
    let (pointer, length) = unpack_ptr_len(packed_result);
    if pointer <= 0 {
        let error_message = read_last_error_message();
        panic!("expected a JSON result buffer, packed_result={packed_result}, error={error_message}");
    }
    let result_json = memory::read_utf8_string(pointer, length).expect("result JSON should decode");
    dealloc(pointer, length);
    serde_json::from_str(&result_json).expect("result JSON should parse")
}

fn assert_last_error_contains(expected_fragment: &str) {
    let error_message = read_last_error_message();
    assert!(
        error_message.contains(expected_fragment),
        "expected error message to contain `{expected_fragment}`, actual `{error_message}`"
    );
}

fn unpack_ptr_len(packed_result: i64) -> (i32, i32) {
    (((packed_result >> 32) & 0xffff_ffff) as i32, packed_result as i32)
}

fn read_last_error_message() -> String {
    let packed_error = get_last_error();
    let (pointer, length) = unpack_ptr_len(packed_error);
    assert!(pointer > 0, "last error should allocate a message buffer");
    assert!(length > 0, "last error should contain a message");

    let error_message = memory::read_utf8_string(pointer, length).expect("error message should decode");
    dealloc(pointer, length);
    error_message
}

struct GuestMemoryBuffer {
    pointer: i32,
    length: i32,
}

impl Drop for GuestMemoryBuffer {
    fn drop(&mut self) {
        dealloc(self.pointer, self.length);
    }
}

fn guest_memory_from_str(value: &str) -> GuestMemoryBuffer {
    let (pointer, length) = memory::write_bytes(value.as_bytes()).expect("guest memory write should succeed");
    GuestMemoryBuffer {
        pointer,
        length,
    }
}

fn guest_memory_from_bytes(value: &[u8]) -> GuestMemoryBuffer {
    let (pointer, length) = memory::write_bytes(value).expect("guest memory write should succeed");
    GuestMemoryBuffer {
        pointer,
        length,
    }
}
