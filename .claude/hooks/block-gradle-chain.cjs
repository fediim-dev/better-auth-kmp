// Blocks two Gradle mistakes in Bash commands: chaining another command after a
// piped Gradle call, which loses Gradle's exit code unless pipefail is set, and
// an install task with no ANDROID_SERIAL, which installs on every connected
// device. It stops mistakes and isn't a security boundary, so it fails open.
const fs = require('fs');

const LOST_EXIT_CODE_MESSAGE =
  "Gradle's exit code is lost in this pipe, so the next command runs even if the build failed. Redirect to a file and check the exit code, or set -o pipefail first and chain with &&.";
const UNSCOPED_INSTALL_MESSAGE =
  'Gradle installs on every connected device. Put ANDROID_SERIAL=<serial> in front of the Gradle call, or export it earlier in the same command.';
const STOP_DAEMON_MESSAGE =
  'Stopping the shared Gradle daemon kills builds in other sessions and worktrees. Pass --no-daemon to your own Gradle command instead.';

const HEREDOC_START = /(?<!<)<<(?!<)(-?)[ \t]*(?:'([^']*)'|"([^"]*)"|([^\s'"<>;&|()]+))/g;
// The lookbehind lets at most one double quote fail to close, which keeps this linear on a long run of escaped quotes.
const QUOTED_TEXT = /'[^']*'|(?<!\\)"(?:[^"\\]|\\[\s\S])*"/g;
const QUOTED_MARKER = 'QUOTED';
const COMMENT = /(^|[\s;&|()])#[^\n]*/g;
const STATEMENT_SEPARATOR = /(&&|\|\||;|\n)/;
const CHAINING_SEPARATORS = [';', '&&', '\n'];
const ASSIGNMENT_PREFIX = /^[A-Za-z_]\w*=/;
const GRADLE_COMMAND = /^gradle$|(?:^|\/)gradlew$/;
const SHORT_OPTIONS_ENDING_IN_O = /^-[a-zA-Z]*o$/;
const INSTALL_TASK = /(^|:)install[A-Z]/;
const OPTIONS_TAKING_A_NON_INSTALL_VALUE = ['-x', '--exclude-task', '--task', '--tests'];
const NON_EMPTY_SERIAL = /^ANDROID_SERIAL=./;

function removeHeredocBodies(command) {
  const keptLines = [];
  const openHeredocs = [];
  let closedHeredocs = 0;
  for (const line of command.split('\n')) {
    if (closedHeredocs < openHeredocs.length) {
      const { stripsTabs, delimiter } = openHeredocs[closedHeredocs];
      if ((stripsTabs ? line.replace(/^\t+/, '') : line) === delimiter) closedHeredocs++;
      continue;
    }
    keptLines.push(line);
    for (const match of line.matchAll(HEREDOC_START)) {
      openHeredocs.push({ stripsTabs: match[1] === '-', delimiter: match[2] ?? match[3] ?? match[4] });
    }
  }
  return keptLines.join('\n');
}

// Each statement keeps the separator that follows it; blank statements are dropped.
function splitStatements(command) {
  const text = removeHeredocBodies(command)
    .replace(QUOTED_TEXT, QUOTED_MARKER)
    .replace(COMMENT, '$1')
    .replace(/\\\n/g, '')
    .replace(/(\|&?)\s*\n/g, '$1 ');
  const parts = text.split(STATEMENT_SEPARATOR);
  const statements = [];
  for (let i = 0; i < parts.length; i += 2) {
    const statementText = parts[i].trim();
    if (statementText) statements.push({ text: statementText, separator: parts[i + 1] });
  }
  return statements;
}

function firstStage(statementText) {
  const words = statementText.split('|')[0].trim().split(/\s+/);
  const commandIndex = words.findIndex((word) => !ASSIGNMENT_PREFIX.test(word));
  if (commandIndex < 0) return { prefixes: words, command: '', args: [] };
  return { prefixes: words.slice(0, commandIndex), command: words[commandIndex], args: words.slice(commandIndex + 1) };
}

function setsPipefail(statementText) {
  const words = statementText.split(/\s+/);
  return words[0] === 'set' && words.some((word, i) => SHORT_OPTIONS_ENDING_IN_O.test(word) && words[i + 1] === 'pipefail');
}

function losesGradleExitCode(statements) {
  let pipefailSet = false;
  for (let i = 0; i < statements.length; i++) {
    const { text, separator } = statements[i];
    const hasFollowingStatement = i < statements.length - 1;
    if (
      !pipefailSet &&
      hasFollowingStatement &&
      CHAINING_SEPARATORS.includes(separator) &&
      text.includes('|') &&
      GRADLE_COMMAND.test(firstStage(text).command)
    ) {
      return true;
    }
    if (setsPipefail(text)) pipefailSet = true;
  }
  return false;
}

function namesInstallTask(args) {
  return args.some(
    (word, i) => !word.startsWith('-') && INSTALL_TASK.test(word) && !OPTIONS_TAKING_A_NON_INSTALL_VALUE.includes(args[i - 1]),
  );
}

function installsOnEveryDevice(statements) {
  let serialExported = false;
  for (const { text } of statements) {
    const { prefixes, command, args } = firstStage(text);
    if (
      GRADLE_COMMAND.test(command) &&
      namesInstallTask(args) &&
      !serialExported &&
      !prefixes.some((word) => NON_EMPTY_SERIAL.test(word))
    ) {
      return true;
    }
    if (command === 'export' && args.some((word) => NON_EMPTY_SERIAL.test(word))) serialExported = true;
  }
  return false;
}

function stopsGradleDaemon(statements) {
  for (const { text } of statements) {
    const { command, args } = firstStage(text);
    if (GRADLE_COMMAND.test(command) && args.includes('--stop')) return true;
  }
  return false;
}

function blockReason(command) {
  const statements = splitStatements(command);
  if (losesGradleExitCode(statements)) return LOST_EXIT_CODE_MESSAGE;
  if (installsOnEveryDevice(statements)) return UNSCOPED_INSTALL_MESSAGE;
  if (stopsGradleDaemon(statements)) return STOP_DAEMON_MESSAGE;
  return null;
}

try {
  const payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
  const reason = blockReason(payload.tool_input.command);
  if (reason) {
    console.error(reason);
    process.exit(2);
  }
} catch (err) {
  // Failing closed would stop every Bash call.
}
process.exit(0);
