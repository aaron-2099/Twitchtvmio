package com.twitchunblock.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final String START_URL = "https://test2-fawn-eta.vercel.app";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String tvScript = "";

    // ---- modo ratón (puntero) para la pantalla del reproductor ----
    private CursorView cursorView;
    private boolean cursorMode = false;
    private float cx = -1, cy = -1;
    private float density = 1f;
    private int cursorSize = 40;

    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c, float density) {
            super(c);
            fill.setColor(Color.WHITE);
            stroke.setColor(Color.parseColor("#9146FF"));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3 * density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getWidth() / 2f;
            canvas.drawCircle(r, r, r - 4, fill);
            canvas.drawCircle(r, r, r - 4, stroke);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void mode(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setCursorMode(on);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tvScript = readAsset("tv.js");
        density = getResources().getDisplayMetrics().density;
        cursorSize = (int) (26 * density);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursorView = new CursorView(this, density);
        root.addView(cursorView, new FrameLayout.LayoutParams(cursorSize, cursorSize));
        cursorView.setVisibility(View.GONE);

        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(DESKTOP_UA);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

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
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                cursorView.bringToFront();
            }

            @Override
            public void onHideCustomView() {
                hideCustom();
            }
        });

        web.requestFocus();
        web.loadUrl(START_URL);
    }

    private void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        web.requestFocus();
    }

    // ------------------------------------------------------------------
    // Puntero
    // ------------------------------------------------------------------

    private View target() {
        return customView != null ? customView : web;
    }

    private void setCursorMode(boolean on) {
        if (on == cursorMode) return;
        cursorMode = on;
        if (on) {
            if (cx < 0) {
                cx = root.getWidth() / 2f;
                cy = root.getHeight() / 2f;
            }
            placeCursor();
            cursorView.setVisibility(View.VISIBLE);
            cursorView.bringToFront();
            sendMouse(MotionEvent.ACTION_HOVER_MOVE, cx, cy, 0, 0L, 0f);
        } else {
            cursorView.setVisibility(View.GONE);
        }
    }

    private void placeCursor() {
        cursorView.setX(cx - cursorSize / 2f);
        cursorView.setY(cy - cursorSize / 2f);
    }

    private void sendMouse(int action, float x, float y, int buttons, long downTime, float vscroll) {
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[1];
        pp[0] = new MotionEvent.PointerProperties();
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[1];
        pc[0] = new MotionEvent.PointerCoords();
        pc[0].x = x;
        pc[0].y = y;
        pc[0].size = 1f;
        pc[0].pressure = (buttons != 0) ? 1f : 0f;
        if (action == MotionEvent.ACTION_SCROLL) {
            pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll);
        }

        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downTime > 0 ? downTime : now, now, action, 1, pp, pc,
                0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        View t = target();
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_SCROLL) {
            t.dispatchGenericMotionEvent(ev);
        } else {
            t.dispatchTouchEvent(ev);
        }
        ev.recycle();
    }

    private void moveCursor(int keyCode, int repeat) {
        float step = (14 + Math.min(repeat, 20) * 3) * density;
        float ox = cx, oy = cy;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  cx -= step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: cx += step; break;
            case KeyEvent.KEYCODE_DPAD_UP:    cy -= step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  cy += step; break;
            default: break;
        }
        cx = Math.max(0, Math.min(cx, root.getWidth() - 1));
        cy = Math.max(0, Math.min(cy, root.getHeight() - 1));
        placeCursor();

        boolean vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        if (vertical && cy == oy) {
            // el puntero ya está en el borde: desplazar la página (como la rueda del ratón)
            sendMouse(MotionEvent.ACTION_SCROLL, cx, cy, 0, 0L,
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1f : -1f);
        } else {
            sendMouse(MotionEvent.ACTION_HOVER_MOVE, cx, cy, 0, 0L, 0f);
        }
    }

    private void clickCursor() {
        long down = SystemClock.uptimeMillis();
        sendMouse(MotionEvent.ACTION_DOWN, cx, cy, MotionEvent.BUTTON_PRIMARY, down, 0f);
        sendMouse(MotionEvent.ACTION_UP, cx, cy, 0, down, 0f);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (cursorMode) {
            int k = e.getKeyCode();
            boolean dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (dpad) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) moveCursor(k, e.getRepeatCount());
                return true;
            }
            if (ok) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) clickCursor();
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
