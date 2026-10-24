'use strict';
// The Windows top bar and its screens. The app sends its state; this draws it and passes clicks back.
(() => {
  const $ = (s) => document.querySelector(s);
  const pm = window.pm;

  const ICON = {
    lock: '<svg viewBox="0 0 24 24"><path d="M6 11h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1ZM8 11V8a4 4 0 0 1 8 0v3"/></svg>',
    open: '<svg viewBox="0 0 24 24"><path d="M6 11h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1ZM8 11V8a4 4 0 0 1 7.5-1.9"/></svg>',
    search: '<svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/></svg>',
    eye: '<svg viewBox="0 0 24 24"><path d="M2 12c3-5.2 6.4-7.25 10-7.25S19 6.8 22 12c-3 5.2-6.4 7.25-10 7.25S5 17.2 2 12Z" stroke="#e2ebe9" stroke-width="1.8"/>'
      + '<circle cx="12" cy="12" r="3.25" fill="#e2ebe9" stroke="none"/><path d="M4.5 4 19.5 20" stroke="#131b1e" stroke-width="5"/>'
      + '<path d="M4.5 4 19.5 20" stroke="#35d0bf" stroke-width="2.2"/></svg>',
    x: '<svg viewBox="0 0 24 24"><path d="M7 7l10 10M17 7 7 17"/></svg>',
    plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    back: '<svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
    home: '<svg viewBox="0 0 24 24"><path d="M4 11.5 12 5l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1Z"/></svg>',
    bookmark: '<svg viewBox="0 0 24 24"><path d="M7 4h10a1 1 0 0 1 1 1v15l-6-4.5L6 20V5a1 1 0 0 1 1-1Z"/></svg>',
  };

  let state = null;
  let screen = null; // 'tabs' | 'bookmarks' | 'settings' while one is open
  let confirmTimer = 0;

  // ---------- Top bar ----------
  const url = $('#url');
  const address = $('#address');

  function drawBar() {
    const now = state.current;
    $('#back').disabled = !state.canGoBack;
    $('#forward').disabled = !state.canGoForward;
    $('#tabs').textContent = state.tabs.length > 99 ? ':D' : String(state.tabs.length);
    $('#tabs').setAttribute('aria-label', state.tabs.length === 1 ? '1 tab open' : state.tabs.length + ' tabs open');
    const bookmark = $('#bookmark');
    bookmark.classList.toggle('on', state.bookmarked); // fills teal when the open page has a bookmark
    bookmark.title = state.bookmarked ? 'Bookmarks (this page is bookmarked)' : 'Bookmarks (Ctrl+D adds this page)';

    const lock = $('#lock');
    lock.className = 'lock' + (now.onHome || now.blocked ? ' search' : now.secure ? '' : ' open');
    lock.innerHTML = now.onHome || now.blocked ? ICON.search : now.secure ? ICON.lock : ICON.open;
    lock.title = now.onHome ? '' : now.secure ? 'Secure connection' : 'Not secure';
    if (document.activeElement !== url) showName();

    const bar = $('#progress i');
    if (state.progress < 100) {
      bar.style.opacity = '1';
      bar.style.transform = `scaleX(${Math.max(0.08, state.progress / 100)})`;
    } else {
      bar.style.transform = 'scaleX(1)';
      bar.style.opacity = '0';
    }
  }

  // "duckduckgo.com" while not typing, the full address while typing (like most browsers).
  function showName() {
    const now = state.current;
    url.classList.toggle('blocked', !!now.blocked);
    url.value = now.onHome ? '' : now.blocked ? 'Blocked: ' + now.blocked : now.host;
  }

  url.addEventListener('focus', () => {
    url.classList.remove('blocked');
    url.value = state && state.current.url ? state.current.url : '';
    $('#clear').hidden = !url.value;
    requestAnimationFrame(() => { if (document.activeElement === url) url.select(); }); // select() alone would grab focus back
  });
  url.addEventListener('input', () => { $('#clear').hidden = !url.value; });
  url.addEventListener('blur', () => { $('#clear').hidden = true; if (state) showName(); });
  url.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { url.blur(); }
  });
  address.addEventListener('submit', (e) => {
    e.preventDefault();
    const text = url.value.trim();
    if (!text) return;
    pm.open(text);
    url.blur();
  });
  $('#clear').addEventListener('mousedown', (e) => e.preventDefault()); // keep typing focus
  $('#clear').addEventListener('click', () => { url.value = ''; $('#clear').hidden = true; url.focus(); });

  $('#back').addEventListener('click', () => pm.back());
  $('#forward').addEventListener('click', () => pm.forward());
  $('#reload').addEventListener('click', () => pm.reload());
  $('#home').addEventListener('click', () => pm.home());
  $('#newtab').addEventListener('click', () => pm.newTab());
  $('#tabs').addEventListener('click', () => openScreen('tabs'));
  $('#bookmark').addEventListener('click', () => openScreen('bookmarks'));
  $('#settings').addEventListener('click', () => openScreen('settings'));

  pm.onFocusAddress(() => { closeScreen(); url.focus(); });

  // ---------- Screens ----------
  const overlay = $('#overlay');

  async function openScreen(name) {
    if (screen === name) return;
    const wasOpen = !!screen;
    screen = name;
    if (!wasOpen) await pm.overlay(true); // the page steps aside and its preview is taken
    overlay.classList.remove('closing');
    overlay.hidden = false;
    overlay.scrollTop = 0;
    drawScreen(true);
  }

  function closeScreen() {
    if (!screen) return;
    screen = null;
    overlay.classList.add('closing');
    setTimeout(() => {
      if (screen) return; // reopened meanwhile
      overlay.hidden = true;
      overlay.classList.remove('closing');
      overlay.innerHTML = '';
      pm.overlay(false);
    }, 190);
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && screen) closeScreen();
  });

  function drawScreen(animate) {
    if (!screen || !state) return;
    overlay.innerHTML = '';
    overlay.append(screen === 'tabs' ? tabsScreen(animate) : screen === 'bookmarks' ? bookmarksScreen(animate) : settingsScreen(animate));
  }

  // Small builder: el('div.class', {attrs}, children...)
  function el(spec, attrs, ...children) {
    const [tag, ...classes] = spec.split('.');
    const node = document.createElement(tag || 'div');
    if (classes.length) node.className = classes.join(' ');
    for (const [k, v] of Object.entries(attrs || {})) {
      if (k === 'html') node.innerHTML = v;
      else if (k === 'text') node.textContent = v;
      else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
      else if (v !== false && v != null) node.setAttribute(k, v === true ? '' : v);
    }
    for (const child of children) if (child) node.append(child);
    return node;
  }

  function head(title, sub, left, right) {
    return el('div.head', {}, left, el('div.titles', {}, el('h1', { text: title }), sub ? el('div.sub', { text: sub }) : null), ...right);
  }

  function closeButton(label) {
    return el('button.icon', { 'aria-label': label, title: label + ' (Esc)', html: ICON.x, onclick: closeScreen });
  }

  function backButton(label) {
    return el('button.icon', { 'aria-label': label, title: label + ' (Esc)', html: ICON.back, onclick: closeScreen });
  }

  // Private tab overview: cards with previews; nothing here is kept.
  function tabsScreen(animate) {
    const n = state.tabs.length;
    const grid = el('div.grid');
    state.tabs.forEach((tab, i) => {
      const shot = el('div.shot', tab.preview ? {} : { html: ICON.home });
      if (tab.preview) shot.style.backgroundImage = `url("${tab.preview}")`;
      const card = el('div.card' + (tab.id === state.current.id ? '.current' : '') + (animate ? '.rise' : ''), {
        role: 'button', tabindex: '0', 'aria-label': tab.label,
        onclick: () => { pm.switchTab(tab.id); closeScreen(); },
        onkeydown: (e) => { if (e.key === 'Enter') { pm.switchTab(tab.id); closeScreen(); } },
      },
      shot,
      el('div.name', { text: tab.label }),
      el('div.host', { html: ICON.eye }, document.createTextNode(tab.host)),
      el('button.x', {
        'aria-label': 'Close tab', title: 'Close tab',
        html: ICON.x,
        onclick: (e) => { e.stopPropagation(); card.classList.add('leaving'); setTimeout(() => pm.closeTab(tab.id), 170); },
      }));
      card.style.setProperty('--i', Math.min(i, 8));
      grid.append(card);
    });
    return el('div', {},
      head('Private tabs', (n === 1 ? '1 tab' : n + ' tabs') + ' · history-free', el('div.badge', { html: ICON.eye }), [
        el('button.pill.teal', { html: ICON.plus + 'New tab', onclick: () => { pm.newTab(); closeScreen(); } }),
        closeButton('Close tab overview'),
      ]),
      el('div.content', {}, grid,
        el('div.note', { html: ICON.eye }, document.createTextNode('These tabs keep no history. Pages, cookies and site data are wiped when you close PrivateMuch.')),
        el('div.center', {}, el('button.pill.outline', { text: 'Close all tabs', onclick: () => { pm.closeAllTabs(); closeScreen(); } }))));
  }

  // Bookmarks are only shortcuts: just the address is kept.
  function bookmarksScreen(animate) {
    const list = state.bookmarks;
    const n = list.length;
    const body = el('div.content.narrow');
    const now = state.current;
    if (!now.onHome && !now.blocked) {
      const right = state.bookmarkable || state.bookmarked
        ? el('button.pill' + (state.bookmarked ? '.outline' : '.teal'), {
          html: state.bookmarked ? `<span class="mark">${ICON.bookmark}</span>Bookmarked` : 'Bookmark',
          'aria-label': state.bookmarked ? 'Remove this page from bookmarks' : 'Bookmark this page',
          onclick: () => pm.toggleBookmark(),
        })
        : el('div.why', { text: "Searches can't be bookmarked" });
      body.append(el('div.this-page', {}, el('div.texts', {}, el('div.t0', { text: 'This page' }), el('div.t1', { text: now.label })), right));
    }
    if (n === 0) {
      body.append(el('div.empty', { html: ICON.bookmark }, document.createTextNode('No bookmarks yet. Open a site, then tap the bookmark button (or press Ctrl+D) to add a shortcut to it here.')));
    } else {
      const rows = el('div.list');
      list.forEach((b, i) => {
        const row = el('div.row' + (animate ? '.rise' : ''), {
          role: 'button', tabindex: '0', title: b.url,
          onclick: () => { pm.openBookmark(b.url); closeScreen(); },
          onkeydown: (e) => { if (e.key === 'Enter') { pm.openBookmark(b.url); closeScreen(); } },
        },
        el('span.mark', { html: ICON.bookmark }),
        el('div.texts', {}, el('div.t1', { text: b.host }), el('div.t2', { text: b.page })),
        el('button.icon.small', {
          'aria-label': 'Remove bookmark', title: 'Remove bookmark', html: ICON.x,
          onclick: (e) => { e.stopPropagation(); row.classList.add('leaving'); setTimeout(() => pm.removeBookmark(b.url), 170); },
        }));
        row.style.setProperty('--i', Math.min(i, 10));
        rows.append(row);
      });
      body.append(rows);
    }
    body.append(el('div.note', { text: "A bookmark is only a shortcut. Just the site's address is kept, on this computer inside PrivateMuch: no page data, cookies or history, and it's never sent anywhere. Opening one is a fresh, private visit." }));
    if (n) {
      const removeAll = el('button.pill.outline', {
        text: 'Remove all bookmarks',
        onclick: () => {
          if (confirmTimer) { clearTimeout(confirmTimer); confirmTimer = 0; pm.clearBookmarks(); return; }
          removeAll.textContent = 'Tap again to remove all'; // removed bookmarks can't be brought back, so ask once more
          confirmTimer = setTimeout(() => { confirmTimer = 0; removeAll.textContent = 'Remove all bookmarks'; }, 3000);
        },
      });
      body.append(el('div.center', {}, removeAll));
    }
    return el('div', {}, head('Bookmarks', n === 0 ? 'No bookmarks yet' : n === 1 ? '1 bookmark' : n + ' bookmarks', backButton('Close bookmarks'), []), body);
  }

  let updateText = '';
  function settingsScreen(animate) {
    const body = el('div.content.narrow', {}, el('div.label', { text: 'Search engine' }));
    state.engines.forEach((engine, i) => {
      const choice = el('button.choice' + (engine.id === state.engine ? '.on' : '') + (animate ? '.rise' : ''), {
        role: 'radio', 'aria-checked': String(engine.id === state.engine),
        onclick: () => {
          for (const c of body.querySelectorAll('.choice')) c.classList.toggle('on', c === choice);
          pm.setEngine(engine.id);
        },
      },
      el('span.radio'),
      el('div', {}, el('div.t1', { text: engine.title }), el('div.t2', { text: engine.description })));
      choice.style.setProperty('--i', i);
      body.append(choice);
    });
    body.append(el('div.small-note', { text: 'Your search engine and your bookmarks (just their addresses, as shortcuts) are the only things PrivateMuch remembers. Tabs, pages, cookies and site data are still wiped every time you close the app.' }));
    body.append(el('div.label', { text: 'Updates' }));
    const status = el('span.status', { id: 'updateStatus', text: updateText });
    body.append(el('div.update-row', {}, el('button.pill.outline', { text: 'Check for updates', onclick: () => pm.checkUpdate() }), status));
    body.append(el('div.version', { text: 'PrivateMuch ' + state.version }));
    return el('div', {}, head('Settings', null, backButton('Close settings'), []), body);
  }

  pm.onUpdateStatus(({ text, done }) => {
    updateText = text;
    const status = document.getElementById('updateStatus');
    if (status) status.textContent = text;
    if (done) setTimeout(() => { if (updateText === text) { updateText = ''; const s = document.getElementById('updateStatus'); if (s) s.textContent = ''; } }, 4000);
  });

  // ---------- Messages ----------
  let toastTimer = 0;
  pm.onToast((text) => {
    const toast = $('#toast');
    toast.textContent = text;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
  });

  pm.onState((next) => {
    state = next;
    drawBar();
    if (screen) drawScreen(false);
  });
  pm.ready();
})();
