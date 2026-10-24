package com.nucleanyt.privatemuch;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Shows the PrivateMuch page (bundled in the app) in a WebView that keeps nothing:
 * cookies, site storage, cache, form data and history are wiped when the app starts and when it closes.
 */
public class MainActivity extends Activity {

    private static final String HOME = "file:///android_asset/index.html";

    private WebView web;
    private LinearLayout bar;      // shown only while a website is open
    private View divider;
    private TextView site;
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
                return !("https".equals(scheme) || "http".equals(scheme));
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

        wipe(); // start clean, even if the app was killed last time before it could clean up
        setContentView(buildLayout());
        web.loadUrl(HOME); // never restore a previous session
    }

    /** A slim top bar (Home + the site's name) above the WebView. */
    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1012"));
        root.setLayoutTransition(new LayoutTransition());

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#131B1E"));
        bar.setPadding(dp(4), 0, dp(16), 0);
        bar.setVisibility(View.GONE);

        ImageButton home = new ImageButton(this);
        home.setImageResource(R.drawable.ic_home);
        home.setContentDescription("Home");
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        home.setBackgroundResource(ripple.resourceId);
        home.setOnClickListener(v -> goHome());
        bar.addView(home, new LinearLayout.LayoutParams(dp(48), dp(48)));

        site = new TextView(this);
        site.setTextColor(Color.parseColor("#8FA29F"));
        site.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        site.setSingleLine(true);
        site.setEllipsize(TextUtils.TruncateAt.END);
        site.setPadding(dp(4), 0, 0, 0);
        bar.addView(site, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#24333A"));
        divider.setVisibility(View.GONE);

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void updateBar(String url) {
        boolean onHome = url == null || url.startsWith("file:///android_asset/");
        int visibility = onHome ? View.GONE : View.VISIBLE;
        if (bar.getVisibility() != visibility) {
            bar.setVisibility(visibility);
            divider.setVisibility(visibility);
        }
        if (!onHome) {
            String host = Uri.parse(url).getHost();
            if (host != null && host.startsWith("www.")) host = host.substring(4);
            site.setText(host == null ? "" : host);
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
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
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
