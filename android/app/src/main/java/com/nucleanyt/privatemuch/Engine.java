package com.nucleanyt.privatemuch;

import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** The search engine picked in Settings. It's the only thing the app remembers between launches. */
enum Engine {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "Duck.ai", "Searches with DuckDuckGo. AI chats use Duck.ai."),
    BRAVE("brave", "Brave Search", "Ask Brave", "Searches with Brave Search. AI chats use Ask Brave, Brave's AI search.");

    private static final String PREFS = "settings";
    private static final String KEY = "engine";

    final String id;
    final String title;
    final String ai;
    final String description;

    Engine(String id, String title, String ai, String description) {
        this.id = id;
        this.title = title;
        this.ai = ai;
        this.description = description;
    }

    /** Strict Safe Search is always on: kp=1 on DuckDuckGo, safesearch=strict on Brave. */
    String searchUrl(String text) {
        String q = encode(text);
        return this == BRAVE
                ? "https://search.brave.com/search?q=" + q + "&safesearch=strict"
                : "https://duckduckgo.com/?q=" + q + "&kp=1";
    }

    /** The home page, told which engine to use. */
    String homeUrl() {
        return this == BRAVE ? Tab.HOME + "?engine=brave" : Tab.HOME;
    }

    static Engine load(Context context) {
        String id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DUCKDUCKGO.id);
        return BRAVE.id.equals(id) ? BRAVE : DUCKDUCKGO;
    }

    void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).apply();
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always available
        }
    }
}
