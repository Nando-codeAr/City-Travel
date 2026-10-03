package net.nandocodear.citytravel;

/**
 * Banda sonora original de City Travel.
 * Melodías chiptune compuestas para el juego, con el espíritu de las
 * máquinas arcade de los 80: saltarinas, pegadizas y bien electrónicas.
 *
 * Notación: cada compás tiene 16 pasos (semicorcheas), separados por '|'.
 *   C5, F#4, Bb5 ... = nota      . = silencio      - = sostener
 * Acordes: uno o varios por compás (se reparten el compás en partes iguales).
 * Batería: k = bombo, s = redoblante, h = hi-hat, o = hi-hat abierto (combinables: "kh").
 */
public final class Music {

    public static final int REST = -1;
    public static final int HOLD = -2;

    public static final int BASS_OCTAVE = 0;
    public static final int BASS_TANGO = 1;
    public static final int BASS_PUMP = 2;
    public static final int BASS_WALK = 3;
    public static final int BASS_LONG = 4;

    // códigos de patrón de bajo: 0 silencio, 1 fundamental, 2 octava, 3 quinta, 4 sostener
    private static final int[][] BASS_PATTERNS = {
            {1, 4, 2, 4, 1, 4, 2, 4, 1, 4, 2, 4, 1, 4, 2, 4},   // octavas disco
            {1, 4, 4, 3, 1, 4, 2, 4, 1, 4, 4, 3, 1, 4, 3, 4},   // habanera / tango
            {1, 0, 1, 0, 2, 0, 1, 3, 1, 0, 1, 0, 2, 0, 3, 0},   // bombeo
            {1, 4, 3, 4, 2, 4, 3, 4, 1, 4, 3, 4, 2, 4, 3, 4},   // caminante
            {1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4},   // nota larga
    };

    public static final class Song {
        public final String name;
        public final float bpm;
        public final float swing;
        public final boolean loop;
        public final float leadDuty;
        public int[] lead;
        public int[][] chords;
        public int[] bass;
        public int[] drums;
        public int length;

        Song(String name, float bpm, float swing, boolean loop, float leadDuty) {
            this.name = name;
            this.bpm = bpm;
            this.swing = swing;
            this.loop = loop;
            this.leadDuty = leadDuty;
        }
    }

    // ------------------------------------------------------------------
    // Canciones
    // ------------------------------------------------------------------

    /** Pantalla de título: "Luces de la Ciudad". */
    public static final Song TITLE = build("Luces de la Ciudad", 124, 0.10f, true, 0.25f,
            "G5 - - . D5 . G5 . B5 - - . A5 . G5 . |" +
            "A5 - - . E5 . A5 . C6 - - . B5 . A5 . |" +
            "B5 . D6 . G6 . D6 . C6 . B5 . A5 . G5 . |" +
            "F#5 . A5 . D6 - - - . . . . A5 . B5 . |" +
            "C6 . E5 . G5 . C6 . E6 . D6 . C6 . B5 . |" +
            "A5 . F#5 . D5 . F#5 . A5 . D6 . C6 . A5 . |" +
            "B5 . G5 . E5 . G5 . A5 . F#5 . D5 . F#5 . |" +
            "G5 - - - D5 . G5 . G4 - - - . . . . ",
            "G | Am | G | D | C | D | Em D | G",
            BASS_OCTAVE,
            "k . h . s . h . k . h k s . h o");

    /** Escenario A: "Avenida Neón" (saltarina, en Do mayor). */
    public static final Song AVENIDA = build("Avenida Neón", 150, 0.16f, true, 0.25f,
            "E5 . G5 . C6 . G5 . A5 - G5 . E5 . C5 . |" +
            "D5 . E5 . F5 . A5 . G5 - - . . . G5 . |" +
            "E5 . G5 . C6 . G5 . A5 - C6 . D6 . E6 . |" +
            "D6 . C6 . A5 . B5 . C6 - - - . . G5 . |" +
            "F5 . A5 . C6 . A5 . G5 . E5 . C5 . E5 . |" +
            "D5 . F5 . A5 . F5 . E5 - - . G5 . . . |" +
            "A5 . G5 . F5 . E5 . D5 . E5 . F5 . D5 . |" +
            "C5 - - . G4 . C5 . E5 . G5 . C6 . . . |" +
            "C6 . . C6 . . B5 . A5 . . G5 . . E5 . |" +
            "F5 . . A5 . . C6 . D6 - - - . . . . |" +
            "B5 . . B5 . . A5 . G5 . . F5 . . D5 . |" +
            "E5 . . G5 . . C6 . E6 - - - . . . . |" +
            "F6 . E6 . D6 . C6 . A5 . C6 . D6 . F6 . |" +
            "E6 . D6 . C6 . A5 . G5 - - - . . . . |" +
            "F5 . G5 . A5 . B5 . C6 . D6 . E6 . F6 . |" +
            "G6 - - - - - - - . . . . G5 . . . ",
            "C | Dm G | C Am | G C | F | Dm | F G | C | " +
            "Am | F | G | C | Dm | C | F G | G7",
            BASS_OCTAVE,
            "k . h . s . h k k . h . s . h h");

    /** Escenario B: "Tango Eléctrico" (La menor, ritmo de habanera). */
    public static final Song TANGO = build("Tango Eléctrico", 132, 0.0f, true, 0.5f,
            "A5 . . E5 . . A5 . C6 . B5 . A5 . . . |" +
            "G#5 . . E5 . . G#5 . B5 . A5 . G#5 . . . |" +
            "A5 . . C6 . . E6 . D6 . C6 . B5 . A5 . |" +
            "B5 . C6 . B5 . A5 . E5 - - - . . . . |" +
            "D6 . . F5 . . A5 . D6 . C6 . B5 . . . |" +
            "C6 . . E5 . . A5 . C6 . B5 . A5 . . . |" +
            "B5 . G#5 . E5 . G#5 . B5 . D6 . C6 . B5 . |" +
            "A5 - - - E5 . A5 . A4 - - - . . . . |" +
            "E6 - - . D6 . C6 . B5 - - . A5 . G#5 . |" +
            "A5 . B5 . C6 . A5 . E5 - - - . . . . |" +
            "F6 - - . E6 . D6 . C6 - - . B5 . A5 . |" +
            "G#5 . A5 . B5 . G#5 . E5 - - - . . . . |" +
            "A5 . C6 . E6 . A6 . G#6 . E6 . B5 . G#5 . |" +
            "F6 . D6 . A5 . F5 . E5 . G#5 . B5 . E6 . |" +
            "D6 . C6 . B5 . A5 . G#5 . A5 . B5 . G#5 . |" +
            "A5 - - - - - - - . . . . E5 . . . ",
            "Am | E | Am | E | Dm | Am | E | Am | " +
            "Am | Am | Dm | E | Am | Dm E | E | Am",
            BASS_TANGO,
            "k . . kh s . k . k . h . s . h o");

    /** Escenario C: "Autopista al Sol" (Fa mayor, rápida). */
    public static final Song AUTOPISTA = build("Autopista al Sol", 162, 0.12f, true, 0.25f,
            "C5 . F5 . A5 . C6 . A5 . F5 . A5 . C6 . |" +
            "D6 . C6 . Bb5 . A5 . G5 - - - . . . . |" +
            "C5 . E5 . G5 . Bb5 . D6 . C6 . Bb5 . G5 . |" +
            "A5 . G5 . F5 . E5 . F5 - - - . . . . |" +
            "A5 . A5 . Bb5 . C6 . D6 . D6 . C6 . Bb5 . |" +
            "A5 . A5 . G5 . F5 . G5 - - - . . . . |" +
            "F5 . A5 . C6 . F6 . E6 . D6 . C6 . Bb5 . |" +
            "A5 . G5 . E5 . G5 . F5 - - - . . C6 . |" +
            "F6 . . F6 . . E6 . D6 . . C6 . . A5 . |" +
            "Bb5 . . D6 . . F6 . E6 - - - . . C6 . |" +
            "D6 . . D6 . . C6 . Bb5 . . A5 . . G5 . |" +
            "A5 . . C6 . . F6 . F6 - - - . . . . ",
            "F | Bb C | C | F | Bb | C | F Bb | C F | Bb | Gm C | Bb Gm | C7 F",
            BASS_PUMP,
            "k h s h k h s h k h s h k k s o");

    /** Fanfarria de largada. */
    public static final Song START = build("Largada", 140, 0f, false, 0.25f,
            "C5 . E5 . G5 . C6 . . . G5 . C6 - - - |" +
            "E6 - - - - - - - - - - - . . . . ",
            "C | C",
            BASS_LONG,
            "k . . . k . . . k . k . s s s s | k . . . . . . . . . . . . . . .");

    /** Ciudad completada. */
    public static final Song CLEAR = build("Ciudad Completa", 150, 0.1f, false, 0.25f,
            "G5 . C6 . E6 . G6 . E6 . G6 - - . . A6 . |" +
            "G6 . E6 . F6 . D6 . G6 - - - . . . . |" +
            "C7 - - - - - - - - - - - . . . . ",
            "C | F G | C",
            BASS_OCTAVE,
            "k . h . s . h . k . h . s s s s | k . . . s . . . k . . . s . . . | k . . . . . . . . . . . . . . .");

    /** Choque / vida perdida. */
    public static final Song MISS = build("Choque", 120, 0f, false, 0.5f,
            "E5 . D#5 . D5 . C#5 . C5 - - - . . . . ",
            "C",
            BASS_LONG,
            ". . . . . . . . . . . . . . . .");

    /** Fin del juego. */
    public static final Song GAMEOVER = build("Fin del Viaje", 96, 0f, false, 0.5f,
            "C5 . . . B4 . . . Bb4 . . . A4 - - - |" +
            "G#4 - - - - - - - . . . . . . . . ",
            "C Cm | Fm",
            BASS_LONG,
            "k . . . s . . . k . . . s . . . | k . . . . . . . . . . . . . . .");

    public static final Song[] STAGE_SONGS = {AVENIDA, TANGO, AUTOPISTA};

    // ------------------------------------------------------------------
    // Compilación de la notación
    // ------------------------------------------------------------------

    private static Song build(String name, float bpm, float swing, boolean loop, float duty,
                              String lead, String chords, int bassStyle, String drums) {
        Song s = new Song(name, bpm, swing, loop, duty);
        s.lead = parseLine(lead);
        s.length = s.lead.length;
        s.chords = parseChords(chords);
        s.bass = makeBass(s.chords, bassStyle);
        s.drums = parseDrums(drums);
        return s;
    }

    private static String[] bars(String s) {
        String[] b = s.split("\\|");
        return b;
    }

    private static String[] tokens(String bar) {
        String t = bar.trim();
        if (t.isEmpty()) return new String[0];
        return t.split("\\s+");
    }

    static int[] parseLine(String s) {
        String[] bs = bars(s);
        int[] out = new int[bs.length * 16];
        for (int b = 0; b < bs.length; b++) {
            String[] tk = tokens(bs[b]);
            for (int i = 0; i < 16; i++) {
                int v = REST;
                if (i < tk.length) {
                    String t = tk[i];
                    if (t.equals(".")) v = REST;
                    else if (t.equals("-")) v = HOLD;
                    else v = noteToMidi(t);
                }
                out[b * 16 + i] = v;
            }
        }
        return out;
    }

    static int semis(char c) {
        switch (Character.toUpperCase(c)) {
            case 'C': return 0;
            case 'D': return 2;
            case 'E': return 4;
            case 'F': return 5;
            case 'G': return 7;
            case 'A': return 9;
            case 'B': return 11;
            default: return 0;
        }
    }

    static int noteToMidi(String t) {
        try {
            int idx = 0;
            int n = semis(t.charAt(idx++));
            if (idx < t.length() && t.charAt(idx) == '#') { n++; idx++; }
            else if (idx < t.length() && t.charAt(idx) == 'b') { n--; idx++; }
            int oct = Integer.parseInt(t.substring(idx));
            return 12 * (oct + 1) + n;
        } catch (Exception e) {
            return REST;
        }
    }

    static int[] chordTones(String c) {
        int idx = 0;
        int root = semis(c.charAt(idx++));
        if (idx < c.length() && c.charAt(idx) == '#') { root++; idx++; }
        else if (idx < c.length() && c.charAt(idx) == 'b') { root--; idx++; }
        String q = c.substring(idx);
        int[] iv;
        switch (q) {
            case "m": iv = new int[]{0, 3, 7}; break;
            case "7": iv = new int[]{0, 4, 7, 10}; break;
            case "m7": iv = new int[]{0, 3, 7, 10}; break;
            case "maj7": iv = new int[]{0, 4, 7, 11}; break;
            case "sus": iv = new int[]{0, 5, 7}; break;
            case "dim": iv = new int[]{0, 3, 6}; break;
            default: iv = new int[]{0, 4, 7}; break;
        }
        int base = 60 + root; // octava 4
        int[] out = new int[iv.length];
        for (int i = 0; i < iv.length; i++) out[i] = base + iv[i];
        return out;
    }

    static int[][] parseChords(String s) {
        String[] bs = bars(s);
        int[][] out = new int[bs.length * 16][];
        for (int b = 0; b < bs.length; b++) {
            String[] tk = tokens(bs[b]);
            if (tk.length == 0) tk = new String[]{"C"};
            int per = 16 / tk.length;
            for (int i = 0; i < 16; i++) {
                int ci = Math.min(tk.length - 1, i / Math.max(1, per));
                out[b * 16 + i] = chordTones(tk[ci]);
            }
        }
        return out;
    }

    static int[] makeBass(int[][] chords, int style) {
        int[] pat = BASS_PATTERNS[style];
        int[] out = new int[chords.length];
        int[] prev = null;
        for (int i = 0; i < chords.length; i++) {
            int[] ch = chords[i];
            int root = ch[0] - 24;
            int code = pat[i % 16];
            boolean chordChanged = prev != null && prev != ch && (prev[0] != ch[0] || prev.length != ch.length);
            if (chordChanged && (code == 4 || code == 0)) code = 1;
            switch (code) {
                case 1: out[i] = root; break;
                case 2: out[i] = root + 12; break;
                case 3: out[i] = root + 7; break;
                case 4: out[i] = HOLD; break;
                default: out[i] = REST; break;
            }
            prev = ch;
        }
        return out;
    }

    static int[] parseDrums(String s) {
        String[] bs = bars(s);
        int[] out = new int[bs.length * 16];
        for (int b = 0; b < bs.length; b++) {
            String[] tk = tokens(bs[b]);
            for (int i = 0; i < 16; i++) {
                int m = 0;
                if (i < tk.length) {
                    String t = tk[i];
                    for (int c = 0; c < t.length(); c++) {
                        switch (t.charAt(c)) {
                            case 'k': m |= 1; break;
                            case 's': m |= 2; break;
                            case 'h': m |= 4; break;
                            case 'o': m |= 8; break;
                            default: break;
                        }
                    }
                }
                out[b * 16 + i] = m;
            }
        }
        return out;
    }

    private Music() {}
}
