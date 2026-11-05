const fs = require('fs');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // ignore
}

const toolInput = payload.tool_input || {};
const content = toolInput.content || '';
const newString = toolInput.new_string || toolInput.newString || '';

const secretPatterns = [
  new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i"),
  new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", ""),
  new RegExp("AKIA[0-9A-Z]{16}", ""),
  new RegExp("\\bghp_[A-Za-z0-9]{36}\\b", ""),
  new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "")
];
const INVISIBLE_RE = /[\u202A-\u202E\u2066-\u2069\u200E\u200F\u061C\u200B\u200C\uFEFF]|(?<!\p{Extended_Pictographic}[\uFE0F\u{1F3FB}-\u{1F3FF}]*)\u200D|\u200D(?!\p{Extended_Pictographic})/u;
const INVISIBLE_HINT = 'write a \\uXXXX escape instead of the literal invisible character';
function invisibleCodepoint(text) {
  const m = INVISIBLE_RE.exec(String(text));
  return m ? 'U+' + m[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0') : '';
}

for (const pattern of secretPatterns) {
  if (pattern.test(content) || pattern.test(newString)) {
    console.error('Hardcoded secret detected.');
    process.exit(2);
  }
}
const invisible = invisibleCodepoint(content) || invisibleCodepoint(newString);
if (invisible) {
  console.error('Invisible character ' + invisible + ' in the content: ' + INVISIBLE_HINT + '.');
  process.exit(2);
}
process.exit(0);
