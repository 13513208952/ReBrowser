// Only interpret known model markers, not arbitrary angle-bracket text or LaTeX.
const dsOutputTokens = [
  /(?<code>```[\s\S]*?(?:```|$)|``[^`\n]*``|`[^`\n]*`)/,
  /<\s*system_reminder\s*>[\s\S]*?<\s*\/\s*system_reminder\s*>/,
  /<\s*system_reminder\s*>[^\r\n]*/,
  /<\s*\/\s*system_reminder\s*>/,
  /<\s*[|｜]+\s*DSML\s*[|｜]+\s*function_calls\s*>[\s\S]*?(?:<\s*\/\s*[|｜]+\s*DSML\s*[|｜]+\s*function_calls\s*>|$)/,
  /<\s*[|｜]+\s*DSML\s*[|｜]+\s*(?<part>invoke|parameter)\b[^>\r\n]*?(?<!\/)>[\s\S]*?<\s*\/\s*[|｜]+\s*DSML\s*[|｜]+\s*\k<part>\s*>/,
  /<\s*\/?\s*[|｜]+\s*DSML\s*[|｜]+[^>\r\n]*(?:>|$)/,
  /<[|｜]+tool▁calls▁begin[|｜]+>[\s\S]*?(?:<[|｜]+tool▁calls▁end[|｜]+>|$)/,
  /<[|｜]+tool▁call▁begin[|｜]+>[\s\S]*?(?:<[|｜]+tool▁call▁end[|｜]+>|$)/,
  /<[|｜]+tool▁(?:calls?|outputs?)▁(?:begin|end|sep)[|｜]+>/,
  /<[|｜]+(?:begin▁of▁sentence|end▁of▁sentence|User|Assistant)[|｜]+>/,
  /<\s*tool_call\b[^>\r\n]*?(?<!\/)>(?:[\s\S]*?)(?:<\s*\/\s*tool_call\s*>|$)/,
  /<\s*\/?\s*tool_call\b[^>\r\n]*(?:>|$)/,
  /<\s*think\s*>[\s\S]*?(?:<\s*\/\s*think\s*>|$)/,
  /<\s*\/\s*think\s*>/,
  /\bUser ID:[ \t]*\d+[ \t]*,[ \t]*Nickname:[^\r\n<]*/,
].map(rule => rule.source).join('|');

const dsOutputPattern = new RegExp(dsOutputTokens, 'gi');

function cleanDsOutput(text) {
  if (typeof text !== 'string') return '';
  return text.replace(dsOutputPattern, (...args) => args.at(-1).code ?? '').trim();
}
