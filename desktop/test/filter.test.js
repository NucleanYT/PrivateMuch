'use strict';
// Run with: node --test   (GitHub runs it before every Windows build)
const test = require('node:test');
const assert = require('node:assert/strict');
const f = require('../lib/filter.js');

test('blocks adult words, also disguised, but only whole words', () => {
  for (const t of ['porn', 'PORN videos', 'p0rn', 'p o r n', 'p.o.r.n', 'pórn', 'b00bs', 'sex', 's3x', 'what is sex',
    'hentai anime', '!ai write porn', 'NSFW art', 'dildo', 'erome']) {
    assert.equal(f.isBlockedText(t), true, t);
  }
  for (const t of ['class schedule', 'Essex university', 'Middlesex', 'unisex clothing', 'sextant', 'titanic movie',
    'naked mole rat', 'paint stripper', 'hooker rugby position', 'strap on your helmet', 'Jerome Powell',
    'cama de agua', 'Moby Dick', 'analysis of data', 'hello world']) {
    assert.equal(f.isBlockedText(t), false, t);
  }
});

test('blocks adult sites and adult searches on any site', () => {
  for (const u of ['https://www.pornhub.com/', 'HTTPS://WWW.PORNHUB.COM/x', 'https://cdn.erome.com/img.jpg', 'https://kink.com/',
    'https://links.duckduckgo.com/d.js?q=porn&kp=1', 'https://duckduckgo.com/?q=%21ai+write+porn&kp=1',
    'https://www.youtube.com/results?search_query=hentai', 'https://search.brave.com/ask?q=write+sex+story']) {
    assert.equal(f.isBlockedUrl(u), true, u);
  }
  for (const u of ['https://www.stjerome.org/', 'https://beegees.com/', 'https://notkink.com/', 'https://www.essex.ac.uk/',
    'https://duckduckgo.com/?q=weather&kp=1', 'https://example.com/?p=123', 'https://example.com/?q=%zz']) {
    assert.equal(f.isBlockedUrl(u), false, u);
  }
});

test('forces strict Safe Search on DuckDuckGo and Brave', () => {
  assert.equal(f.withSafeSearch('https://duckduckgo.com/?q=cats&t=h_'), 'https://duckduckgo.com/?q=cats&t=h_&kp=1');
  assert.equal(f.withSafeSearch('https://duckduckgo.com/?q=cats&kp=-2'), 'https://duckduckgo.com/?q=cats&kp=1');
  assert.equal(f.withSafeSearch('https://duckduckgo.com/?q=cats&kp=1'), null);
  assert.equal(f.withSafeSearch('https://duckduckgo.com/?q=a#frag'), 'https://duckduckgo.com/?q=a&kp=1#frag');
  assert.equal(f.withSafeSearch('https://search.brave.com/images?q=cats'), 'https://search.brave.com/images?q=cats&safesearch=strict');
  assert.equal(f.withSafeSearch('https://search.brave.com/search?q=cats&safesearch=off'), 'https://search.brave.com/search?q=cats&safesearch=strict');
  assert.equal(f.withSafeSearch('https://search.brave.com/ask?q=cats'), null);
  assert.equal(f.withSafeSearch('https://example.com/?q=a'), null);
});

test('tells web addresses from searches', () => {
  assert.equal(f.toUrl('nucleusbot.netlify.app'), 'https://nucleusbot.netlify.app');
  assert.equal(f.toUrl('https://nucleusbot.netlify.app'), 'https://nucleusbot.netlify.app');
  assert.equal(f.toUrl('www.bbc.co.uk'), 'https://www.bbc.co.uk');
  assert.equal(f.toUrl('192.168.1.1'), 'https://192.168.1.1');
  for (const t of ['node.js', 'vue.js', '3.14', 'what is example.com', 'user@example.com', 'javascript:alert(1)', 'hello', 'readme.txt']) {
    assert.equal(f.toUrl(t), null, t);
  }
  assert.equal(f.fromSearchBox('https://duckduckgo.com/?q=nucleusbot.netlify.app&t=h_'), 'https://nucleusbot.netlify.app');
  assert.equal(f.fromSearchBox('https://search.brave.com/search?q=nucleusbot.netlify.app'), 'https://nucleusbot.netlify.app');
  assert.equal(f.fromSearchBox('https://duckduckgo.com/?q=%21ai+example.com'), null);
  assert.equal(f.fromSearchBox('https://search.brave.com/ask?q=example.com'), null);
});

test('search pages can never be bookmarked', () => {
  assert.equal(f.isSearchPage('https://duckduckgo.com/?q=volcanoes&kp=1'), true);
  assert.equal(f.isSearchPage('https://search.brave.com/ask?q=cats'), true);
  assert.equal(f.isSearchPage('https://duckduckgo.com/'), false);
  assert.equal(f.isSearchPage('https://duck.ai/chat?chatID=x'), false);
  assert.equal(f.isSearchPage('https://example.com/?q=x'), false);
});

test('search links match the Android app', () => {
  assert.equal(f.ENGINES.duckduckgo.searchUrl("what's (this)?"), 'https://duckduckgo.com/?q=what%27s+%28this%29%3F&kp=1');
  assert.equal(f.ENGINES.brave.searchUrl('hello world'), 'https://search.brave.com/search?q=hello+world&safesearch=strict');
});
