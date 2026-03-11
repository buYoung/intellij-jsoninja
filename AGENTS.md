# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin for formatting, querying, diffing, loading, and generating JSON inside the IDE. The codebase is organized around IntelliJ extension points, project-scoped services, and Presenter/View-style Swing UI composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: primary plugin implementation.
    - `actions`: IntelliJ `AnAction` entry points for formatting, diffing, query copying, settings, and tab operations.
    - `diff`: `DiffExtension` logic for JSON-aware diff viewers, debouncing, and re-entrancy guards.
    - `extensions`, `listeners`: IDE integration hooks such as paste preprocessing, goto-declaration, activation, and onboarding startup.
    - `icons`: icon accessors and icon-pack mappings used by actions and the tool window.
    - `model`: shared enums and state carriers for formatting, diff display, query type, and icon choices.
    - `services`: project/app services for formatting, querying, onboarding, mapper setup, and JSON generation flows.
        - `schema`: JSON Schema normalization, validation, and value generation services.
    - `settings`: persistent `@State` models and `Configurable` UI for plugin preferences.
    - `ui`: Swing UI composition for the tool window, dialogs, onboarding, and diff-related views.
        - `component`: editor, query, tab, and main-panel Presenter/View wiring.
        - `dialog`: JSON generation, API loading, and large-file warning dialogs.
        - `diff`: diff request helpers and virtual file adapters for editor-tab/window diff flows.
        - `onboarding`: welcome/tutorial dialogs and tooltip support.
        - `toolWindow`: tool-window factory and bootstrap classes.
    - `utils`: JSON path, editor, and file helper utilities shared across actions and UI.
    - `dev`, `events`: currently light-weight package roots reserved for future expansion.
- `src/main/resources`: plugin metadata, localization, and static assets.
    - `META-INF/plugin.xml`: extension-point, action, tool-window, and settings registration.
    - `messages`: `LocalizationBundle` property files for default and translated user-facing text.
    - `icons`, `images/onboarding`: packaged icons and onboarding media.
- `src/test/kotlin/com/livteam/jsoninja`: Kotlin/JUnit coverage for actions, services, and UI flows; mirror production packages when adding tests.
- `pro`: separate companion module tree with its own `src` and plugin descriptor resources; treat it as an adjacent deliverable, not as the main source root.
- `docs`, `prd`, `todos`: contributor documentation, product notes, and task/backlog material.
- `.github/workflows`: CI definitions for build, release, and UI test automation.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`: Gradle and IntelliJ Platform build configuration.

## 3. Core Behaviors & Patterns
- **Logging**: Services, presenters, and integration classes initialize logging with `logger<T>()`, `thisLogger()`, or class-level `LOG`. Use `debug` for diagnostics, `warn` for recoverable failures, and `error` for hard failures such as `OutOfMemoryError` during diff processing.
- **Guard Clauses**: Actions, services, and presenters exit early on missing `Project`, invalid editor state, blank JSON, disposed UI, or unsupported viewer types. Preserve this style instead of nesting control flow.
- **Failure Recovery**: Formatter, query, schema, and diff code generally catch exceptions and fall back to original text, `null`, `false`, or validation messages so plugin UI remains usable after malformed input or external library failures.
- **Threading**: Follow IntelliJ threading boundaries: background work goes through `executeOnPooledThread`, UI updates return via `invokeLater`, and document mutations use `WriteCommandAction.runWriteCommandAction` or `runWriteAction`.
- **Diff Synchronization**: JSON diff formatting uses document-scoped state, content hashes, debounce alarms, and re-entrancy flags (`AtomicBoolean`, `WeakHashMap`, user data keys) to avoid self-triggered update loops and repeated heavy parsing.
- **Service-Driven Flows**: Actions and presenters delegate JSON work to project-scoped services (`JsonFormatterService`, `JsonQueryService`, schema services) rather than embedding parsing or formatting logic directly in UI classes.
- **Query Integration**: Query features support both Jayway JsonPath and JMESPath, with runtime selection coming from persisted settings and helper utilities such as `JsonPathHelper`.
- **Localization**: User-visible titles, descriptions, labels, and dialog text are expected to go through `LocalizationBundle.message(...)`; avoid introducing new hardcoded UI strings.

## 4. Conventions
- **Naming**: Packages stay under `com.livteam.jsoninja.*`. Types use `PascalCase`, functions and properties use `lowerCamelCase`, and booleans typically use `is`/`has` prefixes such as `isDisposed` or `hasOriginalJson`.
- **Role Suffixes**: Keep class names explicit about their layer: `*Action`, `*Service`, `*Presenter`, `*View`, `*Dialog`, `*Factory`, `*State`, and `*Configurable` are all established patterns in the codebase.
- **State Modeling**: Persisted selections and runtime modes are usually `enum class` values stored as strings in state objects, while small transport/state holders are modeled with focused `data class` types.
- **Constants**: Thresholds, keys, and fixed labels are commonly grouped in `companion object` or nested `object Constants` blocks near the owning behavior.
- **UI Composition**: Swing UI code separates Presenter and View responsibilities and registers disposables explicitly with `Disposer.register`; when adding tabs or editors, wire cleanup at creation time.
- **Comments**: Comments are short and selective, often in Korean, and mainly explain non-obvious IntelliJ, threading, or formatting behavior. Avoid verbose commentary for straightforward code.
- **Localization and Resources**: New UI text belongs in `messages/LocalizationBundle*.properties`, and plugin registrations belong in resource descriptors such as `META-INF/plugin.xml` or companion plugin XML files rather than in code literals.

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
