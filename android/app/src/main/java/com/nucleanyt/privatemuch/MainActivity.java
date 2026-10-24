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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows the PrivateMuch page (bundled in the app) in tabs of WebViews that keep nothing:
 * cookies, site storage, cache, form data and history are wiped when the app starts and when it closes.
 */
public class MainActivity extends Activity {

    private static final String HOME = Tab.HOME;
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
    private LinearLayout homeButtons; // Tabs + Update, shown only on the home page
    private TextView homeTabsButton;
    private TextView updateButton;
    private Updater updater;
    private TabsView tabsView;     // the tab overview while it's open

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tab first = new Tab(createWebView());
        tabs.add(first);
        wipe(first.web); // start clean, even if the app was killed last time before it could clean up
        setContentView(buildLayout());
        switchTo(first);
        first.web.loadUrl(HOME); // never restore a previous session
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
                    String typedAddress = UrlInput.fromDuckDuckGoSearch(request.getUrl().toString());
                    if (typedAddress != null) { // a web address typed into DuckDuckGo's box opens the site
                        view.loadUrl(typedAddress);
                        return true;
                    }
                    String safe = ContentFilter.withSafeSearch(request.getUrl().toString());
                    if (safe != null) { // every DuckDuckGo search gets strict Safe Search
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
                if (tab != null && tab.clearHistoryOnHome && HOME.equals(url)) {
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

    /** The address bar above the page, the Tabs + Update buttons on the home page, and room for the tab overview. */
    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1012"));
        root.setLayoutTransition(new LayoutTransition());

        addressBar = new AddressBar(this, new AddressBar.Listener() {
            @Override public void onHome() { goHome(); }
            @Override public void onSubmit(String typed) { open(typed); }
            @Override public void onReload() { current.web.reload(); }
            @Override public void onTabs() { showTabs(); }
        });
        addressBar.setVisibility(View.GONE);
        root.addView(addressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        homeButtons = new LinearLayout(this);
        homeButtons.setOrientation(LinearLayout.HORIZONTAL);
        homeButtons.setGravity(Gravity.CENTER_VERTICAL);
        homeTabsButton = AddressBar.tabsButton(this, true);
        homeTabsButton.setOnClickListener(v -> showTabs());
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        tabsParams.setMarginEnd(dp(8));
        homeButtons.addView(homeTabsButton, tabsParams);
        updateButton = buildUpdateButton();
        updater = new Updater(this, updateButton);
        homeButtons.addView(updateButton);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        corner.setMargins(0, dp(10), dp(12), 0);
        content.addView(homeButtons, corner);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
        tab.web.loadUrl(HOME);
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

    private void updateTabCount() {
        addressBar.setTabCount(tabs.size());
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
            homeButtons.setVisibility(onHome ? View.VISIBLE : View.GONE);
        }
        if (!onHome) addressBar.setUrl(url);
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
        current.web.loadUrl(address != null ? address : searchUrl(text));
    }

    private static String searchUrl(String text) {
        try {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8") + "&kp=1"; // kp=1: strict Safe Search
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always available
        }
    }

    private void goHome() {
        current.clearHistoryOnHome = true;
        current.web.loadUrl(HOME);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------- Lifecycle ----------

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tabsView != null) {
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
