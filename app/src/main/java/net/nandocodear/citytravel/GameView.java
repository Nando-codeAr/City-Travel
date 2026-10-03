package net.nandocodear.citytravel;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Superficie de dibujo, bucle principal y lectura del táctil multipunto. */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private final Game game;
    private Thread loop;
    private volatile boolean running;
    private volatile boolean hasSurface;
    private boolean hardwareFailed;

    public GameView(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        hasSurface = true;
        startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        stopLoop();
    }

    void resume() {
        if (hasSurface) startLoop();
    }

    void pause() {
        stopLoop();
    }

    private synchronized void startLoop() {
        if (running) return;
        running = true;
        loop = new Thread(this, "CityTravel-Loop");
        loop.start();
    }

    private void stopLoop() {
        Thread t;
        synchronized (this) {
            running = false;
            t = loop;
            loop = null;
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private float scale() {
        int h = getHeight();
        return h > 0 ? h / Game.H : 1f;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        SurfaceHolder holder = getHolder();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1e9f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                sleep(16);
                continue;
            }
            float s = h / Game.H;
            game.W = w / s;
            game.bitmapScale = Math.min(2f, Math.max(1f, s));

            // dos sub-pasos para una física más estable
            game.update(dt * 0.5f);
            game.update(dt * 0.5f);

            Canvas c = null;
            Surface surface = holder.getSurface();
            if (surface == null || !surface.isValid()) {
                sleep(16);
                continue;
            }
            boolean hw = false;
            try {
                if (!hardwareFailed) {
                    try {
                        c = surface.lockHardwareCanvas();
                        hw = true;
                    } catch (Exception e) {
                        hardwareFailed = true;
                        c = null;
                    }
                }
                if (c == null) c = holder.lockCanvas();
                if (c != null) {
                    c.save();
                    c.scale(s, s);
                    game.draw(c);
                    c.restore();
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try {
                        if (hw) surface.unlockCanvasAndPost(c);
                        else holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }

            long frame = (System.nanoTime() - now) / 1000000L;
            if (frame < 15) sleep(15 - frame);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float s = scale();
        int action = e.getActionMasked();
        int upIndex = -1;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            upIndex = e.getActionIndex();
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            game.tap(e.getX(i) / s, e.getY(i) / s);
        }
        boolean left = false, right = false, jump = false, fire = false;
        if (action != MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (i == upIndex) continue;
                float x = e.getX(i) / s;
                float y = e.getY(i) / s;
                if (y < 46) continue;
                float bx = game.btnLeftX(), by = game.btnY();
                float rx = game.btnRightX();
                float mid = (bx + rx) / 2;
                // zona izquierda generosa: dividida en dos mitades
                if (x < rx + 70 && y > Game.H * 0.45f) {
                    if (x < mid) left = true;
                    else right = true;
                    continue;
                }
                float jx = game.btnJumpX(), jy = game.btnJumpY();
                float fx = game.btnFireX(), fy = game.btnFireY();
                float dj = dist(x, y, jx, jy);
                float df = dist(x, y, fx, fy);
                if (y > Game.H * 0.45f && x > fx - 70) {
                    if (dj <= df) jump = true;
                    else fire = true;
                }
            }
        }
        game.inLeft = left;
        game.inRight = right;
        game.inJump = jump;
        game.inFire = fire;
        return true;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
