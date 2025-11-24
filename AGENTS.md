# AGENTS Guide
## Overview
- JSON Ninja is a JetBrains IDE plugin for JSON formatting, diffing, escaping/unescaping, querying (JMESPath/JsonPath), random generation, and multi-tab editing via a tool window.
- Kotlin-based; uses IntelliJ Platform services/actions plus Jackson for JSON handling and JsonPath/JMESPath for queries.

## Structure
- src/main/kotlin/com/livteam/jsoninja: core plugin code.
  - actions: IDE actions for prettify/uglify/escape/unescape, diff views, tab control, copy query, generator; guarded by context checks.
  - services: formatter, diff builder, query engine, object mapper, project helper, random JSON generator; settings via JsoninjaSettingsState.
  - ui/component: JsonHelperPanel with JsonHelperTabbedPane/JsonEditor/JmesPathComponent, toolbar setup, WriteCommandAction for editor mutations.
  - ui/diff: JsonDiffRequestChain, JsonDiffVirtualFile; works with JsonDiffExtension and JsonDiffService.
  - extensions: JsoninjaPastePreProcessor auto-formats valid JSON on paste with size-based background processing.
  - util/utils: JsonPathHelper builds JsonPath/JMESPath from PSI; JsonHelperUtils fetches active tab JSON.
  - model: enums for format state, diff display mode, query type controlling behavior.
  - settings: configurable UI for indent/sort/diff mode/query type/large file warnings.
  - icons, LocalizationBundle for messages.
- src/main/resources/META-INF: plugin.xml registers tool window, actions, listeners, diff extension, copy/paste preprocessor, icon mappings; localization bundle.
- docs/: development guide, project structure, technical spec, version update notes; prd/ and todos/ hold plans/notes.
- pro/: premium module scaffold with plugin-pro.xml (no Kotlin sources yet).
- src/test/...: placeholders for future tests.

## Conventions & patterns
- Logging uses IntelliJ `logger<T>()` with local `LOG`; debug for flow, warn for recoverable issues, error for critical failures.
- Naming: PascalCase classes, camelCase methods/vars, UPPER_SNAKE_CASE constants; enums for modes; comments mostly in Korean explaining intent.
- Control flow favors early returns for null/blank checks; invalid JSON returns original text; services validate before formatting and handle failures gracefully.
- Threading/UI: editor changes via `WriteCommandAction`/`runWriteAction`; background work on pooled threads; UI updates with `invokeLater`; disposables registered to parents.
- State/config: project-level `@Service` classes with `service<T>()`/`project.getService`; JsoninjaSettingsState persisted via `@State`, storing enum names as strings.
- JSON formatting: JsonFormatterService caches CustomPrettyPrinter by indent/compact arrays; toggles sorted vs unsorted ObjectMappers; `isValidJson` checks trailing tokens; escape/unescape supports beautified JSON and multi-escape patterns.
- Diff handling: JsonDiffExtension detects JSON with heuristics plus validation, warns on large files, debounces via Alarm, tracks document state in a synchronized WeakHashMap, guards against self-triggered updates.
- Querying: JsonQueryService chooses Jayway JsonPath or JMESPath per setting; suppresses exceptions and returns null on invalid expressions.
- UI behavior: JsonHelperTabbedPane manages + tab creation/closure with listeners and disposables; JsonEditor installs context menu actions and modifier-triggered query tooltips.
- TODOs: GenerateRandomJsonAction notes RandomJsonDataCreator should consume dialog config.

## Working agreements
- Respond in Korean unless the user asks otherwise; keep domain technical terms in English; do not alter fenced code blocks.
- Do not add tests or lint/format tasks unless explicitly requested.
- Prefer simple, minimal changes aligned to user asks; seek clarification when requirements are unclear; avoid unsolicited features or refactors.
- Preserve public APIs/behaviors; highlight any required behavior change.
- Keep new functions/modules small and colocated; avoid new dependencies unless necessary and explain why.
