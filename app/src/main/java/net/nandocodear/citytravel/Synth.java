package net.nandocodear.citytravel;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Envoltorio Android del sintetizador: hilo de audio con AudioTrack en modo streaming. */
public final class Synth implements Runnable {

    public final SynthCore core = new SynthCore();
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;

    public synchronized void start() {
        if (running) return;
        try {
            int min = AudioTrack.getMinBufferSize(SynthCore.SR, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min, 2048 * 2);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SynthCore.SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            running = true;
            thread = new Thread(this, "CityTravel-Audio");
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        } catch (Exception e) {
            running = false;
            track = null;
        }
    }

    public void stop() {
        Thread t;
        synchronized (this) {
            running = false;
            t = thread;
            thread = null;
        }
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) { }
        }
        synchronized (this) {
            if (track != null) {
                try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) { }
                track = null;
            }
        }
    }

    @Override
    public void run() {
        short[] buf = new short[512];
        AudioTrack t = track;
        while (running && t != null) {
            core.render(buf, buf.length);
            int w = t.write(buf, 0, buf.length);
            if (w < 0) break;
        }
    }
}
