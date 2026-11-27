rainClassActivation.then(active => {
  if (!active) return;

  // The desktop course shell clips its 1200px minimum width inside #app.
  const courseLayout = document.createElement('style');
  courseLayout.textContent = '@media (width < 1200px) { .learning-space-student { min-width: 0 !important; } }';
  document.head.append(courseLayout);

  // The course's own editors (discussion box, search) must stay above the
  // on-screen keyboard when the viewport shrinks for it.
  let editor;
  document.addEventListener('focusin', event => {
    editor = event.target.closest?.('textarea, input:not([type="hidden"]), [contenteditable]');
  }, true);
  visualViewport?.addEventListener('resize', () => {
    if (editor?.isConnected) editor.scrollIntoView({ block: 'nearest' });
  });

  const display = browser.runtime.connectNative('rainclass');
  display.onMessage.addListener(message => {
    if (typeof message?.plainAnswers === 'boolean') setPlainAnswers(message.plainAnswers);
  });
  display.postMessage({ type: 'display' });

  const host = document.createElement('rainclass-ds-panel');
  const shadow = host.attachShadow({ mode: 'closed' });
  const icon = (paths) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
  const chat = icon('<path d="M20.5 11.5a8.5 8.5 0 0 1-8.5 8.5 9 9 0 0 1-3.6-.8l-4.9 1.3 1.4-4.6a8.5 8.5 0 1 1 15.6-4.4Z"/><path d="M8.5 11.6h.01m3.49 0h.01m3.49 0h.01" stroke-width="2.4"/>');
  const settingsIcon = icon('<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="2" fill="var(--paper)"/><circle cx="15" cy="17" r="2" fill="var(--paper)"/>');
  const closeIcon = icon('<path d="m6 6 12 12M18 6 6 18"/>');
  const quickIcon = icon('<path d="M12 2.5 14 9l6.5 2-6.5 2-2 6.5L10 13l-6.5-2L10 9l2-6.5Zm6 15v4m-2-2h4"/>');
  const sendIcon = icon('<path d="M12 19V5m-6 6 6-6 6 6"/>');
  const plusIcon = icon('<path d="M12 5v14M5 12h14"/>');

  // This template and its SVGs are extension constants, never reply or page data.
  const panel = new DOMParser().parseFromString(`<body><style>
    :host { all: initial; position: fixed; inset: auto 20px 20px auto; z-index: 2147483647; color-scheme: light dark;
      --paper: light-dark(#fff,#202124); --ink: light-dark(#202124,#f3f4f6);
      --muted: light-dark(#666b74,#b2b6bf); --line: light-dark(#e5e6e9,#41434a);
      --surface: light-dark(#f2f3f5,#2e3036); --accent: light-dark(#314bb9,#a9baff);
      --action: light-dark(#18191b,#edf0f7); --action-ink: light-dark(#fff,#1d2027);
      --shadow: 0 14px 44px #0002, 0 2px 8px #0001;
      color: var(--ink); font: 14px/1.55 system-ui,sans-serif; }
    :host([hidden]), :host([data-capturing]), [hidden] { display: none !important; }
    * { box-sizing: border-box; }
    button, textarea, select { font: inherit; color: inherit; }
    button { cursor: pointer; }
    button:disabled { opacity: .48; cursor: wait; }
    button:focus-visible, select:focus-visible, textarea:focus-visible {
      outline: 2px solid var(--accent); outline-offset: 2px;
    }
    svg { display: block; width: 20px; height: 20px; flex: none; }
    #launch, .icon-button, .send { display: grid; place-items: center; padding: 0; border: 0; }
    #launch { width: 58px; height: 58px; border-radius: 50%; background: var(--action); color: var(--action-ink);
      box-shadow: var(--shadow); }
    #launch svg { width: 28px; height: 28px; }
    #launch:hover, .send:hover:not(:disabled) { opacity: .88; }
    aside { display: flex; flex-direction: column; width: min(418px,calc(100vw - 40px));
      height: min(640px,calc(100dvh - 40px)); background: var(--paper); border: 1px solid var(--line);
      border-radius: 22px; box-shadow: var(--shadow); overflow: hidden; }
    header { flex: none; display: flex; align-items: center; gap: 6px; padding: 12px 12px 12px 18px;
      border-bottom: 1px solid var(--line); }
    .identity { flex: 1; min-width: 0; display: flex; align-items: center; gap: 10px; }
    .avatar { display: grid; place-items: center; flex: none; width: 34px; height: 34px; border-radius: 50%;
      color: var(--accent); background: var(--surface); }
    .identity strong, .identity small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .identity strong { font-size: 15px; line-height: 1.3; }
    .identity small { color: var(--muted); font-size: 11px; }
    .icon-button { width: 35px; height: 35px; border-radius: 10px; background: transparent; color: var(--muted); }
    .icon-button:hover { background: var(--surface); color: var(--ink); }
    #log { flex: 1; min-height: 0; overflow: auto; overscroll-behavior: contain; padding: 17px 17px 8px; }
    #log:empty, #log:not(:empty) + .welcome { display: none; }
    .welcome { flex: 1; min-height: 0; overflow: auto; display: grid; place-items: center; text-align: center; padding: 16px 26px; }
    .welcome svg { margin: 0 auto 12px; width: 29px; height: 29px; color: var(--accent); }
    .welcome h2 { margin: 0 0 8px; font-size: 21px; line-height: 1.25; letter-spacing: -.02em; }
    .welcome p { margin: 0; color: var(--muted); }
    .message { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0 0 16px; }
    .message.user { width: fit-content; max-width: 85%; margin-inline-start: auto; padding: 9px 13px;
      border-radius: 17px 17px 5px 17px; background: var(--surface); }
    .message.assistant { white-space: normal; }
    .message-label { display: block; font-size: 12px; margin-bottom: 5px; }
    .answer-body.plain { white-space: pre-wrap; }
    .answer-body :is(p, ul, ol, pre, blockquote, table) { margin: 0 0 .65em; }
    .answer-body :is(ul, ol) { padding-inline-start: 1.5em; }
    .answer-body :is(h1,h2,h3,h4,h5,h6) { font-size: 1.12em; margin: .7em 0 .35em; }
    .answer-body pre { overflow: auto; padding: 8px; border-radius: 7px; background: var(--surface); }
    .answer-body blockquote { border-inline-start: 2px solid var(--line); padding-inline-start: 9px; color: var(--muted); }
    .answer-body table { display: block; overflow: auto; border-collapse: collapse; }
    .answer-body :is(th, td) { padding: 3px 6px; border: 1px solid var(--line); }
    .answer-body a { color: var(--accent); }
    .math-block { display: block; overflow-x: auto; padding: 4px 0; white-space: normal; }
    footer { flex: none; background: var(--paper); padding: 8px 12px 10px; }
    .tools { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
    #quick { display: flex; align-items: center; gap: 5px; flex: none; border-radius: 99px;
      border: 1px solid var(--line); background: var(--paper); padding: 5px 9px; color: var(--accent); font-weight: 650; }
    #quick svg { width: 16px; height: 16px; }
    #quick:hover:not(:disabled) { background: var(--surface); }
    #next { flex: none; border: 1px solid var(--line); border-radius: 99px; background: var(--paper); padding: 5px 9px; }
    #next:hover:not(:disabled) { background: var(--surface); }
    select.effort { width: auto; flex: none; max-width: 105px; margin-inline-start: auto; }
    .attachments { display: flex; gap: 8px; overflow: auto; padding: 4px 3px 8px; }
    .attachment { position: relative; flex: none; }
    .attachment img { display: block; width: 55px; height: 55px; object-fit: cover; border: 1px solid var(--line); border-radius: 9px; }
    .attachment button { position: absolute; inset: 2px 2px auto auto; border: 0; border-radius: 50%; width: 22px; height: 22px;
      background: var(--action); color: var(--action-ink); display: grid; place-items: center; padding: 0; }
    .attachment button svg { width: 13px; height: 13px; }
    .message small { display: block; color: var(--muted); margin-top: 2px; }
    select { width: 0; min-width: 0; flex: 1; text-overflow: ellipsis; border: 1px solid var(--line);
      border-radius: 9px; background: var(--paper); padding: 5px; font-size: 12px; }
    #questions { width: 100%; margin-bottom: 8px; }
    form { display: flex; align-items: flex-end; gap: 8px; border: 1px solid var(--line);
      background: var(--paper); border-radius: 20px; padding: 5px; box-shadow: 0 2px 4px #0001; }
    form:focus-within { border-color: var(--muted); }
    textarea { min-width: 0; flex: 1; resize: none; max-height: 92px; overflow: auto; field-sizing: content;
      background: transparent; border: 0; border-radius: 15px; padding: 7px 7px 7px 10px; }
    textarea::placeholder { color: var(--muted); opacity: 1; }
    .send { width: 34px; height: 34px; flex: none; border-radius: 50%;
      background: var(--action); color: var(--action-ink); }
    .send svg { width: 17px; height: 17px; }
    #notice { margin: 7px 0 0; text-align: center; color: var(--muted); font-size: 11px; }
    #status:not(:empty) { max-height: 6em; overflow: auto; margin-bottom: 6px; padding: 5px 8px;
      border-radius: 7px; background: var(--surface); color: var(--ink); font-size: 12px; }
    @media (min-width: 700px) and (orientation: landscape) {
      /* Leave the app's 48dp settings button and its margins unobstructed. */
      :host([data-open]) { right: 72px; }
      aside { width: clamp(320px, 32vw, 400px); height: min(520px, 74dvh); border-radius: 18px; }
    }
    @media (max-width: 480px) {
      :host { inset: auto 10px 10px auto; }
      aside { width: calc(100vw - 20px); height: min(640px,calc(100dvh - 20px)); border-radius: 17px; }
    }
  </style>
  <button id="launch" type="button" title="打开 DeepSeek 学习助手" aria-label="打开 DeepSeek 学习助手"
    aria-controls="ds-chat" aria-expanded="false">${chat}</button>
  <aside id="ds-chat" hidden aria-label="DeepSeek 学习助手">
    <header>
      <div class="identity"><span class="avatar">${quickIcon}</span><div><strong>学习助手</strong><small>DeepSeek Flash</small></div></div>
      <button id="reset" class="icon-button" type="button" title="新会话" aria-label="新会话">${plusIcon}</button>
      <button id="settings" class="icon-button" type="button" title="设置" aria-label="设置">${settingsIcon}</button>
      <button id="close" class="icon-button" type="button" title="收起问答" aria-label="收起问答">${closeIcon}</button>
    </header>
    <div id="log" role="log" aria-label="问答记录"></div>
    <div class="welcome"><div>${chat}<h2>这道题，怎么想？</h2>
      <p>从一个提示开始，或输入你卡住的地方。</p></div></div>
    <footer>
      <div id="status" role="status"></div>
      <div class="tools"><button id="quick" type="button">${quickIcon}问问DS</button>
        <button id="next" type="button">下一题</button>
        <select id="effort" class="effort" aria-label="思考深度" title="思考深度">
          <option value="max" selected>深度 · 最高</option><option value="high">深度 · 高</option>
          <option value="low">深度 · 低</option><option value="none">关闭思考</option>
        </select></div>
      <select id="questions" hidden aria-label="选择要询问的题目"></select>
      <div class="attachments" hidden aria-label="待发送图片"></div>
      <input id="files" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple hidden>
      <form><button id="attach" class="icon-button" type="button" title="添加图片" aria-label="添加图片">${plusIcon}</button>
        <textarea aria-label="提问" placeholder="输入问题，或粘贴图片…" rows="1" maxlength="4000"></textarea>
        <button class="send" type="submit" title="发送" aria-label="发送">${sendIcon}</button></form>
      <p id="notice">仅「问问DS」自动截题；图片随消息发送。</p>
    </footer>
  </aside></body>`, 'text/html');
  shadow.append(...panel.body.childNodes);
  const get = selector => shadow.querySelector(selector);
  const pane = get('aside');
  const launcher = get('#launch');
  const quick = get('#quick');
  const list = get('#questions');
  const status = get('#status');
  const log = get('#log');
  const input = get('textarea');
  const send = get('button[type=submit]');
  const next = get('#next');
  const attach = get('#attach');
  const files = get('#files');
  const previews = get('.attachments');
  const effort = get('#effort');

  let url;
  let kind;
  let selected;
  let nodes = [];
  let turns = [];
  let attachments = [];
  let reading = 0;
  let generation = 0;
  let moving = false;
  let busy = false;

  function refreshRoute() {
    if (location.href === url) return;
    url = location.href;
    kind = ds.unit(url);
    host.hidden = !kind;
    selected = null;
    nodes = [];
    list.replaceChildren();
    list.hidden = true;
    next.hidden = !dsNavigation.supports(kind, url);
    resetConversation();
    if (kind && !pane.hidden && !busy) refreshQuestions();
  }

  function refreshQuestions() {
    nodes = questionCapture.candidates(kind);
    if (!nodes.includes(selected)) selected = questionCapture.preferred(nodes);
    const options = nodes.map((node, i) => {
      const text = questionCapture.textOf(node).replace(/\s+/g, ' ').slice(0, 42);
      return new Option(`${i + 1}. ${text || '图片题目'}`, String(i));
    });
    list.replaceChildren(...options);
    list.hidden = options.length < 2;
    if (selected) list.value = String(nodes.indexOf(selected));
    return selected;
  }

  function setOpen(open) {
    pane.hidden = !open;
    launcher.hidden = open;
    host.toggleAttribute('data-open', open);
    launcher.setAttribute('aria-expanded', String(open));
    if (open && !busy) refreshQuestions();
    (open ? get('#close') : launcher).focus();
  }

  let plainAnswers = false;
  const answerText = new WeakMap();

  function setPlainAnswers(enabled) {
    plainAnswers = enabled;
    for (const body of log.querySelectorAll('.answer-body')) {
      body.classList.toggle('plain', enabled);
      renderDsAnswer(body, answerText.get(body), enabled);
    }
  }

  function append(role, text, imageCount = 0) {
    const item = document.createElement('div');
    item.className = `message ${role}`;
    if (role === 'assistant') {
      const title = document.createElement('strong');
      title.className = 'message-label';
      title.textContent = 'DeepSeek';
      const body = document.createElement('div');
      body.className = plainAnswers ? 'answer-body plain' : 'answer-body';
      answerText.set(body, text);
      renderDsAnswer(body, text, plainAnswers);
      item.append(title, body);
    } else {
      item.append(text);
    }
    if (imageCount) {
      const count = document.createElement('small');
      count.textContent = `附有 ${imageCount} 张图片`;
      item.append(count);
    }
    log.append(item);
    log.scrollTop = log.scrollHeight;
  }

  function refreshControls() {
    for (const control of [quick, list, send, attach]) control.disabled = busy || moving || reading > 0;
    next.disabled = busy || moving;
  }

  function renderAttachments() {
    previews.replaceChildren(...attachments.map(file => {
      const preview = document.createElement('span');
      preview.className = 'attachment';
      const image = document.createElement('img');
      image.src = file.data;
      image.alt = file.name;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.title = `移除 ${file.name}`;
      remove.setAttribute('aria-label', remove.title);
      remove.append(get('#close svg').cloneNode(true));
      remove.addEventListener('click', () => {
        if (busy || moving) return;
        attachments = attachments.filter(item => item !== file);
        renderAttachments();
      });
      preview.append(image, remove);
      return preview;
    }));
    previews.hidden = !attachments.length;
  }

  function resetConversation() {
    generation++;
    turns = [];
    attachments = [];
    renderAttachments();
    log.replaceChildren();
    status.textContent = '';
    input.value = '';
  }

  async function addImages(images) {
    if (busy || moving || reading || !images.length) return;
    const session = generation;
    reading++;
    refreshControls();
    try {
      let refused = 0;
      for (const file of images) {
        if (attachments.length >= ds.maxAttachments || file.size > ds.maxImageBytes ||
            !/^image\/(?:jpeg|png|gif|webp)$/.test(file.type)) {
          refused++;
          continue;
        }
        const data = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = () => reject(new Error('无法读取图片。'));
          reader.readAsDataURL(file);
        });
        if (session !== generation) return;
        if (!attachments.some(item => item.data === data)) attachments.push({ data, name: file.name || '剪贴板图片' });
      }
      status.textContent = refused ? '最多 4 张，每张不超过 4 MB，支持 JPG、PNG、GIF、WebP。' : '';
    } catch (error) {
      if (session === generation) status.textContent = error.message;
    } finally {
      reading--;
      renderAttachments();
      refreshControls();
    }
  }

  async function ask(prompt, includeQuestion) {
    refreshRoute();
    if (!kind || busy || moving || reading || (!prompt && !attachments.length)) return;
    const here = url;
    const session = generation;
    const valid = () => session === generation && here === location.href;
    const current = { prompt, text: '', images: attachments.map(file => file.data) };
    const userImages = current.images.length;
    const draft = input.value;
    busy = true;
    refreshControls();
    try {
      if (includeQuestion) {
        const node = refreshQuestions();
        if (!node) throw new Error('未找到已显示的题目。请先打开图文正文或一道测试题。');
        status.textContent = '正在截取题目…';
        const images = await questionCapture.capture(node, here, host);
        if (!valid()) return;
        current.text = questionCapture.requestText(node).slice(0, ds.maxText);
        current.images = [...new Set([...images, ...current.images])];
      }
      status.textContent = effort.value === 'none' ? '正在等待 DeepSeek 回复…' : 'DeepSeek 正在思考…';
      const response = await browser.runtime.sendMessage({
        type: 'ds:ask', url: here, turns: includeQuestion ? [] : turns, current, effort: effort.value,
      });
      if (!valid()) return;
      if (!response?.answer) throw new Error(response?.error || 'DeepSeek 未返回回复。');
      if (includeQuestion) {
        turns = [];
        log.replaceChildren();
      }
      turns.push({ ...current, answer: response.answer });
      append('user', prompt, userImages);
      append('assistant', response.answer);
      if (!includeQuestion && input.value === draft) input.value = '';
      attachments = [];
      renderAttachments();
      status.textContent = '';
    } catch (error) {
      if (valid()) status.textContent = error.message;
    } finally {
      busy = false;
      refreshControls();
      refreshRoute();
    }
  }

  launcher.addEventListener('click', () => setOpen(true));
  quick.addEventListener('click', event => {
    if (event.isTrusted) ask(ds.quickPrompt, true);
  });
  get('form').addEventListener('submit', event => {
    event.preventDefault();
    if (event.isTrusted) ask(input.value.trim(), false);
  });
  attach.addEventListener('click', event => {
    if (event.isTrusted) files.click();
  });
  files.addEventListener('change', () => {
    addImages(Array.from(files.files));
    files.value = '';
  });
  input.addEventListener('paste', event => {
    if (!event.isTrusted) return;
    const images = Array.from(event.clipboardData.items)
      .filter(item => item.kind === 'file' && item.type.startsWith('image/'))
      .map(item => item.getAsFile()).filter(Boolean);
    if (!images.length) return;
    if (!event.clipboardData.getData('text/plain')) event.preventDefault();
    addImages(images);
  });
  get('#reset').addEventListener('click', () => {
    resetConversation();
    input.focus();
  });
  next.addEventListener('click', async event => {
    if (!event.isTrusted || busy || moving) return;
    moving = true;
    refreshControls();
    try {
      const message = await dsNavigation.next(kind);
      if (!message) {
        selected = null;
        resetConversation();
        if (kind) refreshQuestions();
      } else {
        status.textContent = message;
      }
    } catch (error) {
      status.textContent = error.message;
    } finally {
      moving = false;
      refreshControls();
    }
  });
  get('#close').addEventListener('click', () => setOpen(false));
  shadow.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !pane.hidden) {
      event.stopPropagation();
      setOpen(false);
    }
  });
  get('#settings').addEventListener('click', async event => {
    if (!event.isTrusted) return;
    try {
      if ((await browser.runtime.sendNativeMessage('rainclass', { type: 'settings' })) === true) return;
    } catch {
      // Treat an unavailable native delegate the same as a denied request.
    }
    status.textContent = '未能打开设置，请回到雨课堂题目页后重试。';
  });
  list.addEventListener('change', () => {
    selected = nodes[Number(list.value)];
    resetConversation();
  });
  document.addEventListener('click', event => {
    if (event.isTrusted && event.target.closest?.('.problems-aside .subject-item:not(.active)')) {
      selected = null;
      resetConversation();
    }
  }, true);

  document.body.append(host);
  refreshRoute();
  navigation.addEventListener('currententrychange', refreshRoute);
});
