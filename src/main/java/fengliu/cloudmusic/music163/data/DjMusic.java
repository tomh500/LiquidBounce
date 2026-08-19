package fengliu.cloudmusic.music163.data;

import com.google.gson.JsonObject;
import fengliu.cloudmusic.config.Configs;
import fengliu.cloudmusic.music163.*;
import fengliu.cloudmusic.util.HttpClient;
import fengliu.cloudmusic.util.TextClickItem;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class DjMusic extends Music163Obj implements IMusic, ICanComment {
    private final HttpClient api;
    public final long id;
    public final long mainTrackId;
    public final String name;
    public final JsonObject dj;
    public final JsonObject radio;
    public final String coverUrl;
    public final long listenerCount;
    public final long likedCount;
    public final String[] description;
    public final long duration;
    public final String threadId;
    private String resolvedQuality = Configs.PLAY.PLAY_QUALITY.getStringValue();

    /**
     * 初始化对象
     *
     * @param api  HttpClient api
     * @param data 对象数据
     */
    public DjMusic(HttpClient api, JsonObject data) {
        super(api, data);
        this.api = api;
        this.id = data.get("id").getAsLong();
        this.mainTrackId = data.get("mainTrackId").getAsLong();
        this.name = data.get("name").getAsString();
        this.dj = data.getAsJsonObject("dj");
        this.radio = data.getAsJsonObject("radio");
        this.coverUrl = data.get("coverUrl").getAsString();
        this.listenerCount = data.get("listenerCount").getAsLong();
        if (data.has("likedCount")){
            this.likedCount = data.get("likedCount").getAsLong();
        } else {
            this.likedCount = 0;
        }

        this.description = data.get("description").getAsString().split("\n");
        this.duration = data.get("duration").getAsLong();
        this.threadId = "A_DJ_1_%s".formatted(this.id);
    }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getPicUrl() {
        return this.coverUrl;
    }

    @Override
    public String getPlayUrl(){
        Music.PlayUrl playUrl = Music.resolvePlayUrl(this.api, this.mainTrackId);
        if (playUrl == null) {
            throw new ActionException(Component.translatable("cloudmusic.exception.music.get.url", this.name));
        }
        this.resolvedQuality = playUrl.quality();
        return playUrl.url();
    }

    public String getResolvedQuality() { return resolvedQuality; }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public String getThreadId() {
        return this.threadId;
    }

    @Override
    public HttpClient getApi() {
        return this.api;
    }

    @Override
    public void printToChatHud(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(""));

        source.sendFeedback(Component.literal(this.name));

        source.sendFeedback(Component.literal(""));

        source.sendFeedback(new TextClickItem(
                "info.dj.music.radio",
                "/rikkamusic dj " + this.radio.get("id").getAsLong()
        ).append("§b" + this.radio.get("name").getAsString()).build());

        source.sendFeedback(new TextClickItem(
                "info.dj.creator",
                "/rikkamusic user " + this.dj.get("userId").getAsLong()
        ).append("§b" + this.dj.get("nickname").getAsString()).build());

        source.sendFeedback(Component.translatable("cloudmusic.info.dj.music.count", this.listenerCount, this.likedCount));
        source.sendFeedback(Component.translatable("cloudmusic.info.dj.music.duration", this.getDurationToString()));
        source.sendFeedback(Component.translatable("cloudmusic.info.dj.music.id", this.mainTrackId));
        source.sendFeedback(Component.translatable("cloudmusic.info.dj.id", this.id));

        if (this.description != null) {
            source.sendFeedback(Component.literal(""));
            for (String row : this.description) {
                source.sendFeedback(Component.literal("§7" + row));
            }
        }

        source.sendFeedback(TextClickItem.combine(
                new TextClickItem("play", "/rikkamusic dj music play " + this.id),
                new TextClickItem("send.comment", "/rikkamusic dj music send comment " + this.id),
                new TextClickItem("hot.comment", "/rikkamusic dj music hotComment " + this.id),
                new TextClickItem("comment", "/rikkamusic dj music comment " + this.id),
                new TextClickItem("shar", Shares.DJ_MUSIC.getShar(this.id))
        ));
    }
}
