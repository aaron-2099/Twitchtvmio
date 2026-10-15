package com.twitchunblock.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final String VERSION = "1.4";
    private static final float TARGET_CSS_WIDTH = 1600f;
    private static final String START_URL = "https://test2-fawn-eta.vercel.app";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String tvScript = "";
    private FrameLayout scaledHost;
    private float viewK = 1f;
    private int cssW = 0, cssH = 0;

    // ---- modo puntero: el puntero lo dibuja y maneja la propia página (tv.js) ----
    private boolean cursorMode = false;
    private boolean autoMode = false;       // lo que detecta la web (¿estamos en el reproductor?)
    private Boolean manualMode = null;      // lo que elige el usuario con OK mantenido
    private boolean okLong = false;
    private boolean okStartedInCursor = false;
    private boolean fsActive = false;       // pantalla completa simulada dentro de la página

    private class Bridge {
        @JavascriptInterface
        public void mode(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    autoMode = on;
                    manualMode = null;
                    applyMode();
                }
            });
        }

        @JavascriptInterface
        public void fs(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fsActive = on;
                }
            });
        }

        @JavascriptInterface
        public void hover(final double fx, final double fy) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sendHover((float) fx, (float) fy);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tvScript = readAsset("tv.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);

        // La web se dibuja en una "pantalla virtual" más grande (viewK veces) y se reduce
        // para que quepa: así se ve como en un monitor de PC y no con zoom.
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        viewK = Math.max(1f, TARGET_CSS_WIDTH / (dm.widthPixels / density));
        int hw = Math.round(dm.widthPixels * viewK);
        int hh = Math.round(dm.heightPixels * viewK);
        cssW = Math.round(hw / density);
        cssH = Math.round(hh / density);
        scaledHost = new FrameLayout(this);
        scaledHost.setPivotX(0f);
        scaledHost.setPivotY(0f);
        scaledHost.setScaleX(1f / viewK);
        scaledHost.setScaleY(1f / viewK);
        scaledHost.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(scaledHost, new FrameLayout.LayoutParams(hw, hh));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(DESKTOP_UA);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "TVBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sc = request.getUrl().getScheme();
                return !("http".equals(sc) || "https".equals(sc));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                fsActive = false;
                view.evaluateJavascript(tvScript, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                web.setVisibility(View.GONE);
                scaledHost.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                hideCustom();
            }
        });

        web.requestFocus();
        web.loadUrl(START_URL);
        Toast.makeText(this, "TwitchUnblock v" + VERSION + " - vista " + cssW + "x" + cssH
                + " - mantén OK para activar/desactivar el puntero", Toast.LENGTH_LONG).show();
    }

    private void hideCustom() {
        if (customView == null) return;
        scaledHost.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        web.requestFocus();
    }

    // ------------------------------------------------------------------
    // Puntero
    // ------------------------------------------------------------------

    private void applyMode() {
        cursorMode = manualMode != null ? manualMode : autoMode;
        web.evaluateJavascript("window.__tvCursorMode&&window.__tvCursorMode(" + cursorMode + ")", null);
    }

    /** Mueve el ratón "de verdad" (hover) para que funcionen los controles que salen al pasar el ratón. */
    private void sendHover(float fx, float fy) {
        View t = customView != null ? customView : web;
        float x = fx * t.getWidth();
        float y = fy * t.getHeight();
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[1];
        pp[0] = new MotionEvent.PointerProperties();
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[1];
        pc[0] = new MotionEvent.PointerCoords();
        pc[0].x = x;
        pc[0].y = y;
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        t.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void cursorKey(String dir, int repeat) {
        web.evaluateJavascript("window.__tvCur&&window.__tvCur('" + dir + "'," + repeat + ")", null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            int act = e.getAction();
            if (act == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    okLong = false;
                    okStartedInCursor = cursorMode;
                } else if (e.getRepeatCount() == 1 && !okLong) {
                    // OK mantenido: activar / desactivar el puntero a mano
                    okLong = true;
                    manualMode = !cursorMode;
                    applyMode();
                    Toast.makeText(this, cursorMode ? "Puntero activado" : "Puntero desactivado",
                            Toast.LENGTH_SHORT).show();
                }
                if (okStartedInCursor || okLong) return true;
            } else if (act == KeyEvent.ACTION_UP) {
                boolean consume = okStartedInCursor || okLong;
                if (okStartedInCursor && !okLong) {
                    web.evaluateJavascript("window.__tvCurClick&&window.__tvCurClick()", null);
                }
                okStartedInCursor = false;
                okLong = false;
                if (consume) return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (cursorMode) {
            String dir = null;
            if (k == KeyEvent.KEYCODE_DPAD_UP) dir = "up";
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) dir = "down";
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) dir = "left";
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dir = "right";
            if (dir != null) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) cursorKey(dir, e.getRepeatCount());
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void js(String code) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return;" + code + "})();", null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (fsActive) {
                    web.evaluateJavascript(
                            "window.__tvExitFs?(window.__tvExitFs()?'1':'0'):'0'",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String v) {
                                    if (!"\"1\"".equals(v)) {
                                        fsActive = false;
                                        if (web.canGoBack()) web.goBack();
                                    }
                                }
                            });
                    return true;
                }
                if (customView != null) {
                    hideCustom();
                    return true;
                }
                if (web.canGoBack()) {
                    web.goBack();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                js("if(v.paused){v.play();}else{v.pause();}");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                js("v.play();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                js("v.pause();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                js("v.currentTime+=10;");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                js("v.currentTime-=10;");
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
