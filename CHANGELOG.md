<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JSONinja Changelog

## [Unreleased]

### Added

- **Improved JSON Diff feature** (v1.2.0)
  - JSON differences are now displayed in a tab instead of a dialog box.
  - Supports keyboard shortcuts for closing the tab (`Ctrl/Cmd+W`) and the diff window (`Shift+Escape`).

### Changed

- **Complete overhaul of the JSON Diff UI/UX** (v1.2.0)
  - Switched to a more intuitive tab-based interface for improved usability.
- **Improved JSON parsing error handling** (v1.2.0)
  - Provides clearer feedback and more helpful messages when a JSON file is invalid.

### Fixed

- Fixed errors that occurred when parsing certain JSON files. (v1.2.0)
- Improved the overall stability of the JSON Editor. (v1.2.0)
- Fixed the JSON diff tool window opening in the wrong way (v1.2.1)

## [1.2.1] - 2025-07-16

### Fixed

- Fixed the JSON diff tool window opening in the wrong way

## [1.2.0] - 2025-07-16

### Added

- **Improved JSON Diff feature**
  - JSON differences are now displayed in a tab instead of a dialog box.
  - Supports keyboard shortcuts for closing the tab (`Ctrl/Cmd+W`) and the diff window (`Shift+Escape`).

### Changed

- **Complete overhaul of the JSON Diff UI/UX**
  - Switched to a more intuitive tab-based interface for improved usability.
- **Improved JSON parsing error handling**
  - Provides clearer feedback and more helpful messages when a JSON file is invalid.

### Fixed

- Fixed errors that occurred when parsing certain JSON files.
- Improved the overall stability of the JSON Editor.

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

[Unreleased]: https://github.com/buYoung/intellij-jsoninja/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/buYoung/intellij-jsoninja/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/buYoung/intellij-jsoninja/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/buYoung/intellij-jsoninja/compare/1.0.6...1.1.0
[1.0.6]: https://github.com/buYoung/intellij-jsoninja/compare/1.0.5...1.0.6
[1.0.5]: https://github.com/buYoung/intellij-jsoninja/compare/1.0.4...1.0.5
[1.0.4]: https://github.com/buYoung/intellij-jsoninja/compare/1.0.3...1.0.4
[1.0.3]: https://github.com/buYoung/intellij-jsoninja/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/buYoung/intellij-jsoninja/commits/1.0.2
