# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin for editing, formatting, querying, diffing, loading, generating, and converting JSON/JSON5 inside the IDE. The codebase is organized around IntelliJ extension points, project-scoped services, presenter-led Swing flows, and bundled tree-sitter/WASM assets for type conversion.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: primary plugin implementation.
    - `actions`: IntelliJ `AnAction` entry points for tool-window, diff, loading, generation, settings, and type-conversion commands.
    - `actions/editor`: selection-aware editor transforms that share validation and `WriteCommandAction` mutation through `BaseEditorJsonAction`.
    - `diff`: JSON diff extension logic, request keys, debounce handling, document-state tracking, and self-update guards.
    - `extensions`, `listeners`: IDE hooks for paste formatting, template highlighting, goto declaration, activation, and onboarding startup.
    - `icons`, `model`: icon-pack accessors plus enums and transport models for format state, diff mode, query engine, languages, and type conversion.
    - `services`: project and application services for formatting, querying, diffing, resources, onboarding, coroutine scopes, random JSON, and placeholders.
        - `schema`: JSON Schema normalization, validation, constraint parsing, and data generation.
        - `treesitter`: Chicory-hosted WASM runtime, memory bridge, and query result decoding.
        - `typeConversion`: tree-sitter asset registry, declaration analysis, JSON-to-type inference/rendering, and type-to-JSON generation.
    - `settings`: persistent `@State` storage, settings UI, and message-bus listener wiring.
    - `ui`: Swing panels, editors, tabs, dialogs, diff helpers, and onboarding flows.
        - `component`: `main`, `tab`, `editor`, `jsonQuery`, `convertType`, and tab-scoped UI state models.
        - `dialog`: large-file warning plus `generateJson`, `loadJson`, and `convertType` presenter/view flows.
        - `diff`, `onboarding`: diff request helpers and guided onboarding UI.
    - `utils`: shared JSON path, conversion, and editor helpers.
- `src/main/resources`: plugin descriptors, localization bundles, icon packs, onboarding media, and bundled query/WASM assets.
    - `META-INF/plugin.xml`: extension points, listeners, actions, tool window, and settings registration.
    - `messages`: localized `LocalizationBundle*.properties` files; add new user-facing strings here.
    - `tree-sitter`: asset manifest plus query files for supported type-conversion languages.
    - `wasm`: bundled probe modules and the packaged `tree-sitter.wasm` runtime.
- `src/test/kotlin/com/livteam/jsoninja`: focused action, formatter, query, schema, UI-component, diff, and tree-sitter tests.
- `docs`: contributor documentation such as `DEVELOPMENT_GUIDE.md` and `PROJECT_STRUCTURE.md`; keep them aligned when behavior changes.
- `tree-sitter-wasm`: Rust helper crate that builds the tree-sitter WASM binary copied into plugin resources; source lives under `src`, grammar queries under `queries`.
- `todos`, `ai`: backlog material and design/source assets that support plugin work but are not runtime code.
- `scripts`: release and changelog helper scripts.
- `.github/workflows`, `.run`, `qodana.yml`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`: CI, IDE run configurations, and Gradle/IntelliJ Platform build setup.

## 3. Core Behaviors & Patterns
- **Thin Action Entry Points**: `AnAction` classes resolve context and delegate to presenters or services. Editor transforms share `BaseEditorJsonAction` for validation, no-op checks, and `WriteCommandAction` updates.
- **Presenter-Wired Tab Flows**: `JsoninjaPanelView` owns `JsoninjaPanelPresenter`, which owns `JsonTabsPresenter`; `JsonTabContextFactory` creates tab contents. Child components use callbacks/listeners so parent presenters coordinate state.
- **Shared JSON Infrastructure**: `JsonObjectMapperService` provides the Jackson/JSON5 mapper reused by formatter, query, schema, API-loading, tree views, highlighting, and type conversion. `JsonFormatterService` layers sorting, compact arrays, caching, escaping, and placeholder restoration.
- **Runtime-Selectable Querying**: Query execution switches between Jayway JsonPath, JMESPath, and Jackson jq from persisted settings. `JsonQueryPresenter` stores original JSON, listens for settings changes, validates expressions, and writes formatted results through tab callbacks.
- **Schema Generation Pipeline**: Schema-backed generation validates text at the presenter boundary, normalizes references, folds sibling constraints, compiles schemas, generates primary candidates, and falls back to minimal valid nodes. `JsonSchemaGenerationException` carries messages and JSON pointers into UI feedback.
- **Type Conversion Preview Pipeline**: `ConvertTypeDialogPresenter` resolves the initial seed, owns both conversion presenters, and synchronizes language selection with a re-entry guard. `ConvertPreviewExecutor` debounces previews, cancels stale jobs by sequence number, computes off the EDT, and returns view states.
- **Tree-sitter Resource Pipeline**: Type-to-JSON analysis loads query resources through `TreeSitterAssetRegistryService`, reuses `TreeSitterWasmRuntime.getOrCreate()`, writes source through `WasmMemoryBridge`, and decodes `TreeSitterQueryResult`.
- **Guarded Recovery**: Actions, presenters, and services exit early for missing `Project`, blank input, invalid JSON, invalid expressions, disposed UI, unsupported viewers, or invalid URLs. Recoverable failures return original text, `null`, localized `ValidationInfo`, or preview error states.
- **Threading and Disposal Boundaries**: Background work goes through `JsoninjaCoroutineScopeService`; presenters create child scopes and cancel them from `dispose` or `Disposer.register`. UI updates return to `Dispatchers.EDT`, and document mutations use `WriteCommandAction.runWriteCommandAction`.
- **Diff Loop Prevention and Large-File Guardrails**: Diff requests carry user-data markers. `JsonDiffExtension` tracks documents in a synchronized `WeakHashMap`, uses `CHANGE_GUARD_KEY` plus `AtomicBoolean` against self-updates, debounces edits with `Alarm`, skips tiny whitespace edits, and respects large-file warnings.

## 4. Conventions
- **Naming & Packages**: Packages stay under `com.livteam.jsoninja.*`. Types use `PascalCase`, functions and properties use `lowerCamelCase`, booleans prefer `is` or `has`, and numeric settings keep explicit units where meaningful, such as `largeFileThresholdMB`.
- **Role Suffixes**: Class names identify their layer: `*Action`, `*Service`, `*Presenter`, `*View`, `*Dialog`, `*Factory`, `*State`, `*Configurable`, `*Validator`, `*Adapter`, and `*Executor`.
- **Callback & Method Shapes**: Cross-component hooks use `setOn...Callback`, `setOn...Listener`, or `setOn...Requested`. Setup helpers use `setup*`, `create*`, `bind*`, and `apply*`; execution methods stay verb-led, such as `performSearch`, `formatJson`, `generateFromSchema`, and `schedulePreview`.
- **State Modeling**: Persisted selections live in `JsoninjaSettingsState` as enum names or simple primitives. Runtime code converts them through enums, wrappers, or settings adapters such as `JsonToTypeDialogSettingsAdapter` and `TypeToJsonDialogSettingsAdapter` rather than scattering raw string checks.
- **Dialog and Presenter Composition**: `DialogWrapper` shells stay thin and delegate panel creation, validation, preview state, copy/insert behavior, and disposal to presenter/view pairs. Presenters own validation, settings persistence, service calls, and coroutine cancellation.
- **Disposal and Document Ownership**: Tabs, editors, presenters, listeners, alarms, and child coroutine scopes register cleanup with `Disposer.register` or explicit `dispose`. Temporary document flags stay in nearby `userData` keys, and document edits go through IntelliJ write APIs.
- **Localization and Registration**: New user-facing strings belong in `LocalizationBundle*.properties` using dotted namespaces such as `dialog.generate.json.*`, `dialog.load.json.api.*`, `common.convert.*`, `settings.*`, or `validation.*`. Plugin registrations belong in `src/main/resources/META-INF/plugin.xml`, grouped by extensions, listeners, and actions.
- **Bundled Resource Layout**: Tree-sitter query files live under `tree-sitter/queries/<language>/`, language icons under `icons/languages`, icon-pack variants under `icons/classic` and `icons/expui`, and bundled WASM under `wasm/tree-sitter`. Keep asset lookup centralized through registry/resource services or model metadata.
- **Boundary Conventions**: Platform-facing code checks `Project`, viewer type, selected editor, disposed state, file size, and URL validity before work starts. Rich internal errors flatten into localized validation text, dialogs, or preview messages at UI boundaries.
- **Comments and Logging**: Comments are short and selective, usually reserved for IntelliJ threading, lifecycle, resource, or formatting nuance. Logging uses `logger<T>()`, `thisLogger()`, or `Logger.getInstance(...)`; `debug` is for diagnostics, `warn` for recoverable failures, and `error` for hard failures.

## 5. Working Agreements
- Respond in Korean, keep identifiers and technical terms in their original form when needed, and never modify fenced code blocks when translating or documenting.
- Create tests, lint changes, or formatting-only updates only when explicitly requested.
- Build context from related actions, presenters, services, message bundles, and plugin registrations before editing.
- Prefer the simplest change that fits the existing presenter/service/resource wiring; avoid unnecessary abstraction.
- Ask for clarification instead of guessing when requirements are ambiguous or behavior changes would be risky.
- Keep edits minimal, preserve public APIs and existing plugin behavior, and colocate new code near the feature that owns it.
- When code changes need verification, prefer `./gradlew compileKotlin` as the baseline type-safety check.
- Introduce external dependencies only when necessary, and explain why if you add one.

## 6. User Custom
- linear 이슈 작업할때 label[Front-end, Back-end] 상관없이 작업한다. (kotlin, java project는 FE,BE 구분하지않음)

## When Extending
- Register new actions/components in `plugin.xml` and align icons/messages.
- Co-locate new features with existing package patterns (service + action + UI wiring).
- Keep `JsonEditor`/tab lifecycle consistent: dispose resources via `Disposer`, preserve `JSONINJA_EDITOR_KEY`, respect large-file warning thresholds.
- For formatting changes, consider cache keys and `JsonFormatState` semantics (sorting, compact arrays, uglify override).
- For query-related features, handle both Jayway and JMESPath or gate by setting.

## Threading Rules

| Scenario | Pattern |
|----------|---------|
| Background → UI update | `executeOnPooledThread { compute(); invokeLater(ModalityState.any()) { updateUI() } }` |
| Background → Write + Undo | `executeOnPooledThread { compute(); WriteCommandAction.runWriteCommandAction(project) { } }` |
| Write + Undo (EDT) | `WriteCommandAction.runWriteCommandAction(project) { }` |
| Write Only (EDT) | `runWriteAction { }` |
