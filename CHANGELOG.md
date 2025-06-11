<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JSONinja Changelog

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
