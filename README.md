# JSON Helper 2

![Build Status](https://github.com/buYoung/intellij-jsoninja/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/26715.svg)](https://plugins.jetbrains.com/plugin/26715)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/26715.svg)](https://plugins.jetbrains.com/plugin/26715)


## Introduction

JSON Helper 2 is a JSON processing plugin for JetBrains IDEs. This plugin provides the following core features to help developers handle JSON data more easily:

### Key Features

- **JSON Prettify**:
  - Formats JSON data nicely for readability
  - (+1.0.3) Auto-formatting on paste
- **JSON Uglify**: Compresses JSON data into a single line
- **JSON Escape**: Escapes JSON strings
- **JSON Unescape**: Restores escaped JSON strings to their original form
- **JMES Path**: Advanced search and filtering within JSON data using JMES Path
- **JSON Generator**: Generates JSON data

## Development Checklist
- [x] Created project using [IntelliJ Platform Plugin Template][template].
- [x] Reviewed [template documentation][template].
- [x] Modified `pluginGroup` and `pluginName` in [`./gradle.properties`], `id` in [`./src/main/resources/META-INF/plugin.xml`], and the source package in [`./src/main/kotlin`].
- [x] Updated the plugin description in `README` ([Reference][docs:plugin-description]).
- [x] Reviewed [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [x] Performed the first [manual plugin deployment](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate).
- [x] Set `MARKETPLACE_ID` in the README badges above. Can be obtained after the plugin is published on JetBrains Marketplace.
- [ ] Set up [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables) related to [plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate).
- [ ] Set up [deployment token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the [IntelliJ Platform Plugin Template][template] to receive notifications about new features and fixes.

<!-- Plugin description -->
JSONinja is a powerful JSON processing plugin for JetBrains IDEs.  
It provides advanced tools for JSON data manipulation, allowing you to work efficiently without disrupting your development workflow.

Key Features:  
• JSON Prettify: Format JSON data for improved readability  
• JSON Uglify: Compress JSON data into a single line for transmission or storage  
• JSON Escape/Unescape: Process and restore escaped JSON strings  
• JMES Path Support: Find and filter specific values within complex JSON data  
• Multi-tab Interface: Work with multiple JSON documents simultaneously  
• Advanced Formatting Options: Customize indentation size, key sorting, and more

[Under Development]  
• Enhanced JSON Validation  
• JSON Schema Support  
• JSON to Various Format Converters  
• History and Favorites Management

JSONinja is ideal for analyzing REST API responses, editing configuration files, and performing data transformation tasks. Streamline your JSON-related work with our intuitive and efficient interface!

**The Birth of JSONinja** 😄  
Inspired by the need for reliable JSON tools! When we faced compatibility challenges with existing solutions after an IDE update, we saw an opportunity to create something new. Our development journey took a bit longer than expected, but we're excited to finally share JSONinja with the community. Sometimes the best tools come from personal necessity!
<!-- Plugin description end -->

## Installation

- Using the IDE's built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jsoninja"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/26715) page and click the <kbd>Install to ...</kbd> button if your IDE is running.

  Alternatively, download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from the JetBrains Marketplace versions page and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install Plugin from Disk...</kbd>.


---
This plugin is based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation