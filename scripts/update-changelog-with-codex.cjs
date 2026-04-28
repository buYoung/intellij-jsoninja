#!/usr/bin/env node

const { execFileSync, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const rootDirectory = path.resolve(__dirname, "..");
const baseTagArgument = process.argv[2] || "";
const releaseVersion = process.argv[3] || "unknown";

function runGit(argumentsList) {
  return execFileSync("git", argumentsList, {
    cwd: rootDirectory,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
}

function resolveBaseTag() {
  if (baseTagArgument) {
    return baseTagArgument;
  }

  try {
    return runGit(["describe", "--tags", "--abbrev=0", "--match", "v[0-9]*"]);
  } catch {
    return "";
  }
}

function getCommitSummary(baseTag) {
  try {
    return runGit([
      "log",
      "--no-merges",
      "--name-status",
      "--pretty=format:commit %h %s",
      `${baseTag}..HEAD`,
      "--",
      ".",
      ":!CHANGELOG.md",
    ]);
  } catch {
    return "";
  }
}

function updateVersionFiles() {
  const gradlePropertiesPath = path.join(rootDirectory, "gradle.properties");
  const gradleProperties = fs.readFileSync(gradlePropertiesPath, "utf8");
  const nextGradleProperties = gradleProperties.replace(
    /^pluginVersion=.*$/m,
    `pluginVersion=${releaseVersion}`,
  );

  if (nextGradleProperties === gradleProperties) {
    throw new Error("pluginVersion was not found in gradle.properties.");
  }

  fs.writeFileSync(gradlePropertiesPath, nextGradleProperties);

  const pluginXmlPath = path.join(rootDirectory, "src/main/resources/META-INF/plugin.xml");
  const pluginXml = fs.readFileSync(pluginXmlPath, "utf8");
  const nextPluginXml = pluginXml.replace(
    /(<version>)[^<]+(<\/version>)/,
    `$1${releaseVersion}$2`,
  );

  if (nextPluginXml === pluginXml) {
    throw new Error("version element was not found in src/main/resources/META-INF/plugin.xml.");
  }

  fs.writeFileSync(pluginXmlPath, nextPluginXml);
}

function getChangelogReference() {
  const changelogPath = path.join(rootDirectory, "CHANGELOG.md");
  const changelog = fs.readFileSync(changelogPath, "utf8");
  const sectionMatches = [...changelog.matchAll(/^## \[[^\]]+\].*$/gm)];

  if (sectionMatches.length === 0) {
    return changelog.slice(0, 4000);
  }

  const referenceEndIndex = sectionMatches[3]?.index ?? Math.min(changelog.length, 5000);
  return changelog.slice(0, referenceEndIndex).trim();
}

function buildPrompt(baseTag, commitSummary, changelogReference) {
  return [
    "Update only CHANGELOG.md for JSONinja.",
    "",
    "Goal:",
    "- Write a user-facing changelog entry that makes a product user immediately understand what was added, changed, or fixed.",
    "- Do not describe implementation work. Explain the visible behavior or workflow impact.",
    "",
    "Editing constraints:",
    '- Modify only the section below "## [Unreleased]".',
    "- Do not create a versioned release section.",
    "- Do not move existing release sections.",
    "- Preserve the existing Keep a Changelog style.",
    '- Use category headings such as "### Added", "### Changed", and "### Fixed" only when the commits support them.',
    "- If an existing Unreleased bullet already describes the same change, refine it instead of duplicating it.",
    "- Do not edit code, release configuration, package metadata, or any file other than CHANGELOG.md.",
    "- Do not run tests, linting, or formatting commands.",
    "",
    "Writing rules:",
    "- Do not include source file paths, package names, class names, function names, commit hashes, branch names, or test/build details.",
    "- Do not mention refactoring, presenter/service internals, dependency changes, or implementation mechanics unless they directly changed user-visible behavior.",
    '- Prefer the existing pattern: "- **Feature Area**: Clear user-facing sentence."',
    '- Each bullet must answer either "what can users do now?", "what behaves differently?", or "what problem is fixed?".',
    "- Avoid vague wording such as \"improved\", \"updated\", or \"fixed issues\" unless the concrete user-visible result is stated.",
    "- Keep nested bullets only when a feature has multiple user-visible capabilities.",
    "- Use English because the existing changelog is written in English.",
    "",
    `Base release tag: ${baseTag}`,
    `Release version: ${releaseVersion}`,
    "",
    "Existing changelog style reference:",
    changelogReference,
    "",
    "Commits after the base release tag:",
    "Use these commits and changed paths only as evidence. Do not copy technical file names or code identifiers into the changelog.",
    commitSummary,
    "",
  ].join("\n");
}

function getCodexExecHelp() {
  const result = spawnSync("codex", ["exec", "--help"], {
    cwd: rootDirectory,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });

  if (result.error) {
    throw result.error;
  }

  return `${result.stdout || ""}\n${result.stderr || ""}`;
}

function buildCodexArguments() {
  const helpText = getCodexExecHelp();
  const argumentsList = ["exec", "-C", rootDirectory];

  if (helpText.includes("--sandbox")) {
    argumentsList.push("--sandbox", "workspace-write");
  }

  if (helpText.includes("--ask-for-approval")) {
    argumentsList.push("--ask-for-approval", "never");
  } else if (helpText.includes("--full-auto")) {
    argumentsList.push("--full-auto");
  }

  argumentsList.push("-");
  return argumentsList;
}

function runCodex(prompt) {
  const result = spawnSync("codex", buildCodexArguments(), {
    cwd: rootDirectory,
    input: prompt,
    stdio: ["pipe", "inherit", "inherit"],
    encoding: "utf8",
  });

  if (result.error) {
    throw result.error;
  }

  if (result.status !== 0) {
    process.exit(result.status || 1);
  }
}

function assertExpectedFilesOnly() {
  const changedFiles = runGit(["diff", "--name-only"])
    .split("\n")
    .map((filePath) => filePath.trim())
    .filter(Boolean);

  const unexpectedFiles = changedFiles.filter(
    (filePath) =>
      filePath !== "CHANGELOG.md" &&
      filePath !== "gradle.properties" &&
      filePath !== "src/main/resources/META-INF/plugin.xml",
  );

  if (unexpectedFiles.length === 0) {
    return;
  }

  console.error("Codex changed unexpected files:");
  for (const filePath of unexpectedFiles) {
    console.error(filePath);
  }
  console.error("Only CHANGELOG.md, gradle.properties, and src/main/resources/META-INF/plugin.xml may be changed during release.");
  process.exit(1);
}

const baseTag = resolveBaseTag();
if (!baseTag) {
  console.error(
    "No release tag was found. Pass a base tag, for example: node scripts/update-changelog-with-codex.cjs v1.11.3",
  );
  process.exit(1);
}

const commitSummary = getCommitSummary(baseTag);
updateVersionFiles();

if (!commitSummary) {
  console.log(`No commits found after ${baseTag}.`);
  process.exit(0);
}

runCodex(buildPrompt(baseTag, commitSummary, getChangelogReference()));
assertExpectedFilesOnly();
