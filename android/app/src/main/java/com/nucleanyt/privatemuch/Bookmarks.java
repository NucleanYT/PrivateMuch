package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Bookmarks are only shortcuts: just each site's address is kept, nothing about the page, no cookies,
 * no history. Kept privately inside the app (Android backups are off), so they're never copied anywhere.
 */
final class Bookmarks {

    static final int MAX = 200;

    static final class Bookmark {
        final String url;

        Bookmark(String url) {
            this.url = url;
        }
    }

    private static final String PREFS = "bookmarks";
    private static final String KEY = "list";

    private final SharedPreferences prefs;
    private final List<Bookmark> items = new ArrayList<>();

    Bookmarks(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean hadTitles = false;
        try {
            JSONArray saved = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                Object entry = saved.get(i);
                if (entry instanceof JSONObject) { // older versions also kept the page title; drop it
                    hadTitles = true;
                    entry = ((JSONObject) entry).optString("url");
                }
                String url = String.valueOf(entry);
                if (!url.isEmpty() && indexOf(url) < 0) items.add(new Bookmark(url));
            }
        } catch (JSONException e) {
            items.clear(); // unreadable data: start with an empty list rather than crash
        }
        if (hadTitles) save();
    }

    /** Newest first. */
    List<Bookmark> all() {
        return new ArrayList<>(items);
    }

    boolean contains(String url) {
        return indexOf(url) >= 0;
    }

    /** Returns false when the list is full. */
    boolean add(String url) {
        if (url == null || url.isEmpty() || contains(url)) return true;
        if (items.size() >= MAX) return false;
        items.add(0, new Bookmark(withoutFragment(url)));
        save();
        return true;
    }

    void remove(String url) {
        int i = indexOf(url);
        if (i < 0) return;
        items.remove(i);
        save();
    }

    void clear() {
        items.clear();
        save();
    }

    private int indexOf(String url) {
        if (url == null) return -1;
        String target = withoutFragment(url);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(target)) return i;
        }
        return -1;
    }

    /** "page#section" and "page" are the same bookmark. */
    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private void save() {
        JSONArray list = new JSONArray();
        for (Bookmark b : items) list.put(b.url);
        prefs.edit().putString(KEY, list.toString()).apply();
    }
}
