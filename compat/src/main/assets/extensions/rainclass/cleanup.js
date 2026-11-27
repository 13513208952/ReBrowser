// Hide the site's "返回旧版" switch wherever the redesigned shells surface it.
(() => {
  const hide = () => {
    if (!document.body) return;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      if (walker.currentNode.nodeValue.trim() !== '返回旧版') continue;
      const text = walker.currentNode.parentElement;
      if (!text) continue;
      const target = text.closest('a, button, [role="button"], li') || text;
      target.style.setProperty('display', 'none', 'important');
    }
  };
  let pending;
  const schedule = () => {
    clearTimeout(pending);
    pending = setTimeout(hide, 250);
  };
  hide();
  new MutationObserver(schedule).observe(document.documentElement,
      { childList: true, subtree: true, characterData: true });
})();
