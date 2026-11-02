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

public class MainActivity extends Activity {
    static volatile boolean foreground = false;
    private WebView web;
    private String pendingRoom;
    private boolean loaded;

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
        web.loadUrl("file:///android_asset/index.html");

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
        if (web != null) web.evaluateJavascript("window.__onResume&&__onResume()", null);
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
