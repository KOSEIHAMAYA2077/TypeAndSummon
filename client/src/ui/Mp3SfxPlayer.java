package ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** MP3 効果音を Clip にデコードして再生（BGM ループと同時再生可能）。 */
final class Mp3SfxPlayer {
    private static final Map<String, byte[]> PCM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, AudioFormat> FORMAT_CACHE = new ConcurrentHashMap<>();

    private Mp3SfxPlayer() {
    }

    static boolean play(File file, float volume) {
        try {
            Clip clip = openClip(file);
            if (clip == null) {
                return false;
            }
            applyVolume(clip, volume);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
            return true;
        } catch (Exception ex) {
            System.err.println("Mp3SfxPlayer: failed " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
            return false;
        }
    }

    /** MP3 を Clip にデコードして返す（BGM ループ用）。呼び出し側で close する。 */
    static Clip openClip(File file) throws Exception {
        if (file == null || !file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath();
        ensureDecoded(key, file);
        byte[] pcm = PCM_CACHE.get(key);
        AudioFormat format = FORMAT_CACHE.get(key);
        if (pcm == null || format == null) {
            return null;
        }
        Clip clip = AudioSystem.getClip();
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                format,
                pcm.length / format.getFrameSize()
        )) {
            clip.open(stream);
        }
        return clip;
    }

    private static void ensureDecoded(String key, File file) throws IOException {
        if (PCM_CACHE.containsKey(key)) {
            return;
        }
        synchronized (PCM_CACHE) {
            if (PCM_CACHE.containsKey(key)) {
                return;
            }
            decode(file, key);
        }
    }

    private static void decode(File file, String key) throws IOException {
        try {
            AudioFormat[] formatOut = new AudioFormat[1];
            byte[] pcm = GameAudio.decodeMp3Pcm(file, formatOut);
            PCM_CACHE.put(key, pcm);
            FORMAT_CACHE.put(key, formatOut[0]);
        } catch (IOException ex) {
            throw ex;
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        javax.sound.sampled.FloatControl control =
                (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0f, Math.min(1f, volume));
        float gainDb = linear <= 0.0001f ? control.getMinimum() : (float) (20.0 * Math.log10(linear));
        gainDb = Math.max(control.getMinimum(), Math.min(control.getMaximum(), gainDb));
        control.setValue(gainDb);
    }
}
