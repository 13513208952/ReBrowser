function nextDirectoryEntry(nav, eligible) {
  const collapsed = '.nav-item-title:not(.is-expand):has(.expand-icon)';
  const entries = [...nav.querySelectorAll(`.leaf-item, ${collapsed}`)];
  const current = entries.findIndex(entry => entry.matches('.leaf-item.is-active'));
  if (current < 0) return null;
  const available = entry => entry.matches(collapsed) || (
    eligible.test(entry.querySelector('.leaf-item-tag')?.textContent.trim()) &&
    entry.querySelector('.leaf-item-status') &&
    !entry.querySelector('.leaf-item-status :is(.icon-suoding, .icon-yuanquangou-mianzhuang)'));
  return entries.slice(current + 1).find(available) ?? entries.slice(0, current).findLast(available) ?? null;
}
