# AGENTS.md

## 1. Overview
This repository implements JSONinja, a JetBrains IDE plugin for JSON formatting, querying, diffing, and data generation workflows. The codebase is organized around IntelliJ integration points, JSON domain services, and tool-window presenter/view composition.

## 2. Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: core plugin implementation.
    - `actions`: IDE action entry points that trigger JSON format/query/diff/generation flows.
    - `icons`: icon loading and icon-pack bindings used by actions and tool windows.
    - `diff`: diff extension and synchronization guards for editor-tab/window diff updates.
    - `extensions`: IntelliJ extension point hooks (for example paste preprocessing).
    - `listeners`: startup and lifecycle listeners.
    - `model`: shared enums/state models for formatting, query type, icon pack, and diff mode.
    - `services`: project/application services for JSON formatting/querying/diffing/onboarding.
        - `schema`: JSON Schema normalization, validation, and schema-driven data generation.
    - `settings`: persistent plugin settings state and `Configurable` UI wiring.
    - `ui`: tool-window UI composition.
        - `component`: presenter/view pairs for editors, tabs, query UI, and main panel (`editor`, `jsonQuery`, `main`, `model`, `tab`).
        - `dialog`: large-file warning and JSON generation dialogs (`generateJson`).
        - `diff`: diff request chain and virtual file glue.
        - `onboarding`: onboarding dialogs, step presenters, and tooltip controllers.
        - `toolWindow`: tool-window factory registration.
    - `utils`: shared helpers for editor/tool-window/path operations.
    - `dev`, `events`: reserved package roots currently kept for future expansion.
- `src/main/resources`: plugin metadata and assets.
    - `META-INF`: `plugin.xml` and plugin icon metadata.
    - `icons`: `classic` and `expui` icon packs with `v2` variants.
    - `images/onboarding`: onboarding GIF assets.
    - `messages`: localization bundles (`default`, `en`, `ko`, `ja`, `zh_CN`).
- `src/test/kotlin/com/livteam/jsoninja`: JUnit tests for actions and core services (formatter, query, diff, random data generation).
- `docs`: contributor documentation such as development and structure guides.
- `prd`, `todos`: product notes and backlog-style markdown documents.
- `.github/workflows`: CI workflows for build, release, and UI test automation.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Gradle toolchain and plugin build configuration.

## 3. Core Behaviors & Patterns
- Logging follows `logger<T>()` or `thisLogger()` with `LOG` fields and level-based diagnostics (`debug`, `warn`, `error`).
- Guard clauses are pervasive (`?: return`, early `return null`) to exit quickly on invalid project/editor/query/document state.
- Services favor safe fallbacks: invalid JSON/query/schema states typically return original content, `null`, or `false` instead of throwing UI-facing failures.
- Background work runs on pooled threads (`executeOnPooledThread`), while UI/document mutation is marshaled to EDT (`invokeLater`, `WriteCommandAction`, `runWriteAction`).
- Diff/editor synchronization uses re-entrancy guards (`putUserData` keys, atomic flags, content hashes) to avoid self-triggered update loops.
- Query flow supports both Jayway JsonPath and JMESPath, with runtime selection from persisted settings and editor-context query copy actions.
- JSON Schema flow is separated into normalization, constraint modeling, validation, and sample generation services for deterministic schema-based output.
- User-facing text and action labels are localized through bundle keys, keeping UI wording out of business logic.

## 4. Conventions
- Package naming follows `com.livteam.jsoninja.*`; type names use PascalCase and members use lowerCamelCase.
- Role-based suffixes are consistent: `*Action`, `*Service`, `*Presenter`, `*View`, `*SettingsState`, `*Configurable`.
- Constants are commonly placed in `companion object` as `const val`; logger fields are typically named `LOG`.
- Domain options are modeled with `enum class`; shared UI/state payloads use `data class`.
- UI composition favors Presenter/View splits in `ui/component/*` and `ui/dialog/*`, with state carried through focused model objects.
- Comments are concise and predominantly Korean, with selective English for API and interop context.
- User-facing text should come from `LocalizationBundle.message(...)` and `messages/LocalizationBundle*.properties` instead of hardcoded literals.

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
