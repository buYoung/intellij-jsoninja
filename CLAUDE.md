# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSON Ninja (JSONinja) is an IntelliJ IDEA plugin for JSON processing. It's built using:
- Kotlin
- IntelliJ Platform SDK
- Gradle with IntelliJ Platform Gradle Plugin
- Jackson for JSON processing (jackson-databind, jackson-module-kotlin)
- JMESPath for JSON querying
- JsonPath library
- DataFaker for random data generation

Repository: https://github.com/buYoung/intellij-jsoninja

## Essential Commands

### Build and Run
```bash
# Run the plugin in a sandboxed IntelliJ instance
./gradlew runIde

# Build the plugin distribution
./gradlew buildPlugin

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.livteam.jsoninja.services.JsonFormatterServiceTest"

# Clean build
./gradlew clean build
```

### Development Commands
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Generate coverage report
./gradlew koverReport

# Verify plugin compatibility
./gradlew verifyPlugin

# Patch changelog before release
./gradlew patchChangelog
```

## Architecture Overview

### Plugin Entry Points
- **Tool Window**: `JsoninjaToolWindowFactory` creates the main UI panel
- **Actions**: All user actions are in `actions/` package:
  - `PrettifyJsonAction` - Pretty print JSON
  - `UglifyJsonAction` - Minify JSON
  - `EscapeJsonAction` - Escape JSON strings
  - `UnescapeJsonAction` - Unescape JSON strings
  - `GenerateRandomJsonAction` - Generate random JSON data
  - `AddTabAction` - Add new tabs to the tool window
  - `OpenJsonFileAction` - Open JSON files from file system
  - `OpenSettingsAction` - Open plugin settings dialog
- **Settings**: `JsoninjaSettingsConfigurable` provides project-level configuration
- **Listeners**: `JsonHelperActivationListener` handles application activation and dynamic plugin events

### Core Services Architecture
The plugin uses IntelliJ's service pattern:
- `JsonHelperService` - Main service managing JSON format state per project
- `JsonFormatterService` - Handles all JSON transformation operations
- `JmesPathService` - Processes JMESPath queries
- `RandomJsonDataCreator` - Generates random JSON data using DataFaker
- `JsonHelperProjectService` - Project-level service for additional functionality

### Model
- `JsonFormatState` - Enum defining JSON formatting modes:
  - `PRETTIFY` - Standard pretty printing with indentation
  - `UGLIFY` - Minified JSON (no whitespace)
  - `PRETTIFY_SORTED` - Pretty print with keys sorted alphabetically
  - `PRETTIFY_COMPACT` - Pretty print with arrays kept on single lines

### UI Component Hierarchy
```
JsoninjaToolWindowFactory
└── JsonHelperPanel
    ├── JsonHelperActionBar (toolbar)
    └── JsonHelperTabbedPane
        └── JsonEditor (custom editor with paste handling)
            └── JmesPathComponent (query interface)
```

### Key Implementation Details

1. **JSON Editor**: Uses IntelliJ's `EditorTextField` with `JsonFileType` for syntax highlighting
2. **Auto-formatting on Paste**: Implemented via custom `PasteProvider` in `JsonEditor`
3. **Internationalization**: Messages in `LocalizationBundle.properties` (supports English, Korean)
4. **Settings Persistence**: Project-level settings stored via `JsoninjaSettingsState`
5. **Action Icons**: Custom SVG icons with dark/light theme variants in `resources/icons/`

### Testing Approach
- Tests extend `BasePlatformTestCase` for IntelliJ platform testing
- Service tests are in `src/test/kotlin/com/livteam/jsoninja/services/`
- Each service has comprehensive unit tests

### Plugin Configuration
- Plugin ID: `com.livteam.jsoninja`
- Plugin Name: `JSONinja`
- Current Version: `1.0.5`
- Min IDE version: 2024.3 (build 243)
- Max IDE version: 2025.1.* (build 251.*)
- Dependencies: `com.intellij.modules.json`

### Package Structure
All code (both main and test) is under the `com.livteam.jsoninja` package.

## Important Notes

### Development Process
- **IMPORTANT**: When asked to implement features or fix issues, always explain your plan first and wait for confirmation before writing code
- Explicitly describe what changes you'll make and why
- Only proceed with implementation after the user confirms the plan looks good

### Code Style
- Follow existing patterns in neighboring files
- Check imports and existing libraries before adding new dependencies
- Prefer editing existing files over creating new ones