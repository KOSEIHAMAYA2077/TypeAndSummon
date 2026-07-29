package ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * BGM（ループ）と効果音（ワンショット）の再生。
 * 素材は {@code client/assets/audio/}（起動時の作業ディレクトリに依存しない）。
 */
public final class GameAudio {
    public static final String AUDIO_ROOT = "assets/audio";

    public enum BgmTrack {
        TITLE("bgm/title"),
        BATTLE("bgm/battle");

        private final String basePath;

        BgmTrack(String basePath) {
            this.basePath = basePath;
        }

        String basePath() {
            return basePath;
        }
    }

    public enum Sfx {
        UI_CLICK("sfx/click.wav"),
        UI_CONFIRM("sfx/ui_confirm.wav"),
        MATCH_START("sfx/match_start.wav"),
        TYPING_CORRECT("sfx/typing_correct.wav"),
        TYPING_MISS("sfx/miss.wav"),
        DAMAGE_PLAYER("sfx/damage_player.wav"),
        DAMAGE_OPPONENT("sfx/damage_opponent.wav"),
        BOSS_ATTACK("sfx/boss_attack.wav"),
        BOSS_TRANSITION("sfx/boss_transition.wav"),
        VICTORY("sfx/victory.wav"),
        DEFEAT("sfx/defeat.wav");

        private final String relativePath;

        Sfx(String relativePath) {
            this.relativePath = relativePath;
        }

        String relativePath() {
            return relativePath;
        }
    }

    private static final Object BGM_LOCK = new Object();
    private static volatile Path clientRoot;
    private static volatile boolean mp3Available;
    private static volatile boolean bootstrapLogged;
    private static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("game.audio.enabled", "true"));
    private static volatile float masterVolume = parseVolume(System.getProperty("game.audio.masterVolume"), 1.0f);
    private static volatile float bgmVolume = parseVolume(System.getProperty("game.audio.bgmVolume"), 0.45f);
    private static volatile float sfxVolume = parseVolume(System.getProperty("game.audio.sfxVolume"), 0.85f);

    private static Clip currentBgmClip;
    private static BgmTrack currentBgmTrack;
    private static String lastSfxDedupKey = "";
    private static long lastSfxDedupAtMs;
    private static final Set<String> loggedMissingPaths = new HashSet<>();

    private GameAudio() {
    }

    /** クライアントルートと MP3 ライブラリを解決する。GUI 起動時に1回呼ぶ。 */
    public static void bootstrap() {
        clientRoot = locateClientRoot();
        mp3Available = Mp3Engine.ensureReady(clientRoot);
        if (!bootstrapLogged) {
            bootstrapLogged = true;
            System.out.println("GameAudio: root=" + clientRoot.toAbsolutePath()
                    + " mp3=" + mp3Available
                    + " title=" + resolveExistingAudioFile("bgm/title.mp3").exists());
            if (!mp3Available) {
                Path jar = clientRoot.resolve("lib/jlayer-1.0.1.jar");
                if (!Files.isRegularFile(jar)) {
                    System.err.println("GameAudio: MP3 用 JAR がありません -> " + jar.toAbsolutePath());
                    System.err.println("GameAudio: client フォルダで scripts/fetch_jlayer.ps1 を実行するか、run.ps1 で起動してください。");
                }
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            stopBgm();
        }
    }

    public static void playBgm(BgmTrack track) {
        bootstrap();
        if (!enabled || track == null) {
            return;
        }
        synchronized (BGM_LOCK) {
            if (track == currentBgmTrack && isBgmPlaying()) {
                return;
            }
            stopBgmInternal();

            File bgmFile = resolveBgmFile(track);
            if (!bgmFile.isFile()) {
                logMissingOnce("BGM " + track, bgmFile);
                return;
            }

            Clip clip = openPlayableClip(bgmFile);
            if (clip == null) {
                return;
            }
            applyVolume(clip, masterVolume * bgmVolume);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && event.getLine() == clip) {
                    synchronized (BGM_LOCK) {
                        if (clip == currentBgmClip) {
                            currentBgmClip = null;
                            currentBgmTrack = null;
                        }
                    }
                    clip.close();
                }
            });
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            currentBgmClip = clip;
            currentBgmTrack = track;
        }
    }

    public static void stopBgm() {
        synchronized (BGM_LOCK) {
            stopBgmInternal();
        }
    }

    public static void playSfx(Sfx sfx) {
        if (sfx == null) {
            return;
        }
        if (sfx == Sfx.TYPING_MISS) {
            playSfxOnce("miss", () -> playSfxFile(sfx.relativePath()));
            return;
        }
        playSfxFile(sfx.relativePath());
    }

    public static void playSfxFile(String relativePath) {
        bootstrap();
        if (!enabled || relativePath == null || relativePath.isBlank()) {
            return;
        }
        File file = resolveExistingAudioFile(relativePath);
        if (!file.isFile()) {
            logMissingOnce("SFX", file);
            return;
        }
        playResolvedSfxFile(file);
    }

    private static void playResolvedSfxFile(File file) {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            if (!mp3Available) {
                System.err.println("GameAudio: MP3 unavailable for " + file.getName());
                return;
            }
            float volume = masterVolume * sfxVolume;
            if (!Mp3SfxPlayer.play(file, volume)) {
                playMp3OneShotFallback(file);
            }
            return;
        }
        Clip clip = openClipFile(file);
        if (clip == null) {
            return;
        }
        applyVolume(clip, masterVolume * sfxVolume);
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.close();
            }
        });
        clip.setFramePosition(0);
        clip.start();
    }

    public static void playWordAttackSfx(int wordLevel) {
        playSfxOnce("attack:" + wordLevel, () -> {
            if (wordLevel >= 1 && wordLevel <= 4) {
                playSfxFile("sfx/attack1.wav");
            } else if (wordLevel >= 5 && wordLevel <= 9) {
                playSfxFile("sfx/attack2.wav");
            }
        });
    }

    public static void playCoopBossBattleStartSfx(int bossLevel) {
        if (bossLevel == 5) {
            playSfxFile("sfx/lv5.wav");
        }
    }

    public static void playCoopBossAttackSfx(int bossLevel) {
        playSfxOnce("boss-attack:" + bossLevel, () -> {
            switch (bossLevel) {
                case 1 -> playSfxFile("sfx/lv1.wav");
                case 2 -> playSfxFile("sfx/attack2.wav");
                case 3 -> playSfxFile("sfx/lv3.wav");
                case 4 -> playSfxFile("sfx/lv4.wav");
                case 5 -> playSfxFile("sfx/lv5_2.wav");
                default -> {
                }
            }
        });
    }

    public static void playCoopBossAttackFromLog(String log, int fallbackBossLevel) {
        playCoopBossAttackSfx(parseBossLevelFromLog(log, fallbackBossLevel));
    }

    public static int parseBossLevelFromLog(String log, int fallbackBossLevel) {
        if (log == null || log.isBlank()) {
            return fallbackBossLevel;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("ボス Lv\\.?(\\d+)").matcher(log);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallbackBossLevel;
            }
        }
        return fallbackBossLevel;
    }

    public static boolean playWordAttackFromLog(String log) {
        if (log == null || log.isBlank() || !log.contains("正解")) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("正解 Lv(\\d+)").matcher(log);
        if (!matcher.find()) {
            return false;
        }
        try {
            playWordAttackSfx(Integer.parseInt(matcher.group(1)));
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static void shutdown() {
        stopBgm();
    }

    public static String resolvePath(String relativePath) {
        return resolveAudioFile(relativePath).getPath();
    }

    static byte[] decodeMp3Pcm(File file, AudioFormat[] formatOut) throws IOException {
        bootstrap();
        if (!mp3Available) {
            throw new IOException("MP3 library not available");
        }
        try {
            return Mp3Engine.decodeToPcm(file, formatOut);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("decode failed: " + ex.getMessage(), ex);
        }
    }

    public static boolean assetExists(String relativePath) {
        return resolveExistingAudioFile(relativePath).isFile();
    }

    /** mp3 / wav のどちらかが存在すればその File を返す。 */
    private static File resolveExistingAudioFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return resolveAudioFile("");
        }
        File direct = resolveAudioFile(relativePath);
        if (direct.isFile()) {
            return direct;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            File wav = resolveAudioFile(relativePath.substring(0, relativePath.length() - 4) + ".wav");
            if (wav.isFile()) {
                return wav;
            }
        } else if (lower.endsWith(".wav")) {
            File mp3 = resolveAudioFile(relativePath.substring(0, relativePath.length() - 4) + ".mp3");
            if (mp3.isFile()) {
                return mp3;
            }
        }
        return direct;
    }

    private static File resolveAudioFile(String relativePath) {
        Path root = clientRoot != null ? clientRoot : locateClientRoot();
        String normalized = relativePath.replace('\\', '/');
        return root.resolve(AUDIO_ROOT).resolve(normalized.replace('/', File.separatorChar)).toFile();
    }

    private static Path locateClientRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (hasAudioTree(cwd)) {
            return cwd;
        }
        Path nested = cwd.resolve("client");
        if (hasAudioTree(nested)) {
            return nested;
        }
        Path fromCode = clientRootFromCodeSource();
        if (fromCode != null && hasAudioTree(fromCode)) {
            return fromCode;
        }
        for (Path parent = cwd.getParent(); parent != null; parent = parent.getParent()) {
            if (hasAudioTree(parent)) {
                return parent;
            }
            Path client = parent.resolve("client");
            if (hasAudioTree(client)) {
                return client;
            }
        }
        return cwd;
    }

    private static Path clientRootFromCodeSource() {
        try {
            URL location = GameAudio.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            Path codePath = Path.of(location.toURI()).toAbsolutePath().normalize();
            if (Files.isDirectory(codePath)) {
                Path parent = codePath.getParent();
                if (parent != null && hasAudioTree(parent)) {
                    return parent;
                }
            }
        } catch (URISyntaxException | SecurityException ignored) {
            return null;
        }
        return null;
    }

    private static boolean hasAudioTree(Path root) {
        return root != null && Files.isDirectory(root.resolve(AUDIO_ROOT));
    }

    private static void logMissingOnce(String kind, File file) {
        String path = file.getAbsolutePath();
        if (!loggedMissingPaths.add(path)) {
            return;
        }
        System.err.println("GameAudio: missing " + kind + " -> " + path);
    }

    private static void playMp3OneShotFallback(File file) {
        Thread thread = new Thread(() -> {
            try {
                Mp3Engine.playFile(file);
            } catch (Exception ex) {
                System.err.println("GameAudio: MP3 SFX failed " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
            }
        }, "game-audio-mp3-sfx-fallback");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playSfxOnce(String dedupKey, Runnable play) {
        long now = System.currentTimeMillis();
        if (dedupKey.equals(lastSfxDedupKey) && now - lastSfxDedupAtMs < 400L) {
            return;
        }
        lastSfxDedupKey = dedupKey;
        lastSfxDedupAtMs = now;
        play.run();
    }

    private static File resolveBgmFile(BgmTrack track) {
        return resolveExistingAudioFile(track.basePath() + ".mp3");
    }

    private static boolean isBgmPlaying() {
        return currentBgmClip != null && currentBgmClip.isRunning();
    }

    private static void stopBgmInternal() {
        if (currentBgmClip != null) {
            currentBgmClip.stop();
            currentBgmClip.close();
            currentBgmClip = null;
        }
        currentBgmTrack = null;
    }

    private static Clip openPlayableClip(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            if (!mp3Available) {
                logMissingOnce("BGM (MP3 library)", file);
                return null;
            }
            try {
                return Mp3SfxPlayer.openClip(file);
            } catch (Exception ex) {
                System.err.println("GameAudio: failed to decode BGM " + file.getAbsolutePath()
                        + " (" + ex.getMessage() + ")");
                return null;
            }
        }
        return openClipFile(file);
    }

    private static Clip openClipFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
            System.err.println("GameAudio: failed to load " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
            return null;
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0f, Math.min(1f, volume));
        float gainDb = linear <= 0.0001f ? control.getMinimum() : (float) (20.0 * Math.log10(linear));
        gainDb = Math.max(control.getMinimum(), Math.min(control.getMaximum(), gainDb));
        control.setValue(gainDb);
    }

    private static float parseVolume(String raw, float fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 実行時に lib/jlayer-1.0.1.jar を読み込んで MP3 を再生する。 */
    private static final class Mp3Engine {
        private static Class<?> playerClass;
        private static ClassLoader classLoader;
        private static boolean initAttempted;
        private static boolean available;

        private Mp3Engine() {
        }

        static boolean ensureReady(Path root) {
            if (initAttempted) {
                return available;
            }
            initAttempted = true;
            try {
                playerClass = Class.forName("javazoom.jl.player.Player");
                classLoader = GameAudio.class.getClassLoader();
                available = true;
                return true;
            } catch (ClassNotFoundException ignored) {
                // try lib/jlayer-1.0.1.jar next
            }

            Path jar = root.resolve("lib/jlayer-1.0.1.jar");
            if (!Files.isRegularFile(jar)) {
                available = false;
                return false;
            }
            try {
                URI jarUri = jar.toUri();
                URLClassLoader loader = new URLClassLoader(
                        new URL[] { jarUri.toURL() },
                        GameAudio.class.getClassLoader()
                );
                playerClass = Class.forName("javazoom.jl.player.Player", true, loader);
                classLoader = loader;
                available = true;
                return true;
            } catch (Exception ex) {
                System.err.println("GameAudio: failed to load MP3 library " + jar.toAbsolutePath()
                        + " (" + ex.getMessage() + ")");
                available = false;
                return false;
            }
        }

        static byte[] decodeToPcm(File file, AudioFormat[] formatOut) throws Exception {
            if (!available || classLoader == null) {
                throw new IllegalStateException("MP3 library not available");
            }
            Class<?> bitstreamClass = Class.forName("javazoom.jl.decoder.Bitstream", true, classLoader);
            Class<?> decoderClass = Class.forName("javazoom.jl.decoder.Decoder", true, classLoader);
            Class<?> headerClass = Class.forName("javazoom.jl.decoder.Header", true, classLoader);

            try (FileInputStream input = new FileInputStream(file);
                 BufferedInputStream buffered = new BufferedInputStream(input, 65536)) {
                Object bitstream = bitstreamClass.getConstructor(InputStream.class).newInstance(buffered);
                Object decoder = decoderClass.getConstructor().newInstance();
                ByteArrayOutputStream pcm = new ByteArrayOutputStream();
                Object firstHeader = null;
                java.lang.reflect.Method readFrame = bitstreamClass.getMethod("readFrame");
                java.lang.reflect.Method decodeFrame = decoderClass.getMethod("decodeFrame", headerClass, bitstreamClass);
                java.lang.reflect.Method closeFrame = bitstreamClass.getMethod("closeFrame");

                Object header;
                while ((header = readFrame.invoke(bitstream)) != null) {
                    if (firstHeader == null) {
                        firstHeader = header;
                    }
                    Object output = decodeFrame.invoke(decoder, header, bitstream);
                    Class<?> sampleBufferClass = output.getClass();
                    short[] buffer = (short[]) sampleBufferClass.getMethod("getBuffer").invoke(output);
                    int len = (Integer) sampleBufferClass.getMethod("getBufferLength").invoke(output);
                    for (int i = 0; i < len; i++) {
                        short sample = buffer[i];
                        pcm.write(sample & 0xFF);
                        pcm.write((sample >> 8) & 0xFF);
                    }
                    closeFrame.invoke(bitstream);
                }
                bitstreamClass.getMethod("close").invoke(bitstream);
                if (firstHeader == null) {
                    throw new IOException("empty MP3");
                }
                int singleChannel = headerClass.getField("SINGLE_CHANNEL").getInt(null);
                int mode = (Integer) headerClass.getMethod("mode").invoke(firstHeader);
                int channels = mode == singleChannel ? 1 : 2;
                float frequency = ((Number) headerClass.getMethod("frequency").invoke(firstHeader)).floatValue();
                formatOut[0] = new AudioFormat(frequency, 16, channels, true, false);
                return pcm.toByteArray();
            }
        }

        static void playFile(File file) throws Exception {
            if (!available || playerClass == null) {
                throw new IllegalStateException("MP3 library not available");
            }
            Object player = null;
            try (FileInputStream input = new FileInputStream(file);
                 BufferedInputStream buffered = new BufferedInputStream(input, 65536)) {
                player = openPlayer(buffered);
                playBlocking(player);
            } finally {
                closePlayer(player);
            }
        }

        static Object openPlayer(InputStream input) throws Exception {
            return playerClass.getConstructor(InputStream.class).newInstance(input);
        }

        static void playBlocking(Object player) throws Exception {
            playerClass.getMethod("play").invoke(player);
        }

        static void closePlayer(Object player) {
            if (player == null) {
                return;
            }
            try {
                playerClass.getMethod("close").invoke(player);
            } catch (ReflectiveOperationException ignored) {
                // jlayer 1.0.1 may not expose close()
            }
        }
    }
}
