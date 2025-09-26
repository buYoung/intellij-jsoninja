# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

JSON Ninja (JSONinja) is a powerful IntelliJ IDEA plugin for JSON processing built with Kotlin and the IntelliJ Platform SDK. The plugin provides JSON formatting, validation, querying with JMESPath, random data generation, and diff capabilities within a multi-tab interface.

**Plugin Details:**
- ID: `com.livteam.jsoninja`
- Current Version: 1.3.0
- Min IDE Build: 243 (2024.3)
- Max IDE Build: 252.* (2025.1.*)
- JVM Target: Java 17
- Kotlin Version: 2.1.21

## Essential Development Commands

### Build & Run
```bash
# Run plugin in sandbox IDE (primary development command)
./gradlew runIde

# Build plugin distribution ZIP
./gradlew buildPlugin

# Clean and rebuild everything
./gradlew clean build

# Verify plugin compatibility with target IDEs
./gradlew verifyPlugin

# List available IntelliJ Platform releases
./gradlew printProductsReleases
```

### Testing & Quality
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.livteam.jsoninja.services.JsonFormatterServiceTest"

# Run all verification tasks (tests + static analysis)
./gradlew check

# Generate code coverage reports
./gradlew koverHtmlReport

# Generate searchable options index
./gradlew buildSearchableOptions
```

### Development Workflow
```bash
# Patch plugin.xml with version and changelog
./gradlew patchChangelog

# Instrument code for IDE compatibility
./gradlew instrumentCode

# Prepare sandbox environment
./gradlew prepareSandbox

# Run UI tests (if implemented)
./gradlew runIdeForUiTests
```

### Publishing
```bash
# Sign plugin (requires CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD env vars)
./gradlew signPlugin

# Publish to JetBrains Marketplace (requires PUBLISH_TOKEN env var)
./gradlew publishPlugin
```

## Architecture Overview

### Service Layer Architecture
The plugin uses IntelliJ's service pattern with project-scoped services:

- **`JsonFormatterService`** - Core JSON transformation engine supporting 4 format states:
  - `PRETTIFY` - Standard pretty printing with configurable indentation
  - `UGLIFY` - Minified single-line JSON
  - `PRETTIFY_SORTED` - Pretty print with alphabetically sorted keys
  - `PRETTIFY_COMPACT` - Pretty print with compact arrays
- **`JmesPathService`** - JMES Path query processor for JSON filtering
- **`JsonDiffService`** - JSON comparison and diff visualization
- **`RandomJsonDataCreator`** - Generates random JSON using DataFaker library
- **`JsonHelperService`** - Main project service managing format state

### UI Component Hierarchy
```
JsoninjaToolWindowFactory (Tool Window Entry Point)
└── JsonHelperPanel (Main Panel)
    ├── JsonHelperActionBar (Toolbar with format actions)
    └── JsonHelperTabbedPane (Multi-tab JSON editors)
        └── JsonEditor (Custom editor with syntax highlighting)
            └── JmesPathComponent (Query interface)
```

### Action System
All user actions inherit from IntelliJ's AnAction and are organized in:
- **Core Actions**: `PrettifyJsonAction`, `UglifyJsonAction`, `EscapeJsonAction`, `UnescapeJsonAction`
- **Utility Actions**: `GenerateRandomJsonAction`, `AddTabAction`, `OpenJsonFileAction`
- **Diff Actions**: `ShowJsonDiffAction`, `ShowJsonDiffInEditorTabAction`, `ShowJsonDiffInWindowAction`

### Key Implementation Details
- **JSON Processing**: Jackson ObjectMapper with custom PrettyPrinter configurations cached per format state
- **Editor Integration**: Uses `EditorTextField` with `JsonFileType` for syntax highlighting and auto-formatting on paste
- **Settings Persistence**: Project-level settings via `JsoninjaSettingsState` with configurable indentation and key sorting
- **Internationalization**: Message bundles supporting English and Korean via `LocalizationBundle`

### Data Flow
1. User action triggers via toolbar or keyboard shortcut
2. Action retrieves current JSON text from active editor tab  
3. Service layer processes JSON based on format state and user settings
4. Formatted result is written back to editor with proper syntax highlighting
5. Tool window state and tab management handled by UI components

### Extension Points
The plugin registers these IntelliJ Platform extension points:
- `toolWindow` - JSONinja tool window factory
- `projectConfigurable` - Settings UI integration
- `diff.DiffExtension` - Custom JSON diff provider
- Application listeners for plugin lifecycle management

## Development Environment Setup

### Prerequisites
- JDK 17+ (project uses Kotlin JVM toolchain 17)
- Gradle 8.10.2 (via wrapper)
- IntelliJ IDEA Community or Ultimate (for development)
- Network access to JetBrains repositories

### Dependencies
- Jackson (databind + kotlin module) for JSON processing
- JsonPath library for JMES Path queries
- DataFaker for random data generation
- JUnit 4 for testing
- IntelliJ Platform SDK 2024.3 (IC type)

### IDE Setup
1. Import project as Gradle project in IntelliJ IDEA
2. Ensure Project SDK is JDK 17+
3. Gradle sync should configure Kotlin automatically
4. Use `./gradlew runIde` to launch sandbox for testing

## Testing Strategy

Tests are organized under `src/test/kotlin/com/livteam/jsoninja/`:
- **Service Tests**: Unit tests for all core services extending `BasePlatformTestCase`
- **Action Tests**: Tests for user actions and their integration points
- Test naming convention: `should_ExpectedBehavior_When_StateUnderTest`

Run specific service tests:
```bash
./gradlew test --tests "*JsonFormatterServiceTest*"
./gradlew test --tests "*JmesPathServiceTest*"  
./gradlew test --tests "*JsonDiffServiceTest*"
```

## Troubleshooting

### Common Issues
- **Gradle sync failures**: Clear Gradle cache with `./gradlew clean --refresh-dependencies`
- **IDE version compatibility**: Check `pluginSinceBuild`/`pluginUntilBuild` in `gradle.properties`
- **Kotlin compiler issues**: Verify JVM toolchain version matches project requirements
- **Plugin not loading**: Check `build/idea-sandbox/system/log/idea.log` for errors

### Debug Mode
```bash
# Run sandbox IDE with debug output
./gradlew runIde --debug-jvm

# Enable plugin debug logging in sandbox
# Add to sandbox IDE: Help > Diagnostic Tools > Debug Log Settings
# Enter: com.livteam.jsoninja
```

### Build Performance
The project uses Gradle configuration cache and build cache. If experiencing slow builds:
```bash
# Verify cache settings in gradle.properties
grep -E "org.gradle.(configuration-)?cache" gradle.properties

# Clean and rebuild with fresh cache
./gradlew clean build --refresh-dependencies
```

## Release Process

1. **Version Update**: Update `pluginVersion` in `gradle.properties`
2. **Changelog**: Update `CHANGELOG.md` with new version details  
3. **Build & Test**: Run full verification suite
4. **Plugin Verification**: Test with multiple IDE versions via `verifyPlugin`
5. **Signing**: Configure signing certificates as environment variables
6. **Publishing**: Deploy to JetBrains Marketplace

**Required Environment Variables for Release:**
- `CERTIFICATE_CHAIN` - Plugin signing certificate chain
- `PRIVATE_KEY` - Private key for plugin signing  
- `PRIVATE_KEY_PASSWORD` - Password for private key
- `PUBLISH_TOKEN` - JetBrains Marketplace API token

## Code Quality Standards

The project follows "clean code" principles as specified in `.windsurfrules`. Key practices:
- Kotlin coding conventions with 4-space indentation
- Service-oriented architecture with clear separation of concerns
- Comprehensive unit test coverage for all services
- Proper error handling and logging via IntelliJ diagnostic logger
- Resource management and thread safety in concurrent operations
