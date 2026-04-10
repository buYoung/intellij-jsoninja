# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin for editing, formatting, querying, diffing, loading, generating, and converting JSON/JSON5 inside the IDE. The main codebase centers on IntelliJ extension points, project-scoped services, presenter-led Swing flows, and bundled tree-sitter/WASM assets used by the type-conversion features.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: primary plugin implementation.
    - `actions`: IntelliJ `AnAction` entry points, with `actions/editor` for selection-aware editor transforms.
    - `diff`: JSON diff extension logic, request keys, debounce handling, and self-update guards.
    - `extensions`, `listeners`: IDE hooks for paste formatting, goto declaration, highlight filtering, activation, and onboarding startup.
    - `icons`, `model`: icon-pack accessors plus enums and data models for format state, diff mode, query engine, and type conversion.
    - `services`: shared formatter, query, helper, resource, onboarding, and probe services.
        - `schema`: JSON Schema normalization, validation, constraint parsing, and data generation.
        - `treesitter`: bundled WASM runtime, parser handles, memory bridge, and query result decoding.
        - `typeConversion`: tree-sitter asset registry, declaration analyzers, and type-to-JSON generation.
    - `settings`: persistent `@State` storage, settings UI, and message-bus listener wiring.
    - `ui`: Swing tool window, editors, tabs, dialogs, diff helpers, and onboarding flows.
        - `component`: `main`, `tab`, `editor`, `jsonQuery`, `convertType`, and tab-scoped UI models.
        - `dialog`: large-file warning plus `generateJson`, `loadJson`, and `convertType` presenter/view flows.
        - `diff`, `onboarding`, `toolWindow`: diff request helpers, tutorial UI, and tool-window bootstrap.
    - `utils`: shared JSON path, conversion, and editor helpers.
    - `dev`, `events`: light-weight package roots reserved for narrower features and future expansion.
- `src/main/resources`: plugin descriptors, localization bundles, icon packs, onboarding media, and bundled query/WASM assets.
    - `META-INF/plugin.xml`: extension points, listeners, actions, tool window, and settings registration.
    - `messages`: default and Korean `LocalizationBundle*.properties` files; add new user-facing strings here.
    - `tree-sitter`: asset manifest plus query files for supported type-conversion languages.
    - `wasm`: bundled probe modules and the packaged `tree-sitter.wasm` runtime.
- `src/test/kotlin/com/livteam/jsoninja`: focused action, formatter, query, schema, diff, and tree-sitter tests.
- `docs`: contributor documentation such as `DEVELOPMENT_GUIDE.md` and `PROJECT_STRUCTURE.md`; keep them aligned when behavior changes.
- `pro`: premium-module scaffold with its own Gradle build file and `plugin-pro.xml` registrations.
- `tree-sitter-wasm`: Rust helper crate that builds the tree-sitter WASM binary copied into plugin resources.
- `prd`, `todos`, `ai`: product notes, backlog material, and design/source assets that support plugin work but are not runtime code.
- `.github/workflows`, `.run`, `qodana.yml`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`: CI, IDE run configurations, and Gradle/IntelliJ Platform build setup.

## 3. Core Behaviors & Patterns
- **Thin Action Entry Points**: `AnAction` classes usually resolve `Project`, panel, or editor context and then hand work to presenters or services. Selection-aware editor actions centralize validation and `WriteCommandAction` updates in shared base classes instead of duplicating document mutation logic.
- **Presenter-Wired Tab Flows**: Tool-window startup runs from `JsoninjaToolWindowFactory` into `JsoninjaPanelPresenter`, `JsonTabsPresenter`, and `JsonTabContextFactory`. Editors and query controls are wired through `setOn...Callback` and `setOn...Listener` hooks so tab state flows through parent presenters rather than through direct cross-component references.
- **Shared JSON Infrastructure**: `JsonObjectMapperService` supplies the shared Jackson configuration reused by formatter, query, schema, and asset-loading code. `JsonFormatterService` layers sorting, compact-array formatting, and placeholder preservation on top of that shared mapper instead of letting callers build their own parser configuration.
- **Runtime-Selectable Querying**: Query execution switches between Jayway JsonPath, JMESPath, and Jackson jq based on persisted settings. `JsonQueryPresenter` keeps the original JSON as its source of truth, validates expressions before running them in the background, and writes formatted results back into the active editor instead of keeping a separate result buffer.
- **Schema Generation Pipeline**: Schema-backed generation first performs lightweight text validation on the UI side, then normalizes references, resolves local and remote `$ref`/`$dynamicRef`, folds sibling schema constraints into `allOf`, validates compiled schemas, and surfaces `JsonSchemaGenerationException` with JSON pointers for localized error dialogs.
- **Tree-sitter Type Conversion Pipeline**: JSON-to-type and type-to-JSON dialogs delegate language analysis to `typeConversion` services backed by bundled tree-sitter query assets and the packaged WASM runtime. The flow separates asset lookup, declaration analysis, preview generation, and final editor insertion so new languages can extend the pipeline without reshaping the UI layer.
- **Guarded Recovery**: Actions, presenters, and services exit early for missing `Project`, blank JSON, invalid expressions, disposed UI, unsupported viewers, or invalid URLs. Most failures degrade to the original text, `null`, or localized validation/dialog feedback so the plugin remains usable after malformed input.
- **Threading and Disposal Boundaries**: Background work goes through `executeOnPooledThread`, UI updates return through `invokeLater(ModalityState.any())`, and document mutations use `WriteCommandAction.runWriteCommandAction` or `runWriteAction`. Tool-window, tab, editor, and presenter lifetimes are tied together with `Disposer.register`.
- **Diff Loop Prevention and Large-File Guardrails**: Diff requests carry user-data markers, and `JsonDiffExtension` keeps per-document state in a `WeakHashMap`, uses `CHANGE_GUARD_KEY` plus an `AtomicBoolean` to avoid re-entrant self-updates, debounces edits with `Alarm`, skips tiny whitespace-only edits, and respects large-file warnings before auto-formatting diff editors.

## 4. Conventions
- **Naming & Packages**: Packages stay under `com.livteam.jsoninja.*`. Types use `PascalCase`, functions and properties use `lowerCamelCase`, booleans prefer `is` or `has`, and numeric settings keep explicit units where meaningful, such as `largeFileThresholdMB`.
- **Role Suffixes**: Class names stay explicit about their layer: `*Action`, `*Service`, `*Presenter`, `*View`, `*Dialog`, `*Factory`, `*State`, and `*Configurable` are the dominant suffixes across actions, UI flows, and settings.
- **Callback & Method Shapes**: Cross-component hooks are usually named `setOn...Callback` or `setOn...Listener`. Setup helpers use `setup*`, `create*`, and `get*`, while execution methods stay verb-led, such as `performSearch`, `formatJson`, `generateFromSchema`, and `generateJsonFromTypeDeclaration`.
- **State Modeling**: Persisted selections are stored as enum names or simple primitives inside `JsoninjaSettingsState`, and runtime code converts them back through dedicated enums or wrappers instead of scattering raw string checks. Small transport objects stay as focused `data class` types near the flow that owns them.
- **Dialog and Presenter Composition**: `DialogWrapper` shells stay thin and delegate center-panel creation, validation, insert/apply behavior, and disposal to presenter/view pairs. When adding a dialog, keep shell logic minimal and push behavior into the presenter unless the IntelliJ API requires it in the wrapper.
- **Disposal and Document Ownership**: Tabs, editors, presenters, and listeners register cleanup with `Disposer.register`. Temporary document flags stay in nearby `userData` keys, and document edits always go through IntelliJ write APIs instead of ad hoc mutation.
- **Localization and Registration**: New user-facing strings belong in `LocalizationBundle*.properties` using dotted namespaces such as `dialog.generate.json.*`, `dialog.load.json.api.*`, `settings.*`, or `validation.error.*`. Plugin registrations belong in `META-INF/plugin.xml` or `pro/src/main/resources/META-INF/plugin-pro.xml`, not in hardcoded UI assembly code.
- **Bundled Resource Layout**: Tree-sitter assets are registered through `tree-sitter/asset-manifest.json`, language-specific queries live under `tree-sitter/queries/<language>/`, and bundled WASM binaries live under `wasm/`. Keep new asset-loading code aligned with `BundledResourceService` and the registry services instead of opening resources directly from scattered call sites.
- **Comments and Logging**: Comments are short and selective, usually reserved for IntelliJ threading, lifecycle, or formatting nuance. Logging uses `logger<T>()`, `thisLogger()`, or `Logger.getInstance(...)` with `debug` for diagnostics, `warn` for recoverable failures, and `error` for hard failures.

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

## Active Technologies
- Kotlin (JVM), IntelliJ Platform SDK + IntelliJ Platform 2024.3 (`platformVersion=2024.3`), Jackson, JMESPath/jq (002-verification-coroutine-refactor)
- N/A (IDE 플러그인, 별도 저장소 없음) (002-verification-coroutine-refactor)

## Recent Changes
- 002-verification-coroutine-refactor: Added Kotlin (JVM), IntelliJ Platform SDK + IntelliJ Platform 2024.3 (`platformVersion=2024.3`), Jackson, JMESPath/jq
