<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JSONinja Changelog

## [1.2.1] - 2025-07-16

### Fixed

* Fixed the JSON tool window opening in the wrong way

## [1.2.0] - 2025-07-16

### Added

* **Improved JSON Diff feature** - New UI for direct comparison within tabs.
  * Displays JSON diff in a tab instead of a dialog.
  * Supports keyboard shortcuts (Close Tab: `Ctrl/Cmd+W`, Close Diff Window: `Shift+Escape`).
  * Added actions to manage diff tabs (`CloseDiffTabAction`, `CloseDiffWindowAction`, `CloseTabAction`).

### Changed

* **Complete overhaul of the JSON Diff UI/UX.**
  * Refactored `JsonDiffDialog` into `JsonDiffPanel`.
  * Improved usability with a tab-based interface.
  * Moved keyboard shortcut handling to the IntelliJ Action system.
* **Improved JSON parsing error handling.**
  * Provides better error handling and user feedback.
  * Enhanced code quality.

### Fixed

* Fixed JSON parsing errors.
* Improved stability by refining the JSON Editor's UI structure.
* Corrected the `PRETTIFY_SORTED` option in the JSON Formatter service to ensure it functions as intended.

## [1.1.0] - 2025-06-12

### Added
- JsonDiff Feature - New JSON comparison functionality 
  - 3-way diff support for comprehensive JSON comparison 
  - UI consistent with JetBrains' native Diff UI for familiar user experience 
  - Quick action buttons for streamlined workflow 
  - Auto-copy active JsonEditor content to left diff pane for easy comparison

### Features
- 3-way Diff: Compare JSON files with base, left, and right panels
- Native UI Integration: Seamless integration with JetBrains Diff UI components
- Quick Actions: Convenient action buttons for common diff operations
- Smart Content Transfer: Automatically copies active JsonEditor content to left diff panel

## [1.0.6] - 2025-06-11

### Fixed

- Fixed Json Document not being activated after JsonEditor refactoring
- Fixed Json Highlighter not working properly in the refactored JsonEditor

### Changed

- Added support for IntelliJ IDEA 2025.2

## [1.0.5] - 2025-05-26

### Fixed

- Re-engineered the JSON editor UI to improve stability and usability
    - Removed the redundant `JBScrollPane` wrapper to eliminate nested-scroll conflicts
    - Switched the underlying component from **`LanguageTextField`** to **`EditorTextField`** for full IDE editor
      capabilities
    - Restored native Find action support (`Ctrl` + `F` / `Cmd` + `F`) inside the JSON editor

## [1.0.4] - 2025-05-08

### Fixed

- JSON functionality performance improvement [pretty, ugly, escape, unescape]
- Fixed an issue where sort functionality wasn't working during JSON Prettify operation [sort will be added later]
- Fixed an issue where non-JSON data could be deleted when JSON functionality was operating (Added Jackson
  DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

## [1.0.3] - 2025-04-25

### Added

- Change to enforce json pretty on paste
- {G} icon, a popup window will open, and you can now create Json random data.

## [1.0.2] - 2025-03-21

### Fixed

- Support for IntelliJ 2025.1

## [Unreleased]

### Added

- Multi-tab interface - Work with multiple JSON documents simultaneously
- JSON Prettify - Formatting for improved JSON data readability
- JSON Uglify - Single-line compression functionality for JSON data
- JSON Escape/Unescape - Process and restore JSON string escaping
- JMES Path support - Search and filter specific values within complex JSON data
- Advanced formatting options - Custom settings including indentation size, key sorting, etc. [WIP]
- Tab management functions - Including tab addition, deletion, and event handling
- User-friendly interface - Intuitive and efficient UI design
