# AGENTS.md

## Overview
This project provides an IDE plugin that helps users inspect, format, query, and compare JSON within editor workflows. It bundles UI components, services, and resources to support JSON editing, diffing, and generation features.

## Folder Structure
- `src/main/kotlin/com/livteam/jsoninja`: core plugin code.
    - `actions`: IDE actions that trigger JSON operations from menus, shortcuts, and context actions.
    - `diff`: JSON diff viewer extension logic and supporting helpers.
    - `extensions`: IDE extension points like paste preprocessing.
    - `icons`: icon registration and references.
    - `listeners`: lifecycle listeners for application/project activation.
    - `model`: enums and state models for formatting/query/diff.
    - `services`: JSON formatting/query/diff services and data generation helpers.
    - `settings`: persistent settings state and configurable UI wiring.
    - `ui`: tool window, dialogs, presenters/views, and UI models.
        - `component`: presenter/view pairs for editor, query, tabs, and main panel.
        - `dialog`: dialogs and JSON generation flows.
        - `diff`: diff virtual files and request chain helpers.
        - `toolWindow`: tool window factory and registration glue.
    - `utils`: shared JSON helpers and path utilities.
- `src/main/resources`: plugin resources and localization bundles.
    - `META-INF`: plugin manifest and icon metadata.
    - `icons`: SVG assets grouped by theme and size packs.
    - `messages`: localization bundles for UI strings.
- `src/test/kotlin/com/livteam/jsoninja`: tests for actions and services; mirrors main package layout.
- `docs`: development guides and technical documentation.
- `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: build configuration and tooling setup.

## Core Behaviors & Patterns
- Logging uses `logger<T>()`/`thisLogger()` with a `LOG` field; debug/warn/error levels are used to document control flow and failures.
- Guard clauses and early returns handle invalid states (empty JSON, invalid queries, missing project, unsupported viewers).
- JSON operations generally return original input or `null` on failure; exceptions are caught and logged instead of propagated.
- Background work runs on pooled threads; UI updates are marshaled to EDT via `invokeLater`, with debounced formatting using `Alarm`.
- Re-entrancy protection uses document user data keys, atomic flags, and content hashes to skip self-triggered updates.
- Services are accessed via the IntelliJ service container (`@Service`, `project.service`, `project.getService`).

## Conventions
- Package names are lower-case (`com.livteam.jsoninja.*`); classes are PascalCase.
- Actions end with `Action`; services end with `Service`; settings classes use `SettingsState`/`Configurable` suffixes.
- UI layers follow `*Presenter`/`*View` naming; UI state uses `*UiState`; dialog models live under `ui/dialog/.../model`.
- Constants live in `companion object` as `const val`; loggers are named `LOG`.
- KDoc-style comments are common; comments are mostly Korean with occasional English for API semantics.
- Functions and properties use lowerCamelCase; enums/data classes are grouped in `model`.

## Working Agreements
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