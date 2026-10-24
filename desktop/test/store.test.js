'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { Store, MAX_BOOKMARKS } = require('../lib/store.js');

test('keeps only the engine and bookmark addresses, and survives a restart', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pm-store-'));
  const s = new Store(dir);
  assert.equal(s.engine, 'duckduckgo');
  s.setEngine('brave');
  s.addBookmark('https://en.wikipedia.org/wiki/Duck');
  s.addBookmark('https://nucleusbot.netlify.app/#top');
  s.addBookmark('https://en.wikipedia.org/wiki/Duck');
  const again = new Store(dir);
  assert.equal(again.engine, 'brave');
  assert.deepEqual(again.bookmarks, ['https://nucleusbot.netlify.app/', 'https://en.wikipedia.org/wiki/Duck']);
  assert.equal(again.hasBookmark('https://nucleusbot.netlify.app/#other'), true);
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir, 'data.json'), 'utf8')),
    { engine: 'brave', bookmarks: ['https://nucleusbot.netlify.app/', 'https://en.wikipedia.org/wiki/Duck'] });
  again.removeBookmark('https://nucleusbot.netlify.app/');
  assert.equal(new Store(dir).bookmarks.length, 1);
  for (let i = 0; i < 250; i++) again.addBookmark('https://example.com/' + i);
  assert.equal(new Store(dir).bookmarks.length, MAX_BOOKMARKS);
  assert.equal(again.addBookmark('https://full.example/'), false);
  again.clearBookmarks();
  assert.equal(new Store(dir).bookmarks.length, 0);
  fs.writeFileSync(path.join(dir, 'data.json'), '{broken');
  assert.equal(new Store(dir).bookmarks.length, 0);
});
