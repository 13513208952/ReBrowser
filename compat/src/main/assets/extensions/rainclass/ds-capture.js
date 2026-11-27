const questionCapture = (() => {
  const richText = '.main-text-attachment .content-wrap .content';
  const examGroups = '.subject-item .exercise-item, .exam-result .result_item:has(.item-body)';
  const homeworkGroup = '.container-problem .subject-item:has(> .item-body > div > .problem-body)';
  const questionGroups = `.problem-common__main, ${examGroups}, ${homeworkGroup}`;
  const questions = `${questionGroups}, .problem-common__body, .problem-body, .question-content, ` +
    '.question-stem, .problem-stem, .container-problem .problem-content, .swiper-slide-active .problemContainer .courseware';
  const frameSelector = `${ds.exerciseFrame}, #iframeQuizId, .quiz-iframe, .exam-container iframe, .exam__cover iframe`;
  const clipping = /^(auto|scroll|hidden|clip|overlay)$/;
  const scrollable = /^(auto|scroll|overlay)$/;

  function framesFrom(doc) {
    const docs = [doc];
    for (const frame of doc.querySelectorAll(frameSelector)) {
      const child = frame.contentDocument;
      if (child?.body && frame.getClientRects().length && frame.clientWidth && frame.clientHeight) {
        docs.push(...framesFrom(child));
      }
    }
    return docs;
  }

  function candidates(kind) {
    if (kind === 'graph' || kind === 'forum') {
      return [...document.querySelectorAll(richText)].filter(rendered);
    }
    return framesFrom(document).flatMap(doc => {
      const matches = [...doc.querySelectorAll(questions)].filter(rendered);
      return matches.filter(node => {
        if (node.matches(questionGroups)) return !node.parentElement.closest(questionGroups) && parts(node).length > 0;
        if (node.closest(questionGroups)) return false;
        return !node.querySelector(questions) && Boolean(node.innerText.trim() || node.querySelector('img,svg,canvas'));
      });
    });
  }

  function rendered(element) {
    const box = element.getBoundingClientRect();
    return box.width > 0 && box.height > 0 && element.ownerDocument.defaultView.getComputedStyle(element).visibility === 'visible';
  }

  function parts(node) {
    if (node.matches('.problem-common__main')) {
      return [...node.querySelectorAll(':scope > .problem-common__body')].filter(rendered);
    }
    if (node.matches(homeworkGroup)) {
      return [...node.querySelectorAll(':scope > .item-type, :scope > .item-body > div > ' +
        ':is(.problem-body, ul.list-unstyled-radio, ul.list-unstyled-checkbox)')].filter(rendered);
    }
    if (node.matches(examGroups)) {
      const parts = [...node.querySelectorAll('.item-body > .exam-font, .item-body > .item-title, ' +
        '.item-body ul.list-unstyled-radio, .item-body ul.list-unstyled-checkbox')].filter(rendered);
      const shared = node.closest('.subject-item')?.parentElement?.querySelector(':scope > .dict_description');
      if (shared && rendered(shared)) parts.unshift(shared);
      return parts;
    }
    return [node];
  }

  function intersection(box, clip) {
    return {
      left: Math.max(box.left, clip.left), right: Math.min(box.right, clip.right),
      top: Math.max(box.top, clip.top), bottom: Math.min(box.bottom, clip.bottom),
    };
  }

  function clientBox(node) {
    const box = node.getBoundingClientRect();
    return { left: box.left + node.clientLeft, top: box.top + node.clientTop,
      right: box.left + node.clientLeft + node.clientWidth, bottom: box.top + node.clientTop + node.clientHeight };
  }

  function offset(box, dx, dy) {
    return { left: box.left + dx, right: box.right + dx, top: box.top + dy, bottom: box.bottom + dy };
  }

  function context(node) {
    const ancestors = [];
    let target = node;
    while (target) {
      const doc = target.ownerDocument;
      const win = doc.defaultView;
      for (let parent = target.parentElement; parent && parent !== doc.documentElement; parent = parent.parentElement) {
        if (parent === doc.scrollingElement) continue;
        const { overflowX, overflowY } = win.getComputedStyle(parent);
        if (clipping.test(overflowX) || clipping.test(overflowY)) {
          ancestors.push({ parent, overflowX, overflowY });
        }
      }
      ancestors.push({ win, parent: doc.scrollingElement, overflowY: 'auto' });
      target = win === window ? null : win.frameElement;
    }
    return ancestors;
  }

  function geometry(node, ancestors) {
    let box = node.getBoundingClientRect();
    let visible = box;
    for (const { parent, win, overflowX, overflowY } of ancestors) {
      if (win) {
        visible = intersection(visible, { left: 0, top: 0, right: win.innerWidth, bottom: win.innerHeight });
        if (win !== window) {
          const frameBox = clientBox(win.frameElement);
          const { left, top } = frameBox;
          box = offset(box, left, top);
          visible = intersection(offset(visible, left, top), frameBox);
        }
      } else {
        const clip = clientBox(parent);
        visible = intersection(visible, {
          left: clipping.test(overflowX) ? clip.left : -Infinity,
          right: clipping.test(overflowX) ? clip.right : Infinity,
          top: clipping.test(overflowY) ? clip.top : -Infinity,
          bottom: clipping.test(overflowY) ? clip.bottom : Infinity,
        });
      }
    }
    return { box, visible };
  }

  function preferred(nodes) {
    let best = null;
    let bestTop = Infinity;
    for (const node of nodes) {
      const { visible } = geometry(node, context(node));
      if (visible.bottom > visible.top && visible.right > visible.left && visible.top < bestTop) {
        best = node;
        bestTop = visible.top;
      }
    }
    return best ?? nodes[0];
  }

  function textOf(node) {
    return parts(node).map(part => part.innerText.trim()).filter(Boolean).join('\n\n');
  }

  function titleFor(node) {
    return (node.ownerDocument.querySelector('.learning-space-control-unit .unit-title') ??
      document.querySelector('.learning-space-control-unit .unit-title'));
  }

  function requestText(node) {
    return [titleFor(node)?.innerText.trim(), textOf(node)].filter(Boolean).join('\n\n');
  }

  const paint = () => new Promise(resolve => requestAnimationFrame(resolve));

  async function waitForImages(node) {
    const images = [...node.querySelectorAll('img')];
    const pending = images.filter(img => !img.complete || (img.loading === 'lazy' && !img.naturalWidth));
    const original = pending.map(img => img.getAttribute('loading'));
    try {
      for (const img of pending) img.loading = 'eager';
      const timer = AbortSignal.timeout(4500);
      if (pending.length) await Promise.race([
        Promise.allSettled(pending.map(img => img.decode())),
        new Promise(resolve => timer.addEventListener('abort', resolve, { once: true })),
      ]);
      if (images.some(img => (img.currentSrc || img.hasAttribute('src') || img.hasAttribute('srcset')) && !img.naturalWidth)) {
        throw new Error('题目图片尚未加载完成，请等图片显示后重试。');
      }
    } finally {
      pending.forEach((img, i) => original[i] === null ? img.removeAttribute('loading') : img.setAttribute('loading', original[i]));
    }
  }

  function scrollAhead(ancestors, pixels) {
    for (const { parent, overflowY } of ancestors) {
      if (!parent || !scrollable.test(overflowY)) continue;
      const before = parent.scrollTop;
      parent.scrollBy({ top: Math.max(1, pixels), behavior: 'instant' });
      if (parent.scrollTop > before) return true;
    }
    return false;
  }

  async function capturePart(node, url, host, maxImages) {
    const stable = () => {
      if (!node.isConnected || window.location.href !== url || document.visibilityState !== 'visible') {
        throw new Error('题目页已切换，请回到原题重试。');
      }
    };
    await waitForImages(node);
    stable();
    const ancestors = context(node);
    const positions = ancestors.filter(({ parent }) => parent)
      .map(({ parent }) => [parent, parent.scrollLeft, parent.scrollTop]);
    const images = [];
    const wasHidden = host.hasAttribute('data-capturing');
    host.setAttribute('data-capturing', '');
    try {
      node.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'instant' });
      let covered = 0;
      for (let attempts = 0; attempts < maxImages * 4; attempts++) {
        await paint();
        stable();
        const { box, visible } = geometry(node, ancestors);
        const from = Math.max(0, visible.top - box.top);
        const to = Math.min(box.bottom - box.top, visible.bottom - box.top);
        if (visible.left > box.left + 1 || visible.right < box.right - 1 || from > covered + 2) {
          throw new Error('题目被页面遮挡或裁切，请展开题目后重试。');
        }
        if (to > covered + 0.5) {
          if (images.length === maxImages) throw new Error('题目截图过长，请选择单道题再试。');
          const x = Math.ceil(visible.left + window.scrollX);
          const y = Math.ceil(visible.top + window.scrollY);
          const rect = { x, y, width: Math.floor(visible.right + window.scrollX) - x,
            height: Math.floor(visible.bottom + window.scrollY) - y };
          if (rect.width < 1 || rect.height < 1) throw new Error('题目尚未完整显示。');
          const result = await browser.runtime.sendMessage({ type: 'ds:capture', url, rect });
          stable();
          if (!result?.image) throw new Error(result?.error || '未能截取题目。');
          images.push(result.image);
          covered = to;
          if (to >= box.bottom - box.top - 1) return images;
        }
        if (!scrollAhead(ancestors, Math.max(1, Math.min(to - from - 2, box.bottom - box.top - from)))) break;
      }
      throw new Error('未能完整截取题目，请调整页面位置后重试。');
    } finally {
      for (const [parent, left, top] of positions.reverse()) parent.scrollTo({ left, top, behavior: 'instant' });
      if (!wasHidden) host.removeAttribute('data-capturing');
    }
  }

  async function compose(images) {
    if (images.length < 2) return images;
    const decoded = [];
    try {
      for (const image of images) {
        const bytes = Uint8Array.fromBase64(image.slice(image.indexOf(',') + 1));
        decoded.push(await createImageBitmap(new Blob([bytes], { type: 'image/jpeg' })));
      }
      const maxHeight = 4096;
      const composites = [];
      for (let start = 0; start < decoded.length;) {
        let height = 0;
        let width = 0;
        let end = start;
        while (end < decoded.length) {
          const image = decoded[end];
          const nextHeight = height + (height ? 12 : 0) + image.height;
          if (end > start && nextHeight > maxHeight) break;
          height = nextHeight;
          width = Math.max(width, image.width);
          end++;
        }
        const canvas = document.createElement('canvas');
        canvas.width = width + 24;
        canvas.height = height + 24;
        const context = canvas.getContext('2d');
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        let y = 12;
        for (; start < end; start++) {
          context.drawImage(decoded[start], 12, y);
          y += decoded[start].height + 12;
        }
        composites.push(canvas.toDataURL('image/jpeg', 0.94));
      }
      return composites;
    } finally {
      for (const image of decoded) image.close();
    }
  }

  async function capture(node, url, host) {
    const images = [];
    const title = titleFor(node);
    const sections = parts(node);
    if (title && rendered(title) && !sections.some(part => part.contains(title))) sections.unshift(title);
    for (const part of sections) {
      if (images.length === ds.maxImages) throw new Error('题目截图过长，请选择单道题再试。');
      images.push(...await capturePart(part, url, host, ds.maxImages - images.length));
    }
    return compose(images);
  }

  return { candidates, preferred, textOf, requestText, capture };
})();
