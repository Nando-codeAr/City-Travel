# City Travel 🚗⚡

Juego arcade para Android, en horizontal, inspirado en el clásico **City Connection** (Jaleco, 1985).
Manejás un autito **eléctrico** por autopistas elevadas de distintas ciudades del mundo y tenés que
**pintar todo el asfalto** mientras esquivás a los patrulleros.

## Descargar

👉 **[Descargar la última APK](https://github.com/Nando-codeAr/City-Travel/releases/latest/download/CityTravel.apk)**

Cada push a `main` compila automáticamente una APK nueva y la publica en
[Releases](https://github.com/Nando-codeAr/City-Travel/releases). Todas las versiones van firmadas
con la misma clave, así que se instalan una encima de la otra sin perder el récord.

## Cómo se juega

| Control | Acción |
|---|---|
| ◀ ▶ (abajo a la izquierda) | Acelerar / frenar y dar vuelta |
| **SALTO** (abajo a la derecha) | Saltar al nivel de autopista de arriba |
| **PULSO** ⚡ | Disparar un pulso eléctrico (gasta una batería) |
| Botón ⏸ arriba a la derecha | Pausa |

También funciona con teclado o joystick Bluetooth (flechas / A-D, espacio para saltar, X para el pulso).

- Pasá por cada tramo de autopista para pintarlo. Cuando la barra llega al 100 %, la ciudad está completa.
- Los **patrulleros** te persiguen: esquivalos, **caeles encima** para aplastarlos (puntos en combo) o frenalos con un **pulso**.
- Juntá **baterías** verdes para recargar pulsos.
- Agarrá **globos**: cada 10 globos, vida extra.
- ¡Ojo con el **gato** que se sienta en la autopista!

## Recorrido

Buenos Aires → Nueva York → Londres → París → Tokio → Río de Janeiro… y vuelta a empezar, cada vez más difícil.
Cada ciudad tiene su paleta, su skyline con hito propio y fondos con **parallax** (cielo, skyline lejano,
edificios con carteles de neón, farolas y autopistas, cada capa a distinta velocidad).

## Música

Toda la banda sonora es **original** y se genera en tiempo real con un sintetizador chiptune propio
(`SynthCore.java`): lead de doble pulso con vibrato y eco, arpegios de acordes, bajo triangular y
batería sintetizada. Temas: *Luces de la Ciudad* (título), *Avenida Neón*, *Tango Eléctrico* y
*Autopista al Sol*, más jingles de largada, ciudad completa y fin del viaje.

Las canciones están escritas en una notación de texto simple en `Music.java`, así que es fácil
componer o modificar melodías.

## Estructura

```
app/src/main/java/net/nandocodear/citytravel/
  MainActivity.java   pantalla completa horizontal, ciclo de vida, teclado/joystick
  GameView.java       bucle principal, dibujo acelerado por GPU, táctil multipunto
  Game.java           lógica, física, IA de patrulleros, dibujo de autos, HUD y pantallas
  Backdrop.java       fondos con parallax por ciudad
  City.java           datos de cada ciudad
  Music.java          canciones (notación de texto)
  SynthCore.java      sintetizador y efectos de sonido
  Synth.java          salida de audio con AudioTrack
```

Compilación local: `gradle assembleRelease` (Gradle 8.7, JDK 17, Android SDK 34).

---
© 2026 Fernando V. Geddo — Nando-codeAr. Homenaje no oficial; *City Connection* es una marca de sus respectivos dueños.
