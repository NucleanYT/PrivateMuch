'use strict';
// The only things PrivateMuch keeps after it closes: the search engine choice and bookmark addresses.
// Bookmarks are only shortcuts (just the address: no titles, page data, cookies or history), same as Android.
const fs = require('fs');
const path = require('path');

const MAX_BOOKMARKS = 200;

class Store {
  constructor(folder) {
    this.file = path.join(folder, 'data.json');
    this.engine = 'duckduckgo';
    this.bookmarks = []; // newest first
    try {
      const data = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      if (data.engine === 'brave') this.engine = 'brave';
      if (Array.isArray(data.bookmarks)) {
        for (const url of data.bookmarks) {
          if (typeof url === 'string' && url && !this.bookmarks.includes(url)) this.bookmarks.push(url);
        }
      }
    } catch (err) {
      // no file yet, or unreadable: start fresh rather than crash
    }
  }

  setEngine(id) {
    this.engine = id === 'brave' ? 'brave' : 'duckduckgo';
    this.save();
  }

  hasBookmark(url) {
    return !!url && this.bookmarks.includes(withoutFragment(url));
  }

  /** Returns false when the list is full. */
  addBookmark(url) {
    if (!url || this.hasBookmark(url)) return true;
    if (this.bookmarks.length >= MAX_BOOKMARKS) return false;
    this.bookmarks.unshift(withoutFragment(url));
    this.save();
    return true;
  }

  removeBookmark(url) {
    const i = this.bookmarks.indexOf(withoutFragment(url || ''));
    if (i < 0) return;
    this.bookmarks.splice(i, 1);
    this.save();
  }

  clearBookmarks() {
    this.bookmarks = [];
    this.save();
  }

  // Written to a temporary file first, so a crash mid-write never leaves a broken file.
  save() {
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    const temp = this.file + '.tmp';
    fs.writeFileSync(temp, JSON.stringify({ engine: this.engine, bookmarks: this.bookmarks }));
    fs.renameSync(temp, this.file);
  }
}

// "page#section" and "page" are the same bookmark.
function withoutFragment(url) {
  const hash = url.indexOf('#');
  return hash < 0 ? url : url.slice(0, hash);
}

module.exports = { Store, MAX_BOOKMARKS };
