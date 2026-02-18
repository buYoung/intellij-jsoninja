# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin centered on JSON formatting, querying, diffing, API loading, and data generation workflows. The architecture combines IntelliJ extension points with project-scoped JSON services and Presenter/View-based tool-window UI composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: main plugin source.
    - `actions`: IntelliJ action entry points (format, query, diff, generate, load-from-API, tab control).
    - `diff`: `DiffExtension` integration and re-entrancy guarded document synchronization for JSON diffs.
    - `extensions`, `listeners`: IDE extension-point hooks and startup/lifecycle listeners.
    - `icons`: icon loaders and icon-pack bindings used by actions/tool windows.
    - `model`: shared enum/data state for format/query/icon/diff options.
    - `services`: project/application services for formatting, query execution, diff invocation, onboarding, and random data.
        - `schema`: JSON Schema normalization/validation/value generation pipeline.
    - `settings`: persistent settings state and `Configurable` UI binding.
    - `ui`: tool-window and dialog composition.
        - `component`: Presenter/View pairs for editors, tabs, query pane, and main panel.
        - `dialog`: large-file warning, JSON generation (`generateJson`), and API load dialog (`loadJson`).
        - `diff`: diff request chain and virtual-file adapters.
        - `onboarding`: tutorial dialog flow and tooltip controllers.
        - `toolWindow`: tool-window factory registration.
    - `utils`: editor/path/query helper utilities.
    - `dev`, `events`: package roots reserved for future expansion.
- `src/main/resources`: plugin metadata and assets.
    - `META-INF/plugin.xml`: action/extension/tool-window registration and bundle binding.
    - `icons`: classic/expui icon sets with `v2` variants.
    - `images/onboarding`: onboarding GIF resources.
    - `messages`: localization bundles (`default`, `en`, `ko`, `ja`, `zh_CN`).
- `src/test/kotlin/com/livteam/jsoninja`: JUnit coverage for actions/services/UI flows.
- `docs`: contributor-focused guides (`DEVELOPMENT_GUIDE`, structure notes).
- `.github/workflows`: CI workflows for build/release/UI test runs.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Gradle and IntelliJ Platform build configuration.
- `ai`: source design assets used for icon generation work.
- `prd`, `todos`: product notes and backlog-style task documents.

## 3. Core Behaviors & Patterns
- Logging is standardized with `logger<T>()`, `thisLogger()`, and class-level `LOG`, with `debug/warn/error` used for diagnostics and recovery context.
- Guard-clause control flow is pervasive (`?: return`, `if (...) return`, early `return null/false`) to exit quickly on invalid editor/project/query states.
- Error handling prefers safe fallback behavior: formatter/query/schema flows catch exceptions and return original text, `null`, or validation errors instead of propagating UI-breaking failures.
- Threading follows IntelliJ patterns: expensive operations on pooled threads (`executeOnPooledThread`), UI mutation on EDT (`invokeLater`), document writes through `WriteCommandAction` or `runWriteAction`.
- JSON diff auto-formatting uses debounce + re-entrancy guards (`putUserData`, `AtomicBoolean`, content hash checks, `WeakHashMap` state) to prevent self-triggered loops.
- Query behavior supports dual engines (Jayway JsonPath and JMESPath) with runtime selection from persisted settings and shared query-copy helpers.
- Schema generation is split into parse/normalize/validate/generate steps, including fallback generation paths when primary generation fails.
- User-visible labels/messages are consistently routed through `LocalizationBundle.message(...)` and resource bundles.

## 4. Conventions
- Package layout follows `com.livteam.jsoninja.*`; types use PascalCase and members use lowerCamelCase.
- Role-oriented naming is consistent across layers: `*Action`, `*Service`, `*Presenter`, `*View`, `*Dialog`, `*Factory`, `*SettingsState`, `*Configurable`.
- Constants are commonly defined in `companion object` with `const val`; static-like keys/thresholds are grouped near related behavior.
- Domain choices and persisted options are modeled with `enum class`; shared UI/runtime payloads are represented with focused `data class` types.
- UI code favors Presenter/View separation with explicit disposable lifecycle management (`Disposer.register`, disposal on tab/component teardown).
- Comments are concise and mostly Korean, with English preserved for API/library interop terminology.
- User-facing strings should be sourced from `LocalizationBundle.message(...)` and `messages/LocalizationBundle*.properties`, not hardcoded literals.

## 5. Working Agreements
- Respond in Korean (keep tech terms in English, never translate code blocks)
- Create tests/lint only when explicitly requested
- Build context by reviewing related usages and patterns before editing
- Prefer simple solutions; avoid unnecessary abstraction
- Ask for clarification when requirements are ambiguous
- Minimal changes; preserve public APIs
- New functions/modules: single-purpose and colocated with related code
- External dependencies: only when necessary, explain why

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
