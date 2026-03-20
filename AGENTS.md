# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin for editing, formatting, querying, diffing, loading, and generating JSON/JSON5 inside the IDE. The codebase is organized around IntelliJ extension points, project-scoped services, shared JSON infrastructure, and presenter-led Swing UI flows.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: primary plugin implementation.
    - `actions`: IntelliJ `AnAction` entry points that resolve context and delegate to presenters or services.
    - `diff`: JSON-aware diff extension logic, request keys, debouncing, and re-entrancy guards.
    - `extensions`, `listeners`: IDE hooks for paste handling, goto declaration, highlight filtering, activation, and onboarding startup.
    - `icons`, `model`: icon-pack accessors plus enums/state carriers for format mode, diff display, query type, and icon selection.
    - `services`: project/app services for formatting, querying, shared mapper setup, onboarding, helper state, placeholder support, and JSON generation.
        - `schema`: JSON Schema normalization, validation, constraint parsing, and value generation.
    - `settings`: persistent `@State` settings and `Configurable` UI for plugin preferences.
    - `ui`: Swing UI composition for the tool window, tabs, editors, dialogs, onboarding, and diff helpers.
        - `component`: `main`, `tab`, `editor`, `jsonQuery`, and `model` packages that wire presenters/views and tab-scoped state.
        - `dialog`: large-file warning plus `generateJson` and `loadJson` flows with dedicated presenter/view/model subpackages.
        - `diff`, `onboarding`, `toolWindow`: diff request helpers, onboarding dialog/tooltip flows, and tool-window bootstrap.
    - `utils`: shared JSON path plus file/editor helper utilities.
    - `dev`, `events`: currently light-weight package roots reserved for future expansion.
- `src/main/resources`: plugin descriptor, localized message bundles, icons, and onboarding media.
    - `META-INF/plugin.xml`: extension points, listeners, actions, tool window, and settings registration.
    - `messages`: default and translated `LocalizationBundle*.properties` files; add new UI strings here.
    - `icons`, `images/onboarding`: packaged assets grouped by theme and icon pack.
- `src/test/kotlin/com/livteam/jsoninja`: focused action/service/schema tests; mirror production packages when tests are added.
- `docs`: contributor documentation such as `DEVELOPMENT_GUIDE.md` and `PROJECT_STRUCTURE.md`; keep implementation changes aligned when relevant.
- `prd`, `todos`, `ai`: product notes, backlog material, and design/source assets that support plugin work but are not runtime code.
- `.github/workflows`, `.run`, `qodana.yml`: CI, IDE run configurations, and static-analysis configuration.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/`: root Gradle and IntelliJ Platform build configuration.

## 3. Core Behaviors & Patterns
- **Thin Action Entry Points**: `AnAction` classes usually resolve `Project`, panel, or editor context and then delegate to presenters or services rather than embedding JSON logic in the action itself.
- **Presenter-Wired Tab Flows**: Tool-window startup flows through `JsoninjaToolWindowFactory` to `JsoninjaPanelPresenter`, then into `JsonTabsPresenter` and `JsonTabContextFactory`, where editor and query components communicate through registered callbacks instead of direct cross-component references.
- **Shared JSON Infrastructure**: `JsonObjectMapperService` provides the shared Jackson configuration used by formatter, query, diff, and schema code. Formatter/editor flows also preserve template placeholders through `TemplatePlaceholderSupport` instead of stripping them during normalization.
- **Guarded Recovery**: Actions, presenters, and services exit early for missing `Project`, invalid editor state, blank JSON, disposed UI, invalid URL input, or unsupported viewers. Failures generally degrade to original text, `null`, `false`, validation messages, or localized dialogs so the plugin remains usable after malformed input.
- **Threading Boundaries**: Background work goes through `executeOnPooledThread`, UI updates return through `invokeLater(ModalityState.any())`, and document mutations use `WriteCommandAction.runWriteCommandAction` or `runWriteAction`.
- **Diff Loop Prevention**: Diff requests carry user-data markers, and `JsonDiffExtension` keeps document-scoped `DocumentState` in a `WeakHashMap`, combines debounce alarms with self-update guards, and respects large-file warning thresholds before auto-formatting diff editors.
- **Runtime-Selectable Querying**: Query execution switches between Jayway JsonPath and JMESPath from persisted settings, and tab-level search writes results back into the editor using the active formatting state instead of maintaining a separate result buffer.
- **Schema Generation Pipeline**: Schema flows normalize input, resolve `$ref`/`$dynamicRef`/anchors, validate constraints, and surface `JsonSchemaGenerationException` details such as JSON pointers back to localized UI errors.

## 4. Conventions
- **Naming & Packages**: Packages stay under `com.livteam.jsoninja.*`. Types use `PascalCase`, functions/properties use `lowerCamelCase`, booleans prefer `is`/`has`, and numeric settings include units when meaningful, such as `largeFileThresholdMB`.
- **Role Suffixes**: Keep class names explicit about their layer: `*Action`, `*Service`, `*Presenter`, `*View`, `*Dialog`, `*Factory`, `*State`, and `*Configurable` are established patterns throughout the codebase.
- **Callback & Method Shapes**: External UI hooks are usually named `setOn...Callback` or `setOn...Listener`; setup/build helpers use `setup*`, `create*`, and `get*`, while work methods use verb-led names such as `performSearch`, `formatJson`, and `generateFromSchema`.
- **State Modeling**: Persisted selections are stored as enum names inside `JsoninjaSettingsState`, and runtime code converts them back through dedicated enums rather than scattering raw string checks. Small transport objects stay as focused `data class` types.
- **Dialog/Presenter Composition**: `DialogWrapper` shells stay thin and delegate component creation, validation, disposal, and per-mode behavior to presenter/view pairs or tab presenters.
- **Disposal & Document Ownership**: Tab, editor, and query components register cleanup with `Disposer.register`; document listeners are removed on disposal, and temporary per-document flags travel through nearby `userData` keys instead of broad shared mutable state.
- **Localization & Registration**: New user-facing strings belong in `LocalizationBundle*.properties` with dotted namespaces like `dialog.generate.json.*` or `settings.*`. Plugin registrations belong in `META-INF/plugin.xml` or `pro/src/main/resources/META-INF/plugin-pro.xml`, not in hardcoded UI text.
- **Comments & Logging**: Comments are short and selective, often explaining IntelliJ threading, formatting, or lifecycle nuance. Logging uses `logger<T>()`, `thisLogger()`, or `Logger.getInstance(...)` with `debug` for diagnostics, `warn` for recoverable problems, and `error` for hard failures.

## 5. Working Agreements
- Respond in Korean, keep technical terms in English, and do not modify fenced code blocks when translating or documenting.
- Create tests, lint changes, or formatting-only updates only when explicitly requested.
- Build context from related usages, registrations, and existing flows before editing.
- Prefer the simplest change that satisfies the request; avoid unnecessary abstraction.
- Ask for clarification instead of guessing when requirements are ambiguous or behavior changes would be risky.
- Keep edits minimal, preserve public APIs and behavior, and colocate new code near related features.
- Introduce external dependencies only when necessary, and explain why if you add one.

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
