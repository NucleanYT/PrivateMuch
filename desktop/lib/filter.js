'use strict';
// The adult filter, web-address detection and search-engine links for the Windows app.
// Same lists and rules as index.html and the Android app (ContentFilter.java, UrlInput.java, Engine.java);
// CI fails if the lists differ, and desktop/test/filter.test.js checks the rules.

// BLOCKLIST-START (keep identical to index.html and the Android app; CI checks)
const BLOCKED_WORDS = [
  'porn', 'porno', 'pornography', 'pornographic', 'xxx', 'nsfw', 'nude', 'nudes', 'nudity',
  'boob', 'boobs', 'boobies', 'tits', 'titties', 'titty', 'pussy', 'hentai', 'ecchi', 'rule34', 'rule 34',
  'milf', 'sex', 'sexy', 'sexting', 'erotic', 'erotica',
  'fetish', 'bdsm', 'lewd', 'horny', 'orgasm', 'masturbate', 'masturbating', 'masturbation',
  'blowjob', 'handjob', 'cumshot', 'deepthroat', 'gangbang', 'threesome', 'dick pic', 'strip club',
  'camgirl', 'cam girl', 'onlyfans', 'fansly', 'pornhub', 'xvideos', 'xnxx', 'xhamster', 'redtube',
  'youporn', 'brazzers', 'chaturbate', 'spankbang', 'playboy',
  'sexcam', 'sexchat', 'sexvideo', 'sextape', 'jerk off', 'jerking off',
  'wank', 'wanking', 'fap', 'fapping', 'smut', 'upskirt', 'voyeur', 'camwhore', 'whore', 'slut',
  'creampie', 'bukkake', 'dildo', 'butt plug', 'fleshlight', 'strapon', 'nip slip', 'nipple slip',
  'incest', 'stepsis', 'futanari', 'ahegao', 'lolicon', 'shotacon', 'seggs', 'naked pics', 'naked girls',
  'naked women', 'naked selfie', 'topless', 'escort service', 'jizz', 'youjizz', 'literotica',
  'nutaku', 'hanime', 'fakku', 'manyvids', 'erome', 'fapello', 'thothub', 'imagefap', 'xxxvideos',
  'porntube', 'dilf', 'pawg', 'stripchat', 'livejasmin',
];
// BLOCKLIST-END

const LOOKALIKES = { 0: 'o', 1: 'i', '!': 'i', 3: 'e', 4: 'a', '@': 'a', 5: 's', $: 's', 7: 't' };
// Lowercase, accents removed, look-alike digits and symbols turned into letters, spaced-out letters joined.
function normalize(text) {
return text.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase()
  .replace(/[013457!@$]/g, (c) => LOOKALIKES[c] || c)
  .replace(/[^a-z]+/g, ' ').trim()
  .replace(/\b(?:[a-z] )+[a-z]\b/g, (m) => m.replace(/ /g, '')); // "p o r n" -> "porn"
}
// Whole words only (plus plurals), so "class" or "Essex" are never blocked.
const BLOCKED = new RegExp('(?:^| )(?:' + BLOCKED_WORDS.map(normalize).join('|') + ')(?:e?s)?(?= |$)');
const isBlockedText = (text) => !!text && BLOCKED.test(normalize(text));

// HOSTLIST-START (keep identical to index.html and the Android app; CI checks)
const BLOCKED_HOST_PARTS = [
  'porn', 'xxx', 'hentai', 'xvideos', 'xnxx', 'xhamster', 'onlyfans', 'fansly', 'chaturbate',
  'brazzers', 'redtube', 'spankbang', 'rule34', 'nsfw', 'livejasmin', 'stripchat', 'bongacams',
  'camsoda', 'motherless', 'eporner', 'tnaflix', 'jizz', 'sxyprn', 'porntrex',
];
// HOSTLIST-END

// SITELIST-START (keep identical to index.html and the Android app; CI checks)
const BLOCKED_SITES = [
  'erome.com', 'beeg.com', 'tube8.com', 'hclips.com', 'efukt.com', 'fapello.com', 'thothub.to',
  'coomer.su', 'coomer.party', 'manyvids.com', 'clips4sale.com', 'hanime.tv', 'fakku.net', '4tube.com',
  'noodlemagazine.com', 'fuq.com', 'drtuber.com', 'imagefap.com', 'literotica.com', 'f95zone.to',
  'nutaku.net', 'kink.com', 'playboy.com', 'cam4.com', 'myfreecams.com', 'flirt4free.com',
  'jerkmate.com', 'erome.io', 'nudogram.com', 'sexstories.com',
];
// SITELIST-END

// TLDLIST-START (keep identical to index.html and the Android app; CI checks)
const WEB_TLDS = [
  'com', 'net', 'org', 'edu', 'gov', 'mil', 'int', 'info', 'biz', 'app', 'dev', 'xyz', 'site', 'online',
  'store', 'shop', 'tech', 'blog', 'cloud', 'page', 'link', 'live', 'art', 'news', 'top', 'club', 'wiki',
  'pro', 'mobi', 'name', 'design', 'space', 'website', 'fun', 'games', 'world', 'email', 'digital',
  'media', 'studio', 'tools', 'social', 'chat', 'bot', 'zone', 'one', 'today', 'life', 'work', 'network',
  'systems', 'software', 'agency', 'company', 'global', 'school', 'academy',
];
// TLDLIST-END
// COUNTRYLIST-START (keep identical to index.html and the Android app; CI checks)
const COUNTRY_CODES =
  'ac ad ae af ag ai al am ao aq ar as at au aw ax az ba bb bd be bf bg bh bi bj bl bm bn bo bq br ' +
  'bs bt bv bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cw cx cy cz de dj dk dm do dz ec ' +
  'ee eg eh er es et eu fi fj fk fm fo fr ga gb gd ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy ' +
  'hk hm hn hr ht hu id ie il im in io iq ir is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la ' +
  'lb lc li lk lr ls lt lu lv ly ma mc md me mf mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my ' +
  'mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr ps pt pw py qa re ro rs ' +
  'ru rw sa sb sc sd se sg sh si sj sk sl sm sn so sr ss st sv sx sy sz tc td tf tg th tj tk tl tm ' +
  'tn to tr tt tv tw tz ua ug uk um us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw';
// COUNTRYLIST-END

// ---------- Addresses ----------

// The part between "://" and the path, without a login or port, lowercase (same as the Android app's host()).
function hostOf(url) {
  const start = url.indexOf('://');
  if (start < 0) return null;
  let host = url.slice(start + 3).split(/[/?#]/, 1)[0];
  const at = host.lastIndexOf('@');
  if (at >= 0) host = host.slice(at + 1);
  const colon = host.indexOf(':');
  if (colon >= 0) host = host.slice(0, colon);
  return host.toLowerCase();
}

function pathOf(url) {
  const start = url.indexOf('://');
  if (start < 0) return '';
  const slash = url.indexOf('/', start + 3);
  if (slash < 0) return '';
  return url.slice(slash).split(/[?#]/, 1)[0];
}

function rawQuery(url) {
  const q = url.indexOf('?');
  if (q < 0) return null;
  const hash = url.indexOf('#', q);
  return hash < 0 ? url.slice(q + 1) : url.slice(q + 1, hash);
}

// Like Java's URLDecoder: "+" is a space; a broken %-escape leaves the text as it was.
function decode(value) {
  try { return decodeURIComponent(value.replace(/\+/g, ' ')); } catch (err) { return value; }
}

// Query parameters that carry what someone searched for, on DuckDuckGo and other search sites.
const SEARCH_PARAMS = ['q', 'query', 'search_query', 'p'];

// True for adult sites, and for searches (like ?q=...) with blocked words on any site.
function isBlockedUrl(url) {
  if (!url) return false;
  const host = hostOf(url);
  if (host !== null) {
    if (BLOCKED_HOST_PARTS.some((part) => host.includes(part))) return true;
    if (BLOCKED_SITES.some((site) => host === site || host.endsWith('.' + site))) return true;
  }
  const query = rawQuery(url);
  if (query === null) return false;
  for (const pair of query.split('&')) {
    const eq = pair.indexOf('=');
    if (eq <= 0) continue;
    if (SEARCH_PARAMS.includes(pair.slice(0, eq)) && isBlockedText(decode(pair.slice(eq + 1)))) return true;
  }
  return false;
}

// The search address with strict Safe Search forced on (kp=1 on DuckDuckGo, safesearch=strict on Brave
// search, images, videos and news), or null when it isn't a search there or already has it.
function withSafeSearch(url) {
  const host = hostOf(url);
  if (host === null) return null;
  let strict;
  if (host === 'duckduckgo.com' || host.endsWith('.duckduckgo.com')) {
    strict = 'kp=1';
  } else if (host === 'search.brave.com' && /^\/(search|images|videos|news)\/?$/.test(pathOf(url))) {
    strict = 'safesearch=strict';
  } else {
    return null;
  }
  const key = strict.slice(0, strict.indexOf('=') + 1);
  const query = rawQuery(url);
  if (query === null) return null;
  let hasSearch = false;
  let kept = '';
  for (const pair of query.split('&')) {
    if (pair === strict) return null; // already strict
    if (pair.startsWith(key) || pair === '') continue;
    if (pair.startsWith('q=')) hasSearch = true;
    kept += pair + '&';
  }
  if (!hasSearch) return null;
  const q = url.indexOf('?');
  const hash = url.indexOf('#', q);
  const fragment = hash < 0 ? '' : url.slice(hash);
  return url.slice(0, q + 1) + kept + strict + fragment;
}

const COUNTRY_TLDS = new Set(COUNTRY_CODES.split(' '));
const LABEL = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
function isWebHost(hostAndPort) {
  const m = /^([^:]+)(?::\d{1,5})?$/.exec(hostAndPort.toLowerCase());
  if (!m) return false;
  const parts = m[1].split('.');
  if (parts.length === 4 && parts.every((p) => /^\d{1,3}$/.test(p) && Number(p) <= 255)) return true; // IP address
  if (parts.length < 2 || !parts.every((p) => LABEL.test(p))) return false;
  const tld = parts[parts.length - 1];
  return COUNTRY_TLDS.has(tld) || WEB_TLDS.includes(tld);
}

// The address to open when the text is a web address ("example.com", "https://..."), otherwise null.
function toUrl(text) {
  if (text == null) return null;
  const s = String(text).trim();
  if (!s || /\s/.test(s)) return null;
  const scheme = /^https?:\/\//i.exec(s);
  const rest = scheme ? s.slice(scheme[0].length) : s;
  if (!isWebHost(rest.split(/[/?#]/, 1)[0])) return null;
  return scheme ? s : 'https://' + s;
}

// A web address typed into DuckDuckGo's or Brave's own search box, so the app can open it directly.
// Null for normal searches and for "!" shortcuts like "!ai".
function fromSearchBox(url) {
  if (!url) return null;
  const start = url.indexOf('://');
  if (start < 0) return null;
  const end = start + 3 + url.slice(start + 3).split(/[/?#]/, 1)[0].length;
  const host = url.slice(start + 3, end).toLowerCase();
  const duckDuckGo = host === 'duckduckgo.com' || host === 'www.duckduckgo.com';
  const brave = host === 'search.brave.com' && url.startsWith('/search', end);
  if (!duckDuckGo && !brave) return null;
  const query = rawQuery(url);
  if (query === null) return null;
  for (const pair of query.split('&')) {
    if (!pair.startsWith('q=')) continue;
    const typed = decode(pair.slice(2)).trim();
    return typed.startsWith('!') ? null : toUrl(typed);
  }
  return null;
}

// DuckDuckGo and Brave search or AI pages whose address carries what was typed (?q=...),
// so they can't be bookmarked and a search never ends up kept.
function isSearchPage(url) {
  if (!url) return false;
  const host = hostOf(url);
  if (host === null) return false;
  const engine = host === 'duckduckgo.com' || host.endsWith('.duckduckgo.com')
    || host === 'search.brave.com' || host === 'duck.ai';
  if (!engine) return false;
  const query = rawQuery(url);
  return query !== null && query.split('&').some((pair) => pair.startsWith('q='));
}

// ---------- Search engines (same as the Android app's Engine) ----------

const ENGINES = {
  duckduckgo: {
    id: 'duckduckgo', title: 'DuckDuckGo', ai: 'Duck.ai',
    description: 'Searches with DuckDuckGo. AI chats use Duck.ai.',
    searchUrl: (text) => 'https://duckduckgo.com/?q=' + encodeForm(text) + '&kp=1',
  },
  brave: {
    id: 'brave', title: 'Brave Search', ai: 'Ask Brave',
    description: "Searches with Brave Search. AI chats use Ask Brave, Brave's AI search.",
    searchUrl: (text) => 'https://search.brave.com/search?q=' + encodeForm(text) + '&safesearch=strict',
  },
};

// Like Java's URLEncoder (spaces become "+"), so the Windows and Android apps build the same links.
function encodeForm(text) {
  return encodeURIComponent(text).replace(/%20/g, '+').replace(/[!'()~]/g, (c) => '%' + c.charCodeAt(0).toString(16).toUpperCase());
}

// "duckduckgo.com" instead of the full address, like most browsers.
function displayName(url) {
  if (!url) return '';
  const host = hostOf(url);
  if (!host) return url;
  return host.startsWith('www.') ? host.slice(4) : host;
}

module.exports = {
  normalize, isBlockedText, isBlockedUrl, withSafeSearch, toUrl, fromSearchBox, isSearchPage,
  displayName, hostOf, ENGINES,
};
