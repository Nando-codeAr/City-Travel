package net.nandocodear.citytravel;

/**
 * Sintetizador chiptune por software (sin dependencias de Android, para poder probarlo).
 * Voces: lead doble pulso con vibrato y eco, arpegio de acordes, bajo triangular,
 * batería sintetizada (bombo, redoblante, hi-hats) y dos canales de efectos.
 */
public final class SynthCore {

    public static final int SR = 32000;

    // ---------------- efectos de sonido ----------------
    public static final int SFX_JUMP = 0;
    public static final int SFX_PAINT = 1;
    public static final int SFX_PULSE = 2;
    public static final int SFX_HIT = 3;
    public static final int SFX_PICKUP = 4;
    public static final int SFX_BALLOON = 5;
    public static final int SFX_CRASH = 6;
    public static final int SFX_MEOW = 7;
    public static final int SFX_ONEUP = 8;
    public static final int SFX_SELECT = 9;
    public static final int SFX_STOMP = 10;
    public static final int SFX_TICK = 11;

    // wave: 0 pulso, 1 triángulo, 2 ruido, 3 seno
    // cada segmento: {freqIni, freqFin, duración(s), onda, volumen, duty}
    private static final float[][][] SFX = {
            {{260, 900, 0.16f, 0, 0.16f, 0.25f}},                                        // salto
            {{1500, 1900, 0.025f, 0, 0.035f, 0.5f}},                                     // pintar
            {{1800, 220, 0.20f, 0, 0.16f, 0.125f}, {3000, 3000, 0.08f, 2, 0.08f, 0}},    // pulso eléctrico
            {{900, 90, 0.30f, 0, 0.16f, 0.5f}, {4000, 4000, 0.20f, 2, 0.12f, 0}},       // impacto patrullero
            {{988, 988, 0.06f, 0, 0.13f, 0.5f}, {1319, 1319, 0.06f, 0, 0.13f, 0.5f}, {1976, 1976, 0.10f, 0, 0.10f, 0.5f}}, // batería
            {{784, 784, 0.05f, 0, 0.12f, 0.25f}, {1047, 1047, 0.05f, 0, 0.12f, 0.25f}, {1319, 1319, 0.05f, 0, 0.12f, 0.25f}, {1568, 1568, 0.10f, 0, 0.12f, 0.25f}}, // globo
            {{2500, 2500, 0.55f, 2, 0.22f, 0}, {420, 40, 0.5f, 0, 0.14f, 0.5f}},        // choque
            {{650, 1100, 0.12f, 3, 0.14f, 0}, {1100, 560, 0.25f, 3, 0.14f, 0}},          // miau
            {{523, 523, 0.07f, 0, 0.13f, 0.5f}, {659, 659, 0.07f, 0, 0.13f, 0.5f}, {784, 784, 0.07f, 0, 0.13f, 0.5f}, {1047, 1047, 0.07f, 0, 0.13f, 0.5f}, {1568, 1568, 0.18f, 0, 0.13f, 0.5f}}, // vida extra
            {{660, 660, 0.05f, 0, 0.12f, 0.25f}, {990, 990, 0.08f, 0, 0.12f, 0.25f}},     // selección
            {{300, 1300, 0.10f, 0, 0.15f, 0.5f}, {1300, 1300, 0.06f, 0, 0.1f, 0.25f}},    // pisotón
            {{1200, 1200, 0.03f, 0, 0.08f, 0.5f}},                                       // tic
    };

    // ---------------- estado de canción ----------------
    private Music.Song song;
    private Music.Song pending;
    private boolean pendingFlag;
    private int step;
    private double stepCounter;
    private double stepLen;
    private boolean finished = true;

    private final Voice lead = new Voice();
    private final Voice lead2 = new Voice();
    private final Voice arp = new Voice();
    private final Voice bass = new Voice();
    private int[] arpTones;
    private int arpIndex;
    private int arpCounter;

    // batería
    private double kickT = 10, snareT = 10, hatT = 10, openT = 10;
    private double kickPhase, snarePhase;
    private float prevNoise;

    // eco del lead
    private final float[] delay = new float[SR];
    private int delayPos;
    private int delayLen = SR / 4;

    // efectos
    private final SfxVoice[] sfx = {new SfxVoice(), new SfxVoice()};
    private int nextSfx;
    private final int[] sfxQueue = new int[16];
    private int sfxQueueN;

    private int rng = 0x1234567;
    private float musicVolume = 1f;
    private float sfxVolume = 1f;

    private static final double[] MIDI = new double[128];

    static {
        for (int i = 0; i < 128; i++) MIDI[i] = 440.0 * Math.pow(2, (i - 69) / 12.0);
    }

    private static final class Voice {
        double phase, freq;
        double env;
        int stage; // 0 apagado, 1 ataque, 2 decaimiento/sostén, 3 relajación
        double atk = 0.004, dec = 0.12, sus = 0.6, rel = 0.08;
        double time;
        int wave;
        double duty = 0.25;
        double detune = 1.0;

        void on(double f) {
            freq = f;
            stage = 1;
            time = 0;
        }

        void off() {
            if (stage != 0) stage = 3;
        }

        double envStep(double dt) {
            switch (stage) {
                case 1:
                    env += dt / atk;
                    if (env >= 1) { env = 1; stage = 2; }
                    break;
                case 2:
                    env -= dt / dec * (1 - sus);
                    if (env < sus) env = sus;
                    break;
                case 3:
                    env -= dt / rel;
                    if (env <= 0) { env = 0; stage = 0; }
                    break;
                default:
                    env = 0;
            }
            time += dt;
            return env;
        }

        double osc(double f, SynthCore c) {
            phase += f * detune / SR;
            if (phase >= 1) phase -= Math.floor(phase);
            switch (wave) {
                case 1:
                    return phase < 0.5 ? (4 * phase - 1) : (3 - 4 * phase);
                case 3:
                    return Math.sin(phase * 2 * Math.PI);
                default:
                    return phase < duty ? 0.8 : -0.8;
            }
        }
    }

    private static final class SfxVoice {
        float[][] segs;
        int seg = -1;
        double t;
        double phase;
        float noiseHold;
        double noiseAcc;
    }

    public SynthCore() {
        lead.wave = 0;
        lead2.wave = 0;
        lead2.detune = 1.004;
        arp.wave = 0;
        arp.duty = 0.125;
        arp.atk = 0.002;
        arp.dec = 0.09;
        arp.sus = 0.25;
        arp.rel = 0.03;
        bass.wave = 1;
        bass.atk = 0.003;
        bass.dec = 0.2;
        bass.sus = 0.8;
        bass.rel = 0.04;
    }

    // ---------------- API (llamar desde cualquier hilo) ----------------

    public synchronized void play(Music.Song s) {
        pending = s;
        pendingFlag = true;
    }

    public synchronized void sfx(int id) {
        if (sfxQueueN < sfxQueue.length) sfxQueue[sfxQueueN++] = id;
    }

    public synchronized boolean isFinished() {
        return finished && !pendingFlag;
    }

    public synchronized Music.Song current() {
        return pendingFlag ? pending : song;
    }

    public void setMusicVolume(float v) {
        musicVolume = v;
    }

    public void setSfxVolume(float v) {
        sfxVolume = v;
    }

    // ---------------- render ----------------

    private float noise() {
        rng ^= rng << 13;
        rng ^= rng >>> 17;
        rng ^= rng << 5;
        return (rng & 0xFFFF) / 32768f - 1f;
    }

    private void startSong(Music.Song s) {
        song = s;
        step = -1;
        stepCounter = 0;
        finished = (s == null);
        lead.off();
        lead2.off();
        arp.off();
        bass.off();
        arpTones = null;
        if (s != null) {
            delayLen = (int) Math.min(delay.length - 1, SR * 60.0 / s.bpm * 0.75); // eco de corchea con puntillo
        }
    }

    private double stepLength(int stepIndex) {
        double base = SR * 60.0 / (song.bpm * 4.0);
        float sw = song.swing;
        return (stepIndex % 2 == 0) ? base * (1 + sw) : base * (1 - sw);
    }

    private void advanceStep() {
        step++;
        if (step >= song.length) {
            if (song.loop) {
                step = 0;
            } else {
                finished = true;
                lead.off();
                lead2.off();
                arp.off();
                bass.off();
                arpTones = null;
                song = null;
                return;
            }
        }
        stepLen = stepLength(step);
        stepCounter += stepLen;

        int l = song.lead[step];
        if (l >= 0) {
            double f = MIDI[l];
            lead.duty = song.leadDuty;
            lead2.duty = song.leadDuty;
            lead.on(f);
            lead2.on(f);
        } else if (l == Music.REST) {
            lead.off();
            lead2.off();
        }

        int[] ch = song.chords[step % song.chords.length];
        arpTones = ch;
        arp.on(MIDI[ch[0] + 12]);
        arpCounter = 0;
        arpIndex = 0;

        int b = song.bass[step % song.bass.length];
        if (b >= 0) bass.on(MIDI[b]);
        else if (b == Music.REST) bass.off();

        int d = song.drums[step % song.drums.length];
        if ((d & 1) != 0) { kickT = 0; kickPhase = 0; }
        if ((d & 2) != 0) { snareT = 0; }
        if ((d & 4) != 0) { hatT = 0; }
        if ((d & 8) != 0) { openT = 0; }
    }

    private void startSfx(int id) {
        if (id < 0 || id >= SFX.length) return;
        // el sonido de pintar usa siempre el canal 1 para no tapar a los demás
        SfxVoice v;
        if (id == SFX_PAINT || id == SFX_TICK) {
            v = sfx[1];
            if (v.seg >= 0 && v.segs != SFX[SFX_PAINT] && v.segs != SFX[SFX_TICK]) return;
        } else {
            boolean oneFree = sfx[1].seg < 0 || sfx[1].segs == SFX[SFX_PAINT] || sfx[1].segs == SFX[SFX_TICK];
            if (sfx[0].seg < 0) v = sfx[0];
            else if (oneFree) v = sfx[1];
            else { v = sfx[nextSfx]; nextSfx ^= 1; }
        }
        v.segs = SFX[id];
        v.seg = 0;
        v.t = 0;
        v.phase = 0;
    }

    private double renderSfx(SfxVoice v, double dt) {
        if (v.seg < 0) return 0;
        float[] s = v.segs[v.seg];
        double dur = s[2];
        double k = v.t / dur;
        double f = s[0] + (s[1] - s[0]) * k;
        double env = 1 - k * 0.7;
        double out;
        int wave = (int) s[3];
        if (wave == 2) {
            v.noiseAcc += f / SR;
            if (v.noiseAcc >= 1) { v.noiseAcc -= 1; v.noiseHold = noise(); }
            out = v.noiseHold * (1 - k);
        } else {
            v.phase += f / SR;
            if (v.phase >= 1) v.phase -= Math.floor(v.phase);
            if (wave == 3) out = Math.sin(v.phase * 2 * Math.PI);
            else if (wave == 1) out = v.phase < 0.5 ? (4 * v.phase - 1) : (3 - 4 * v.phase);
            else out = v.phase < s[5] ? 0.8 : -0.8;
            out *= env;
        }
        out *= s[4];
        v.t += dt;
        if (v.t >= dur) {
            v.seg++;
            v.t = 0;
            if (v.seg >= v.segs.length) v.seg = -1;
        }
        return out;
    }

    public void render(short[] buf, int n) {
        synchronized (this) {
            if (pendingFlag) {
                startSong(pending);
                pendingFlag = false;
                pending = null;
            }
            for (int i = 0; i < sfxQueueN; i++) startSfx(sfxQueue[i]);
            sfxQueueN = 0;
        }
        final double dt = 1.0 / SR;
        final int arpPeriod = SR / 46;
        for (int i = 0; i < n; i++) {
            double mus = 0;
            if (song != null) {
                stepCounter -= 1;
                if (stepCounter <= 0) advanceStep();
            }
            // lead con vibrato tardío
            double le = lead.envStep(dt);
            lead2.envStep(dt);
            double leadOut = 0;
            if (lead.stage != 0) {
                double vib = 1.0;
                if (lead.time > 0.14) vib = 1.0 + 0.006 * Math.sin(lead.time * 2 * Math.PI * 5.8);
                leadOut = (lead.osc(lead.freq * vib, this) * 0.6 + lead2.osc(lead.freq * vib, this) * 0.4) * le;
            }
            // eco
            int rp = delayPos - delayLen;
            if (rp < 0) rp += delay.length;
            float echo = delay[rp];
            delay[delayPos] = (float) (leadOut + echo * 0.38);
            delayPos++;
            if (delayPos >= delay.length) delayPos = 0;
            mus += leadOut * 0.19 + echo * 0.07;

            // arpegio
            if (arpTones != null && arp.stage != 0) {
                arpCounter++;
                if (arpCounter >= arpPeriod) {
                    arpCounter = 0;
                    arpIndex = (arpIndex + 1) % arpTones.length;
                    arp.freq = MIDI[arpTones[arpIndex] + 12];
                }
            }
            double ae = arp.envStep(dt);
            if (arp.stage != 0) mus += arp.osc(arp.freq, this) * ae * 0.055;

            // bajo
            double be = bass.envStep(dt);
            if (bass.stage != 0) mus += bass.osc(bass.freq, this) * be * 0.30;

            // batería
            if (kickT < 0.3) {
                double f = 42 + 120 * Math.exp(-kickT * 38);
                kickPhase += f / SR;
                mus += Math.sin(kickPhase * 2 * Math.PI) * Math.exp(-kickT * 13) * 0.55;
                kickT += dt;
            }
            float nz = noise();
            if (snareT < 0.25) {
                snarePhase += 185.0 / SR;
                mus += (nz * 0.22 + Math.sin(snarePhase * 2 * Math.PI) * 0.18) * Math.exp(-snareT * 24);
                snareT += dt;
            }
            float hp = nz - prevNoise;
            prevNoise = nz;
            if (hatT < 0.08) {
                mus += hp * 0.05 * Math.exp(-hatT * 85);
                hatT += dt;
            }
            if (openT < 0.35) {
                mus += hp * 0.04 * Math.exp(-openT * 14);
                openT += dt;
            }

            double fx = renderSfx(sfx[0], dt) + renderSfx(sfx[1], dt);
            double x = mus * musicVolume + fx * sfxVolume;
            x *= 1.25;
            // saturación suave
            if (x > 3) x = 3;
            if (x < -3) x = -3;
            x = x * (27 + x * x) / (27 + 9 * x * x);
            buf[i] = (short) (x * 30000);
        }
    }
}
