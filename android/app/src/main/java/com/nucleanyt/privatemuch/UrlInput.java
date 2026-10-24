package com.nucleanyt.privatemuch;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells web addresses ("example.com", "https://...") apart from searches, like a browser's address bar.
 * Plain Java so it can be tested off the phone. The same rules live in index.html (toUrl).
 */
final class UrlInput {

    // Only these endings and real country endings count, so "node.js" is still a search.
    // TLDLIST-START (keep identical to WEB_TLDS in index.html)
    private static final String[] WEB_TLDS = {
        "com", "net", "org", "edu", "gov", "mil", "int", "info", "biz", "app", "dev", "xyz", "site", "online",
        "store", "shop", "tech", "blog", "cloud", "page", "link", "live", "art", "news", "top", "club", "wiki",
        "pro", "mobi", "name", "design", "space", "website", "fun", "games", "world", "email", "digital",
        "media", "studio", "tools", "social", "chat", "bot", "zone", "one", "today", "life", "work", "network",
        "systems", "software", "agency", "company", "global", "school", "academy",
    };
    // TLDLIST-END
    // COUNTRYLIST-START (keep identical to COUNTRY_TLDS in index.html)
    private static final String COUNTRY_TLDS =
        "ac ad ae af ag ai al am ao aq ar as at au aw ax az ba bb bd be bf bg bh bi bj bl bm bn bo bq br " +
        "bs bt bv bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cw cx cy cz de dj dk dm do dz ec " +
        "ee eg eh er es et eu fi fj fk fm fo fr ga gb gd ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy " +
        "hk hm hn hr ht hu id ie il im in io iq ir is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la " +
        "lb lc li lk lr ls lt lu lv ly ma mc md me mf mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my " +
        "mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr ps pt pw py qa re ro rs " +
        "ru rw sa sb sc sd se sg sh si sj sk sl sm sn so sr ss st sv sx sy sz tc td tf tg th tj tk tl tm " +
        "tn to tr tt tv tw tz ua ug uk um us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw";
    // COUNTRYLIST-END

    private static final Set<String> TLDS = new HashSet<>(Arrays.asList(WEB_TLDS));
    private static final Set<String> COUNTRIES = new HashSet<>(Arrays.asList(COUNTRY_TLDS.split(" ")));
    private static final Pattern HOST_AND_PORT = Pattern.compile("^([^:]+)(?::\\d{1,5})?$");
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern NUMBER = Pattern.compile("^\\d{1,3}$");
    private static final Pattern SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private UrlInput() {}

    /** The address to open when the text is a web address, otherwise null. */
    static String toUrl(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty() || s.matches("(?s).*\\s.*")) return null;
        Matcher scheme = SCHEME.matcher(s);
        boolean hasScheme = scheme.find();
        String rest = hasScheme ? s.substring(scheme.end()) : s;
        int end = 0;
        while (end < rest.length() && "/?#".indexOf(rest.charAt(end)) < 0) end++;
        if (!isWebHost(rest.substring(0, end))) return null;
        return hasScheme ? s : "https://" + s;
    }

    /**
     * When someone types a web address into DuckDuckGo's or Brave's own search box, returns that address so
     * the app can open it directly. Returns null for normal searches and for "!" shortcuts like "!ai".
     */
    static String fromSearchBox(String url) {
        if (url == null) return null;
        int start = url.indexOf("://");
        if (start < 0) return null;
        start += 3;
        int end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        String host = url.substring(start, end).toLowerCase(Locale.ROOT);
        boolean duckDuckGo = host.equals("duckduckgo.com") || host.equals("www.duckduckgo.com");
        boolean brave = host.equals("search.brave.com") && url.startsWith("/search", end);
        if (!duckDuckGo && !brave) return null;
        int q = url.indexOf('?');
        if (q < 0) return null;
        int hash = url.indexOf('#', q);
        String query = hash < 0 ? url.substring(q + 1) : url.substring(q + 1, hash);
        for (String pair : query.split("&")) {
            if (!pair.startsWith("q=")) continue;
            String typed = decode(pair.substring(2)).trim();
            return typed.startsWith("!") ? null : toUrl(typed);
        }
        return null;
    }

    private static boolean isWebHost(String hostAndPort) {
        Matcher m = HOST_AND_PORT.matcher(hostAndPort.toLowerCase(Locale.ROOT));
        if (!m.matches()) return false;
        String[] parts = m.group(1).split("\\.", -1);
        if (parts.length == 4) { // IP address
            boolean ip = true;
            for (String p : parts) ip &= NUMBER.matcher(p).matches() && Integer.parseInt(p) <= 255;
            if (ip) return true;
        }
        if (parts.length < 2) return false;
        for (String p : parts) {
            if (!LABEL.matcher(p).matches()) return false;
        }
        String tld = parts[parts.length - 1];
        return COUNTRIES.contains(tld) || TLDS.contains(tld);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return value;
        }
    }
}
