<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JSONinja Changelog

## [1.0.4] - 2025-05-08
### Fixed
- JSON functionality performance improvement [pretty, ugly, escape, unescape]
- Fixed an issue where sort functionality wasn't working during JSON Prettify operation [sort will be added later]
- Fixed an issue where non-JSON data could be deleted when JSON functionality was operating (Added Jackson DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

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
