package com.nucleanyt.privatemuch;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows the PrivateMuch page (bundled in the app) in tabs of WebViews that keep nothing:
 * cookies, site storage, cache, form data and history are wiped when the app starts and when it closes.
 */
public class MainActivity extends Activity {

    private static final int MAX_TABS = 12; // each tab is a whole WebView, so keep memory in check
    private static final String BLOCKED_PAGE = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<body style='margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;box-sizing:border-box;"
            + "background:#0b1012;color:#e2ebe9;font:16px/1.5 sans-serif;text-align:center'>"
            + "<div><div style='font-size:22px;font-weight:600;margin-bottom:6px'>Blocked</div>"
            + "<div style='color:#8fa29f'>PrivateMuch blocks adult content.</div></div>";

    private final List<Tab> tabs = new ArrayList<>();
    private Tab current;
    private FrameLayout screen;    // everything, with the tab overview drawn on top
    private FrameLayout content;   // holds the current tab's WebView
    private AddressBar addressBar; // shown only while a website is open
    private BottomBar bottomBar;   // Back, Forward, Tabs, New tab; also only while a website is open
    private LinearLayout homeLeft;  // Tabs + Settings, top-left of the home page
    private View homeRight;         // Update, top-right of the home page
    private TextView homeTabsButton;
    private TextView updateButton;
    private Updater updater;
    private TabsView tabsView;     // the tab overview while it's open
    private SettingsView settingsView; // settings while they're open
    private BookmarksView bookmarksView; // bookmarks while they're open
    private Bookmarks bookmarks;
    private Engine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = Engine.load(this);
        bookmarks = new Bookmarks(this);
        Tab first = new Tab(createWebView());
        tabs.add(first);
        wipe(first.web); // start clean, even if the app was killed last time before it could clean up
        setContentView(buildLayout());
        switchTo(first);
        first.web.loadUrl(engine.homeUrl()); // never restore a previous session
    }

    private WebView createWebView() {
        WebView web = new PrivateWebView(this);
        web.setBackgroundColor(Color.parseColor("#0B1012"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Keep the system autofill service from saving what is typed.
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // many sites break without it; wiped on start and exit
        s.setDatabaseEnabled(false);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);  // bundled assets still load
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setSaveFormData(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                // Stay inside the app for web pages; ignore app links (intent:, market:, tel: ...).
                if (!("https".equals(scheme) || "http".equals(scheme))) return true;
                if (request.isForMainFrame()) {
                    String typedAddress = UrlInput.fromSearchBox(request.getUrl().toString());
                    if (typedAddress != null) { // a web address typed into the search engine's box opens the site
                        view.loadUrl(typedAddress);
                        return true;
                    }
                    String safe = ContentFilter.withSafeSearch(request.getUrl().toString());
                    if (safe != null) { // every DuckDuckGo and Brave search gets strict Safe Search
                        view.loadUrl(safe);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Runs for every page, image, frame and script, so adult sites can't load any part.
                if (!ContentFilter.isBlockedUrl(request.getUrl().toString())) return null;
                if (request.isForMainFrame()) {
                    return new WebResourceResponse("text/html", "utf-8",
                            new ByteArrayInputStream(BLOCKED_PAGE.getBytes(StandardCharsets.UTF_8)));
                }
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                        Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (isCurrent(view)) updateBar(url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (isCurrent(view)) updateBar(url); // also catches in-page navigation, like Duck.ai switching chats
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Tab tab = tabFor(view);
                if (tab != null && tab.clearHistoryOnHome && url != null && url.startsWith(Tab.HOME)) {
                    view.clearHistory(); // after Home, Back doesn't reopen the site
                    tab.clearHistoryOnHome = false;
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int percent) {
                if (isCurrent(view)) addressBar.setProgress(percent);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                Tab tab = tabFor(view);
                if (tab != null) tab.title = title == null ? "" : title;
            }
        });
        return web;
    }

    /** The address bar above the page, the buttons on the home page, and room for the tab overview and settings. */
    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1012"));
        root.setLayoutTransition(new LayoutTransition());

        addressBar = new AddressBar(this, new AddressBar.Listener() {
            @Override public void onHome() { goHome(); }
            @Override public void onSubmit(String typed) { open(typed); }
            @Override public void onReload() { current.web.reload(); }
            // The bottom bar steps aside while typing, so it doesn't ride on top of the keyboard.
            @Override public void onEditing(boolean editing) { bottomBar.setVisibility(editing || current.onHome() ? View.GONE : View.VISIBLE); }
        });
        addressBar.setVisibility(View.GONE);
        root.addView(addressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        // Tabs and Settings on the left, Update on the right, so the PrivateMuch name in between stays clear.
        homeLeft = new LinearLayout(this);
        homeLeft.setOrientation(LinearLayout.HORIZONTAL);
        homeLeft.setGravity(Gravity.CENTER_VERTICAL);
        homeTabsButton = AddressBar.tabsButton(this, true);
        homeTabsButton.setOnClickListener(v -> showTabs());
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        tabsParams.setMarginEnd(dp(8));
        homeLeft.addView(homeTabsButton, tabsParams);
        LinearLayout.LayoutParams bookmarksParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        bookmarksParams.setMarginEnd(dp(8));
        homeLeft.addView(squareButton(R.drawable.ic_bookmark, "Bookmarks", v -> showBookmarks()), bookmarksParams);
        homeLeft.addView(squareButton(R.drawable.ic_settings, "Settings", v -> showSettings()), new LinearLayout.LayoutParams(dp(34), dp(34)));
        FrameLayout.LayoutParams leftCorner = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        leftCorner.setMargins(dp(12), dp(10), 0, 0);
        content.addView(homeLeft, leftCorner);

        updateButton = buildUpdateButton();
        updater = new Updater(this, updateButton);
        homeRight = updateButton;
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        corner.setMargins(0, dp(10), dp(12), 0);
        content.addView(updateButton, corner);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new BottomBar(this, new BottomBar.Listener() {
            @Override public void onBack() { if (current.web.canGoBack()) current.web.goBack(); }
            @Override public void onForward() { if (current.web.canGoForward()) current.web.goForward(); }
            @Override public void onBookmarks() { showBookmarks(); }
            @Override public void onTabs() { showTabs(); }
            @Override public void onNewTab() { newTab(); }
        });
        bottomBar.setVisibility(View.GONE);
        root.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        screen = new FrameLayout(this);
        screen.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return screen;
    }

    /** A small pill in the top-right corner of the home page. */
    private TextView buildUpdateButton() {
        TextView b = new TextView(this);
        b.setText("Update");
        b.setContentDescription("Check for updates");
        b.setTextColor(Color.parseColor("#8FA29F"));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_update, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(6));
        b.setPadding(dp(12), dp(8), dp(14), dp(8));

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#E6131B1E"));
        pill.setCornerRadius(dp(18));
        pill.setStroke(dp(1), Color.parseColor("#24333A"));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), pill, null));

        b.setOnClickListener(v -> updater.start());
        AddressBar.addSpringPress(b);
        return b;
    }

    /** A rounded square with an icon, matching the tabs button next to it. */
    private View squareButton(int icon, String description, View.OnClickListener onClick) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(description);
        GradientDrawable square = new GradientDrawable();
        square.setCornerRadius(dp(9));
        square.setColor(Color.parseColor("#E6131B1E"));
        square.setStroke(dp(1), Color.parseColor("#24333A"));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), square, null));
        b.setOnClickListener(onClick);
        AddressBar.addSpringPress(b);
        return b;
    }

    // ---------- Bookmarks ----------

    private void showBookmarks() {
        if (bookmarksView != null) return;
        addressBar.stopEditing();
        bookmarksView = new BookmarksView(this, new BookmarksView.Listener() {
            @Override public void onToggleCurrent() { toggleBookmark(); refreshBookmarks(false); }
            @Override public void onOpen(Bookmarks.Bookmark bookmark) { hideBookmarks(); current.web.loadUrl(bookmark.url); }
            @Override public void onRemove(Bookmarks.Bookmark bookmark) { bookmarks.remove(bookmark.url); refreshBookmarks(false); }
            @Override public void onDeleteAll() { bookmarks.clear(); refreshBookmarks(false); }
            @Override public void onDismiss() { hideBookmarks(); }
        });
        refreshBookmarks(true);
        screen.addView(bookmarksView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void refreshBookmarks(boolean animate) {
        String url = current.url();
        boolean onSite = !current.onHome();
        bookmarksView.show(bookmarks.all(), onSite ? current.label() : null, onSite && bookmarks.contains(url),
                !UrlInput.isSearchPage(url), animate);
        bottomBar.setBookmarked(onSite && bookmarks.contains(url));
    }

    /**
     * Adds a shortcut to the open page, or removes it if there already is one.
     * Search results (their address holds what was typed) and blocked sites can't be bookmarked.
     */
    private void toggleBookmark() {
        String url = current.url();
        if (current.onHome()) return;
        if (bookmarks.contains(url)) {
            bookmarks.remove(url);
        } else if (UrlInput.isSearchPage(url)) {
            Toast.makeText(this, "Searches can't be bookmarked, so they're never kept.", Toast.LENGTH_SHORT).show();
        } else if (ContentFilter.isBlockedUrl(url)) {
            Toast.makeText(this, "Adult sites can't be bookmarked.", Toast.LENGTH_SHORT).show();
        } else if (!bookmarks.add(url)) {
            Toast.makeText(this, "You can have up to " + Bookmarks.MAX + " bookmarks. Remove some first.", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideBookmarks() {
        if (bookmarksView == null) return;
        BookmarksView closing = bookmarksView;
        bookmarksView = null;
        closing.dismiss(() -> screen.removeView(closing));
    }

    // ---------- Settings ----------

    private void showSettings() {
        if (settingsView != null) return;
        settingsView = new SettingsView(this, engine, new SettingsView.Listener() {
            @Override public void onEngine(Engine picked) { setEngine(picked); }
            @Override public void onDismiss() { hideSettings(); }
        });
        screen.addView(settingsView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideSettings() {
        if (settingsView == null) return;
        SettingsView closing = settingsView;
        settingsView = null;
        closing.dismiss(() -> screen.removeView(closing));
    }

    /** Saves the choice and reloads every tab that's on the home page so it shows the new engine. */
    private void setEngine(Engine picked) {
        if (picked == engine) return;
        engine = picked;
        engine.save(this);
        for (Tab tab : tabs) {
            if (tab.onHome()) tab.web.loadUrl(engine.homeUrl());
        }
    }

    // ---------- Tabs ----------

    private Tab tabFor(WebView view) {
        for (Tab tab : tabs) {
            if (tab.web == view) return tab;
        }
        return null;
    }

    private boolean isCurrent(WebView view) {
        return current != null && current.web == view;
    }

    private void newTab() {
        if (tabs.size() >= MAX_TABS) {
            Toast.makeText(this, "You can have up to " + MAX_TABS + " tabs. Close one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Tab tab = new Tab(createWebView());
        tabs.add(tab);
        switchTo(tab);
        tab.web.loadUrl(engine.homeUrl());
    }

    private void switchTo(Tab tab) {
        if (current == tab) return;
        if (current != null) {
            current.capturePreview();
            current.web.onPause(); // background tabs stop their scripts and animations
            content.removeView(current.web);
        }
        current = tab;
        if (tab.web.getParent() == null) {
            content.addView(tab.web, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        tab.web.onResume();
        updateBar(tab.url());
        addressBar.setProgress(tab.web.getProgress());
        updateTabCount();
    }

    private void closeTab(Tab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) return;
        boolean wasCurrent = tab == current;
        if (wasCurrent) current = null; // so switching away never touches the closed tab
        tabs.remove(index);
        content.removeView(tab.web);
        tab.web.destroy();
        if (tabs.isEmpty()) {
            newTab(); // closing the last tab leaves a fresh one
        } else if (wasCurrent) {
            switchTo(tabs.get(Math.max(0, index - 1)));
        }
        updateTabCount();
    }

    /** Closes every tab and starts over with one fresh one. */
    private void closeAllTabs() {
        current = null;
        for (Tab tab : tabs) {
            content.removeView(tab.web);
            tab.web.destroy();
        }
        tabs.clear();
        newTab();
    }

    private void updateTabCount() {
        bottomBar.setTabCount(tabs.size());
        homeTabsButton.setText(String.valueOf(tabs.size()));
        homeTabsButton.setContentDescription(tabs.size() == 1 ? "1 tab open" : tabs.size() + " tabs open");
    }

    private void showTabs() {
        if (tabsView != null) return;
        addressBar.stopEditing();
        current.capturePreview();
        tabsView = new TabsView(this, tabs, current, new TabsView.Listener() {
            @Override public void onSelect(Tab tab) { switchTo(tab); hideTabs(); }
            @Override public void onClose(Tab tab) { closeTab(tab); if (tabsView != null) tabsView.refresh(current); }
            @Override public void onNewTab() { newTab(); hideTabs(); }
            @Override public void onCloseAll() { closeAllTabs(); hideTabs(); }
            @Override public void onDismiss() { hideTabs(); }
        });
        screen.addView(tabsView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideTabs() {
        if (tabsView == null) return;
        TabsView closing = tabsView;
        tabsView = null;
        closing.dismiss(() -> screen.removeView(closing));
    }

    // ---------- Address bar ----------

    private void updateBar(String url) {
        boolean onHome = url == null || url.isEmpty() || url.startsWith("file:///android_asset/");
        int visibility = onHome ? View.GONE : View.VISIBLE;
        if (addressBar.getVisibility() != visibility) {
            if (onHome) addressBar.stopEditing();
            addressBar.setVisibility(visibility);
            bottomBar.setVisibility(visibility);
            homeLeft.setVisibility(onHome ? View.VISIBLE : View.GONE);
            homeRight.setVisibility(onHome ? View.VISIBLE : View.GONE);
        }
        if (!onHome) addressBar.setUrl(url);
        bottomBar.setBookmarked(!onHome && bookmarks.contains(url));
        bottomBar.setNavigation(current != null && current.web.canGoBack(), current != null && current.web.canGoForward());
    }

    /** What the address bar does with typed text: same rules as the home page's search box. */
    private void open(String typed) {
        String text = typed.trim();
        if (text.isEmpty()) return;
        if (ContentFilter.isBlockedText(text)) {
            Toast.makeText(this, "Adult searches are blocked.", Toast.LENGTH_SHORT).show();
            return;
        }
        String address = UrlInput.toUrl(text);
        if (address != null && ContentFilter.isBlockedUrl(address)) {
            Toast.makeText(this, "Adult sites are blocked.", Toast.LENGTH_SHORT).show();
            return;
        }
        addressBar.stopEditing();
        current.web.loadUrl(address != null ? address : engine.searchUrl(text));
    }

    private void goHome() {
        current.clearHistoryOnHome = true;
        current.web.loadUrl(engine.homeUrl());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------- Lifecycle ----------

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (bookmarksView != null) {
            hideBookmarks();
        } else if (settingsView != null) {
            hideSettings();
        } else if (tabsView != null) {
            hideTabs();
        } else if (addressBar.isEditing()) {
            addressBar.stopEditing();
        } else if (current.web.canGoBack()) {
            current.web.goBack();
        } else if (tabs.size() > 1) {
            closeTab(current); // at the start of a tab, Back closes it and returns to the one before
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updater.resume();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && !tabs.isEmpty()) {
            wipe(tabs.get(0).web);
        }
        for (Tab tab : tabs) {
            tab.web.destroy();
        }
        tabs.clear();
        super.onDestroy();
    }

    /** Clears everything every tab could have left behind. Cookies, storage and cache are shared, so any one WebView will do. */
    @SuppressWarnings("deprecation")
    private void wipe(WebView any) {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        cookies.flush();
        WebStorage.getInstance().deleteAllData();
        WebViewDatabase db = WebViewDatabase.getInstance(this);
        db.clearHttpAuthUsernamePassword();
        db.clearFormData();
        any.clearCache(true);
        for (Tab tab : tabs) {
            tab.web.clearHistory();
            tab.web.clearFormData();
        }
        deleteContents(getCacheDir());
    }

    private static void deleteContents(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteContents(f);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Tells the keyboard not to learn from anything typed here (like Gboard's incognito mode). */
    private static final class PrivateWebView extends WebView {
        PrivateWebView(Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection connection = super.onCreateInputConnection(outAttrs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
            }
            return connection;
        }
    }
}
