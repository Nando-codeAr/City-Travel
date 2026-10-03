package net.nandocodear.citytravel;

/** Datos de cada ciudad del recorrido: paleta, hito, música. */
final class City {

    static final int LM_OBELISCO = 0;
    static final int LM_RASCACIELOS = 1;
    static final int LM_RELOJ = 2;
    static final int LM_EIFFEL = 3;
    static final int LM_TORRE_TOKIO = 4;
    static final int LM_CERRO = 5;

    final String name;
    final String country;
    final int skyTop, skyBottom;
    final int farColor;
    final int midColor;
    final int accent;
    final int accent2;
    final int windowColor;
    final float night;      // 0 = día, 1 = noche cerrada
    final int landmark;
    final Music.Song song;
    final long seed;
    final String tagline;

    City(String name, String country, int skyTop, int skyBottom, int farColor, int midColor,
         int accent, int accent2, int windowColor, float night, int landmark, Music.Song song,
         long seed, String tagline) {
        this.name = name;
        this.country = country;
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.farColor = farColor;
        this.midColor = midColor;
        this.accent = accent;
        this.accent2 = accent2;
        this.windowColor = windowColor;
        this.night = night;
        this.landmark = landmark;
        this.song = song;
        this.seed = seed;
        this.tagline = tagline;
    }

    static final City[] ALL = {
            new City("BUENOS AIRES", "ARGENTINA",
                    0xFF1B1446, 0xFFFF8A5C, 0xFF3A2D6B, 0xFF4B3A86,
                    0xFF6FD3FF, 0xFFFFD54F, 0xFFFFE6A0, 0.55f, LM_OBELISCO, Music.TANGO, 1985,
                    "Atardecer sobre la 9 de Julio"),
            new City("NUEVA YORK", "ESTADOS UNIDOS",
                    0xFF050A24, 0xFF2A3478, 0xFF1A2045, 0xFF2B3566,
                    0xFFFFC93C, 0xFF4DE1FF, 0xFFFFD27A, 1f, LM_RASCACIELOS, Music.AVENIDA, 2026,
                    "La ciudad que nunca duerme"),
            new City("LONDRES", "REINO UNIDO",
                    0xFF3D4F6B, 0xFFB7C3D0, 0xFF5A6A80, 0xFF6E5A6E,
                    0xFFFF3B5C, 0xFFFFFFFF, 0xFFFFF0B0, 0.35f, LM_RELOJ, Music.AUTOPISTA, 1868,
                    "Niebla, ladrillo y buses rojos"),
            new City("PARÍS", "FRANCIA",
                    0xFF3B1E5A, 0xFFFF9EC4, 0xFF6B3D78, 0xFF7E4A82,
                    0xFFFF4FD8, 0xFFFFF07A, 0xFFFFE8C0, 0.45f, LM_EIFFEL, Music.TANGO, 1889,
                    "Luces rosadas sobre el Sena"),
            new City("TOKIO", "JAPÓN",
                    0xFF0A0420, 0xFF3A0A55, 0xFF22103D, 0xFF2C1650,
                    0xFFFF2E88, 0xFF35F2FF, 0xFF9CF6FF, 1f, LM_TORRE_TOKIO, Music.AUTOPISTA, 1958,
                    "Neón hasta donde llega la vista"),
            new City("RÍO DE JANEIRO", "BRASIL",
                    0xFF2E8BFF, 0xFFA8EEFF, 0xFF3B7A6A, 0xFFE0A050,
                    0xFF39FF88, 0xFFFFE14D, 0xFFBFEFFF, 0f, LM_CERRO, Music.AVENIDA, 1931,
                    "Sol, mar y cerros"),
    };
}
