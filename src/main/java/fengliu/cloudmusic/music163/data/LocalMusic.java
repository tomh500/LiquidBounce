package fengliu.cloudmusic.music163.data;

import fengliu.cloudmusic.music163.IMusic;
import javax.sound.sampled.AudioFileFormat;
import java.io.File;
import javax.sound.sampled.AudioSystem;

/** A local audio file exposed through the same player queue contract. */
public final class LocalMusic implements IMusic {
    private final File file;
    private final long duration;

    public LocalMusic(File file) {
        this.file = file;
        this.duration = readDuration(file);
    }

    private static long readDuration(File file) {
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
    @Override public String getPicUrl() { return ""; }
    @Override public String getPlayUrl() { return file.toURI().toString(); }
    @Override public long getDuration() { return duration; }
    @Override public void printToChatHud(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) { }
}
