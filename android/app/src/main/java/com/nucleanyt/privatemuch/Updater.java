package com.nucleanyt.privatemuch;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs only when the Update button is tapped: asks GitHub for the latest build, downloads it if it is
 * newer than this one, and opens the system installer. Nothing is checked in the background.
 */
final class Updater {

    private static final String RELEASE_API =
            "https://api.github.com/repos/NucleanYT/PrivateMuch/releases/tags/latest";
    private static final Pattern BUILD = Pattern.compile("build (\\d+)"); // release title: "PrivateMuch build 12"

    private final Activity activity;
    private final TextView button;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private boolean installWaiting; // waiting for the "install unknown apps" permission

    Updater(Activity activity, TextView button) {
        this.activity = activity;
        this.button = button;
    }

    void start() {
        if (busy) return;
        busy = true;
        button.setText("Checking…");
        new Thread(this::check, "update").start();
    }

    /** Call from onResume: finishes an install that was waiting for permission. */
    void resume() {
        if (installWaiting && canInstall()) {
            installWaiting = false;
            install();
        }
    }

    private void check() {
        try {
            JSONObject release = new JSONObject(readText(RELEASE_API));
            Matcher m = BUILD.matcher(release.optString("name"));
            long latest = m.find() ? Long.parseLong(m.group(1)) : -1;
            String apkUrl = null;
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url");
                    break;
                }
            }
            if (apkUrl == null || latest <= installedBuild()) {
                finish("Up to date");
                return;
            }
            download(apkUrl, ApkProvider.apkFile(activity));
            main.post(() -> {
                busy = false;
                button.setText("Update");
                install();
            });
        } catch (Exception e) {
            finish("Couldn't update");
        }
    }

    private void finish(String message) {
        main.post(() -> {
            button.setText(message);
            main.postDelayed(() -> {
                busy = false;
                button.setText("Update");
            }, 2500);
        });
    }

    private void install() {
        if (!canInstall()) {
            installWaiting = true;
            Toast.makeText(activity, "Allow PrivateMuch to install updates, then come back.", Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(ApkProvider.URI, ApkProvider.MIME);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Couldn't open the installer.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean canInstall() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    @SuppressWarnings("deprecation")
    private long installedBuild() throws PackageManager.NameNotFoundException {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    private void download(String url, File out) throws IOException {
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream(); OutputStream file = new FileOutputStream(out)) {
            long total = connection.getContentLengthLong();
            long received = 0;
            int shown = -1;
            byte[] buffer = new byte[16 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) {
                file.write(buffer, 0, n);
                received += n;
                if (total > 0) {
                    final int percent = (int) (received * 100 / total);
                    if (percent != shown) {
                        shown = percent;
                        main.post(() -> button.setText("Downloading " + percent + "%"));
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) bytes.write(buffer, 0, n);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "PrivateMuch");
        int code = connection.getResponseCode(); // follows GitHub's https redirect to the file
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        return connection;
    }
}
