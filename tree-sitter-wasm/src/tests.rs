use crate::memory;
use crate::types::WasmErrorCode;
use crate::{
    dealloc, get_last_error, get_supported_languages, parse, parser_create, parser_destroy,
    query_execute, tree_destroy,
};

const JAVA_QUERY: &str = include_str!("../../src/main/resources/tree-sitter/queries/java/type-declarations.scm");
const KOTLIN_QUERY: &str =
    include_str!("../../src/main/resources/tree-sitter/queries/kotlin/type-declarations.scm");
const TYPESCRIPT_QUERY: &str = include_str!(
    "../../src/main/resources/tree-sitter/queries/typescript/type-declarations.scm"
);
const GO_QUERY: &str = include_str!("../../src/main/resources/tree-sitter/queries/go/type-declarations.scm");

const JAVA_SOURCE: &str = include_str!("../tests/fixtures/java/sample.java");
const KOTLIN_SOURCE: &str = include_str!("../tests/fixtures/kotlin/sample.kt");
const TYPESCRIPT_SOURCE: &str = include_str!("../tests/fixtures/typescript/sample.ts");
const GO_SOURCE: &str = include_str!("../tests/fixtures/go/sample.go");

const JAVA_EXPECTED: &str = include_str!("../tests/fixtures/java/expected-captures.txt");
const KOTLIN_EXPECTED: &str = include_str!("../tests/fixtures/kotlin/expected-captures.txt");
const TYPESCRIPT_EXPECTED: &str = include_str!("../tests/fixtures/typescript/expected-captures.txt");
const GO_EXPECTED: &str = include_str!("../tests/fixtures/go/expected-captures.txt");

#[test]
fn get_supported_languages_returns_expected_json() {
    let packed_result = get_supported_languages();
    let (pointer, length) = unpack_ptr_len(packed_result);

    let languages_json = memory::read_utf8_string(pointer, length).expect("languages JSON should decode");
    assert_eq!(languages_json, "[\"java\",\"kotlin\",\"typescript\",\"go\"]");

    dealloc(pointer, length);
}

#[test]
fn parser_and_tree_handles_have_independent_lifetimes() {
    assert_matches_expected_captures(0, JAVA_SOURCE, JAVA_QUERY, JAVA_EXPECTED);
    assert_matches_expected_captures(1, KOTLIN_SOURCE, KOTLIN_QUERY, KOTLIN_EXPECTED);
    assert_matches_expected_captures(2, TYPESCRIPT_SOURCE, TYPESCRIPT_QUERY, TYPESCRIPT_EXPECTED);
    assert_matches_expected_captures(3, GO_SOURCE, GO_QUERY, GO_EXPECTED);
}

#[test]
fn invalid_handles_and_pointers_return_error_codes_without_panicking() {
    let source_memory = guest_memory_from_str(JAVA_SOURCE);
    let invalid_parse_handle = parse(-1, source_memory.pointer, source_memory.length);
    assert_eq!(invalid_parse_handle, -1);
    assert_last_error_contains("Unknown parser handle: -1");
    drop(source_memory);

    let parser_handle = parser_create(0);
    assert!(parser_handle > 0);

    let invalid_pointer_parse_handle = parse(parser_handle, 0, 4);
    assert_eq!(invalid_pointer_parse_handle, -1);
    assert_last_error_contains("A non-zero buffer length requires a positive pointer.");

    let query_memory = guest_memory_from_str(JAVA_QUERY);
    let source_memory = guest_memory_from_str(JAVA_SOURCE);
    let packed_error = query_execute(-1, query_memory.pointer, query_memory.length, source_memory.pointer, source_memory.length);
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

fn assert_matches_expected_captures(
    language_id: i32,
    source_code: &str,
    query_source: &str,
    expected_capture_text: &str,
) {
    let parser_handle = parser_create(language_id);
    if parser_handle <= 0 {
        let error_message = read_last_error_message();
        panic!("parser handle should be positive for language id {language_id}; error={error_message}");
    }

    let source_memory = guest_memory_from_str(source_code);
    let tree_handle = parse(parser_handle, source_memory.pointer, source_memory.length);
    assert!(tree_handle > 0, "tree handle should be positive for language id {language_id}");

    assert_eq!(parser_destroy(parser_handle), ());

    let query_memory = guest_memory_from_str(query_source);
    let packed_result = query_execute(
        tree_handle,
        query_memory.pointer,
        query_memory.length,
        source_memory.pointer,
        source_memory.length,
    );

    let (result_pointer, result_length) = unpack_ptr_len(packed_result);
    if result_pointer <= 0 {
        let error_message = read_last_error_message();
        panic!(
            "query result should be stored in guest memory for language id {language_id}; \
             packed_result={packed_result}, error={error_message}"
        );
    }
    assert!(result_length > 0, "query result length should be positive");

    let query_result_json = memory::read_utf8_string(result_pointer, result_length)
        .expect("query result JSON should decode");
    let actual_captures = extract_capture_name_text_pairs(&query_result_json);
    let expected_captures = parse_expected_capture_pairs(expected_capture_text);

    let comparable_actual_captures = actual_captures
        .iter()
        .filter(|(name, _)| name != "type.declaration" && name != "field.declaration")
        .cloned()
        .collect::<Vec<_>>();

    assert!(
        actual_captures.iter().any(|(name, _)| name == "type.declaration"),
        "type.declaration capture should exist for language id {language_id}"
    );
    assert!(
        actual_captures.iter().any(|(name, _)| name == "field.declaration"),
        "field.declaration capture should exist for language id {language_id}"
    );
    assert_eq!(
        comparable_actual_captures,
        expected_captures,
        "unexpected captures for language id {language_id}"
    );

    dealloc(result_pointer, result_length);
    assert_eq!(tree_destroy(tree_handle), ());
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

fn parse_expected_capture_pairs(expected_capture_text: &str) -> Vec<(String, String)> {
    expected_capture_text
        .lines()
        .filter(|line| !line.trim().is_empty())
        .map(|line| {
            let (name, text) = line
                .split_once('|')
                .expect("expected capture line should contain a `|` separator");
            (name.to_string(), text.to_string())
        })
        .collect()
}

fn extract_capture_name_text_pairs(query_result_json: &str) -> Vec<(String, String)> {
    let mut capture_pairs = Vec::new();
    let mut search_start_index = 0;

    while let Some(name_start_index) = query_result_json[search_start_index..].find("\"name\":\"") {
        let name_value_start_index = search_start_index + name_start_index + "\"name\":\"".len();
        let name_value_end_index = query_result_json[name_value_start_index..]
            .find("\",\"text\":\"")
            .map(|offset| name_value_start_index + offset)
            .expect("capture name should be followed by text");
        let text_value_start_index = name_value_end_index + "\",\"text\":\"".len();
        let text_value_end_index = query_result_json[text_value_start_index..]
            .find("\",\"start_row\":")
            .map(|offset| text_value_start_index + offset)
            .expect("capture text should be followed by positions");

        let capture_name = query_result_json[name_value_start_index..name_value_end_index].to_string();
        let capture_text = query_result_json[text_value_start_index..text_value_end_index].to_string();
        capture_pairs.push((capture_name, capture_text));

        search_start_index = text_value_end_index;
    }

    capture_pairs
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
