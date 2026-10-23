package fengliu.cloudmusic.platform;

import com.google.gson.JsonObject;
import fengliu.cloudmusic.music163.IMusic;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** QQ track metadata. Expiring playback URLs and session credentials are never serialized. */
public final class QqMusic implements IMusic {
    private final JsonObject metadata;

    public QqMusic(JsonObject data) {
        metadata = new JsonObject();
        String mid = text(data, "mid");
        if (mid.isEmpty()) mid = text(data, "songmid");
        if (!mid.matches("[A-Za-z0-9]{1,64}")) throw new IllegalArgumentException("QQ 歌曲 MID 无效");
        metadata.addProperty("mid", mid);
        metadata.addProperty("id", data.has("id") ? data.get("id").getAsLong() : data.get("songid").getAsLong());
        metadata.addProperty("name", data.has("title") ? text(data, "title")
                : data.has("name") ? text(data, "name") : text(data, "songname"));
        metadata.addProperty("interval", data.get("interval").getAsLong());
        String artist = text(data, "artist");
        if (data.has("singer")) {
            var names = new java.util.ArrayList<String>();
            for (var singer : data.getAsJsonArray("singer")) names.add(text(singer.getAsJsonObject(), "name"));
            artist = String.join(" / ", names);
        }
        metadata.addProperty("artist", artist);
        JsonObject album = data.has("album") && data.get("album").isJsonObject() ? data.getAsJsonObject("album") : data;
        metadata.addProperty("albumName", album == data ? text(data, "albumName") : text(album, "name"));
        metadata.addProperty("albumMid", album == data ? text(data, "albumMid") : text(album, "mid"));
        if (text(metadata, "albumName").isEmpty()) metadata.addProperty("albumName", text(data, "albumname"));
        if (text(metadata, "albumMid").isEmpty()) metadata.addProperty("albumMid", text(data, "albummid"));
        String mediaMid = data.has("file") ? text(data.getAsJsonObject("file"), "media_mid") : text(data, "mediaMid");
        metadata.addProperty("mediaMid", mediaMid.matches("[A-Za-z0-9]{1,64}") ? mediaMid : mid);
    }

    static String text(JsonObject object, String key) {
        var value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    public JsonObject metadata() { return metadata.deepCopy(); }
    public String getArtist() { return text(metadata, "artist"); }
    public String getAlbum() { return text(metadata, "albumName"); }
    public String getMediaMid() { return text(metadata, "mediaMid"); }
    @Override public String getSource() { return "qq"; }
    @Override public String getSourceId() { return text(metadata, "mid"); }
    @Override public long getId() { return metadata.get("id").getAsLong(); }
    @Override public String getName() { return text(metadata, "name"); }
    @Override public long getDuration() { return metadata.get("interval").getAsLong() * 1000; }
    @Override public String getPicUrl() {
        String mid = text(metadata, "albumMid");
        return mid.matches("[A-Za-z0-9]{1,64}") ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + mid + ".jpg" : "";
    }
    @Override public String getPlayUrl() { return QqMusicApi.playUrl(this); }
    @Override public void printToChatHud(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(getName() + " · " + getArtist() + " [QQ 音乐]"));
    }
}
