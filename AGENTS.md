# AGENTS.md

## 1. Overview
This project is a JetBrains IDE plugin focused on JSON editing workflows such as formatting, querying, diffing, and generation. The codebase combines IntelliJ action wiring, service-layer JSON logic, and tool-window UI composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: main plugin implementation.
    - `actions`: `AnAction` entry points for menu/context/shortcut-triggered JSON operations.
    - `diff`: diff viewer extension logic, document guards, and formatting orchestration.
    - `extensions`: IDE extension points such as paste preprocessing.
    - `icons`: runtime icon selection and icon pack helpers.
    - `listeners`: application/project lifecycle listeners.
    - `model`: enums and shared state models for formatting/query/diff behavior.
    - `services`: core JSON business logic (formatting, querying, diff preparation, random generation).
    - `settings`: persistent settings state and configurable UI binding.
    - `ui`: tool-window and dialog UI layers.
        - `component`: presenter/view pairs for editor, tabs, query panel, and main panel.
        - `dialog`: JSON generation and warning dialogs.
        - `diff`: diff virtual files and request chain helpers.
        - `toolWindow`: tool-window factory and registration glue.
    - `utils`: shared helpers (tool-window lookups, JSON path extraction, etc.).
- `src/main/resources`: plugin descriptors and resources.
    - `META-INF`: `plugin.xml` and plugin metadata.
    - `icons`: SVG assets for classic/expui variants and multiple sizes.
    - `messages`: localization bundles (`en`, `ko`, `ja`, `zh_CN`, default).
- `src/test/kotlin/com/livteam/jsoninja`: test sources that mirror main package concerns when tests are added.
- `docs`: contributor-oriented architecture and development guides.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: build and toolchain configuration.
- `pro`: separate nested workspace for pro plugin artifacts/sources; keep root plugin changes isolated unless explicitly requested.

## 3. Core Behaviors & Patterns
- Logging is centralized through `logger<T>()` or `thisLogger()` with `LOG` fields and level-based diagnostics (`debug`/`warn`/`error`).
- Guard clauses are pervasive (`?: return`, early `return null`) to exit fast on invalid project/editor/query/document states.
- Service methods favor safe fallbacks: invalid JSON/query inputs typically return original content, `null`, or `false` instead of throwing upward.
- Background-heavy work runs on pooled threads (`executeOnPooledThread`), while UI mutations are marshaled back to EDT (`invokeLater`, `WriteCommandAction`, `runWriteAction`).
- Diff and editor update flows use re-entrancy guards (`putUserData` keys, atomic flags, content hashes, debounce alarms) to avoid self-triggered loops.
- IntelliJ service container access is standard (`@Service`, `project.service<T>()`, `project.getService(...)`, `service<T>()`).

## 4. Conventions
- Package naming follows `com.livteam.jsoninja.*`; type names use PascalCase and members use lowerCamelCase.
- Role-based suffixes are consistent: `*Action`, `*Service`, `*Presenter`, `*View`, `*SettingsState`, `*Configurable`.
- Constants are usually declared in `companion object` as `const val`; logger property name is typically `LOG`.
- Domain options are modeled with `enum class`; UI/state carriers use `data class` under `model` or UI model packages.
- Comments are concise and predominantly Korean, with selective English for API/interop context.
- String resources and user-facing text are managed through localization bundles rather than hardcoded UI literals where feasible.

## 5. Working Agreements
- Respond in Korean (keep tech terms in English, never translate code blocks)
- Create tests/lint only when explicitly requested
- Build context by reviewing related usages and patterns before editing
- Prefer simple solutions; avoid unnecessary abstraction
- Ask for clarification when requirements are ambiguous
- Minimal changes; preserve public APIs
- New functions/modules: single-purpose, colocated with related code
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