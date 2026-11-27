const dsMarkdown = (() => {
  const md = markdownit({ html: false, breaks: true });
  const math = /\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\((.+?)\\\)|\$(?![\s$])((?:\\.|[^$\\\n])+?)(?<!\s)\$(?![\d$])/y;
  const looseTitle = /^\*\*[ \t]+([^*\n]+?)[ \t]+\*\*[ \t]*$/gm;

  md.inline.ruler.before('escape', 'ds_math', (state, silent) => {
    math.lastIndex = state.pos;
    const match = math.exec(state.src);
    if (!match || math.lastIndex > state.posMax) return false;
    const [raw, dollars, brackets, parentheses, inline] = match;
    if (!silent) {
      const token = state.push('ds_math', 'span', 0);
      token.content = dollars ?? brackets ?? parentheses ?? inline;
      token.meta = { raw, display: dollars !== undefined || brackets !== undefined };
    }
    state.pos += raw.length;
    return true;
  });

  md.core.ruler.before('inline', 'ds_loose_title', state => {
    for (const token of state.tokens) {
      if (token.type === 'inline') {
        token.content = token.content.replace(looseTitle, (_, title) => `**${title.trim()}**`);
      }
    }
  });

  md.disable(['html_block', 'html_inline']);
  return md;
})();

const dsMarkdownTags = new Set([
  'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'blockquote',
  'em', 'strong', 's', 'a', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
]);

// Linearize rendered MathML rather than stripping TeX commands from their operands.
function plainDsMath(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.data;
  if (node.localName === 'annotation' || node.localName === 'annotation-xml') return '';
  const children = Array.from(node.childNodes, plainDsMath);
  const [a, b, c] = children;
  switch (node.localName) {
    case 'mfrac': return `(${a})/(${b})`;
    case 'msup': return `${a}^(${b})`;
    case 'msub': return `${a}_(${b})`;
    case 'msubsup': return `${a}_(${b})^(${c})`;
    case 'msqrt': return `√(${children.join('')})`;
    case 'mroot': return `root(${b}, ${a})`;
    case 'mover': return `${a} ⟨${b}⟩`;
    case 'munder': return `${a}_(${b})`;
    case 'munderover': return `${a}_(${b})^(${c})`;
    case 'mtable': return `[${children.join('; ')}]`;
    case 'mtr': return children.join(', ');
    case 'mspace': return ' ';
    default: return children.join('');
  }
}

function renderDsAnswer(target, text, plain = false) {
  const fragment = document.createDocumentFragment();
  const stack = [fragment];
  function appendTokens(tokens) {
    for (const token of tokens) {
      const parent = stack.at(-1);
      if (token.children && token.type === 'inline') {
        appendTokens(token.children);
      } else if (token.type === 'text' || token.type === 'image') {
        parent.append(token.content);
      } else if (token.type === 'softbreak' || token.type === 'hardbreak' || token.type === 'hr') {
        parent.append(document.createElement(token.type === 'hr' ? 'hr' : 'br'));
      } else if (token.type === 'code_inline' || token.type === 'code_block' || token.type === 'fence') {
        const code = document.createElement('code');
        code.textContent = token.content;
        if (token.type === 'code_inline') parent.append(code);
        else {
          const pre = document.createElement('pre');
          pre.append(code);
          parent.append(pre);
        }
      } else if (token.type === 'ds_math') {
        const span = document.createElement('span');
        if (token.meta.display) span.className = 'math-block';
        try {
          katex.render(token.content, span, {
            displayMode: token.meta.display, output: 'mathml', trust: false, maxExpand: 500, maxSize: 30,
          });
          if (plain) {
            if (token.meta.display) parent.append(document.createElement('br'));
            parent.append(plainDsMath(span));
            if (token.meta.display) parent.append(document.createElement('br'));
          } else parent.append(span);
        } catch {
          parent.append(plain ? token.content : token.meta.raw);
        }
      } else if (!token.hidden && dsMarkdownTags.has(token.tag)) {
        if (token.nesting < 0) stack.pop();
        else if (token.nesting > 0) {
          const element = document.createElement(token.tag);
          if (token.tag === 'a') {
            const href = token.attrGet('href') ?? '';
            if (/^https?:\/\//i.test(href)) {
              element.href = href;
              element.target = '_blank';
              element.rel = 'noopener noreferrer';
            }
          } else if (token.tag === 'ol' && /^\d+$/.test(token.attrGet('start') ?? '')) {
            element.start = Number(token.attrGet('start'));
          }
          parent.append(element);
          stack.push(element);
        }
      }
    }
  }
  appendTokens(dsMarkdown.parse(text, {}));
  if (!plain) {
    target.replaceChildren(fragment);
    return;
  }
  const lines = [];
  function walk(node) {
    if (node.nodeType === Node.TEXT_NODE) {
      lines.push(node.data);
      return;
    }
    if (node.nodeName === 'BR') lines.push('\n');
    else if (node.nodeName === 'HR') lines.push('\n\n');
    else {
      for (const child of node.childNodes) walk(child);
      if (/^(?:P|H[1-6]|PRE|BLOCKQUOTE|TABLE)$/.test(node.nodeName)) lines.push('\n\n');
      else if (/^(?:LI|TR)$/.test(node.nodeName)) lines.push('\n');
      if (/^(?:TH|TD)$/.test(node.nodeName) && node.nextElementSibling) lines.push('\t');
    }
  }
  walk(fragment);
  target.textContent = lines.join('').replace(/\n{3,}/g, '\n\n').trim();
}
