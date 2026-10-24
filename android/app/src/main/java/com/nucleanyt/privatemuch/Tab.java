package com.nucleanyt.privatemuch;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.webkit.WebView;

/** One open tab: its own WebView, its title and a small preview for the tab overview. Kept in memory only. */
final class Tab {

    static final String HOME = "file:///android_asset/index.html";

    final WebView web;
    String title = "";
    boolean clearHistoryOnHome;
    Bitmap preview;

    Tab(WebView web) {
        this.web = web;
    }

    String url() {
        String url = web.getUrl();
        return url == null ? "" : url;
    }

    boolean onHome() {
        String url = url();
        return url.isEmpty() || url.startsWith("file:///android_asset/");
    }

    /** What the tab overview shows as the card's title. */
    String label() {
        if (onHome()) return "New tab";
        return title.isEmpty() || title.startsWith("http") ? AddressBar.displayName(url()) : title;
    }

    /** A small picture of what's on screen right now. */
    void capturePreview() {
        int w = web.getWidth();
        int h = web.getHeight();
        if (w == 0 || h == 0) return;
        float scale = 0.4f;
        Bitmap picture = Bitmap.createBitmap(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(picture);
        canvas.scale(scale, scale);
        canvas.translate(-web.getScrollX(), -web.getScrollY()); // draw() starts at the top of the page, not the scrolled spot
        web.draw(canvas);
        preview = picture;
    }
}
