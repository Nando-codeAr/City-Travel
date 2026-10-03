package net.nandocodear.citytravel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Fondo de cada ciudad con varias capas de parallax:
 * cielo degradado, sol/luna y estrellas, nubes, skyline lejano con el hito
 * de la ciudad y una fila de edificios cercanos con ventanas y carteles de neón.
 */
final class Backdrop {

    static final float FAR_W = 1400f;
    static final float MID_W = 1700f;
    static final float GROUND_Y = Game.GROUND_Y;

    final City city;
    private final float H;
    private final Bitmap far;
    private final Bitmap mid;
    private final Rect farSrc, midSrc;
    private final RectF dst = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint skyPaint = new Paint();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float[] starX = new float[90];
    private final float[] starY = new float[90];
    private final float[] starP = new float[90];
    private final float[] cloudX = new float[7];
    private final float[] cloudY = new float[7];
    private final float[] cloudS = new float[7];

    Backdrop(City city, float H, float bitmapScale) {
        this.city = city;
        this.H = H;
        Random r = new Random(city.seed);

        skyPaint.setShader(new LinearGradient(0, 0, 0, GROUND_Y, city.skyTop, city.skyBottom, Shader.TileMode.CLAMP));

        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat() * 2000f;
            starY[i] = 40 + r.nextFloat() * (GROUND_Y * 0.6f);
            starP[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < cloudX.length; i++) {
            cloudX[i] = r.nextFloat() * 2400f;
            cloudY[i] = 50 + r.nextFloat() * 150f;
            cloudS[i] = 0.6f + r.nextFloat() * 0.9f;
        }

        int fw = Math.max(1, (int) (FAR_W * bitmapScale));
        int mw = Math.max(1, (int) (MID_W * bitmapScale));
        int bh = Math.max(1, (int) (H * bitmapScale));
        far = Bitmap.createBitmap(fw, bh, Bitmap.Config.ARGB_8888);
        mid = Bitmap.createBitmap(mw, bh, Bitmap.Config.ARGB_8888);
        farSrc = new Rect(0, 0, fw, bh);
        midSrc = new Rect(0, 0, mw, bh);

        Canvas c = new Canvas(far);
        c.scale(bitmapScale, bitmapScale);
        buildFar(c, r);
        c = new Canvas(mid);
        c.scale(bitmapScale, bitmapScale);
        buildMid(c, r);
    }

    void recycle() {
        far.recycle();
        mid.recycle();
    }

    // ------------------------------------------------------------------

    static int lerpColor(int a, int b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a), aa = Color.alpha(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b), ba = Color.alpha(b);
        return Color.argb((int) (aa + (ba - aa) * t), (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    static int shade(int c, float hueShift, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(0, Math.min(1, hsv[1] * satMul));
        hsv[2] = Math.max(0, Math.min(1, hsv[2] * valMul));
        return Color.HSVToColor(Color.alpha(c), hsv);
    }

    private void buildFar(Canvas c, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int base = city.farColor;
        float x = 0;
        float landmarkX = FAR_W * 0.55f;
        while (x < FAR_W) {
            float w = 36 + r.nextFloat() * 70;
            if (FAR_W - (x + w) < 30) w = FAR_W - x;
            float h = 70 + r.nextFloat() * 170;
            if (Math.abs(x + w / 2 - landmarkX) < 120) h *= 0.6f;
            float top = GROUND_Y - h;
            p.setColor(shade(base, 0, 1f, 0.85f + r.nextFloat() * 0.3f));
            c.drawRect(x, top, x + w, GROUND_Y + 40, p);
            // remates
            int kind = r.nextInt(5);
            if (kind == 0) {
                c.drawRect(x + w * 0.3f, top - 14, x + w * 0.7f, top, p);
            } else if (kind == 1) {
                p.setStrokeWidth(2);
                c.drawLine(x + w * 0.5f, top, x + w * 0.5f, top - 26, p);
            } else if (kind == 2) {
                Path tri = new Path();
                tri.moveTo(x, top);
                tri.lineTo(x + w / 2, top - 18);
                tri.lineTo(x + w, top);
                tri.close();
                c.drawPath(tri, p);
            }
            // ventanitas tenues
            if (city.night > 0.2f) {
                p.setColor(Color.argb((int) (90 * city.night), Color.red(city.windowColor),
                        Color.green(city.windowColor), Color.blue(city.windowColor)));
                for (float wy = top + 8; wy < GROUND_Y - 6; wy += 11) {
                    for (float wx = x + 5; wx < x + w - 5; wx += 9) {
                        if (r.nextFloat() < 0.28f) c.drawRect(wx, wy, wx + 3, wy + 4, p);
                    }
                }
            }
            x += w + (r.nextFloat() < 0.3f ? 6 + r.nextFloat() * 14 : 0);
        }
        drawLandmark(c, landmarkX, shade(base, 0, 1.1f, 1.25f), p);
    }

    private void drawLandmark(Canvas c, float cx, int col, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        Path path = new Path();
        float g = GROUND_Y + 10;
        switch (city.landmark) {
            case City.LM_OBELISCO: {
                path.moveTo(cx - 16, g);
                path.lineTo(cx - 10, g - 300);
                path.lineTo(cx, g - 322);
                path.lineTo(cx + 10, g - 300);
                path.lineTo(cx + 16, g);
                path.close();
                c.drawPath(path, p);
                p.setColor(Color.argb(200, 255, 230, 140));
                c.drawRect(cx - 3, g - 296, cx + 3, g - 290, p);
                break;
            }
            case City.LM_RASCACIELOS: {
                c.drawRect(cx - 44, g - 210, cx + 44, g, p);
                c.drawRect(cx - 32, g - 270, cx + 32, g - 205, p);
                c.drawRect(cx - 20, g - 315, cx + 20, g - 265, p);
                c.drawRect(cx - 9, g - 345, cx + 9, g - 312, p);
                p.setStrokeWidth(3);
                c.drawLine(cx, g - 345, cx, g - 395, p);
                p.setColor(Color.argb(170, 255, 210, 110));
                for (float yy = g - 340; yy < g - 10; yy += 14) c.drawRect(cx - 3, yy, cx + 3, yy + 6, p);
                p.setColor(Color.argb(255, 255, 60, 60));
                c.drawCircle(cx, g - 396, 3, p);
                break;
            }
            case City.LM_RELOJ: {
                c.drawRect(cx - 22, g - 250, cx + 22, g, p);
                c.drawRect(cx - 27, g - 300, cx + 27, g - 245, p);
                path.moveTo(cx - 27, g - 300);
                path.lineTo(cx, g - 360);
                path.lineTo(cx + 27, g - 300);
                path.close();
                c.drawPath(path, p);
                p.setColor(Color.argb(255, 255, 240, 190));
                c.drawCircle(cx, g - 272, 15, p);
                p.setColor(col);
                p.setStrokeWidth(2.5f);
                c.drawLine(cx, g - 272, cx, g - 283, p);
                c.drawLine(cx, g - 272, cx + 8, g - 270, p);
                // parlamento
                c.drawRect(cx + 22, g - 120, cx + 200, g, p);
                for (float xx = cx + 30; xx < cx + 196; xx += 22) c.drawRect(xx, g - 136, xx + 8, g - 118, p);
                break;
            }
            case City.LM_EIFFEL: {
                path.moveTo(cx - 70, g);
                path.quadTo(cx - 30, g - 120, cx - 9, g - 300);
                path.lineTo(cx, g - 350);
                path.lineTo(cx + 9, g - 300);
                path.quadTo(cx + 30, g - 120, cx + 70, g);
                path.lineTo(cx + 40, g);
                path.quadTo(cx, g - 90, cx - 40, g);
                path.close();
                c.drawPath(path, p);
                c.drawRect(cx - 44, g - 112, cx + 44, g - 102, p);
                c.drawRect(cx - 22, g - 215, cx + 22, g - 207, p);
                p.setColor(Color.argb(200, 255, 230, 150));
                for (int i = 0; i < 18; i++) {
                    float t = i / 17f;
                    float yy = g - 20 - t * 300;
                    float half = 60 * (1 - t) * (1 - t) + 6;
                    c.drawCircle(cx - half * 0.8f, yy, 1.6f, p);
                    c.drawCircle(cx + half * 0.8f, yy, 1.6f, p);
                }
                break;
            }
            case City.LM_TORRE_TOKIO: {
                int red = 0xFFE8344E;
                p.setColor(shade(red, 0, 0.9f, 0.75f));
                path.moveTo(cx - 55, g);
                path.lineTo(cx - 8, g - 280);
                path.lineTo(cx - 3, g - 360);
                path.lineTo(cx + 3, g - 360);
                path.lineTo(cx + 8, g - 280);
                path.lineTo(cx + 55, g);
                path.lineTo(cx + 32, g);
                path.lineTo(cx, g - 120);
                path.lineTo(cx - 32, g);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFFF0F0F0);
                c.drawRect(cx - 30, g - 150, cx + 30, g - 138, p);
                c.drawRect(cx - 16, g - 245, cx + 16, g - 235, p);
                p.setColor(0xFFFFE070);
                c.drawCircle(cx, g - 362, 3, p);
                break;
            }
            case City.LM_CERRO: {
                path.moveTo(cx - 260, g);
                path.quadTo(cx - 120, g - 200, cx - 20, g - 280);
                path.quadTo(cx, g - 300, cx + 20, g - 282);
                path.quadTo(cx + 140, g - 180, cx + 300, g);
                path.close();
                c.drawPath(path, p);
                // estatua con brazos abiertos
                p.setColor(shade(col, 0, 0.5f, 1.25f));
                c.drawRect(cx - 3, g - 330, cx + 3, g - 290, p);
                c.drawRect(cx - 22, g - 324, cx + 22, g - 319, p);
                c.drawCircle(cx, g - 334, 4, p);
                // otro morro
                p.setColor(col);
                Path p2 = new Path();
                p2.moveTo(cx + 340, g);
                p2.quadTo(cx + 380, g - 210, cx + 420, g - 200);
                p2.quadTo(cx + 450, g - 150, cx + 520, g);
                p2.close();
                c.drawPath(p2, p);
                break;
            }
            default:
                break;
        }
    }

    private void buildMid(Canvas c, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = 0;
        float night = city.night;
        while (x < MID_W) {
            float w = 60 + r.nextFloat() * 90;
            if (MID_W - (x + w) < 50) w = MID_W - x;
            float h = 110 + r.nextFloat() * 220;
            float top = GROUND_Y - h;
            int body = shade(city.midColor, (r.nextFloat() - 0.5f) * 50f, 0.8f + r.nextFloat() * 0.5f,
                    0.75f + r.nextFloat() * 0.45f);
            p.setColor(body);
            c.drawRect(x, top, x + w, GROUND_Y + 40, p);
            // borde iluminado
            p.setColor(shade(body, 0, 0.8f, 1.35f));
            c.drawRect(x, top, x + 3, GROUND_Y + 40, p);
            c.drawRect(x, top, x + w, top + 4, p);

            // ventanas
            int lit = city.windowColor;
            int dark = shade(body, 0, 1f, 0.6f);
            int glass = Color.argb(255, 170, 220, 255);
            float ww = 7 + r.nextInt(3) * 2;
            float wh = 9 + r.nextInt(3) * 2;
            float gapx = 6 + r.nextInt(3) * 2;
            float gapy = 7;
            for (float wy = top + 12; wy < GROUND_Y - 14; wy += wh + gapy) {
                for (float wx = x + 8; wx + ww < x + w - 6; wx += ww + gapx) {
                    float roll = r.nextFloat();
                    if (night > 0.3f) {
                        p.setColor(roll < 0.55f * night ? lit : dark);
                    } else {
                        p.setColor(roll < 0.5f ? glass : lerpColor(glass, dark, 0.45f));
                    }
                    c.drawRect(wx, wy, wx + ww, wy + wh, p);
                }
            }
            // cartel de neón
            if (r.nextFloat() < 0.45f && w > 70) {
                int neon = r.nextBoolean() ? city.accent : city.accent2;
                float sy = top + 20 + r.nextFloat() * Math.max(10, h * 0.4f);
                float sw = w * (0.5f + r.nextFloat() * 0.3f);
                float sx = x + (w - sw) / 2;
                p.setColor(Color.argb(70, Color.red(neon), Color.green(neon), Color.blue(neon)));
                c.drawRoundRect(new RectF(sx - 6, sy - 6, sx + sw + 6, sy + 26), 8, 8, p);
                p.setColor(0xFF120820);
                c.drawRoundRect(new RectF(sx, sy, sx + sw, sy + 20), 4, 4, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f);
                p.setColor(neon);
                c.drawRoundRect(new RectF(sx, sy, sx + sw, sy + 20), 4, 4, p);
                p.setStyle(Paint.Style.FILL);
                // "letras" abstractas
                for (float lx = sx + 6; lx < sx + sw - 8; lx += 9) {
                    c.drawRect(lx, sy + 6, lx + 5, sy + 14, p);
                }
            }
            // antenas y tanques
            int roof = r.nextInt(4);
            p.setColor(shade(body, 0, 1f, 0.7f));
            if (roof == 0) {
                c.drawRect(x + w * 0.6f, top - 22, x + w * 0.8f, top, p);
                c.drawRect(x + w * 0.58f, top - 26, x + w * 0.82f, top - 20, p);
            } else if (roof == 1) {
                p.setStrokeWidth(2);
                c.drawLine(x + w * 0.3f, top, x + w * 0.3f, top - 34, p);
                p.setColor(0xFFFF3040);
                c.drawCircle(x + w * 0.3f, top - 35, 2.5f, p);
            }
            x += w + (r.nextFloat() < 0.25f ? 10 + r.nextFloat() * 20 : 0);
        }
    }

    // ------------------------------------------------------------------

    void draw(Canvas c, float camX, float W, float time) {
        // cielo
        c.drawRect(0, 0, W, GROUND_Y + 1, skyPaint);

        // estrellas
        if (city.night > 0.3f) {
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < starX.length; i++) {
                float sx = mod(starX[i] - camX * 0.02f, 2000f);
                if (sx > W) continue;
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + starP[i]);
                paint.setColor(Color.argb((int) (220 * city.night * tw), 255, 255, 255));
                c.drawCircle(sx, starY[i], i % 7 == 0 ? 1.8f : 1.1f, paint);
            }
        }

        // sol o luna
        float bx = W * 0.78f - mod(camX * 0.01f, 60f);
        if (city.night > 0.7f) {
            paint.setColor(0x33FFFFFF);
            c.drawCircle(bx, 90, 46, paint);
            paint.setColor(0xFFFFF6D8);
            c.drawCircle(bx, 90, 30, paint);
            paint.setColor(city.skyTop);
            c.drawCircle(bx + 12, 82, 26, paint);
        } else {
            int sun = city.night > 0.2f ? 0xFFFFB45A : 0xFFFFF3A0;
            paint.setColor(Color.argb(60, Color.red(sun), Color.green(sun), Color.blue(sun)));
            c.drawCircle(bx, city.night > 0.2f ? 250 : 95, 78, paint);
            paint.setColor(sun);
            c.drawCircle(bx, city.night > 0.2f ? 250 : 95, 48, paint);
            if (city.night > 0.2f) {
                // franjas de atardecer estilo retro
                paint.setColor(city.skyBottom);
                for (int i = 0; i < 5; i++) {
                    float yy = 250 + 8 + i * 9;
                    c.drawRect(bx - 60, yy, bx + 60, yy + 2 + i, paint);
                }
            }
        }

        // nubes
        int cloudCol = city.night > 0.7f ? 0x40B0B8FF : (city.night > 0.2f ? 0x70FFD0E0 : 0xD0FFFFFF);
        paint.setColor(cloudCol);
        for (int i = 0; i < cloudX.length; i++) {
            float sx = mod(cloudX[i] - camX * 0.06f - time * 8f * cloudS[i], 2400f) - 200;
            if (sx > W + 200) continue;
            float s = cloudS[i];
            float cy = cloudY[i];
            dst.set(sx, cy - 12 * s, sx + 120 * s, cy + 14 * s);
            c.drawOval(dst, paint);
            dst.set(sx + 25 * s, cy - 28 * s, sx + 85 * s, cy + 4 * s);
            c.drawOval(dst, paint);
        }

        // capa lejana
        drawTiled(c, far, farSrc, FAR_W, camX * 0.18f, W);
        // capa media
        drawTiled(c, mid, midSrc, MID_W, camX * 0.45f, W);
    }

    private void drawTiled(Canvas c, Bitmap b, Rect src, float tileW, float offset, float W) {
        float x = -mod(offset, tileW);
        while (x < W) {
            dst.set(x, 0, x + tileW + 0.5f, H);
            c.drawBitmap(b, src, dst, bmpPaint);
            x += tileW;
        }
    }

    static float mod(float a, float m) {
        float r = a % m;
        return r < 0 ? r + m : r;
    }
}
