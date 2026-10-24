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
import java.util.Collections;

/**
 * Shows the PrivateMuch page (bundled in the app) in a WebView that keeps nothing:
 * cookies, site storage, cache, form data and history are wiped when the app starts and when it closes.
 */
public class MainActivity extends Activity {

    private static final String HOME = "file:///android_asset/index.html";
    private static final String BLOCKED_PAGE = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<body style='margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;box-sizing:border-box;"
            + "background:#0b1012;color:#e2ebe9;font:16px/1.5 sans-serif;text-align:center'>"
            + "<div><div style='font-size:22px;font-weight:600;margin-bottom:6px'>Blocked</div>"
            + "<div style='color:#8fa29f'>PrivateMuch blocks adult content.</div></div>";

    private WebView web;
    private AddressBar addressBar; // shown only while a website is open
    private TextView updateButton; // shown only on the home page
    private Updater updater;
    private boolean clearHistoryOnHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new PrivateWebView(this);
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
                updateBar(url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                updateBar(url); // also catches in-page navigation, like Duck.ai switching chats
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (clearHistoryOnHome && HOME.equals(url)) {
                    view.clearHistory(); // after Home, Back closes the app instead of reopening the site
                    clearHistoryOnHome = false;
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int percent) {
                addressBar.setProgress(percent);
            }
        });

        wipe(); // start clean, even if the app was killed last time before it could clean up
        setContentView(buildLayout());
        web.loadUrl(HOME); // never restore a previous session
    }

    /** The address bar above the WebView, and the Update pill floating on the home page. */
    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1012"));
        root.setLayoutTransition(new LayoutTransition());

        addressBar = new AddressBar(this, new AddressBar.Listener() {
            @Override public void onHome() { goHome(); }
            @Override public void onSubmit(String typed) { open(typed); }
            @Override public void onReload() { web.reload(); }
        });
        addressBar.setVisibility(View.GONE);
        root.addView(addressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(this);
        content.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateButton = buildUpdateButton();
        updater = new Updater(this, updateButton);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        corner.setMargins(0, dp(10), dp(12), 0);
        content.addView(updateButton, corner);

        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
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

    private void updateBar(String url) {
        boolean onHome = url == null || url.startsWith("file:///android_asset/");
        int visibility = onHome ? View.GONE : View.VISIBLE;
        if (addressBar.getVisibility() != visibility) {
            if (onHome) addressBar.stopEditing();
            addressBar.setVisibility(visibility);
            updateButton.setVisibility(onHome ? View.VISIBLE : View.GONE);
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
        web.loadUrl(address != null ? address : searchUrl(text));
    }

    private static String searchUrl(String text) {
        try {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8") + "&kp=1"; // kp=1: strict Safe Search
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always available
        }
    }

    private void goHome() {
        clearHistoryOnHome = true;
        web.loadUrl(HOME);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (addressBar.isEditing()) {
            addressBar.stopEditing();
        } else if (web.canGoBack()) {
            web.goBack();
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
        if (isFinishing()) {
            wipe();
        }
        web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void wipe() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        cookies.flush();
        WebStorage.getInstance().deleteAllData();
        WebViewDatabase db = WebViewDatabase.getInstance(this);
        db.clearHttpAuthUsernamePassword();
        db.clearFormData();
        web.clearCache(true);
        web.clearHistory();
        web.clearFormData();
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
