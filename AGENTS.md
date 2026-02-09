# AGENTS.md

## 1. Overview
This repository implements a JetBrains IDE plugin for JSON-centric editing workflows such as formatting, querying, diffing, and sample generation. The architecture separates IDE integration points, JSON domain services, and tool-window UI composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: main plugin implementation.
    - `actions`: `AnAction` entry points for menu/context/shortcut-triggered JSON operations.
    - `diff`: diff extension and synchronization guards around diff editor behavior.
    - `extensions`: IDE extension points such as paste preprocessing.
    - `icons`: runtime icon selection logic and icon pack helpers.
    - `listeners`: application and project lifecycle listeners.
    - `model`: enums and shared state models for formatter/query/diff options.
    - `onboarding`: onboarding domain models and flow state.
    - `services`: JSON business logic (formatting, querying, diff preparation, random data generation).
    - `settings`: persistent settings state and configurable binding.
    - `ui`: tool-window, dialog, onboarding, and diff UI composition.
        - `component`: presenter/view pairs for editors, tabs, query panel, and main panel.
        - `dialog`: JSON generation and warning dialogs.
        - `diff`: diff virtual files and request-chain helpers.
        - `onboarding`: onboarding views and presentation wiring.
        - `toolWindow`: tool-window factory and registration glue.
    - `utils`: shared helpers (tool-window lookup, JSON path extraction, editor helpers).
- `src/main/resources`: plugin descriptors and resources.
    - `META-INF`: `plugin.xml` and plugin metadata.
    - `icons`: SVG assets for classic/expui variants and `v2` icon packs.
    - `messages`: localization bundles (`en`, `ko`, `ja`, `zh_CN`, default).
- `src/test/kotlin/com/livteam/jsoninja`: test sources; mirror main package boundaries when tests are added.
- `docs`: contributor-facing architecture and development guides.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Gradle wrapper/toolchain/build configuration.

## 3. Core Behaviors & Patterns
- Logging follows `logger<T>()` or `thisLogger()` with `LOG` fields and level-based diagnostics (`debug`, `warn`, `error`).
- Guard clauses are pervasive (`?: return`, early `return null`) to exit quickly on invalid project/editor/query/document state.
- Services prefer safe fallbacks: invalid JSON/query input usually returns original content, `null`, or `false` instead of propagating exceptions.
- Background work runs on pooled threads (`executeOnPooledThread`), while UI/document mutations are marshaled to EDT (`invokeLater`, `WriteCommandAction`, `runWriteAction`).
- Diff/editor synchronization uses re-entrancy guards (`putUserData` keys, atomic flags, content hashes, alarms) to avoid self-triggered update loops.
- Query flow supports both Jayway JsonPath and JMESPath, selected by settings-level query type state.

## 4. Conventions
- Package naming follows `com.livteam.jsoninja.*`; type names use PascalCase and members use lowerCamelCase.
- Role-based suffixes are consistent: `*Action`, `*Service`, `*Presenter`, `*View`, `*SettingsState`, `*Configurable`.
- Constants are commonly placed in `companion object` as `const val`; logger property name is typically `LOG`.
- Domain options are modeled with `enum class`; shared UI/state payloads use `data class`.
- Comments are concise and predominantly Korean, with selective English for API and interop context.
- User-facing strings should come from localization bundles rather than hardcoded literals when practical.

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