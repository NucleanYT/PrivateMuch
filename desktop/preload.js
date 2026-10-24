'use strict';
// The only things the top bar and its screens can ask the app to do.
const { contextBridge, ipcRenderer } = require('electron');

const on = (channel) => (callback) => ipcRenderer.on(channel, (event, data) => callback(data));

contextBridge.exposeInMainWorld('pm', {
  ready: () => ipcRenderer.send('ready'),
  onState: on('state'),
  onToast: on('toast'),
  onFocusAddress: on('focus-address'),
  onUpdateStatus: on('update-status'),
  open: (text) => ipcRenderer.send('open', String(text)),
  back: () => ipcRenderer.send('back'),
  forward: () => ipcRenderer.send('forward'),
  reload: () => ipcRenderer.send('reload'),
  home: () => ipcRenderer.send('home'),
  newTab: () => ipcRenderer.send('new-tab'),
  switchTab: (id) => ipcRenderer.send('switch-tab', Number(id)),
  closeTab: (id) => ipcRenderer.send('close-tab', Number(id)),
  closeAllTabs: () => ipcRenderer.send('close-all-tabs'),
  overlay: (open) => ipcRenderer.invoke('overlay', !!open),
  toggleBookmark: () => ipcRenderer.send('toggle-bookmark'),
  openBookmark: (url) => ipcRenderer.send('open-bookmark', String(url)),
  removeBookmark: (url) => ipcRenderer.send('remove-bookmark', String(url)),
  clearBookmarks: () => ipcRenderer.send('clear-bookmarks'),
  setEngine: (id) => ipcRenderer.send('set-engine', String(id)),
  checkUpdate: () => ipcRenderer.send('check-update'),
  windowControl: (action) => ipcRenderer.send('window', String(action)),
});
