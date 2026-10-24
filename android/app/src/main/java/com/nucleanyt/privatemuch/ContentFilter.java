package com.nucleanyt.privatemuch;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks adult searches and adult sites. Plain Java (no Android classes) so it can be tested off the phone.
 * The same word list and text matching live in index.html; the build fails if the two lists differ.
 */
final class ContentFilter {

    // BLOCKLIST-START (keep identical to BLOCKED_WORDS in index.html)
    private static final String[] BLOCKED_WORDS = {
        "porn", "porno", "pornography", "pornographic", "xxx", "nsfw", "nude", "nudes", "nudity",
        "boob", "boobs", "boobies", "tits", "titties", "titty", "pussy", "hentai", "ecchi", "rule34", "rule 34",
        "milf", "sex", "sexy", "sexting", "erotic", "erotica",
        "fetish", "bdsm", "lewd", "horny", "orgasm", "masturbate", "masturbating", "masturbation",
        "blowjob", "handjob", "cumshot", "deepthroat", "gangbang", "threesome", "dick pic", "strip club",
        "camgirl", "cam girl", "onlyfans", "fansly", "pornhub", "xvideos", "xnxx", "xhamster", "redtube",
        "youporn", "brazzers", "chaturbate", "spankbang", "playboy",
        "sexcam", "sexchat", "sexvideo", "sextape", "jerk off", "jerking off",
        "wank", "wanking", "fap", "fapping", "smut", "upskirt", "voyeur", "camwhore", "whore", "slut",
        "creampie", "bukkake", "dildo", "butt plug", "fleshlight", "strapon", "nip slip", "nipple slip",
        "incest", "stepsis", "futanari", "ahegao", "lolicon", "shotacon", "seggs", "naked pics", "naked girls",
        "naked women", "naked selfie", "topless", "escort service", "jizz", "youjizz", "literotica",
        "nutaku", "hanime", "fakku", "manyvids", "erome", "fapello", "thothub", "imagefap", "xxxvideos",
        "porntube", "dilf", "pawg", "stripchat", "livejasmin",
    };
    // BLOCKLIST-END

    /** Any site whose address contains one of these is blocked. */
    // HOSTLIST-START (keep identical to BLOCKED_HOST_PARTS in index.html)
    private static final String[] BLOCKED_HOST_PARTS = {
        "porn", "xxx", "hentai", "xvideos", "xnxx", "xhamster", "onlyfans", "fansly", "chaturbate",
        "brazzers", "redtube", "spankbang", "rule34", "nsfw", "livejasmin", "stripchat", "bongacams",
        "camsoda", "motherless", "eporner", "tnaflix", "jizz", "sxyprn", "porntrex",
    };
    // HOSTLIST-END

    /** Exact sites (and their subdomains), for names too short or common to match as part of an address. */
    // SITELIST-START (keep identical to BLOCKED_SITES in index.html)
    private static final String[] BLOCKED_SITES = {
    "erome.com", "beeg.com", "tube8.com", "hclips.com", "efukt.com", "fapello.com", "thothub.to",
        "coomer.su", "coomer.party", "manyvids.com", "clips4sale.com", "hanime.tv", "fakku.net", "4tube.com",
        "noodlemagazine.com", "fuq.com", "drtuber.com", "imagefap.com", "literotica.com", "f95zone.to",
        "nutaku.net", "kink.com", "playboy.com", "cam4.com", "myfreecams.com", "flirt4free.com",
        "jerkmate.com", "erome.io", "nudogram.com", "sexstories.com",
    };
    // SITELIST-END

    /** Query parameters that carry what someone searched for, on DuckDuckGo and other search sites. */
    private static final String[] SEARCH_PARAMS = {"q", "query", "search_query", "p"};

    private static final Pattern SPACED_LETTERS = Pattern.compile("\\b(?:[a-z] )+[a-z]\\b");
    private static final Pattern WORDS = buildWordPattern();

    private ContentFilter() {}

    /** True if the text contains a blocked word, also when written like "p0rn", "p o r n" or "pórn". */
    static boolean isBlockedText(String text) {
        if (text == null || text.isEmpty()) return false;
        return WORDS.matcher(normalize(text)).find();
    }

    /** True for adult sites, and for searches (like ?q=...) with blocked words on any site. */
    static boolean isBlockedUrl(String url) {
        if (url == null) return false;
        String host = host(url);
        if (host != null) {
            for (String part : BLOCKED_HOST_PARTS) {
                if (host.contains(part)) return true;
            }
            for (String site : BLOCKED_SITES) {
                if (host.equals(site) || host.endsWith("." + site)) return true;
            }
        }
        String query = rawQuery(url);
        if (query == null) return false;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq);
            for (String param : SEARCH_PARAMS) {
                if (param.equals(key) && isBlockedText(decode(pair.substring(eq + 1)))) return true;
            }
        }
        return false;
    }

    /**
     * Returns the search address with strict Safe Search forced on (kp=1 on DuckDuckGo, safesearch=strict on
     * Brave Search), or null when the address isn't a search there or already has it.
     */
    static String withSafeSearch(String url) {
        String host = host(url);
        if (host == null) return null;
        String strict;
        if (host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com")) {
            strict = "kp=1";
        } else if (host.equals("search.brave.com") && path(url).matches("/(search|images|videos|news)/?")) {
            strict = "safesearch=strict";
        } else {
            return null;
        }
        String key = strict.substring(0, strict.indexOf('=') + 1);
        String query = rawQuery(url);
        if (query == null) return null;
        boolean hasSearch = false;
        StringBuilder kept = new StringBuilder();
        for (String pair : query.split("&")) {
            if (pair.equals(strict)) return null; // already strict
            if (pair.startsWith(key) || pair.isEmpty()) continue;
            if (pair.startsWith("q=")) hasSearch = true;
            kept.append(pair).append('&');
        }
        if (!hasSearch) return null;
        int q = url.indexOf('?');
        int hash = url.indexOf('#', q);
        String fragment = hash < 0 ? "" : url.substring(hash);
        return url.substring(0, q + 1) + kept + strict + fragment;
    }

    /** Lowercase, accents removed, look-alike digits and symbols turned into letters, spaced-out letters joined. */
    static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case '0': sb.append('o'); break;
                case '1': case '!': sb.append('i'); break;
                case '3': sb.append('e'); break;
                case '4': case '@': sb.append('a'); break;
                case '5': case '$': sb.append('s'); break;
                case '7': sb.append('t'); break;
                default: sb.append(c);
            }
        }
        t = sb.toString().replaceAll("[^a-z]+", " ").trim();
        Matcher m = SPACED_LETTERS.matcher(t); // "p o r n" -> "porn"
        StringBuffer joined = new StringBuffer();
        while (m.find()) m.appendReplacement(joined, m.group().replace(" ", ""));
        m.appendTail(joined);
        return joined.toString();
    }

    private static Pattern buildWordPattern() {
        StringBuilder alternatives = new StringBuilder();
        for (String word : BLOCKED_WORDS) {
            if (alternatives.length() > 0) alternatives.append('|');
            alternatives.append(normalize(word)); // only letters and spaces after normalizing
        }
        // Whole words only (plus plurals), so "class" or "Essex" are never blocked.
        return Pattern.compile("(?:^| )(?:" + alternatives + ")(?:e?s)?(?= |$)");
    }

    private static String host(String url) {
        int start = url.indexOf("://");
        if (start < 0) return null;
        start += 3;
        int end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        String host = url.substring(start, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return host.toLowerCase(Locale.ROOT);
    }

    private static String path(String url) {
        int start = url.indexOf("://");
        if (start < 0) return "";
        int slash = url.indexOf('/', start + 3);
        if (slash < 0) return "";
        int end = slash;
        while (end < url.length() && "?#".indexOf(url.charAt(end)) < 0) end++;
        return url.substring(slash, end);
    }

    private static String rawQuery(String url) {
        int q = url.indexOf('?');
        if (q < 0) return null;
        int hash = url.indexOf('#', q);
        return hash < 0 ? url.substring(q + 1) : url.substring(q + 1, hash);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return value;
        }
    }
}
