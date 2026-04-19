#!/usr/bin/env node

// Usage: node ./scripts/add-unknown-words.js
// Runs cspell on project, extracts unknown words, adds to .vscode/settings.json

const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

function findProjectRoot() {
  let dir = process.cwd();
  while (dir !== path.dirname(dir)) {
    if (fs.existsSync(path.join(dir, "package.json"))) return dir;
    dir = path.dirname(dir);
  }
  return process.cwd();
}

function ensureCSpell() {
  try {
    // Check if cspell-cli is available
    execSync("npx cspell-cli --version", { stdio: "ignore" });
    return;
  } catch (err) {
    // Not found, install it
    console.log("cspell-cli not found, installing globally...");
    try {
      execSync("npm i cspell-cli@latest -g", { stdio: "inherit" });
      console.log("✓ cspell-cli installed successfully\n");
    } catch (installErr) {
      console.error("Failed to install cspell-cli");
      process.exit(1);
    }
  }
}

function extractUnknownWords(output) {
  // Match pattern: "filename:line:col - Unknown word (word)"
  // Use .+? to capture any characters (including Unicode) until closing parenthesis
  const regex = /Unknown word \((.+?)\)/g;
  const words = new Set();
  
  let match;
  while ((match = regex.exec(output)) !== null) {
    words.add(match[1].toLowerCase());
  }
  
  return words;
}

function updateSettings(root, words) {
  const settingsPath = path.join(root, ".vscode", "settings.json");
  
  // Create .vscode directory if needed
  fs.mkdirSync(path.dirname(settingsPath), { recursive: true });

  // Read existing settings
  let settings = {};
  if (fs.existsSync(settingsPath)) {
    try {
      settings = JSON.parse(fs.readFileSync(settingsPath, "utf8"));
    } catch (err) {
      console.log("Warning: Invalid settings.json, creating new file");
    }
  }

  // Initialize cSpell.words array
  settings["cSpell.words"] = settings["cSpell.words"] || [];
  
  // Filter out words already in dictionary
  const existing = new Set(settings["cSpell.words"].map(w => w.toLowerCase()));
  const newWords = [...words].filter(w => !existing.has(w));

  if (newWords.length === 0) {
    console.log("✓ No new words to add");
    return;
  }

  // Add new words and sort
  settings["cSpell.words"] = [...settings["cSpell.words"], ...newWords].sort();
  
  // Write settings
  fs.writeFileSync(settingsPath, JSON.stringify(settings, null, 2) + "\n");
  console.log(`✓ Added ${newWords.length} word(s): ${newWords.join(", ")}`);
}

// Main
ensureCSpell();

const root = findProjectRoot();
console.log(`Scanning project: ${root}`);

try {
  // Run cspell and capture output (including stderr where results go)
  execSync("npx cspell-cli --gitignore .", {
    cwd: root,
    encoding: "utf8",
    stdio: "pipe"
  });
  
  // No unknown words found
  console.log("✓ No unknown words found");
} catch (err) {
  // cspell exits with error code when unknown words found
  const output = err.stdout + err.stderr;
  const words = extractUnknownWords(output);
  
  if (words.size === 0) {
    console.log("✓ No unknown words found");
  } else {
    console.log(`Found ${words.size} unique unknown word(s)`);
    updateSettings(root, words);
  }
}
