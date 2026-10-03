package net.nandocodear.citytravel;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Random;

/**
 * City Travel — lógica y dibujo del juego.
 *
 * Inspirado en el clásico arcade City Connection (Jaleco, 1985): un autito que
 * recorre autopistas elevadas pintándolas mientras esquiva patrulleros, pero en
 * versión moderna con un auto eléctrico que dispara pulsos de energía.
 */
final class Game {

    // ------------------------------------------------------------------
    // Constantes del mundo (coordenadas virtuales: alto fijo de 480)
    // ------------------------------------------------------------------
    static final float H = 480f;
    static final float GROUND_Y = 440f;
    static final float[] LEVEL_Y = {440f, 345f, 250f, 155f};
    static final float WL = 2880f;          // largo del circuito (da la vuelta)
    static final float CELL = 32f;
    static final float DECK = 12f;
    static final float GRAV = 1900f;
    static final float JUMP_V = 700f;
    static final float MAX_SPEED = 290f;
    static final float CRUISE = 110f;

    static final int ST_TITLE = 0;
    static final int ST_INTRO = 1;
    static final int ST_PLAY = 2;
    static final int ST_DYING = 3;
    static final int ST_CLEAR = 4;
    static final int ST_GAMEOVER = 5;

    static final int P_NORMAL = 0;
    static final int P_STUNNED = 1;
    static final int P_WAIT = 2;

    // ------------------------------------------------------------------
    // Clases internas
    // ------------------------------------------------------------------
    static final class Seg {
        final int level;
        final float x0;
        final float len;
        final boolean[] painted;
        final float[] paintFlash;

        Seg(int level, float x0, float len) {
            this.level = level;
            this.x0 = mod(x0, WL);
            this.len = len;
            int n = Math.max(1, Math.round(len / CELL));
            painted = new boolean[n];
            paintFlash = new float[n];
        }
    }

    static final class Car {
        float x, y, vx, vy;
        int dir = 1;
        boolean grounded;
        int level;
        float wheelA;
        float spin;
        // patrullero
        int state = P_NORMAL;
        float timer;
        float decisionT;
        boolean edgeChecked;
        float speed;
        float coyote;
        float jumpBuffer;
    }

    static final class Shot {
        float x, y;
        int dir;
        float life;
    }

    static final class Item {
        float x, y;
        float phase;
        float life;
    }

    static final class Particle {
        float x, y, vx, vy, life, max, size;
        int color;
        boolean gravity;
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------
    final SynthCore snd;
    private final SharedPreferences prefs;
    private final Random rnd = new Random();

    float W = 854f;
    float bitmapScale = 1.5f;
    int state = ST_TITLE;
    float stateT;
    float time;
    boolean paused;
    boolean muted;

    // entradas (las escribe el hilo de UI)
    volatile boolean inLeft, inRight, inJump, inFire;
    private volatile boolean keyLeft, keyRight, keyJump, keyFire;
    private boolean prevJump, prevFire;
    private final float[] tapQueue = new float[16];
    private int tapN;

    int stageNum;
    City city;
    Backdrop backdrop;
    private Backdrop titleBackdrop;
    private int titleCity;
    private float titleCam;

    final ArrayList<Seg> segs = new ArrayList<>();
    int totalCells, paintedCells;

    final Car player = new Car();
    final ArrayList<Car> police = new ArrayList<>();
    final ArrayList<Shot> shots = new ArrayList<>();
    final ArrayList<Item> batteries = new ArrayList<>();
    final ArrayList<Item> balloons = new ArrayList<>();
    final ArrayList<Particle> particles = new ArrayList<>();
    Item cat;
    float catTimer, batteryTimer, balloonTimer;

    int score, hiScore, lives, ammo, balloonCount;
    int nextExtra = 30000;
    int stompCombo;
    float invuln;
    float camX, camLook;
    float paintSoundT;
    int clearBonus;
    float shake;
    String toast;
    float toastT;

    // ------------------------------------------------------------------
    // Pinceles
    // ------------------------------------------------------------------
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path carBody = new Path();
    private final Path carGlass = new Path();
    private final Path policeBody = new Path();
    private final Path bolt = new Path();
    private final Path tmp = new Path();
    private final RectF rf = new RectF();
    private final Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    private final Typeface sans = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC);

    Game(SynthCore snd, SharedPreferences prefs) {
        this.snd = snd;
        this.prefs = prefs;
        hiScore = prefs.getInt("hi", 10000);
        muted = prefs.getBoolean("mute", false);
        applyVolume();

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(mono);
        logo.setTypeface(sans);
        logoStroke.setTypeface(sans);
        logoStroke.setStyle(Paint.Style.STROKE);
        logoStroke.setStrokeJoin(Paint.Join.ROUND);

        // auto eléctrico (mirando a la derecha, origen abajo al centro)
        carBody.moveTo(-29, -6);
        carBody.lineTo(-29, -14);
        carBody.quadTo(-28, -18, -22, -19);
        carBody.lineTo(-12, -27);
        carBody.quadTo(-8, -30, -1, -30);
        carBody.lineTo(8, -30);
        carBody.quadTo(14, -29, 19, -22);
        carBody.lineTo(27, -19);
        carBody.quadTo(31, -17, 31, -12);
        carBody.lineTo(31, -6);
        carBody.close();

        carGlass.moveTo(-10, -21);
        carGlass.lineTo(-5, -27);
        carGlass.lineTo(7, -27);
        carGlass.lineTo(14, -21);
        carGlass.close();

        policeBody.moveTo(-30, -6);
        policeBody.lineTo(-30, -17);
        policeBody.lineTo(-16, -19);
        policeBody.lineTo(-10, -29);
        policeBody.lineTo(10, -29);
        policeBody.lineTo(17, -19);
        policeBody.lineTo(29, -17);
        policeBody.quadTo(31, -15, 31, -11);
        policeBody.lineTo(31, -6);
        policeBody.close();

        bolt.moveTo(2, -10);
        bolt.lineTo(-5, 1);
        bolt.lineTo(0, 1);
        bolt.lineTo(-3, 10);
        bolt.lineTo(5, -2);
        bolt.lineTo(0, -2);
        bolt.close();

        enterTitle();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------
    static float mod(float a, float m) {
        float r = a % m;
        return r < 0 ? r + m : r;
    }

    /** Diferencia mínima en el circuito circular, en [-WL/2, WL/2). */
    static float wd(float d) {
        return mod(d + WL / 2f, WL) - WL / 2f;
    }

    private void applyVolume() {
        snd.setMusicVolume(muted || paused ? 0f : 1f);
        snd.setSfxVolume(muted ? 0f : 1f);
    }

    private void sfx(int id) {
        if (!muted) snd.sfx(id);
    }

    private static String pad(int v, int n) {
        String s = Integer.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < n; i++) b.append('0');
        return b.append(s).toString();
    }

    private static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------
    // Entradas
    // ------------------------------------------------------------------
    synchronized void tap(float x, float y) {
        if (tapN < tapQueue.length - 1) {
            tapQueue[tapN++] = x;
            tapQueue[tapN++] = y;
        }
    }

    void key(int which, boolean down) {
        switch (which) {
            case 0: keyLeft = down; break;
            case 1: keyRight = down; break;
            case 2: keyJump = down; break;
            case 3: keyFire = down; break;
            default: break;
        }
    }

    /** Botón atrás. Devuelve false si la app debería cerrarse. */
    synchronized boolean onBack() {
        if (state == ST_TITLE) return false;
        if (state == ST_PLAY || state == ST_INTRO || state == ST_DYING || state == ST_CLEAR) {
            if (paused) {
                saveHi();
                enterTitle();
            } else {
                setPaused(true);
            }
            return true;
        }
        enterTitle();
        return true;
    }

    void setPaused(boolean p) {
        if (state == ST_TITLE || state == ST_GAMEOVER) p = false;
        paused = p;
        applyVolume();
    }

    void onAppPause() {
        if (state != ST_TITLE && state != ST_GAMEOVER) setPaused(true);
        saveHi();
    }

    private void saveHi() {
        if (score > hiScore) hiScore = score;
        prefs.edit().putInt("hi", hiScore).putBoolean("mute", muted).apply();
    }

    // botones en pantalla (coordenadas virtuales)
    float btnLeftX() { return 74; }
    float btnRightX() { return 188; }
    float btnY() { return H - 66; }
    float btnJumpX() { return W - 84; }
    float btnJumpY() { return H - 78; }
    float btnFireX() { return W - 212; }
    float btnFireY() { return H - 58; }

    // ------------------------------------------------------------------
    // Transiciones
    // ------------------------------------------------------------------
    private void enterTitle() {
        state = ST_TITLE;
        stateT = 0;
        paused = false;
        applyVolume();
        snd.play(Music.TITLE);
        if (titleBackdrop == null) {
            titleBackdrop = new Backdrop(City.ALL[titleCity], H, bitmapScale);
        }
    }

    private void newGame() {
        score = 0;
        lives = 3;
        ammo = 3;
        balloonCount = 0;
        nextExtra = 30000;
        stageNum = 0;
        startStage();
    }

    private void startStage() {
        City c = City.ALL[stageNum % City.ALL.length];
        if (city != c || backdrop == null) {
            if (backdrop != null) backdrop.recycle();
            city = c;
            backdrop = new Backdrop(c, H, bitmapScale);
        }
        buildStage();
        resetActors();
        ammo = Math.max(ammo, 3);
        state = ST_INTRO;
        stateT = 0;
        paused = false;
        applyVolume();
        snd.play(Music.START);
    }

    private void buildStage() {
        Random r = new Random(city.seed * 31L + stageNum * 7919L);
        segs.clear();
        segs.add(new Seg(0, 0, WL));

        // nivel 1
        ArrayList<Seg> l1 = new ArrayList<>();
        float start = r.nextInt(6) * CELL;
        float x = start;
        while (true) {
            float len = (7 + r.nextInt(11)) * CELL;
            float gap = (4 + r.nextInt(6)) * CELL;
            if (x + len + 4 * CELL > start + WL) break;
            Seg s = new Seg(1, x, len);
            l1.add(s);
            x += len + gap;
        }
        segs.addAll(l1);

        ArrayList<Seg> l2 = stackLevel(r, l1, 2, 0.85f, 6, 8);
        segs.addAll(l2);
        ArrayList<Seg> l3 = stackLevel(r, l2, 3, 0.7f, 5, 6);
        segs.addAll(l3);

        totalCells = 0;
        for (Seg s : segs) totalCells += s.painted.length;
        paintedCells = 0;
    }

    private ArrayList<Seg> stackLevel(Random r, ArrayList<Seg> below, int level, float prob, int minC, int varC) {
        ArrayList<Seg> out = new ArrayList<>();
        for (Seg b : below) {
            if (r.nextFloat() > prob) continue;
            float len = (minC + r.nextInt(varC)) * CELL;
            float x0 = b.x0 + (r.nextInt(9) - 3) * CELL;
            float maxX0 = b.x0 + b.len - 4 * CELL;
            float minX0 = b.x0 + 4 * CELL - len;
            if (x0 > maxX0) x0 = maxX0;
            if (x0 < minX0) x0 = minX0;
            boolean ok = true;
            for (Seg o : out) {
                if (overlaps(x0, len, o.x0, o.len, 3 * CELL)) { ok = false; break; }
            }
            if (ok) out.add(new Seg(level, x0, len));
        }
        // revisar el cierre del circuito
        if (out.size() > 1) {
            Seg a = out.get(out.size() - 1);
            Seg b = out.get(0);
            if (overlaps(a.x0, a.len, b.x0, b.len, 3 * CELL)) out.remove(out.size() - 1);
        }
        return out;
    }

    private static boolean overlaps(float a0, float alen, float b0, float blen, float margin) {
        float d1 = mod(b0 - a0, WL);
        float d2 = mod(a0 - b0, WL);
        return d1 < alen + margin || d2 < blen + margin;
    }

    private void resetActors() {
        player.x = 120;
        player.y = LEVEL_Y[0];
        player.vx = 0;
        player.vy = 0;
        player.dir = 1;
        player.grounded = true;
        player.level = 0;
        player.spin = 0;
        invuln = 0;
        camLook = W * 0.12f;

        police.clear();
        int n = Math.min(5, 2 + stageNum / 2);
        for (int i = 0; i < n; i++) {
            Car p = new Car();
            p.x = mod(player.x + WL * 0.35f + i * (WL * 0.5f / n), WL);
            p.y = LEVEL_Y[0];
            p.grounded = true;
            p.level = 0;
            p.dir = (i % 2 == 0) ? -1 : 1;
            p.speed = Math.min(235f, 125f + stageNum * 12f + rnd.nextFloat() * 25f);
            p.decisionT = 1f + rnd.nextFloat();
            police.add(p);
        }
        shots.clear();
        batteries.clear();
        balloons.clear();
        particles.clear();
        cat = null;
        catTimer = 16f + rnd.nextFloat() * 8f;
        batteryTimer = 5f;
        balloonTimer = 9f;
        stompCombo = 0;
    }

    // ------------------------------------------------------------------
    // Física
    // ------------------------------------------------------------------
    Seg segAt(int level, float x) {
        for (int i = 0, n = segs.size(); i < n; i++) {
            Seg s = segs.get(i);
            if (s.level != level) continue;
            if (mod(x - s.x0, WL) < s.len) return s;
        }
        return null;
    }

    private void moveCar(Car c, float target, float accel, float dt) {
        if (c.vx < target) c.vx = Math.min(target, c.vx + accel * dt);
        else if (c.vx > target) c.vx = Math.max(target, c.vx - accel * dt);
        c.x = mod(c.x + c.vx * dt, WL);
        c.wheelA += c.vx * dt / 7f;

        if (c.grounded) {
            if (segAt(c.level, c.x) == null) {
                c.grounded = false;
                c.vy = 60f;      // empujoncito hacia abajo para que caiga sin dudar
                c.y += 1f;
                c.coyote = 0.09f;
            }
        }
        if (!c.grounded) {
            c.coyote -= dt;
            float prevY = c.y;
            c.vy += GRAV * dt;
            if (c.vy > 1100) c.vy = 1100;
            c.y += c.vy * dt;
            if (c.vy > 0) {
                for (int L = 3; L >= 0; L--) {
                    float ly = LEVEL_Y[L];
                    // prevY < ly (estricto): si el auto acaba de salir del borde de este
                    // mismo nivel, no lo vuelve a apoyar y cae limpio al de abajo
                    if (prevY < ly - 0.5f && c.y >= ly && segAt(L, c.x) != null) {
                        c.y = ly;
                        c.vy = 0;
                        c.grounded = true;
                        c.level = L;
                        break;
                    }
                }
            }
        }
    }

    private void paintAt(float x) {
        if (!player.grounded) return;
        Seg s = segAt(player.level, x);
        if (s == null) return;
        int i = (int) (mod(x - s.x0, WL) / CELL);
        if (i < 0 || i >= s.painted.length || s.painted[i]) return;
        s.painted[i] = true;
        s.paintFlash[i] = 1f;
        paintedCells++;
        addScore(10);
        if (paintSoundT <= 0) {
            sfx(SynthCore.SFX_PAINT);
            paintSoundT = 0.06f;
        }
        float cx = s.x0 + i * CELL + CELL / 2;
        for (int k = 0; k < 3; k++) {
            spawn(cx, LEVEL_Y[s.level], (rnd.nextFloat() - 0.5f) * 120, -60 - rnd.nextFloat() * 120,
                    0.45f, 2.5f, k == 0 ? city.accent2 : city.accent, true);
        }
    }

    private void addScore(int pts) {
        score += pts;
        if (score >= nextExtra) {
            nextExtra += 30000;
            lives++;
            sfx(SynthCore.SFX_ONEUP);
            showToast("¡VIDA EXTRA!");
        }
    }

    private void showToast(String s) {
        toast = s;
        toastT = 1.6f;
    }

    private void spawn(float x, float y, float vx, float vy, float life, float size, int color, boolean gravity) {
        if (particles.size() > 350) return;
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.life = life;
        p.max = life;
        p.size = size;
        p.color = color;
        p.gravity = gravity;
        particles.add(p);
    }

    private void burst(float x, float y, int n, int c1, int c2, float speed) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = speed * (0.3f + rnd.nextFloat());
            spawn(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s - speed * 0.3f,
                    0.5f + rnd.nextFloat() * 0.6f, 2f + rnd.nextFloat() * 2.5f, i % 2 == 0 ? c1 : c2, true);
        }
    }

    // ------------------------------------------------------------------
    // Actualización
    // ------------------------------------------------------------------
    synchronized void update(float dt) {
        // procesar toques
        for (int i = 0; i + 1 < tapN; i += 2) handleTap(tapQueue[i], tapQueue[i + 1]);
        tapN = 0;

        if (paused) return;
        time += dt;
        stateT += dt;
        if (toastT > 0) toastT -= dt;
        if (shake > 0) shake = Math.max(0, shake - dt * 2.5f);

        boolean left = inLeft || keyLeft;
        boolean right = inRight || keyRight;
        boolean jump = inJump || keyJump;
        boolean fire = inFire || keyFire;
        boolean jumpPressed = jump && !prevJump;
        boolean firePressed = fire && !prevFire;
        prevJump = jump;
        prevFire = fire;

        switch (state) {
            case ST_TITLE:
                titleCam += dt * 70f;
                if (stateT > 9f) {
                    stateT = 0;
                    titleCity = (titleCity + 1) % City.ALL.length;
                    if (titleBackdrop != null) titleBackdrop.recycle();
                    titleBackdrop = new Backdrop(City.ALL[titleCity], H, bitmapScale);
                }
                if (jumpPressed || firePressed) {
                    sfx(SynthCore.SFX_SELECT);
                    newGame();
                }
                break;
            case ST_INTRO:
                updateCamera(dt);
                updateParticles(dt);
                if (stateT > 2.8f) {
                    state = ST_PLAY;
                    stateT = 0;
                    snd.play(city.song);
                }
                break;
            case ST_PLAY:
                updatePlay(dt, left, right, jumpPressed, firePressed);
                break;
            case ST_DYING:
                player.spin += dt * 720f * Math.max(0.1f, 1f - stateT / 1.5f);
                updatePolice(dt);
                updateParticles(dt);
                if (stateT > 2.4f) {
                    lives--;
                    if (lives <= 0) {
                        state = ST_GAMEOVER;
                        stateT = 0;
                        saveHi();
                        snd.play(Music.GAMEOVER);
                    } else {
                        respawn();
                    }
                }
                break;
            case ST_CLEAR:
                player.vx = Math.min(MAX_SPEED, player.vx + 200 * dt);
                player.dir = 1;
                moveCar(player, MAX_SPEED * 0.8f, 300f, dt);
                updateCamera(dt);
                updatePolice(dt);
                updateParticles(dt);
                if (rnd.nextFloat() < dt * 5) {
                    float fx = camX + rnd.nextFloat() * W;
                    float fy = 80 + rnd.nextFloat() * 180;
                    burst(fx, fy, 26, city.accent, city.accent2, 260);
                    if (rnd.nextFloat() < 0.4f) sfx(SynthCore.SFX_TICK);
                }
                if (stateT > 5.5f) {
                    stageNum++;
                    startStage();
                }
                break;
            case ST_GAMEOVER:
                updateParticles(dt);
                if (stateT > 6f || (stateT > 1.5f && (jumpPressed || firePressed))) enterTitle();
                break;
            default:
                break;
        }
    }

    private void handleTap(float x, float y) {
        // botón de sonido/pausa arriba a la derecha
        boolean corner = x > W - 60 && y < 46;
        if (state == ST_TITLE) {
            if (corner) {
                muted = !muted;
                applyVolume();
                saveHi();
                if (!muted) sfx(SynthCore.SFX_SELECT);
                return;
            }
            sfx(SynthCore.SFX_SELECT);
            newGame();
            return;
        }
        if (paused) {
            if (x > W / 2 - 140 && x < W / 2 + 140 && y > H / 2 + 46 && y < H / 2 + 84) {
                saveHi();
                enterTitle();
                return;
            }
            setPaused(false);
            return;
        }
        if (corner && (state == ST_PLAY || state == ST_INTRO || state == ST_DYING || state == ST_CLEAR)) {
            setPaused(true);
            return;
        }
        if (state == ST_GAMEOVER && stateT > 1.5f) enterTitle();
    }

    private void updateCamera(float dt) {
        float target = player.dir * W * 0.14f;
        camLook += (target - camLook) * Math.min(1f, dt * 1.6f);
        camX = mod(player.x - W * 0.5f + camLook, WL);
    }

    private void updatePlay(float dt, boolean left, boolean right, boolean jumpPressed, boolean firePressed) {
        Car p = player;
        // control horizontal
        int want = right && !left ? 1 : (left && !right ? -1 : 0);
        float target, accel;
        if (want != 0) {
            target = want * MAX_SPEED;
            boolean braking = Math.signum(p.vx) != want && Math.abs(p.vx) > 10;
            accel = braking ? 1100f : 520f;
        } else {
            target = p.dir * CRUISE;
            accel = 300f;
        }
        if (!p.grounded) accel *= 0.55f;
        if (Math.abs(p.vx) > 6) p.dir = p.vx > 0 ? 1 : -1;
        else if (want != 0) p.dir = want;

        // salto con margen ("coyote time") y buffer
        if (jumpPressed) p.jumpBuffer = 0.12f;
        else p.jumpBuffer -= dt;
        if (p.jumpBuffer > 0 && (p.grounded || p.coyote > 0)) {
            p.grounded = false;
            p.coyote = 0;
            p.jumpBuffer = 0;
            p.vy = -JUMP_V;
            sfx(SynthCore.SFX_JUMP);
            for (int k = 0; k < 6; k++)
                spawn(p.x - p.dir * 20, p.y, -p.dir * 40 + (rnd.nextFloat() - 0.5f) * 60, -rnd.nextFloat() * 60,
                        0.35f, 2.5f, 0xFF7FF6FF, false);
        }

        boolean wasGrounded = p.grounded;
        moveCar(p, target, accel, dt);
        if (!wasGrounded && p.grounded) {
            stompCombo = 0;
            for (int k = 0; k < 8; k++)
                spawn(p.x + (rnd.nextFloat() - 0.5f) * 40, p.y, (rnd.nextFloat() - 0.5f) * 160, -rnd.nextFloat() * 80,
                        0.3f, 2f, 0xFFFFFFFF, true);
        }

        // pintar
        if (paintSoundT > 0) paintSoundT -= dt;
        paintAt(p.x - 18);
        paintAt(p.x);
        paintAt(p.x + 18);

        // estela eléctrica
        if (p.grounded && Math.abs(p.vx) > 60 && rnd.nextFloat() < 0.6f) {
            spawn(p.x - p.dir * 28, p.y - 6 - rnd.nextFloat() * 6, -p.vx * 0.2f, (rnd.nextFloat() - 0.5f) * 30,
                    0.3f, 2f, rnd.nextBoolean() ? 0xFF00E5FF : city.accent, false);
        }

        // pulso eléctrico
        if (firePressed) {
            if (ammo > 0) {
                ammo--;
                Shot s = new Shot();
                s.x = mod(p.x + p.dir * 30, WL);
                s.y = p.y - 14;
                s.dir = p.dir;
                s.life = 1.1f;
                shots.add(s);
                sfx(SynthCore.SFX_PULSE);
            } else {
                sfx(SynthCore.SFX_TICK);
                showToast("SIN CARGA — juntá baterías");
            }
        }

        if (invuln > 0) invuln -= dt;

        updateCamera(dt);
        updateShots(dt);
        updatePolice(dt);
        updateItems(dt);
        updateParticles(dt);
        for (Seg s : segs) {
            for (int i = 0; i < s.paintFlash.length; i++) if (s.paintFlash[i] > 0) s.paintFlash[i] -= dt * 2.5f;
        }

        // colisiones con patrulleros
        if (state == ST_PLAY) {
            for (Car c : police) {
                if (c.state != P_NORMAL) continue;
                float dx = wd(c.x - p.x);
                if (Math.abs(dx) < 42 && Math.abs(c.y - p.y) < 24) {
                    if (p.vy > 60 && p.y <= c.y - 8) {
                        stun(c, p.dir);
                        p.vy = -520;
                        p.grounded = false;
                        stompCombo++;
                        int pts = 800 * stompCombo;
                        addScore(pts);
                        sfx(SynthCore.SFX_STOMP);
                        showToast("¡PISOTÓN! +" + pts);
                    } else if (invuln <= 0) {
                        crash();
                        break;
                    }
                }
            }
        }
        if (state == ST_PLAY && cat != null && cat.life > 0.6f && invuln <= 0) {
            float dx = wd(cat.x - p.x);
            if (Math.abs(dx) < 28 && Math.abs(cat.y - p.y) < 18) {
                sfx(SynthCore.SFX_MEOW);
                crash();
            }
        }

        if (state == ST_PLAY && paintedCells >= totalCells) {
            state = ST_CLEAR;
            stateT = 0;
            clearBonus = 3000 + stageNum * 1000 + lives * 1000;
            addScore(clearBonus);
            snd.play(Music.CLEAR);
            for (Car c : police) if (c.state == P_NORMAL) stun(c, rnd.nextBoolean() ? 1 : -1);
            cat = null;
        }
    }

    private void crash() {
        state = ST_DYING;
        stateT = 0;
        shake = 1f;
        sfx(SynthCore.SFX_CRASH);
        snd.play(Music.MISS);
        burst(player.x, player.y - 14, 40, 0xFFFFD040, 0xFFFF4060, 320);
        burst(player.x, player.y - 14, 20, 0xFF00E5FF, 0xFFFFFFFF, 200);
    }

    private void respawn() {
        state = ST_PLAY;
        stateT = 0;
        Car p = player;
        p.y = LEVEL_Y[0];
        p.vy = 0;
        p.vx = 0;
        p.grounded = true;
        p.level = 0;
        p.spin = 0;
        invuln = 2.6f;
        for (Car c : police) {
            if (c.state == P_NORMAL && Math.abs(wd(c.x - p.x)) < 420) {
                c.state = P_WAIT;
                c.timer = 1.5f + rnd.nextFloat();
            }
        }
        if (cat != null && Math.abs(wd(cat.x - p.x)) < 300) cat = null;
        snd.play(city.song);
    }

    private void stun(Car c, int dir) {
        c.state = P_STUNNED;
        c.vy = -560;
        c.vx = dir * (160 + rnd.nextFloat() * 120);
        c.grounded = false;
        burst(c.x, c.y - 14, 18, 0xFF4DA6FF, 0xFFFF4D6A, 220);
    }

    private void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            s.x = mod(s.x + s.dir * 560 * dt, WL);
            s.life -= dt;
            if (rnd.nextFloat() < 0.7f) spawn(s.x, s.y + (rnd.nextFloat() - 0.5f) * 10, -s.dir * 60, (rnd.nextFloat() - 0.5f) * 60,
                    0.25f, 2f, 0xFF9AF7FF, false);
            boolean hit = false;
            for (Car c : police) {
                if (c.state != P_NORMAL) continue;
                if (Math.abs(wd(c.x - s.x)) < 30 && Math.abs((c.y - 13) - s.y) < 20) {
                    stun(c, s.dir);
                    addScore(500);
                    sfx(SynthCore.SFX_HIT);
                    showToast("¡ZAP! +500");
                    hit = true;
                    break;
                }
            }
            if (hit || s.life <= 0) shots.remove(i);
        }
    }

    private void updatePolice(float dt) {
        for (Car c : police) {
            switch (c.state) {
                case P_NORMAL: {
                    float dx = wd(player.x - c.x);
                    c.decisionT -= dt;
                    if (c.decisionT <= 0) {
                        c.decisionT = 0.6f + rnd.nextFloat() * 1.1f;
                        boolean chase = rnd.nextFloat() < 0.72f;
                        c.dir = chase ? (dx >= 0 ? 1 : -1) : (rnd.nextBoolean() ? 1 : -1);
                        if (c.grounded && c.level < 3 && player.y < c.y - 40 && Math.abs(dx) < 280
                                && rnd.nextFloat() < 0.55f && segAt(c.level + 1, c.x + c.dir * 60) != null) {
                            c.vy = -JUMP_V;
                            c.grounded = false;
                        }
                    }
                    if (c.grounded && c.level > 0) {
                        if (segAt(c.level, c.x + c.dir * 26) == null) {
                            if (!c.edgeChecked) {
                                c.edgeChecked = true;
                                boolean playerBelow = player.y > c.y + 20;
                                if (!playerBelow && rnd.nextFloat() < 0.6f) c.dir = -c.dir;
                            }
                        } else {
                            c.edgeChecked = false;
                        }
                    }
                    float spd = c.speed;
                    if (state != ST_PLAY) spd *= 0.5f;
                    moveCar(c, c.dir * spd, 420f, dt);
                    break;
                }
                case P_STUNNED:
                    c.spin += dt * 900f;
                    c.vy += GRAV * dt;
                    c.x = mod(c.x + c.vx * dt, WL);
                    c.y += c.vy * dt;
                    if (c.y > H + 120) {
                        c.state = P_WAIT;
                        c.timer = 3.5f + rnd.nextFloat() * 2f;
                    }
                    break;
                case P_WAIT:
                default:
                    c.timer -= dt;
                    if (c.timer <= 0 && state == ST_PLAY) {
                        c.state = P_NORMAL;
                        c.spin = 0;
                        float side = rnd.nextBoolean() ? 1 : -1;
                        c.x = mod(player.x + side * (W * 0.6f + 150 + rnd.nextFloat() * 300), WL);
                        c.level = rnd.nextFloat() < 0.6f ? 0 : 1;
                        if (segAt(c.level, c.x) == null) c.level = 0;
                        c.y = LEVEL_Y[c.level];
                        c.vx = 0;
                        c.vy = 0;
                        c.grounded = true;
                        c.dir = side > 0 ? -1 : 1;
                        c.decisionT = 0.8f;
                        c.speed = Math.min(250f, c.speed + 4f);
                    }
                    break;
            }
        }
    }

    private Seg randomSeg(float avoidX, float minDist) {
        for (int tries = 0; tries < 20; tries++) {
            Seg s = segs.get(rnd.nextInt(segs.size()));
            float x = s.x0 + CELL + rnd.nextFloat() * Math.max(1, s.len - 2 * CELL);
            if (Math.abs(wd(x - avoidX)) >= minDist) {
                tmpX = mod(x, WL);
                return s;
            }
        }
        return null;
    }

    private float tmpX;

    private void updateItems(float dt) {
        // baterías
        batteryTimer -= dt;
        if (batteryTimer <= 0) {
            batteryTimer = 6f + rnd.nextFloat() * 4f;
            if (batteries.size() < 3) {
                Seg s = randomSeg(player.x, 200);
                if (s != null) {
                    Item it = new Item();
                    it.x = tmpX;
                    it.y = LEVEL_Y[s.level] - 30;
                    it.phase = rnd.nextFloat() * 6f;
                    it.life = 18f;
                    batteries.add(it);
                }
            }
        }
        for (int i = batteries.size() - 1; i >= 0; i--) {
            Item it = batteries.get(i);
            it.life -= dt;
            it.phase += dt;
            float dx = wd(it.x - player.x);
            if (state == ST_PLAY && Math.abs(dx) < 30 && Math.abs(it.y - (player.y - 16)) < 30) {
                ammo = Math.min(9, ammo + 1);
                addScore(100);
                sfx(SynthCore.SFX_PICKUP);
                burst(it.x, it.y, 12, 0xFF6BFF8A, 0xFFFFFFFF, 150);
                batteries.remove(i);
            } else if (it.life <= 0) {
                batteries.remove(i);
            }
        }

        // globos
        balloonTimer -= dt;
        if (balloonTimer <= 0) {
            balloonTimer = 7f + rnd.nextFloat() * 6f;
            Item b = new Item();
            b.x = mod(camX + 80 + rnd.nextFloat() * (W - 160), WL);
            b.y = H + 30;
            b.phase = rnd.nextInt(5);
            b.life = 0;
            balloons.add(b);
        }
        for (int i = balloons.size() - 1; i >= 0; i--) {
            Item b = balloons.get(i);
            b.life += dt;
            b.y -= 42 * dt;
            b.x = mod(b.x + (float) Math.sin(b.life * 1.7f) * 20 * dt, WL);
            float dx = wd(b.x - player.x);
            if (state == ST_PLAY && Math.abs(dx) < 28 && Math.abs(b.y - (player.y - 16)) < 34) {
                balloonCount++;
                addScore(300);
                sfx(SynthCore.SFX_BALLOON);
                burst(b.x, b.y, 14, balloonColor((int) b.phase), 0xFFFFFFFF, 160);
                balloons.remove(i);
                if (balloonCount % 10 == 0) {
                    lives++;
                    sfx(SynthCore.SFX_ONEUP);
                    showToast("¡10 GLOBOS! VIDA EXTRA");
                } else {
                    showToast("GLOBO " + (balloonCount % 10) + "/10");
                }
            } else if (b.y < -40) {
                balloons.remove(i);
            }
        }

        // el gato que se cruza en el camino
        if (cat == null) {
            catTimer -= dt;
            if (catTimer <= 0) {
                catTimer = 15f + rnd.nextFloat() * 10f;
                Seg s = randomSeg(player.x, 380);
                if (s != null) {
                    cat = new Item();
                    cat.x = tmpX;
                    cat.y = LEVEL_Y[s.level];
                    cat.life = 0;
                    cat.phase = 7f;
                    sfx(SynthCore.SFX_MEOW);
                }
            }
        } else {
            cat.life += dt;
            if (cat.life > cat.phase) cat = null;
        }
    }

    private static int balloonColor(int i) {
        switch (i) {
            case 0: return 0xFFFF4D6A;
            case 1: return 0xFFFFD23F;
            case 2: return 0xFF3FC5FF;
            case 3: return 0xFF7CFF6B;
            default: return 0xFFC77DFF;
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            if (p.gravity) p.vy += 700 * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------
    synchronized void draw(Canvas c) {
        if (state == ST_TITLE) {
            drawTitle(c);
            return;
        }
        c.save();
        if (shake > 0) {
            c.translate((rnd.nextFloat() - 0.5f) * 10 * shake, (rnd.nextFloat() - 0.5f) * 10 * shake);
        }
        backdrop.draw(c, camX, W, time);
        drawWorld(c);
        c.restore();
        drawHud(c);
        if (state == ST_PLAY || state == ST_DYING || state == ST_INTRO) drawControls(c);

        switch (state) {
            case ST_INTRO:
                drawIntro(c);
                break;
            case ST_CLEAR:
                drawClear(c);
                break;
            case ST_GAMEOVER:
                drawGameOver(c);
                break;
            default:
                break;
        }
        if (toastT > 0 && toast != null && state == ST_PLAY) {
            float a = Math.min(1f, toastT * 2f);
            centerText(c, toast, W / 2, 96, 22, withAlpha(city.accent2, (int) (255 * a)), true);
        }
        if (paused) drawPause(c);
    }

    private float sx(float x) {
        return wd(x - camX);
    }

    private void drawWorld(Canvas c) {
        // farolas a nivel de calle (parallax 1:1)
        float lampStep = 180f;
        float first = (float) Math.floor(camX / lampStep) * lampStep;
        for (float lx = first; lx < camX + W + lampStep; lx += lampStep) {
            float x = lx - camX;
            stroke.setColor(0xFF2A2440);
            stroke.setStrokeWidth(4);
            c.drawLine(x, GROUND_Y, x, GROUND_Y - 78, stroke);
            c.drawLine(x, GROUND_Y - 78, x + 16, GROUND_Y - 82, stroke);
            fill.setColor(withAlpha(city.windowColor, (int) (60 + 70 * city.night)));
            c.drawCircle(x + 18, GROUND_Y - 80, 12, fill);
            fill.setColor(city.windowColor);
            c.drawCircle(x + 18, GROUND_Y - 80, 4, fill);
        }

        int pillar = Backdrop.shade(city.midColor, 0, 0.6f, 0.45f);
        int pillarHi = Backdrop.shade(city.midColor, 0, 0.6f, 0.65f);
        // pilares
        for (Seg s : segs) {
            if (s.level == 0) continue;
            for (int k = -1; k <= 1; k++) {
                float base = sx(s.x0) + k * WL;
                if (base > W + 20 || base + s.len < -20) continue;
                float ly = LEVEL_Y[s.level] + DECK;
                for (float px = 30; px < s.len - 10; px += 150) {
                    float x = base + px;
                    if (x < -20 || x > W + 20) continue;
                    fill.setColor(pillar);
                    c.drawRect(x - 7, ly, x + 7, GROUND_Y, fill);
                    fill.setColor(pillarHi);
                    c.drawRect(x - 7, ly, x - 4, GROUND_Y, fill);
                }
            }
        }

        // barandas y tableros
        for (Seg s : segs) {
            for (int k = -1; k <= 1; k++) {
                float base = sx(s.x0) + k * WL;
                if (base > W + 20 || base + s.len < -20) continue;
                drawDeck(c, s, base);
            }
        }

        // tierra bajo la calle
        fill.setColor(0xFF15101F);
        c.drawRect(0, GROUND_Y + DECK, W, H, fill);
        fill.setColor(Backdrop.shade(city.midColor, 0, 0.4f, 0.55f));
        c.drawRect(0, GROUND_Y + DECK, W, GROUND_Y + DECK + 4, fill);

        // baterías
        for (Item it : batteries) {
            float x = sx(it.x);
            if (x < -40 || x > W + 40) continue;
            float y = it.y + (float) Math.sin(it.phase * 3) * 4;
            boolean blink = it.life < 4 && ((int) (it.life * 8) % 2 == 0);
            if (blink) continue;
            fill.setColor(0x406BFF8A);
            c.drawCircle(x, y, 18 + (float) Math.sin(it.phase * 6) * 2, fill);
            fill.setColor(0xFF1E2A22);
            rf.set(x - 8, y - 12, x + 8, y + 12);
            c.drawRoundRect(rf, 3, 3, fill);
            fill.setColor(0xFF6BFF8A);
            rf.set(x - 6, y - 4, x + 6, y + 10);
            c.drawRoundRect(rf, 2, 2, fill);
            fill.setColor(0xFFDDDDDD);
            c.drawRect(x - 3, y - 15, x + 3, y - 12, fill);
            c.save();
            c.translate(x, y - 2);
            c.scale(0.8f, 0.8f);
            fill.setColor(0xFFFFFFFF);
            c.drawPath(bolt, fill);
            c.restore();
        }

        // gato
        if (cat != null) drawCat(c);

        // patrulleros
        for (Car p : police) {
            if (p.state == P_WAIT) continue;
            float x = sx(p.x);
            if (x < -60 || x > W + 60) continue;
            drawPolice(c, x, p.y, p.dir, p.wheelA, p.spin);
        }

        // jugador
        boolean visible = invuln <= 0 || ((int) (time * 14) % 2 == 0);
        if (state == ST_DYING) visible = stateT < 1.6f || ((int) (time * 10) % 2 == 0);
        if (visible) drawCar(c, sx(player.x), player.y, player.dir, player.wheelA, player.spin);

        // disparos
        for (Shot s : shots) {
            float x = sx(s.x);
            fill.setColor(0x5539E6FF);
            c.drawCircle(x, s.y, 13 + rnd.nextFloat() * 3, fill);
            fill.setColor(0xFFBFFAFF);
            c.drawCircle(x, s.y, 6, fill);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(1.6f);
            tmp.reset();
            tmp.moveTo(x - s.dir * 4, s.y);
            for (int i = 1; i <= 4; i++) {
                tmp.lineTo(x - s.dir * (4 + i * 7), s.y + (rnd.nextFloat() - 0.5f) * 14);
            }
            c.drawPath(tmp, stroke);
        }

        // globos
        for (Item b : balloons) {
            float x = sx(b.x);
            if (x < -40 || x > W + 40) continue;
            int col = balloonColor((int) b.phase);
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(1.2f);
            c.drawLine(x, b.y + 16, x + (float) Math.sin(b.life * 3) * 4, b.y + 40, stroke);
            fill.setColor(col);
            rf.set(x - 13, b.y - 17, x + 13, b.y + 16);
            c.drawOval(rf, fill);
            fill.setColor(0x88FFFFFF);
            rf.set(x - 8, b.y - 12, x - 2, b.y - 3);
            c.drawOval(rf, fill);
        }

        // partículas
        for (Particle p : particles) {
            float x = sx(p.x);
            if (x < -10 || x > W + 10) continue;
            float a = p.life / p.max;
            fill.setColor(withAlpha(p.color, (int) (255 * a)));
            c.drawCircle(x, p.y, p.size * (0.5f + a * 0.5f), fill);
        }
    }

    private void drawDeck(Canvas c, Seg s, float base) {
        float ly = LEVEL_Y[s.level];
        int asphalt = s.level == 0 ? 0xFF2B2838 : 0xFF34304A;
        // baranda trasera
        if (s.level > 0) {
            stroke.setColor(withAlpha(0xFFC8C8E8, 130));
            stroke.setStrokeWidth(2);
            c.drawLine(Math.max(-10, base), ly - 10, Math.min(W + 10, base + s.len), ly - 10, stroke);
            stroke.setStrokeWidth(1.5f);
            float startPost = base + 8;
            if (startPost < -10) startPost += (float) Math.ceil((-10 - startPost) / 16f) * 16f;
            for (float px = startPost; px < base + s.len && px < W + 10; px += 16) {
                c.drawLine(px, ly - 10, px, ly, stroke);
            }
        }
        int n = s.painted.length;
        int i0 = Math.max(0, (int) Math.floor((-base) / CELL) - 1);
        int i1 = Math.min(n - 1, (int) Math.ceil((W - base) / CELL) + 1);
        int acc = city.accent;
        for (int i = i0; i <= i1; i++) {
            float x = base + i * CELL;
            if (s.painted[i]) {
                fill.setColor(acc);
                c.drawRect(x, ly, x + CELL + 0.5f, ly + DECK, fill);
                fill.setColor(0xA0FFFFFF);
                c.drawRect(x, ly, x + CELL + 0.5f, ly + 2.5f, fill);
                float fl = s.paintFlash[i];
                if (fl > 0) {
                    fill.setColor(withAlpha(0xFFFFFFFF, (int) (200 * fl)));
                    c.drawRect(x, ly - 4 * fl, x + CELL, ly + DECK, fill);
                }
            } else {
                fill.setColor(asphalt);
                c.drawRect(x, ly, x + CELL + 0.5f, ly + DECK, fill);
                fill.setColor(0x55FFFFFF);
                c.drawRect(x + 8, ly + 5, x + 22, ly + 7, fill);
            }
        }
        // borde inferior
        fill.setColor(0xFF141022);
        float l = Math.max(-10, base), r = Math.min(W + 10, base + s.len);
        c.drawRect(l, ly + DECK - 2, r, ly + DECK, fill);
        if (s.level > 0) {
            // brillo neón bajo la autopista
            fill.setColor(withAlpha(city.accent, 60));
            c.drawRect(l, ly + DECK, r, ly + DECK + 3, fill);
        }
    }

    private void drawWheel(Canvas c, float x, float y, float a) {
        fill.setColor(0xFF0E0C16);
        c.drawCircle(x, y, 7, fill);
        fill.setColor(0xFF8A90A8);
        c.drawCircle(x, y, 3.8f, fill);
        stroke.setColor(0xFF2C2F40);
        stroke.setStrokeWidth(1.4f);
        for (int k = 0; k < 3; k++) {
            double ang = a + k * 2.094;
            c.drawLine(x, y, x + (float) Math.cos(ang) * 3.6f, y + (float) Math.sin(ang) * 3.6f, stroke);
        }
    }

    private void drawCar(Canvas c, float x, float y, int dir, float wheelA, float spin) {
        c.save();
        c.translate(x, y);
        if (spin != 0) c.rotate(spin, 0, -14);
        if (dir < 0) c.scale(-1, 1);

        // luz de piso (underglow)
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 8);
        fill.setColor(withAlpha(0xFF00E5FF, (int) (70 + 60 * pulse)));
        rf.set(-34, -6, 34, 5);
        c.drawOval(rf, fill);

        // haz de los faros
        if (city.night > 0.3f) {
            fill.setColor(0x30FFF6C0);
            tmp.reset();
            tmp.moveTo(30, -12);
            tmp.lineTo(110, -26);
            tmp.lineTo(110, 2);
            tmp.close();
            c.drawPath(tmp, fill);
        }

        // carrocería
        fill.setColor(0xFFF4F7FF);
        c.drawPath(carBody, fill);
        // franja inferior cian
        fill.setColor(0xFF00C8F0);
        c.drawRect(-29, -11, 31, -6, fill);
        fill.setColor(0xFF7FF6FF);
        c.drawRect(-29, -12, 31, -11, fill);
        // vidrio
        fill.setColor(0xFF1B2B4A);
        c.drawPath(carGlass, fill);
        stroke.setColor(0x8899E8FF);
        stroke.setStrokeWidth(1.5f);
        c.drawLine(-3, -25, 4, -25, stroke);
        // rayo
        c.save();
        c.translate(-14, -14);
        c.scale(0.55f, 0.55f);
        fill.setColor(0xFF00B4E0);
        c.drawPath(bolt, fill);
        c.restore();
        // faro y luz trasera
        fill.setColor(0xFFFFF8C8);
        rf.set(26, -16, 31, -13);
        c.drawRoundRect(rf, 1.5f, 1.5f, fill);
        fill.setColor(0xFFFF2E6A);
        c.drawRect(-29, -16, -26, -13, fill);
        // ruedas
        drawWheel(c, -17, -6, wheelA);
        drawWheel(c, 18, -6, wheelA);
        c.restore();
    }

    private void drawPolice(Canvas c, float x, float y, int dir, float wheelA, float spin) {
        c.save();
        c.translate(x, y);
        if (spin != 0) c.rotate(spin, 0, -14);
        if (dir < 0) c.scale(-1, 1);
        boolean phase = ((int) (time * 7)) % 2 == 0;
        // destello de sirena
        fill.setColor(phase ? 0x40FF2040 : 0x402060FF);
        c.drawCircle(0, -32, 22, fill);
        fill.setColor(0xFF1A1A28);
        c.drawPath(policeBody, fill);
        fill.setColor(0xFFF2F2F2);
        c.drawRect(-30, -17, 30, -11, fill);
        fill.setColor(0xFF9FD0FF);
        tmp.reset();
        tmp.moveTo(-8, -20);
        tmp.lineTo(-6, -26);
        tmp.lineTo(8, -26);
        tmp.lineTo(13, -20);
        tmp.close();
        c.drawPath(tmp, fill);
        // balizas
        fill.setColor(phase ? 0xFFFF2B45 : 0xFF551018);
        c.drawRect(-8, -33, 0, -29, fill);
        fill.setColor(phase ? 0xFF102055 : 0xFF2E7BFF);
        c.drawRect(0, -33, 8, -29, fill);
        fill.setColor(0xFFFFF0A0);
        c.drawRect(27, -16, 31, -13, fill);
        drawWheel(c, -17, -6, wheelA);
        drawWheel(c, 18, -6, wheelA);
        c.restore();
    }

    private void drawCat(Canvas c) {
        float x = sx(cat.x);
        if (x < -40 || x > W + 40) return;
        float y = cat.y;
        float appear = Math.min(1f, cat.life / 0.6f);
        float leave = Math.min(1f, (cat.phase - cat.life) / 0.6f);
        float a = Math.min(appear, leave);
        if (cat.life < 0.6f && ((int) (cat.life * 12) % 2 == 0)) a *= 0.3f;
        int col = withAlpha(0xFF101018, (int) (255 * a));
        fill.setColor(withAlpha(0xFFFFE060, (int) (60 * a)));
        c.drawCircle(x, y - 14, 22, fill);
        fill.setColor(col);
        rf.set(x - 10, y - 20, x + 10, y);
        c.drawOval(rf, fill);
        c.drawCircle(x + 2, y - 24, 8, fill);
        tmp.reset();
        tmp.moveTo(x - 5, y - 29);
        tmp.lineTo(x - 3, y - 37);
        tmp.lineTo(x + 1, y - 31);
        tmp.lineTo(x + 5, y - 37);
        tmp.lineTo(x + 9, y - 29);
        tmp.close();
        c.drawPath(tmp, fill);
        stroke.setColor(col);
        stroke.setStrokeWidth(3);
        float wag = (float) Math.sin(time * 4) * 6;
        tmp.reset();
        tmp.moveTo(x - 9, y - 4);
        tmp.quadTo(x - 22, y - 8, x - 18 + wag, y - 22);
        c.drawPath(tmp, stroke);
        fill.setColor(withAlpha(0xFFFFE040, (int) (255 * a)));
        c.drawCircle(x - 1, y - 25, 1.8f, fill);
        c.drawCircle(x + 5, y - 25, 1.8f, fill);
    }

    // ------------------------------------------------------------------
    // HUD y pantallas
    // ------------------------------------------------------------------
    private void label(Canvas c, String s, float x, float y, float size, int color, Paint.Align al) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(al);
        text.setShadowLayer(0, 0, 0, 0);
        c.drawText(s, x, y, text);
    }

    private void centerText(Canvas c, String s, float x, float y, float size, int color, boolean glow) {
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        if (glow) {
            text.setColor(0xFF000000);
            text.setShadowLayer(0, 0, 0, 0);
            c.drawText(s, x + 2, y + 2, text);
            text.setShadowLayer(10, 0, 0, color);
        } else {
            text.setShadowLayer(0, 0, 0, 0);
        }
        text.setColor(color);
        c.drawText(s, x, y, text);
        text.setShadowLayer(0, 0, 0, 0);
    }

    private void drawHud(Canvas c) {
        fill.setColor(0xAA07040F);
        c.drawRect(0, 0, W, 36, fill);
        fill.setColor(withAlpha(city.accent, 180));
        c.drawRect(0, 36, W, 38, fill);

        label(c, "PUNTOS", 14, 14, 11, city.accent, Paint.Align.LEFT);
        label(c, pad(score, 7), 14, 31, 17, 0xFFFFFFFF, Paint.Align.LEFT);

        label(c, "MÁXIMO", W - 66, 14, 11, city.accent2, Paint.Align.RIGHT);
        label(c, pad(Math.max(hiScore, score), 7), W - 66, 31, 17, 0xFFFFFFFF, Paint.Align.RIGHT);

        // botón pausa
        fill.setColor(0x55FFFFFF);
        c.drawCircle(W - 30, 19, 15, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawRect(W - 36, 12, W - 32, 26, fill);
        c.drawRect(W - 28, 12, W - 24, 26, fill);

        // ciudad y progreso
        float cx = W * 0.5f;
        String name = city.name + (stageNum >= City.ALL.length ? "  · VUELTA " + (stageNum / City.ALL.length + 1) : "");
        label(c, name, cx, 13, 12, 0xFFFFFFFF, Paint.Align.CENTER);
        float bw = Math.min(220, W * 0.26f);
        float pct = totalCells > 0 ? paintedCells / (float) totalCells : 0;
        fill.setColor(0xFF231C38);
        rf.set(cx - bw / 2, 19, cx + bw / 2, 29);
        c.drawRoundRect(rf, 5, 5, fill);
        fill.setColor(city.accent);
        rf.set(cx - bw / 2, 19, cx - bw / 2 + bw * pct, 29);
        c.drawRoundRect(rf, 5, 5, fill);
        label(c, (int) (pct * 100) + "%", cx + bw / 2 + 6, 29, 11, city.accent, Paint.Align.LEFT);

        // vidas y carga (segunda fila)
        for (int i = 0; i < Math.min(lives, 6); i++) {
            c.save();
            c.translate(26 + i * 30, 62);
            c.scale(0.45f, 0.45f);
            fill.setColor(0xFFF4F7FF);
            c.drawPath(carBody, fill);
            fill.setColor(0xFF00C8F0);
            c.drawRect(-29, -11, 31, -6, fill);
            c.restore();
        }
        float ax = 26 + Math.min(lives, 6) * 30 + 8;
        for (int i = 0; i < ammo; i++) {
            c.save();
            c.translate(ax + i * 13, 53);
            c.scale(0.9f, 0.9f);
            fill.setColor(0xFF6BFF8A);
            c.drawPath(bolt, fill);
            c.restore();
        }
        if (balloonCount % 10 > 0) {
            label(c, "GLOBOS " + (balloonCount % 10) + "/10", 14, 82, 10, 0xCCFFFFFF, Paint.Align.LEFT);
        }
    }

    private void drawControls(Canvas c) {
        drawButton(c, btnLeftX(), btnY(), 46, inLeft || keyLeft);
        drawButton(c, btnRightX(), btnY(), 46, inRight || keyRight);
        drawButton(c, btnJumpX(), btnJumpY(), 54, inJump || keyJump);
        drawButton(c, btnFireX(), btnFireY(), 42, inFire || keyFire);

        fill.setColor(0xDDFFFFFF);
        // flechas
        tmp.reset();
        float lx = btnLeftX(), y = btnY();
        tmp.moveTo(lx - 16, y);
        tmp.lineTo(lx + 10, y - 16);
        tmp.lineTo(lx + 10, y + 16);
        tmp.close();
        c.drawPath(tmp, fill);
        tmp.reset();
        float rx = btnRightX();
        tmp.moveTo(rx + 16, y);
        tmp.lineTo(rx - 10, y - 16);
        tmp.lineTo(rx - 10, y + 16);
        tmp.close();
        c.drawPath(tmp, fill);
        // salto
        float jx = btnJumpX(), jy = btnJumpY();
        stroke.setColor(0xDDFFFFFF);
        stroke.setStrokeWidth(6);
        tmp.reset();
        tmp.moveTo(jx - 16, jy + 2);
        tmp.lineTo(jx, jy - 14);
        tmp.lineTo(jx + 16, jy + 2);
        c.drawPath(tmp, stroke);
        label(c, "SALTO", jx, jy + 26, 12, 0xDDFFFFFF, Paint.Align.CENTER);
        // pulso
        float fx = btnFireX(), fy = btnFireY();
        c.save();
        c.translate(fx, fy - 4);
        c.scale(1.6f, 1.6f);
        fill.setColor(ammo > 0 ? 0xFF6BFF8A : 0x88FFFFFF);
        c.drawPath(bolt, fill);
        c.restore();
        label(c, "PULSO", fx, fy + 24, 11, 0xDDFFFFFF, Paint.Align.CENTER);
    }

    private void drawButton(Canvas c, float x, float y, float r, boolean pressed) {
        fill.setColor(pressed ? withAlpha(city != null ? city.accent : 0xFF00E5FF, 120) : 0x3AFFFFFF);
        c.drawCircle(x, y, r, fill);
        stroke.setColor(0x66FFFFFF);
        stroke.setStrokeWidth(2);
        c.drawCircle(x, y, r, stroke);
    }

    private void panel(Canvas c, float cx, float cy, float w, float h) {
        fill.setColor(0xCC0B0618);
        rf.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        c.drawRoundRect(rf, 18, 18, fill);
        stroke.setColor(city.accent);
        stroke.setStrokeWidth(3);
        c.drawRoundRect(rf, 18, 18, stroke);
    }

    private void drawIntro(Canvas c) {
        float t = Math.min(1f, stateT / 0.4f);
        float cx = W / 2;
        float cy = H / 2 - 20;
        panel(c, cx, cy, Math.min(W - 40, 560) * t, 190);
        if (t < 1) return;
        String stage = "ETAPA " + (stageNum + 1);
        centerText(c, stage, cx, cy - 52, 16, city.accent2, false);
        centerText(c, city.name, cx, cy - 8, 40, city.accent, true);
        centerText(c, city.country + " · " + city.tagline, cx, cy + 22, 13, 0xFFE0E0FF, false);
        String hint = stageNum == 0 ? "Pintá todas las autopistas · Saltá sobre los patrulleros" : "¡Pintá toda la ciudad!";
        centerText(c, hint, cx, cy + 52, 13, 0xFFFFFFFF, false);
        if (stageNum == 0) centerText(c, "Juntá baterías para disparar pulsos ⚡ · ¡Cuidado con el gato!", cx, cy + 72, 11, 0xFFB8B8D8, false);
    }

    private void drawClear(Canvas c) {
        float cx = W / 2;
        float cy = H / 2 - 30;
        panel(c, cx, cy, Math.min(W - 40, 520), 170);
        float bounce = (float) Math.abs(Math.sin(stateT * 4)) * 6;
        centerText(c, "¡CIUDAD COMPLETA!", cx, cy - 30 - bounce, 32, city.accent, true);
        centerText(c, city.name + " quedó brillando", cx, cy + 4, 15, 0xFFFFFFFF, false);
        centerText(c, "BONUS  +" + clearBonus, cx, cy + 40, 22, city.accent2, true);
        City next = City.ALL[(stageNum + 1) % City.ALL.length];
        centerText(c, "Próxima parada: " + next.name, cx, cy + 66, 12, 0xFFB8B8D8, false);
    }

    private void drawGameOver(Canvas c) {
        fill.setColor(0x99000000);
        c.drawRect(0, 0, W, H, fill);
        float cx = W / 2;
        float cy = H / 2 - 20;
        panel(c, cx, cy, Math.min(W - 40, 500), 190);
        centerText(c, "FIN DEL VIAJE", cx, cy - 34, 38, 0xFFFF4D6A, true);
        centerText(c, "PUNTOS  " + pad(score, 7), cx, cy + 6, 20, 0xFFFFFFFF, false);
        boolean record = score >= hiScore && score > 0;
        centerText(c, record ? "¡NUEVO RÉCORD!" : "Récord: " + pad(hiScore, 7), cx, cy + 36, 16,
                record ? city.accent2 : 0xFFB8B8D8, record);
        centerText(c, "Llegaste a " + city.name, cx, cy + 62, 12, 0xFFB8B8D8, false);
    }

    private void drawPause(Canvas c) {
        fill.setColor(0xAA000000);
        c.drawRect(0, 0, W, H, fill);
        float cx = W / 2;
        float cy = H / 2;
        panel(c, cx, cy, Math.min(W - 40, 420), 200);
        centerText(c, "PAUSA", cx, cy - 34, 40, city.accent, true);
        centerText(c, "Tocá la pantalla para seguir", cx, cy + 4, 15, 0xFFFFFFFF, false);
        fill.setColor(0x33FFFFFF);
        rf.set(cx - 140, cy + 46, cx + 140, cy + 84);
        c.drawRoundRect(rf, 12, 12, fill);
        centerText(c, "Volver al menú", cx, cy + 71, 15, city.accent2, false);
    }

    private void drawTitle(Canvas c) {
        Backdrop b = titleBackdrop;
        City tc = City.ALL[titleCity];
        float cam = titleCam;
        b.draw(c, cam, W, time + stateT);

        // calle con estela pintada
        fill.setColor(0xFF2B2838);
        c.drawRect(0, GROUND_Y, W, GROUND_Y + DECK, fill);
        float carX = W * 0.30f;
        fill.setColor(tc.accent);
        c.drawRect(0, GROUND_Y, carX, GROUND_Y + DECK, fill);
        fill.setColor(0xA0FFFFFF);
        c.drawRect(0, GROUND_Y, carX, GROUND_Y + 2.5f, fill);
        fill.setColor(0x55FFFFFF);
        float off = mod(cam, CELL);
        for (float x = carX - off + CELL; x < W; x += CELL) c.drawRect(x + 8, GROUND_Y + 5, x + 22, GROUND_Y + 7, fill);
        fill.setColor(0xFF15101F);
        c.drawRect(0, GROUND_Y + DECK, W, H, fill);

        City saved = city;
        city = tc;
        float bounce = (float) Math.abs(Math.sin(stateT * 5)) * -4;
        drawCar(c, carX, GROUND_Y + bounce * 0.5f, 1, cam / 7f, 0);
        city = saved;

        // logo
        float cx = W / 2;
        float ly = 150 + (float) Math.sin(stateT * 2) * 4;
        drawLogo(c, "CITY", cx, ly, 92, tc);
        drawLogo(c, "TRAVEL", cx, ly + 84, 92, tc);

        centerText(c, "Un viaje eléctrico por las ciudades del mundo", cx, ly + 120, 15, 0xFFFFFFFF, true);
        if (((int) (stateT * 2.2f)) % 2 == 0) {
            centerText(c, "TOCÁ PARA EMPEZAR", cx, ly + 168, 22, tc.accent2, true);
        }
        label(c, "RÉCORD " + pad(hiScore, 7), 16, 26, 15, 0xFFFFFFFF, Paint.Align.LEFT);
        label(c, "© 2026 Nando-codeAr · Homenaje a City Connection (Jaleco, 1985)", cx, H - 10, 10, 0xAAFFFFFF, Paint.Align.CENTER);

        // botón de sonido
        float sx = W - 30, sy = 24;
        fill.setColor(0x55FFFFFF);
        c.drawCircle(sx, sy, 17, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawRect(sx - 9, sy - 4, sx - 4, sy + 4, fill);
        tmp.reset();
        tmp.moveTo(sx - 4, sy - 4);
        tmp.lineTo(sx + 2, sy - 9);
        tmp.lineTo(sx + 2, sy + 9);
        tmp.lineTo(sx - 4, sy + 4);
        tmp.close();
        c.drawPath(tmp, fill);
        stroke.setStrokeWidth(2);
        if (muted) {
            stroke.setColor(0xFFFF4D6A);
            c.drawLine(sx + 5, sy - 5, sx + 12, sy + 5, stroke);
            c.drawLine(sx + 12, sy - 5, sx + 5, sy + 5, stroke);
        } else {
            stroke.setColor(0xFFFFFFFF);
            rf.set(sx - 2, sy - 8, sx + 10, sy + 8);
            c.drawArc(rf, -50, 100, false, stroke);
        }
    }

    private void drawLogo(Canvas c, String s, float cx, float y, float size, City tc) {
        logo.setTextSize(size);
        logo.setTextAlign(Paint.Align.CENTER);
        logoStroke.setTextSize(size);
        logoStroke.setTextAlign(Paint.Align.CENTER);
        // sombra 3D
        logo.setShader(null);
        logo.setColor(0xFF120626);
        for (int i = 6; i >= 1; i--) c.drawText(s, cx + i * 1.5f, y + i * 1.5f, logo);
        logoStroke.setColor(0xFF120626);
        logoStroke.setStrokeWidth(10);
        c.drawText(s, cx, y, logoStroke);
        logo.setShader(new LinearGradient(0, y - size * 0.75f, 0, y, tc.accent2, tc.accent, Shader.TileMode.CLAMP));
        c.drawText(s, cx, y, logo);
        logo.setShader(null);
        logoStroke.setColor(0xFFFFFFFF);
        logoStroke.setStrokeWidth(2);
        c.drawText(s, cx, y, logoStroke);
    }
}
