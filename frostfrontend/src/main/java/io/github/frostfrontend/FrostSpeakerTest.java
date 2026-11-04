package io.github.frostfrontend;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

/**
 * Native Android stereo channel diagnostic for any game using FrostFrontend.
 * Plays three short tones: LEFT, RIGHT, BOTH. Does not depend on game audio,
 * Ruffle, WebView or any copyrighted audio recording.
 */
public final class FrostSpeakerTest {
    private static final String TAG = "FrostSpeakerTest";
    private static final int SAMPLE_RATE = 48000;
    private static final double FREQUENCY_HZ = 440.0;
    private volatile Thread worker;

    /** Start asynchronously. Returns false if a test is already running. */
    public synchronized boolean start() {
        if (worker != null && worker.isAlive()) return false;
        Thread thread = new Thread(this::play, "FrostSpeakerTest");
        worker = thread;
        thread.start();
        return true;
    }

    public synchronized boolean isPlaying() {
        return worker != null && worker.isAlive();
    }

    /** Cancel playback without blocking the Android UI thread. */
    public synchronized void stop() {
        if (worker != null) worker.interrupt();
    }

    private void play() {
        AudioTrack track = null;
        try {
            int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) {
                Log.e(TAG, "Stereo PCM audio output unavailable");
                return;
            }
            AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(minBuffer, SAMPLE_RATE * 4 / 5))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Stereo AudioTrack could not initialize");
                return;
            }
            track.play();
            int stepFrames = (int) (SAMPLE_RATE * 0.75);
            int fadeFrames = (int) (SAMPLE_RATE * 0.02);
            int gapFrames = (int) (SAMPLE_RATE * 0.18);
            for (int channel = 0; channel < 3; channel++) {
                if (Thread.currentThread().isInterrupted()) break;
                Log.d(TAG, "Playing " + new String[]{"LEFT", "RIGHT", "BOTH"}[channel]);
                short[] pcm = new short[stepFrames * 2];
                for (int i = 0; i < stepFrames; i++) {
                    double envelope = Math.min(1.0,
                        Math.min(i / (double) fadeFrames,
                            (stepFrames - 1 - i) / (double) fadeFrames));
                    short sample = (short) (Math.sin(
                        2 * Math.PI * FREQUENCY_HZ * i / SAMPLE_RATE) *
                        Math.max(0, envelope) * Short.MAX_VALUE * 0.18);
                    pcm[i * 2] = channel == 1 ? 0 : sample;
                    pcm[i * 2 + 1] = channel == 0 ? 0 : sample;
                }
                if (!write(track, pcm)) break;
                if (channel < 2 && !write(track, new short[gapFrames * 2]))
                    break;
            }
            long expectedFrames = 3L * stepFrames + 2L * gapFrames;
            long deadline = SystemClock.uptimeMillis() + 4500;
            while (!Thread.currentThread().isInterrupted() &&
                   track.getPlaybackHeadPosition() < expectedFrames &&
                   SystemClock.uptimeMillis() < deadline) {
                Thread.sleep(25);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            Log.e(TAG, "Speaker test failed", error);
        } finally {
            if (track != null) {
                try { track.pause(); } catch (Exception ignored) {}
                try { track.flush(); } catch (Exception ignored) {}
                track.release();
            }
            synchronized (this) {
                if (Thread.currentThread() == worker) worker = null;
            }
        }
    }

    private static boolean write(AudioTrack track, short[] pcm) {
        int offset = 0;
        while (offset < pcm.length && !Thread.currentThread().isInterrupted()) {
            int count = track.write(pcm, offset, pcm.length - offset,
                AudioTrack.WRITE_BLOCKING);
            if (count <= 0) return false;
            offset += count;
        }
        return offset == pcm.length;
    }
}
