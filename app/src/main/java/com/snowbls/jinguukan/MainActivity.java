package com.snowbls.jinguukan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static volatile boolean foreground = false;
    private WebView web;
    private String pendingRoom;
    private boolean loaded;

    /* 화면 파일 원격 업데이트: 깃허브에 올라간 최신 index.html을 받아 두었다가 다음에 앱으로 돌아올 때 적용 */
    static final String REMOTE = "https://raw.githubusercontent.com/snowbls/app/main/app/src/main/assets/index.html";
    static final String BASE = "file:///android_asset/index.html";
    private long loadedVer = 0;
    private volatile String readyHtml = null;
    private volatile long readyVer = 0;
    private boolean checking = false;

    static long verOf(String html) {
        if (html == null) return 0;
        Matcher m = Pattern.compile("<!--jgk-web:(\\d+)-->").matcher(html.length() > 600 ? html.substring(0, 600) : html);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private File cacheFile() { return new File(getFilesDir(), "web.html"); }

    private long assetVer() {
        try {
            InputStream in = getAssets().open("index.html");
            byte[] b = new byte[600];
            int n = in.read(b);
            in.close();
            return verOf(new String(b, 0, Math.max(n, 0), StandardCharsets.UTF_8));
        } catch (Exception e) { return 0; }
    }

    private void loadBest() {
        long av = assetVer();
        String html = null;
        try {
            File f = cacheFile();
            if (f.exists()) {
                String c = readAll(new java.io.FileInputStream(f));
                if (verOf(c) > av && c.contains("__onPause")) html = c;
            }
        } catch (Exception ignored) { }
        loaded = false;
        if (html != null) {
            loadedVer = verOf(html);
            web.loadDataWithBaseURL(BASE, html, "text/html", "utf-8", null);
        } else {
            loadedVer = av;
            web.loadUrl(BASE);
        }
    }

    private void checkRemote() {
        if (checking) return;
        checking = true;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(REMOTE + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(20000);
                c.setUseCaches(false);
                if (c.getResponseCode() == 200) {
                    String html = readAll(c.getInputStream());
                    long v = verOf(html);
                    if (v > loadedVer && html.contains("__onPause") && html.contains("</html>")) {
                        File tmp = new File(getFilesDir(), "web.tmp");
                        FileOutputStream o = new FileOutputStream(tmp);
                        o.write(html.getBytes(StandardCharsets.UTF_8));
                        o.close();
                        if (tmp.renameTo(cacheFile())) { readyHtml = html; readyVer = v; }
                    }
                }
                c.disconnect();
            } catch (Exception ignored) { }
            checking = false;
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#2FBF5B"));
        Notifier.ensureChannel(this);

        pendingRoom = getIntent().getStringExtra("room");
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                loaded = true;
                openPending();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        loadBest();
        checkRemote();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingRoom = intent.getStringExtra("room");
        if (loaded) openPending();
    }

    private void openPending() {
        String r = pendingRoom;
        pendingRoom = null;
        if (web == null || r == null || !r.matches("[a-z_]+")) return;
        web.evaluateJavascript("window.__openRoom&&__openRoom('" + r + "')", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (web != null && readyHtml != null && readyVer > loadedVer) {
            String h = readyHtml;
            readyHtml = null;
            loaded = false;
            loadedVer = readyVer;
            web.loadDataWithBaseURL(BASE, h, "text/html", "utf-8", null);
        } else if (web != null) {
            web.evaluateJavascript("window.__onResume&&__onResume()", null);
            checkRemote();
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        if (web != null) web.evaluateJavascript("window.__onPause&&__onPause()", null);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.__onBack?__onBack():Android.exit()", null);
    }

    class Bridge {
        @JavascriptInterface
        public boolean isForeground() { return foreground; }

        @JavascriptInterface
        public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public void post(String title, String body, int id, String who, String room) {
            Notifier.post(MainActivity.this, title, body, id, who, room);
        }

        @JavascriptInterface
        public void schedule(String json) {
            Notifier.schedule(MainActivity.this, json);
        }

        @JavascriptInterface
        public void clearAll() {
            Notifier.clearAll(MainActivity.this);
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(() -> moveTaskToBack(true));
        }
    }
}
