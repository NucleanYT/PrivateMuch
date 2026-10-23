package com.nucleanyt.privatemuch;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;

import java.io.File;

/**
 * Shows the PrivateMuch page (bundled in the app) in a WebView that keeps nothing:
 * cookies, site storage, cache, form data and history are wiped when the app starts and when it closes.
 */
public class MainActivity extends Activity {

    private static final String HOME = "file:///android_asset/index.html";

    private WebView web;

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
        });

        wipe(); // start clean, even if the app was killed last time before it could clean up
        setContentView(web);
        web.loadUrl(HOME); // never restore a previous session
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
