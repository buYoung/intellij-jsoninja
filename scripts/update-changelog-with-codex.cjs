#!/usr/bin/env node

const { execFileSync, spawnSync } = require("node:child_process");
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

function buildPrompt(baseTag, commitSummary) {
  return [
    "Update only CHANGELOG.md for JSONinja.",
    "",
    "Constraints:",
    '- Modify only the section below "## [Unreleased]".',
    "- Do not create a versioned release section.",
    "- Do not move existing release sections.",
    "- Preserve the existing Keep a Changelog style.",
    '- Use category headings such as "### Added", "### Changed", and "### Fixed" only when the commits support them.',
    "- Keep the bullets concise, user-facing, and similar to the existing CHANGELOG.md wording.",
    "- If an existing Unreleased bullet already describes the same change, refine it instead of duplicating it.",
    "- Do not edit code, release configuration, package metadata, or any file other than CHANGELOG.md.",
    "- Do not run tests, linting, or formatting commands.",
    "",
    `Base release tag: ${baseTag}`,
    `Release version: ${releaseVersion}`,
    "",
    "Commits after the base release tag:",
    commitSummary,
    "",
  ].join("\n");
}

function runCodex(prompt) {
  const result = spawnSync(
    "codex",
    ["exec", "-C", rootDirectory, "--sandbox", "workspace-write", "--ask-for-approval", "never", "-"],
    {
      cwd: rootDirectory,
      input: prompt,
      stdio: ["pipe", "inherit", "inherit"],
      encoding: "utf8",
    },
  );

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
    (filePath) => filePath !== "CHANGELOG.md" && filePath !== "gradle.properties",
  );

  if (unexpectedFiles.length === 0) {
    return;
  }

  console.error("Codex changed unexpected files:");
  for (const filePath of unexpectedFiles) {
    console.error(filePath);
  }
  console.error("Only CHANGELOG.md and gradle.properties may be changed during release.");
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
if (!commitSummary) {
  console.log(`No commits found after ${baseTag}.`);
  process.exit(0);
}

runCodex(buildPrompt(baseTag, commitSummary));
assertExpectedFilesOnly();
