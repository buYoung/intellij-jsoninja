# **Command-Line Tool Instructions for File and Code Searching**

**Never use the head pipeline for search results for `rg` and `sg`.**

**1. `ripgrep` (rg): File Content Search**

1. **Purpose**
    - Ultra-fast text/pattern search in large codebases
    - Efficient search with automatic .gitignore rules
    - Powerful regex and Unicode support

2. **Usage Tips**
    - Search hidden files: `rg -uu` (disable all automatic filtering)
    - Search specific file types: `rg 'pattern' -t rust` or `rg 'pattern' -g '*.py'`
    - **Note: Search by directory, not by specific files**
    - Search binary files like PDFs: Use `--pre` option with preprocessor
    - Set default options: Create `~/.ripgreprc` configuration file

**2. `ast-grep` (sg): Code Structure (AST) Based Search**

1. **Purpose**
    - Accurate pattern matching by analyzing code syntax structure
    - Search for specific function calls, API usage patterns, etc.
    - Automatically excludes matches in comments or strings

2. **Usage Tips**
    - **Language specification required**: Use `-l` option (e.g., `-l ts`, `-l python`)
    - Pattern variables: Capture code parts with `$A`, `$B`, etc.
    - Simple pattern example: `sg -p 'console.log($$$)' -l js` (find all console.log)
    - Complex structure search: `sg -p 'function $NAME($PARAMS) { $$$ }' -l js`
    - **Context display**: Use `-C` (context), `-B` (before), `-A` (after) options to see surrounding content (e.g., `-C 3` shows 3 lines before/after)

## **Workflow for Efficient Searching**

**Workflow**

1. **Broad Exploration → Precise Analysis**
    - First, use `rg` to search keywords or patterns across the entire project to identify relevant code locations
    - Based on code content found in `rg` results, use `sg` to precisely analyze where methods or functions are used throughout the project
    - If information from `sg`'s basic search results is insufficient, use **context display** options (`-C`, `-B`, `-A`) to check surrounding content

2. **Appropriate Use Cases by Tool**
    - **Text-based information** (comments, config values, log messages, etc.): Search with `rg`
    - **Structural information** (function calls, specific syntax patterns, API usage, etc.): Search with `sg`

3. **Comprehensive Evaluation of Results**
    - Evaluate both text matches from `rg` (comments, variable names, etc.) and structural matches from `sg` (function calls, syntax structures, etc.) together to deeply understand code functionality and intent