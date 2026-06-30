# AGENTS.md

## 1. Overview
`tree-sitter-wasm` is the tree-sitter-based WASM bridge crate used by the parent plugin repository for type-declaration analysis and query execution. It combines integer handles, a host/guest memory bridge, language-specific analyzers, and type normalization so the host can receive parsing and analysis results as JSON.

## 2. Folder Structure
- `src`: core implementation containing the FFI boundary, runtime state, shared IR, and language-specific analyzers.
  - `lib.rs`: exposes `#[no_mangle] pub extern "C"` entry points and the `execute_i32` / `execute_i64` / `execute_void` error boundaries.
  - `parser.rs`, `query.rs`: create parser/tree handles, parse source text, compile queries, and collect captures and diagnostics.
  - `memory.rs`, `runtime_state.rs`: manage linear-memory I/O, the host-side fallback memory store, the last error message, and global handle stores.
  - `analyzer`: contains the Java, Kotlin, TypeScript, and Go declaration analyzers that normalize output into the shared `AnalysisOutput` IR.
  - `type_parser`: converts language-specific type nodes into `TypeReference` variants and degrades unsupported syntax into diagnostics plus `Unknown`.
  - `diagnostics.rs`, `ir.rs`, `source.rs`: define diagnostic models, serialized IR types, and AST text/span helper functions.
  - `error.rs`, `handle_store.rs`, `language.rs`, `query_result.rs`, `utils.rs`: provide error codes, integer handle storage, the language registry, query result models, and allocation helpers.
  - `tests.rs`: holds regression tests for the public FFI surface and the memory/error contracts.
- `queries`: language-specific tree-sitter query assets.
  - `java`, `kotlin`, `typescript`, `go`: each directory stores `type-declarations.scm`.
- `tests/fixtures`: language-specific sample sources and expected result assets.
  - `<language>/sample.*`: input examples used for query capture verification.
  - `<language>/analysis.*`, `expected-analysis.json`, `expected-captures.txt`: regression baselines for analysis IR and query captures.
- `.cargo/config.toml`: defines toolchain environment variables for host-side tests and explicit `wasm32-wasip1` builds.
- `Cargo.toml`: defines the `cdylib` output, grammar features, dependencies, and release optimization policy.
- `build.rs`: limits rebuild triggers to changes under `src`, `queries`, `tests`, and `Cargo.toml`.
- `README.md`: explains the crate purpose and the host integration flow.

## 3. Core Behaviors & Patterns
- **Thin FFI Boundary**: All external calls enter through `src/lib.rs`, where pointer/length inputs are decoded and return values are packed before work is delegated to `parser`, `query`, or `analyzer`. When adding a new ABI, keep argument conversion and error packing in `lib.rs` and move actual logic into an internal module.
- **Flattened Errors and Panic Containment**: Every public function clears the previous error message before executing inside `catch_unwind`. Failures become `-1` on `i32` paths or packed error codes on `i64` paths, while detailed messages remain available through `get_last_error()`.
- **Handle-Based Lifetime Management**: `Parser` and `Tree` values are never exposed directly; they are stored behind positive integer handles issued by `HandleStore` inside `RuntimeState`. Parser and tree lifetimes are intentionally separate, so any new shared state should follow the same storage and `InvalidHandle` error pattern.
- **Dual Host/Guest Memory Paths**: `memory.rs` reads and writes real linear memory for WASM builds and uses `HostMemoryStore` for non-WASM tests. Pointer validation and JSON serialization should stay at this boundary so upper layers can operate on strings and structured values only.
- **Shared-IR Analysis Pipeline**: `analyze_source` first builds a syntax tree, then collects syntax diagnostics, then lets the language-specific analyzer extract declarations while `type_parser` normalizes type nodes into `TypeReference` variants. The final result is wrapped in `AnalysisOutput` and returned through the `serde_json` serialization path.
- **Partial Results with Diagnostics**: Syntax errors and unsupported type constructs do not stop analysis immediately. The code keeps as much declaration data as possible and records loss of fidelity through `Diagnostic::error` or `Diagnostic::warning`, so new analysis logic should prefer `Unknown` plus diagnostics over fail-fast behavior.
- **Language Asset Synchronization**: Supported languages move as a single set across `SupportedLanguage`, `analyzer`, `type_parser`, `queries/<language>`, and `tests/fixtures/<language>`. When adding a language or changing a query contract, update all of those locations together.

## 4. Conventions
- **Naming**: Functions use verb-oriented `snake_case`, such as `parser_create`, `tree_query`, `collect_syntax_diagnostics`, and `parse_type_reference`. Types and enums use `PascalCase` names that expose responsibility, such as `WasmRuntimeError`, `DiagnosticSeverity`, `AnalysisOutput`, and `TypeReference`.
- **Public API Placement**: Keep `#[no_mangle] pub extern "C"` functions in `src/lib.rs` only. Centralize language dispatch in `analyzer/mod.rs` and `type_parser/mod.rs`, and keep language-specific parsing logic in the per-language files rather than growing the dispatch modules.
- **Shared Constructors and Helpers**: Create diagnostics through `Diagnostic::error` and `Diagnostic::warning`, and runtime failures through `WasmRuntimeError::new`. For type normalization, prefer the shared helper constructors in `type_parser/mod.rs`, such as `named_type`, `list_type`, `map_type`, `nullable_type`, and `unknown_type_with_message`, so output shapes stay consistent across languages.
- **Serialization Rules**: Models returned to the host use derived `serde::Serialize`, with JSON naming controlled through attributes like `#[serde(rename_all = "snake_case")]` and `#[serde(rename = "type")]`. When the output schema changes, update the serialized models instead of introducing manual JSON assembly.
- **Deterministic Ordering**: Use `BTreeSet` and `BTreeMap` for collected type-parameter names and constraints when result ordering matters. The fixture-based regression tests compare JSON output, so new collections should preserve deterministic ordering whenever practical.
- **Comments and Conditional Compilation**: Keep comments short and reserve them for ABI contracts, memory boundaries, and toolchain-specific context, as seen in `memory.rs` and `.cargo/config.toml`. Prefer compile-time branching with `#[cfg(target_arch = "wasm32")]` and feature gates over runtime branching.
- **Asset Layout**: Store query assets under `queries/<language>/type-declarations.scm`, and keep fixtures in `tests/fixtures/<language>/` with the `sample.*`, `analysis.*`, `expected-analysis.json`, and `expected-captures.txt` naming pattern. Follow the same layout when adding languages or new regression cases.

## 5. Working Agreements
- Follow the shared working rules from the root `/AGENTS.md`.
- When changing the FFI contract, update `src/lib.rs` together with `src/error.rs`, `src/memory.rs`, and the relevant tests rather than adjusting one layer in isolation.
- When changing language support, update `SupportedLanguage`, the per-language `analyzer` and `type_parser` modules, query assets, and fixture assets in one pass.
- When changing the analysis result schema, review `ir.rs`, `query_result.rs`, `tests/fixtures/*/expected-analysis.json`, and `src/tests.rs` together so regression baselines stay aligned.
- Prefer the smallest change that preserves the existing meaning of host-visible JSON field names and error codes.
