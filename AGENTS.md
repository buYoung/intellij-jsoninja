# JSON Ninja – Contributor Guide

## Overview
- JetBrains IDE plugin providing JSON formatting, escaping, diffing, querying (Jayway JsonPath/JMESPath), multi-tab editing, and paste preprocessing with optional key sorting and compact arrays.

## Structure
- `src/main/kotlin/com/livteam/jsoninja`: core code.
  - `actions`: UI actions wiring toolbar/menu to panel methods.
  - `services`: JSON formatting, diff, query, object mapper, settings/state helpers, random data generator.
  - `ui`: tool window factory, panels, tabbed editors, dialogs, diff view scaffolding, query components.
  - `extensions`: paste preprocessor for auto-format on paste.
  - `model`: enums for format state, diff mode, query type.
  - `utils/util`: shared helpers (JSON paths, action utils, icons).
  - `settings`: persistent state + configurable UI.
- `src/main/resources`: `plugin.xml`, message bundles, icons, JSON icon map.
- `docs`: development/structure/spec/release guides; align changes with these when relevant.
- `src/test/kotlin`: present but empty; mirror main packages if tests are added.

## Core Behaviors & Patterns
- **Formatting/escape**: `JsonFormatterService` uses shared `JsonObjectMapperService`; supports prettify/uglify, sorted keys, compact arrays, escape/unescape (including beautified JSON), validation, fully-unescape loops, and indent/key-sorting settings from `JsoninjaSettingsState`. Uses cached pretty printers keyed by indent + compact mode.
- **State**: `JsoninjaSettingsState` persists indent, sortKeys, default/paste format state, diff mode, query type, large-file warning settings. `JsonHelperService` stores current format state as enum name.
- **Queries**: `JsonQueryService` switches between Jayway JsonPath and JMESPath runtimes based on settings; validates expressions and returns results serialized via shared mapper.
- **Diff**: `JsonDiffService` validates + formats both sides (semantic option sorts keys) and builds `SimpleDiffRequest` tagged with `JsonDiffKeys`.
- **UI flow**: `JsoninjaToolWindowFactory` creates `JsonHelperPanel` with toolbar actions; `JsonHelperTabbedPane` manages numbered JSON tabs plus “+” tab, disposes per-tab resources, prevents closing last JSON tab. `JsonEditor` wraps `EditorTextField`, installs context menu (copy/paste + copy query), applies JSON syntax highlighting, tracks original JSON, emits content-change callbacks, shows JsonPath/JMESPath tooltip on modifier hover. JMESPath component stores original JSON before searches and formats results through current format state.
- **Paste handling**: `JsoninjaPastePreProcessor` detects JSONinja editors via `JSONINJA_EDITOR_KEY`, validates JSON, formats on paste using `pasteFormatState`; falls back safely for invalid/large content (background with progress over threshold).
- **Actions**: Thin `AnAction` classes delegate to panel helpers; `JsonHelperActionUtils.getPanel` gatekeeps availability. Diff actions choose view mode; copy action uses helper to derive query path.
- **Utilities**: `JsonPathHelper` builds JsonPath/JMESPath strings from PSI elements (quoted when needed). Icons centralized in `JsoninjaIcons`.
- **Localization**: Messages via `LocalizationBundle` with resource bundle under `messages/`; keep keys aligned when adding UI strings.

## Conventions
- **Naming**: Classes PascalCase, functions/vars camelCase, enums upper snake in settings; constants `UPPER_SNAKE_CASE`. Tab titles prefixed `JSON `; “+” tab named `addNewTab`.
- **Logging**: IntelliJ `logger<T>()`; debug for validation failures, warn for formatting/query errors; avoid noisy info logs.
- **Control flow**: Guard clauses for empty/invalid JSON; prefer service helpers over direct mapper use; use `WriteCommandAction`/`runWriteAction` for document edits; `invokeLater` for UI-safe tab operations.
- **Comments**: Brief, often Korean explanations; TODO-style notes inline rather than separate list.
- **Settings usage**: Read via `JsoninjaSettingsState.getInstance(project)`; store enums as string names; update format state through `JsonHelperService`.

## When Extending
- Register new actions/components in `plugin.xml` and align icons/messages.
- Co-locate new features with existing package patterns (service + action + UI wiring).
- Keep `JsonEditor`/tab lifecycle consistent: dispose resources via `Disposer`, preserve `JSONINJA_EDITOR_KEY`, respect large-file warning thresholds.
- For formatting changes, consider cache keys and `JsonFormatState` semantics (sorting, compact arrays, uglify override).
- For query-related features, handle both Jayway and JMESPath or gate by setting.

## Working Agreements
- Respond to users in Korean unless they request another language; keep domain terms in English, leave fenced code blocks untouched.
- Do not add tests or lint/format tasks unless the user explicitly asks for them.
- Build minimal context before edits (find related usages/flows); prefer simple, minimal changes; avoid new deps unless necessary and justify if added.
- Preserve behavior/public APIs unless requested; call out any behavior changes. Keep new functions small and near related code.
- If requirements are unclear, ask for clarification instead of guessing.
- This operation must comply with the threading and write rules described in the "Threading & Write Rules" section.

## Threading & Write Rules

**Write Only**: `runWriteAction { }`
**Write + Undo**: `WriteCommandAction.runWriteCommandAction(project) { }`
**Write, No Undo**: `invokeLater { }`  
**Background → Write + Undo**: `executeOnPooledThread { compute(); WriteCommandAction.runWriteCommandAction(project) { } }`
**Background → Write, No Undo**: `executeOnPooledThread { compute(); invokeLater { } }`
**Background Only**: `executeOnPooledThread { compute() }`

### ModalityState (for invokeLater)

**Default (modal-aware)**: `invokeLater { }` or `invokeLater(ModalityState.defaultModalityState()) { }`
**Ignore modals**: `invokeLater(ModalityState.NON_MODAL) { }`
**Always run**: `invokeLater(ModalityState.any()) { }`
**Current context**: `invokeLater(ModalityState.current()) { }`
