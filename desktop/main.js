'use strict';
// PrivateMuch for Windows: the same private search page and browser as the Android app.
// Every site runs in a memory-only session, so nothing from browsing ever touches the disk.
const { app, BrowserWindow, WebContentsView, Menu, clipboard, ipcMain, nativeTheme, net, session } = require('electron');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const filter = require('./lib/filter.js');
const { Store } = require('./lib/store.js');

const INK = '#0B1012';
const TOOLBAR = 52;   // height of the top bar (it doubles as the window's title bar)
const MAX_TABS = 12;  // same limit as the Android app
const HOME_FILE = path.join(__dirname, 'web', 'index.html');
const BLOCKED_FILE = path.join(__dirname, 'chrome', 'blocked.html');
const RELEASE_API = 'https://api.github.com/repos/NucleanYT/PrivateMuch/releases/tags/latest';

// ---------- One window at a time ----------
// Opening PrivateMuch again brings the open window to the front. A second copy would also delete the first
// one's temporary folder (below) while it's still in use. main.js is a CommonJS module, so `return` stops it here.
if (!app.requestSingleInstanceLock()) {
  app.quit();
  return;
}
app.on('second-instance', () => {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
});

// ---------- Nothing left behind ----------
// Chromium's own files (GPU cache, crash reports, local state) go to a fresh temporary folder that's
// deleted when the app closes. Leftovers from a run that crashed are deleted on the next start.
const PROFILE_PREFIX = 'PrivateMuch-profile-';
const SETUP_PREFIX = 'PrivateMuch-Setup-'; // installers downloaded by Check for updates
for (const name of safeList(os.tmpdir())) {
  if (name.startsWith(PROFILE_PREFIX) || name.startsWith(SETUP_PREFIX)) removeFolder(path.join(os.tmpdir(), name));
}
const profile = fs.mkdtempSync(path.join(os.tmpdir(), PROFILE_PREFIX));
app.setPath('userData', profile);
app.setPath('sessionData', profile);
let installingUpdate = false;
app.on('will-quit', () => {
  removeFolder(profile);
  // The helper below runs from the app's own folder, which the installer is about to replace; the next start
  // deletes the leftovers instead.
  if (installingUpdate) return;
  // Chromium still writes a few empty bookkeeping files while it shuts down, after this point. A tiny helper
  // (the app's own program, run as plain Node) waits for the app to exit, then deletes the folder for good.
  try {
    const script = 'const fs=require("fs");const pid=' + process.pid + ';const dir=' + JSON.stringify(profile) + ';'
      + 'const gone=()=>{try{fs.rmSync(dir,{recursive:true,force:true})}catch(e){}process.exit(0)};'
      + 'setInterval(()=>{try{process.kill(pid,0)}catch(e){setTimeout(gone,300)}},200);setTimeout(gone,30000);';
    spawn(process.execPath, ['-e', script], {
      detached: true, stdio: 'ignore', windowsHide: true, env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' },
    }).unref();
  } catch (err) {
    // if the helper can't start, the next launch deletes the leftovers
  }
});

// Chromium normally phones home on its own (component updates and other background requests to Google).
// PrivateMuch only connects to the sites you open.
app.commandLine.appendSwitch('disable-component-update');
app.commandLine.appendSwitch('disable-background-networking');
app.commandLine.appendSwitch('disable-domain-reliability');
app.commandLine.appendSwitch('no-pings');

const store = new Store(path.join(app.getPath('appData'), 'PrivateMuch'));

let win;
let tabsSession;
const tabs = [];
let current = null;
let nextId = 1;
let overlayOpen = false;
let fullScreenTab = null;

// ---------- Startup ----------

app.whenReady().then(() => {
  nativeTheme.themeSource = 'dark';
  Menu.setApplicationMenu(null);

  // No "persist:" in the name, so Chromium keeps this session in memory only.
  tabsSession = session.fromPartition('privatemuch-tabs');
  // The spell checker would download dictionaries from Google and learn words you type; both sessions go without it.
  for (const s of [session.defaultSession, tabsSession]) {
    s.setSpellCheckerEnabled(false);
    s.setSpellCheckerDictionaryDownloadURL('http://127.0.0.1:9/');
  }
  tabsSession.setPermissionRequestHandler((wc, permission, callback) => {
    callback(permission === 'fullscreen' || permission === 'clipboard-sanitized-write');
  });
  tabsSession.setPermissionCheckHandler((wc, permission) => permission === 'fullscreen' || permission === 'clipboard-sanitized-write');
  tabsSession.webRequest.onBeforeRequest({ urls: ['<all_urls>'] }, onRequest);
  tabsSession.on('will-download', (event, item) => {
    // Downloads still work, but the file goes where you choose; nothing about it is remembered afterwards.
    item.once('done', () => sendState());
  });

  win = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 560,
    minHeight: 460,
    show: false,
    title: 'PrivateMuch',
    backgroundColor: INK,
    icon: path.join(__dirname, 'web', 'icons', 'icon-512.png'),
    titleBarStyle: 'hidden', // the top bar draws its own minimize / maximize / close buttons
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      spellcheck: false,
    },
  });
  win.loadFile(path.join(__dirname, 'chrome', 'index.html'));
  win.once('ready-to-show', () => win.show());
  // If the first frame never arrives (a stuck graphics driver, for one), show the window anyway rather than
  // leave PrivateMuch running with nothing on screen.
  setTimeout(() => { if (!win.isDestroyed() && !win.isVisible()) win.show(); }, 3000);
  win.on('resize', layout);
  win.on('maximize', sendState);
  win.on('unmaximize', sendState);
  win.webContents.on('before-input-event', (event, input) => {
    if (shortcut(input, null)) event.preventDefault();
  });

  newTab();
});

app.on('window-all-closed', () => app.quit());

// ---------- Privacy and the adult filter, for every request any tab makes ----------

function onRequest(details, callback) {
  const url = details.url;
  if (!/^https?:/i.test(url)) return callback({});
  if (details.resourceType === 'mainFrame') {
    const typed = filter.fromSearchBox(url); // a web address typed into the search engine's box opens the site
    if (typed) return callback({ redirectURL: typed });
    if (filter.isBlockedUrl(url)) {
      callback({ cancel: true });
      const tab = tabs.find((t) => t.view.webContents.id === details.webContentsId);
      if (tab) setImmediate(() => showBlocked(tab, url));
      return;
    }
    const safe = filter.withSafeSearch(url); // every DuckDuckGo and Brave search gets strict Safe Search
    if (safe) return callback({ redirectURL: safe });
    return callback({});
  }
  // Pages, frames, images and scripts from adult sites can't load at all.
  callback(filter.isBlockedUrl(url) ? { cancel: true } : {});
}

function showBlocked(tab, url) {
  tab.blockedSite = filter.displayName(url);
  tab.view.webContents.loadFile(BLOCKED_FILE, { query: { site: tab.blockedSite } });
}

// ---------- Tabs ----------

function homeUrlQuery() {
  return store.engine === 'brave' ? { engine: 'brave' } : {};
}

function loadHome(tab) {
  tab.view.webContents.loadFile(HOME_FILE, { query: homeUrlQuery() });
}

// File addresses always use forward slashes, also on Windows.
function isHome(url) {
  return !url || (url.startsWith('file:') && url.includes('/web/index.html'));
}

function isBlockedPage(url) {
  return !!url && url.startsWith('file:') && url.includes('/chrome/blocked.html');
}

function newTab(url) {
  if (tabs.length >= MAX_TABS) {
    send('toast', `You can have up to ${MAX_TABS} tabs. Close one first.`);
    return null;
  }
  const view = new WebContentsView({
    webPreferences: { session: tabsSession, contextIsolation: true, sandbox: true, spellcheck: false },
  });
  view.setBackgroundColor(INK);
  const tab = { id: nextId++, view, title: '', preview: null, blockedSite: null, progress: 100, clearHistoryOnHome: false };
  tabs.push(tab);
  const wc = view.webContents;

  wc.setWindowOpenHandler(({ url: target }) => { // links that want a new window open in a new tab
    if (/^https?:/i.test(target)) newTab(target);
    return { action: 'deny' };
  });
  wc.on('will-navigate', (event, target) => {
    // Stay inside the app for web pages; ignore app links (mailto:, tel:, ms-settings: ...).
    if (!/^(https?|file):/i.test(target)) event.preventDefault();
  });
  wc.on('did-start-navigation', (details) => {
    if (details.isMainFrame && !details.isSameDocument && !isBlockedPage(details.url)) tab.blockedSite = null;
  });
  wc.on('did-start-loading', () => { tab.progress = 10; sendState(); });
  wc.on('dom-ready', () => { tab.progress = Math.max(tab.progress, 70); sendState(); });
  wc.on('did-stop-loading', () => {
    tab.progress = 100;
    if (tab.clearHistoryOnHome && isHome(wc.getURL())) { // after Home, Back doesn't reopen the site
      wc.navigationHistory.clear();
      tab.clearHistoryOnHome = false;
    }
    sendState();
  });
  for (const name of ['did-navigate', 'did-navigate-in-page', 'page-title-updated']) wc.on(name, () => {
    tab.title = wc.getTitle();
    sendState();
  });
  wc.on('enter-html-full-screen', () => { fullScreenTab = tab; layout(); });
  wc.on('leave-html-full-screen', () => { fullScreenTab = null; layout(); });
  wc.on('before-input-event', (event, input) => {
    if (shortcut(input, tab)) event.preventDefault();
  });
  wc.on('context-menu', (event, params) => pageMenu(tab, params));

  if (url) wc.loadURL(url); else loadHome(tab);
  switchTo(tab);
  return tab;
}

function switchTo(tab) {
  if (current === tab) return layout();
  if (current) {
    capturePreview(current);
    win.contentView.removeChildView(current.view); // hidden tabs are slowed down by Chromium, like paused tabs on Android
  }
  current = tab;
  if (!overlayOpen) win.contentView.addChildView(tab.view);
  layout();
  sendState();
}

function closeTab(tab) {
  const index = tabs.indexOf(tab);
  if (index < 0) return;
  const wasCurrent = tab === current;
  tabs.splice(index, 1);
  win.contentView.removeChildView(tab.view);
  tab.view.webContents.close();
  if (wasCurrent) current = null; // so switching away never touches the closed tab
  if (tabs.length === 0) {
    newTab(); // closing the last tab leaves a fresh one
  } else if (wasCurrent) {
    switchTo(tabs[Math.max(0, index - 1)]);
  }
  sendState();
}

function closeAllTabs() {
  for (const tab of tabs.splice(0)) {
    win.contentView.removeChildView(tab.view);
    tab.view.webContents.close();
  }
  current = null;
  newTab();
}

function cycleTab(step) {
  if (tabs.length < 2) return;
  const i = tabs.indexOf(current);
  switchTo(tabs[(i + step + tabs.length) % tabs.length]);
}

function layout() {
  if (!win || !current) return;
  const [width, height] = win.getContentSize();
  const top = fullScreenTab === current ? 0 : TOOLBAR;
  current.view.setBounds({ x: 0, y: top, width, height: Math.max(0, height - top) });
}

async function capturePreview(tab) {
  try {
    const image = await tab.view.webContents.capturePage();
    if (!image.isEmpty()) tab.preview = image.resize({ width: 420 }).toDataURL();
  } catch (err) {
    // a tab that's still loading can't be pictured yet; the overview shows its icon instead
  }
}

// ---------- What the top bar shows ----------

function stateOf(tab) {
  const url = tab.view.webContents.getURL();
  const onHome = isHome(url);
  const blocked = isBlockedPage(url);
  const realUrl = blocked ? '' : url;
  return {
    id: tab.id,
    onHome,
    blocked: blocked ? tab.blockedSite || 'This site' : null,
    url: onHome ? '' : realUrl,
    host: onHome ? 'PrivateMuch' : blocked ? tab.blockedSite || '' : filter.displayName(realUrl),
    label: onHome ? 'New tab' : blocked ? 'Blocked' : labelOf(tab, realUrl),
    secure: /^https:/i.test(realUrl),
    preview: tab.preview,
  };
}

function labelOf(tab, url) {
  const title = tab.title || '';
  return !title || /^https?:/i.test(title) ? filter.displayName(url) : title;
}

let pending = false;
function sendState() {
  if (pending) return;
  pending = true;
  setImmediate(() => {
    pending = false;
    if (!win || win.isDestroyed() || !current) return;
    const now = stateOf(current);
    const wc = current.view.webContents;
    const url = now.url;
    send('state', {
      tabs: tabs.map(stateOf),
      current: now,
      canGoBack: wc.navigationHistory.canGoBack(),
      canGoForward: wc.navigationHistory.canGoForward(),
      progress: current.progress,
      bookmarked: !!url && store.hasBookmark(url),
      bookmarkable: !!url && !filter.isSearchPage(url) && !filter.isBlockedUrl(url),
      bookmarks: store.bookmarks.map((u) => ({ url: u, host: filter.displayName(u), page: pageOf(u) })),
      engine: store.engine,
      engines: Object.values(filter.ENGINES).map(({ id, title, description }) => ({ id, title, description })),
      version: app.getVersion(),
      maximized: win.isMaximized(),
    });
  });
}

// The page part of the address ("/wiki/Duck"), or "Home page" for the site's front page.
function pageOf(url) {
  try {
    const p = new URL(url).pathname;
    return !p || p === '/' ? 'Home page' : decodeURI(p);
  } catch (err) {
    return '';
  }
}

function send(channel, data) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, data);
}

// ---------- What the top bar and screens ask for ----------

ipcMain.on('ready', () => sendState());

// Typed into the address bar: the same rules as the home page's search box.
ipcMain.on('open', (event, typed) => {
  const text = String(typed || '').trim();
  if (!text) return;
  if (filter.isBlockedText(text)) return send('toast', 'Adult searches are blocked.');
  const address = filter.toUrl(text);
  if (address && filter.isBlockedUrl(address)) return send('toast', 'Adult sites are blocked.');
  current.view.webContents.loadURL(address || filter.ENGINES[store.engine].searchUrl(text));
  current.view.webContents.focus();
});

ipcMain.on('back', () => { if (current.view.webContents.navigationHistory.canGoBack()) current.view.webContents.navigationHistory.goBack(); });
ipcMain.on('forward', () => { if (current.view.webContents.navigationHistory.canGoForward()) current.view.webContents.navigationHistory.goForward(); });
ipcMain.on('reload', () => current.view.webContents.reload());
ipcMain.on('home', () => goHome());
ipcMain.on('new-tab', () => newTab());
ipcMain.on('switch-tab', (event, id) => { const tab = tabs.find((t) => t.id === id); if (tab) switchTo(tab); });
ipcMain.on('close-tab', (event, id) => { const tab = tabs.find((t) => t.id === id); if (tab) closeTab(tab); });
ipcMain.on('close-all-tabs', () => closeAllTabs());

// The top bar's own minimize / maximize / close buttons.
ipcMain.on('window', (event, action) => {
  if (action === 'minimize') win.minimize();
  else if (action === 'maximize') win.isMaximized() ? win.unmaximize() : win.maximize();
  else if (action === 'close') win.close();
});

// The tab overview, Bookmarks and Settings cover the page, so the page steps aside while one is open.
ipcMain.handle('overlay', async (event, open) => {
  if (open && !overlayOpen) {
    await capturePreview(current);
    overlayOpen = true;
    win.contentView.removeChildView(current.view);
  } else if (!open && overlayOpen) {
    overlayOpen = false;
    win.contentView.addChildView(current.view);
    layout();
  }
  sendState();
});

ipcMain.on('toggle-bookmark', () => toggleBookmark());
ipcMain.on('open-bookmark', (event, url) => { if (typeof url === 'string' && /^https?:/i.test(url)) current.view.webContents.loadURL(url); });
ipcMain.on('remove-bookmark', (event, url) => { store.removeBookmark(url); sendState(); });
ipcMain.on('clear-bookmarks', () => { store.clearBookmarks(); sendState(); });

ipcMain.on('set-engine', (event, id) => {
  if (id === store.engine || !filter.ENGINES[id]) return;
  store.setEngine(id);
  for (const tab of tabs) {
    if (isHome(tab.view.webContents.getURL())) loadHome(tab); // tabs on the home page show the new engine
  }
  sendState();
});

ipcMain.on('check-update', () => checkForUpdate());

function goHome() {
  current.clearHistoryOnHome = true;
  loadHome(current);
}

/** Adds a shortcut to the open page, or removes it. Search results and blocked sites can't be bookmarked. */
function toggleBookmark() {
  const s = stateOf(current);
  if (!s.url) return;
  if (store.hasBookmark(s.url)) {
    store.removeBookmark(s.url);
  } else if (filter.isSearchPage(s.url)) {
    send('toast', "Searches can't be bookmarked, so they're never kept.");
  } else if (filter.isBlockedUrl(s.url)) {
    send('toast', "Adult sites can't be bookmarked.");
  } else if (!store.addBookmark(s.url)) {
    send('toast', 'You can have up to 200 bookmarks. Remove some first.');
  }
  sendState();
}

// ---------- Keyboard shortcuts (work whether the page or the top bar has focus) ----------

function shortcut(input, tab) {
  if (input.type !== 'keyDown') return false;
  const ctrl = input.control || input.meta;
  const key = input.key.toLowerCase();
  const wc = current && current.view.webContents;
  if (ctrl && key === 't') { newTab(); return true; }
  if (ctrl && key === 'w') { closeTab(current); return true; }
  if ((ctrl && key === 'l') || key === 'f6' || (input.alt && key === 'd')) { focusAddressBar(); return true; }
  if ((ctrl && key === 'r') || key === 'f5') { wc.reload(); return true; }
  if (ctrl && key === 'd') { toggleBookmark(); return true; }
  if (ctrl && key === 'tab') { cycleTab(input.shift ? -1 : 1); return true; }
  if (input.alt && key === 'arrowleft') { if (wc.navigationHistory.canGoBack()) wc.navigationHistory.goBack(); return true; }
  if (input.alt && key === 'arrowright') { if (wc.navigationHistory.canGoForward()) wc.navigationHistory.goForward(); return true; }
  if (input.alt && key === 'home') { goHome(); return true; }
  if (ctrl && (key === '=' || key === '+')) { wc.setZoomLevel(Math.min(wc.getZoomLevel() + 0.5, 5)); return true; }
  if (ctrl && key === '-') { wc.setZoomLevel(Math.max(wc.getZoomLevel() - 0.5, -3)); return true; }
  if (ctrl && key === '0') { wc.setZoomLevel(0); return true; }
  if (key === 'escape' && tab && fullScreenTab === tab) { tab.view.webContents.executeJavaScript('document.exitFullscreen && document.exitFullscreen()'); return true; }
  return false;
}

function focusAddressBar() {
  win.webContents.focus();
  send('focus-address');
}

// ---------- Right-click menu on pages ----------

function pageMenu(tab, params) {
  const wc = tab.view.webContents;
  const items = [];
  if (params.linkURL && /^https?:/i.test(params.linkURL)) {
    items.push({ label: 'Open link in new tab', click: () => newTab(params.linkURL) });
    items.push({ label: 'Copy link address', click: () => clipboard.writeText(params.linkURL) });
    items.push({ type: 'separator' });
  }
  if (params.isEditable) {
    items.push({ label: 'Cut', role: 'cut', enabled: params.editFlags.canCut });
    items.push({ label: 'Copy', role: 'copy', enabled: params.editFlags.canCopy });
    items.push({ label: 'Paste', role: 'paste', enabled: params.editFlags.canPaste });
    items.push({ label: 'Select all', role: 'selectAll' });
  } else if (params.selectionText.trim()) {
    const text = params.selectionText.trim();
    items.push({ label: 'Copy', role: 'copy' });
    if (!filter.isBlockedText(text)) {
      const shortText = text.length > 30 ? text.slice(0, 30) + '…' : text;
      items.push({ label: `Search for "${shortText}"`, click: () => newTab(filter.ENGINES[store.engine].searchUrl(text)) });
    }
  } else {
    items.push({ label: 'Back', enabled: wc.navigationHistory.canGoBack(), click: () => wc.navigationHistory.goBack() });
    items.push({ label: 'Forward', enabled: wc.navigationHistory.canGoForward(), click: () => wc.navigationHistory.goForward() });
    items.push({ label: 'Reload', click: () => wc.reload() });
  }
  Menu.buildFromTemplate(items).popup({ window: win });
}

// ---------- Update (only when you press the button; nothing checks in the background) ----------

let updating = false;
async function checkForUpdate() {
  if (updating) return;
  updating = true;
  const status = (text, done) => send('update-status', { text, done: !!done });
  try {
    status('Checking…');
    const response = await net.fetch(RELEASE_API, { headers: { 'User-Agent': 'PrivateMuch', Accept: 'application/vnd.github+json' } });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const release = await response.json();
    const match = /build (\d+)/.exec(release.name || '');
    const latest = match ? Number(match[1]) : -1;
    const mine = Number(app.getVersion().split('.').pop());
    const asset = (release.assets || []).find((a) => a.name === 'PrivateMuch-Setup.exe');
    if (!asset || latest <= mine) return status('Up to date', true);
    if (process.platform !== 'win32') return status(`Build ${latest} is out (Windows only)`, true);

    const file = path.join(os.tmpdir(), `PrivateMuch-Setup-${latest}.exe`);
    const download = await net.fetch(asset.browser_download_url, { headers: { 'User-Agent': 'PrivateMuch' } });
    if (!download.ok) throw new Error('HTTP ' + download.status);
    const total = Number(download.headers.get('content-length')) || asset.size || 0;
    const out = fs.createWriteStream(file);
    const reader = download.body.getReader();
    let received = 0;
    let shown = -1;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      out.write(Buffer.from(value));
      received += value.length;
      const percent = total ? Math.floor((received * 100) / total) : 0;
      if (percent !== shown) { shown = percent; status(`Downloading ${percent}%`); }
    }
    await new Promise((resolve, reject) => out.end((err) => (err ? reject(err) : resolve())));
    status('Installing…');
    // The installer replaces this version and starts the new one; this window closes so it can. --updated is the
    // installer's update mode: it waits for PrivateMuch to close instead of asking you to close it.
    installingUpdate = true;
    spawn(file, ['--updated'], { detached: true, stdio: 'ignore' }).unref();
    setTimeout(() => app.quit(), 600);
  } catch (err) {
    status("Couldn't update", true);
  } finally {
    updating = false;
  }
}

// ---------- Helpers ----------

function safeList(dir) {
  try { return fs.readdirSync(dir); } catch (err) { return []; }
}

function removeFolder(dir) {
  try { fs.rmSync(dir, { recursive: true, force: true }); } catch (err) { /* in use: removed on the next start */ }
}
