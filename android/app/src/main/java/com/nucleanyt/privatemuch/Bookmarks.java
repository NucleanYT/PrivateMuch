package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved sites. Along with the search engine choice, they're the only things PrivateMuch keeps after it closes.
 * Stored privately inside the app; the app turns off Android backups, so they're never copied anywhere.
 */
final class Bookmarks {

    static final int MAX = 200;

    static final class Bookmark {
        final String title;
        final String url;

        Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final String PREFS = "bookmarks";
    private static final String KEY = "list";

    private final SharedPreferences prefs;
    private final List<Bookmark> items = new ArrayList<>();

    Bookmarks(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray saved = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.getJSONObject(i);
                items.add(new Bookmark(o.optString("title"), o.optString("url")));
            }
        } catch (JSONException e) {
            items.clear(); // unreadable data: start with an empty list rather than crash
        }
    }

    /** Newest first. */
    List<Bookmark> all() {
        return new ArrayList<>(items);
    }

    boolean contains(String url) {
        return indexOf(url) >= 0;
    }

    /** Returns false when the list is full. */
    boolean add(String title, String url) {
        if (url == null || url.isEmpty() || contains(url)) return true;
        if (items.size() >= MAX) return false;
        items.add(0, new Bookmark(title == null ? "" : title, withoutFragment(url)));
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
        try {
            for (Bookmark b : items) {
                list.put(new JSONObject().put("title", b.title).put("url", b.url));
            }
        } catch (JSONException e) {
            return; // can't happen with plain strings; never save a half-written list
        }
        prefs.edit().putString(KEY, list.toString()).apply();
    }
}
