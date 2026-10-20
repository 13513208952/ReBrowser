/* Inert offline TEXT presentation only. Native source/account/document authority stays outside.
 * No network, HTML parsing, postMessage, file or native command channel. */
(function () {
  'use strict';
  var MAX_TOTAL = 1024 * 1024, MAX_PARAGRAPHS = 2000, MAX_HITS = 200;
  var paragraphs = [], rendering = false;
  function element(id) { var node = document.getElementById(id); if (!node) throw new Error('reader structure missing'); return node; }
  function text(value, max) {
    if (typeof value !== 'string' || value.length > max) throw new Error('invalid bounded text');
    for (var i = 0; i < value.length; i++) { var c = value.charCodeAt(i); if (c >= 0xD800 && c <= 0xDBFF) { if (++i >= value.length || value.charCodeAt(i) < 0xDC00 || value.charCodeAt(i) > 0xDFFF) throw new Error('invalid UTF16'); } else if (c >= 0xDC00 && c <= 0xDFFF) throw new Error('invalid UTF16'); }
    return value;
  }
  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }
  function status(kind, message) { var n = element('status'); n.setAttribute('data-state', kind); n.textContent = text(message, 256); }
  function prepared(payload) {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload) || (Object.getPrototypeOf(payload) !== Object.prototype && Object.getPrototypeOf(payload) !== null)) throw new Error('plain payload required');
    var keys = Object.keys(payload), allowed = ['subject', 'meta', 'paragraphs', 'attachments'];
    keys.forEach(function (key) { var d = Object.getOwnPropertyDescriptor(payload, key); if (allowed.indexOf(key) < 0 || !d || d.get || d.set) throw new Error('unexpected payload field'); });
    var subject = text(payload.subject === undefined ? '离线邮件' : payload.subject, 512), meta = text(payload.meta === undefined ? '' : payload.meta, 256);
    var source = payload.paragraphs === undefined ? [] : payload.paragraphs, attachments = payload.attachments === undefined ? [] : payload.attachments;
    if (!Array.isArray(source) || source.length > MAX_PARAGRAPHS || !Array.isArray(attachments) || attachments.length > 100) throw new Error('list limit');
    var total = subject.length + meta.length, copy = [], names = [];
    for (var i = 0; i < source.length; i++) { var s = text(source[i], 20000); total += s.length; if (total > MAX_TOTAL) throw new Error('body limit'); copy.push(s); }
    for (var j = 0; j < attachments.length; j++) { var name = text(attachments[j], 256); total += name.length; if (total > MAX_TOTAL) throw new Error('body limit'); names.push(name); }
    return { subject: subject, meta: meta, paragraphs: copy, attachments: names };
  }
  function bodyFragment(lines, needle) {
    var fragment = document.createDocumentFragment(), hits = 0;
    lines.forEach(function (line) {
      var p = document.createElement('p'), at = 0, next;
      while (needle && hits < MAX_HITS && (next = line.indexOf(needle, at)) >= 0) { p.appendChild(document.createTextNode(line.slice(at, next))); var mark = document.createElement('mark'); mark.className = 'search-hit'; mark.textContent = needle; p.appendChild(mark); at = next + needle.length; hits++; }
      p.appendChild(document.createTextNode(line.slice(at))); fragment.appendChild(p);
    }); return fragment;
  }
  function attachmentFragment(names) { var fragment = document.createDocumentFragment(); if (names.length) { var heading = document.createElement('h2'); heading.textContent = '附件（仅显示名称）'; fragment.appendChild(heading); var ul = document.createElement('ul'); names.forEach(function (name) { var li = document.createElement('li'); li.textContent = name; ul.appendChild(li); }); fragment.appendChild(ul); } return fragment; }
  function reset(kind, reason) { paragraphs = []; element('subject').textContent = '离线邮件'; element('meta').textContent = ''; clear(element('body')); clear(element('attachments')); element('attachments').hidden = true; status(kind, text(reason, 256)); }
  function render(payload) {
    if (rendering) throw new Error('reentrant render'); rendering = true;
    try { var data = prepared(payload), body = bodyFragment(data.paragraphs, null), attachments = attachmentFragment(data.attachments); element('subject').textContent = data.subject; element('meta').textContent = data.meta; clear(element('body')); clear(element('attachments')); element('body').appendChild(body); element('attachments').appendChild(attachments); element('attachments').hidden = !data.attachments.length; paragraphs = data.paragraphs; status(paragraphs.length ? 'ready' : 'empty', paragraphs.length ? '纯文本正文 · 不加载远程内容' : '没有可显示的正文'); }
    catch (failure) { reset('error', '内容不可显示，请从原生窗口重新读取'); throw failure; }
    finally { rendering = false; }
  }
  function showUnavailable(reason) { reset('unknown', reason === undefined ? '离线内容不可用' : text(reason, 256)); }
  function showFailure(reason) { reset('error', reason === undefined ? '读取失败' : text(reason, 256)); }
  function highlight(literal) { var needle = text(literal, 64); if (!needle.length) throw new Error('empty literal'); var body = bodyFragment(paragraphs, needle); clear(element('body')); element('body').appendChild(body); }
  window.ReBrowserReader = Object.freeze({ render: render, showUnavailable: showUnavailable, showFailure: showFailure, highlight: highlight });
  showUnavailable('等待原生离线邮件内容');
})();
