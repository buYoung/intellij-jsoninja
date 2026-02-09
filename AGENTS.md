# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin focused on JSON editing workflows such as formatting, querying, diffing, and sample generation. The architecture separates IDE integration wiring, JSON domain services, and tool-window UI composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: core plugin source code.
    - `actions`: `AnAction` entry points for menu/context/shortcut-triggered JSON commands.
    - `diff`: diff extension behavior and sync guards to prevent recursive updates.
    - `extensions`: IntelliJ extension points such as paste preprocessing.
    - `icons`: runtime icon pack selection and icon mapping helpers.
    - `listeners`: app/project startup and lifecycle listeners.
    - `model`: shared enums and state models for format/query/diff options.
    - `onboarding`: onboarding state and flow-related domain types.
    - `services`: JSON business logic (format/query/diff preparation/random generation/onboarding orchestration).
    - `settings`: persistent settings state and `Configurable` integration.
    - `ui`: tool-window and dialog composition.
        - `component`: presenter/view pairs for editors, tabs, query, and main panel state.
        - `dialog`: warning and JSON generation dialogs.
        - `diff`: diff virtual files and request-chain helpers.
        - `onboarding`: onboarding UI and step presentation.
        - `toolWindow`: tool-window factory and registration glue.
    - `utils`: shared editor/tool-window/path helpers.
- `src/main/resources`: plugin metadata and static assets.
    - `META-INF`: `plugin.xml` action/extension registration.
    - `icons`: classic/expui SVG packs with `v2` variants.
    - `images`: onboarding and UI image assets.
    - `messages`: localization bundles (`en`, `ko`, `ja`, `zh_CN`, default).
- `src/test/kotlin/com/livteam/jsoninja`: test sources; keep package layout aligned with main code when adding tests.
- `docs`: contributor-facing development and structure guides.
- `.github/workflows`: CI workflows for plugin build and release checks.
- `tasks`, `todos`: project notes and backlog-style markdown documents.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Gradle wrapper/toolchain/build configuration.

## 3. Core Behaviors & Patterns
- Logging follows `logger<T>()` or `thisLogger()` with `LOG` fields and level-based diagnostics (`debug`, `warn`, `error`).
- Guard clauses are pervasive (`?: return`, early `return null`) to exit quickly on invalid project/editor/query/document state.
- Services favor safe fallbacks: invalid JSON/query input typically returns original content, `null`, or `false` rather than surfacing hard failures.
- Background work runs on pooled threads (`executeOnPooledThread`), while UI/document mutation is marshaled to EDT (`invokeLater`, `WriteCommandAction`, `runWriteAction`).
- Diff/editor synchronization uses re-entrancy guards (`putUserData` keys, atomic flags, content hashes) to avoid self-triggered update loops.
- Query flow supports both Jayway JsonPath and JMESPath, selected by settings-level query type state.

## 4. Conventions
- Package naming follows `com.livteam.jsoninja.*`; type names use PascalCase and members use lowerCamelCase.
- Role-based suffixes are consistent: `*Action`, `*Service`, `*Presenter`, `*View`, `*SettingsState`, `*Configurable`.
- Constants are commonly placed in `companion object` as `const val`; logger fields are typically named `LOG`.
- Domain options are modeled with `enum class`; shared UI/state payloads use `data class`.
- Comments are concise and predominantly Korean, with selective English for API and interop context.
- User-facing text should come from localization bundles (`messages.LocalizationBundle`) instead of hardcoded literals when practical.

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