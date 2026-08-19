package fengliu.cloudmusic.music163.data;

import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.music163.Music163;
import fengliu.cloudmusic.util.M4aAudio;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import javax.sound.sampled.AudioFileFormat;
import java.io.File;
import javax.sound.sampled.AudioSystem;

/** A local audio file exposed through the same player queue contract. */
public final class LocalMusic implements IMusic {
    private final File file;
    private final long duration;
    private final String embeddedLyrics;
    private final byte[] embeddedCover;
    private volatile Music matchedMusic;

    public LocalMusic(File file) {
        this.file = file;
        this.duration = readDuration(file);
        var metadata = readMetadata(file);
        this.embeddedLyrics = metadata.lyrics;
        this.embeddedCover = metadata.cover;
    }

    private record Metadata(String lyrics, byte[] cover) { }

    private static Metadata readMetadata(File file) {
        try {
            var tag = AudioFileIO.read(file).getTag();
            if (tag == null) return new Metadata("", null);
            var artwork = tag.getFirstArtwork();
            return new Metadata(tag.getFirst(FieldKey.LYRICS), artwork == null ? null : artwork.getBinaryData());
        } catch (Exception ignored) {
            return new Metadata("", null);
        }
    }

    private static long readDuration(File file) {
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".m4a")) {
            try {
                long duration = M4aAudio.durationMs(file);
                if (duration > 0) return duration;
            } catch (Exception ignored) { }
        }
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);
            Object duration = fileFormat.properties().get("duration");
            if (duration instanceof Long microseconds && microseconds > 0) {
                return microseconds / 1000L;
            }
            var format = fileFormat.getFormat();
            if (fileFormat.getFrameLength() > 0 && format.getFrameRate() > 0) {
                return (long) (fileFormat.getFrameLength() * 1000d / format.getFrameRate());
            }
        } catch (Exception ignored) { }
        try (var stream = AudioSystem.getAudioInputStream(file)) {
            var format = stream.getFormat();
            if (stream.getFrameLength() > 0 && format.getFrameRate() > 0) {
                return (long) (stream.getFrameLength() * 1000d / format.getFrameRate());
            }
        } catch (Exception ignored) { }
        return 0L;
    }

    @Override public long getId() { return file.getAbsolutePath().hashCode() & 0xffffffffL; }
    @Override public String getName() { return file.getName().replaceFirst("\\.[^.]+$", ""); }
    @Override public String getPicUrl() { return matchedMusic == null ? "" : matchedMusic.getPicUrl(); }
    @Override public String getPlayUrl() { return file.toURI().toString(); }
    @Override public long getDuration() { return duration; }
    @Override public void printToChatHud(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) { }

    public String getEmbeddedLyrics() { return embeddedLyrics; }
    public byte[] getEmbeddedCover() { return embeddedCover; }

    /** Searches NetEase once for an exact title match before falling back to embedded metadata. */
    public synchronized Music resolveNetEaseMatch(Music163 api) {
        if (matchedMusic != null) return matchedMusic;
        try {
            JsonObject response = api.search(getName(), 1, 1, 8);
            JsonObject result = response.getAsJsonObject("result");
            if (result == null) return null;
            JsonArray songs = result.getAsJsonArray("songs");
            if (songs == null) return null;
            String expected = normalizeTitle(getName());
            for (var entry : songs) {
                if (!entry.isJsonObject()) continue;
                JsonObject song = entry.getAsJsonObject();
                if (song.has("name") && normalizeTitle(song.get("name").getAsString()).equals(expected)) {
                    matchedMusic = new Music(api.getHttpClient(), song, null);
                    return matchedMusic;
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static String normalizeTitle(String value) {
        return value.replaceAll("[\\s\\p{Punct}（）【】]+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
