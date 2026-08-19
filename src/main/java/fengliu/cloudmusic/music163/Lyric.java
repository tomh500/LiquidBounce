package fengliu.cloudmusic.music163;

import com.google.gson.JsonObject;
import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.util.MusicPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 滚动歌词
 */
public class Lyric implements Runnable{
    private static final Logger LOGGER = LoggerFactory.getLogger("cloudmusic");
    /**
     * 网易云 LRC 开头常见的制作信息行(作词/作曲/编曲等), 展示时跳过, 避免歌曲刚播放时占用歌词位置
     */
    private static final Pattern METADATA_LINE = Pattern.compile("^\\s*(作词|作曲|编曲|制作人|制作|混音|母带|录音|监制|和声|和声编写|吉他|贝斯|鼓|键盘|弦乐|小提琴|大提琴|钢琴|手风琴|打击乐|鼓手|贝斯手|吉他手|键盘手|OP|SP|出品|发行|企划|统筹|配唱|视觉|设计|文案)\\s*[:：]");

    private final Map<Long, String> lyric;
    private final Map<Long, String> tlyric;
    private volatile boolean loopIn = true;
    private volatile boolean load = true;
    private volatile String[] toLyric = {};

    /**
     * 将歌词时间字符串转换为毫秒
     *
     * 网易云返回的 LRC 时间戳是三位毫秒格式(如 [00:12.345]),
     * 这里兼容 1/2/3 位小数, 之前的实现按两位厘秒 *10 计算,
     * 会把三位毫秒的时间算大 0~9 秒, 导致歌词显示明显慢于歌曲
     *
     * @param n 歌词时间字符串
     * @return 歌词时间毫秒
     */
    public static long timeStrToTime(String n){
        try{
            String[] timeStr = n.split(":");
            String[] secondStr = timeStr[1].split("\\.");

            int minute = Integer.parseInt(timeStr[0]) * 60 * 1000;
            int second = Integer.parseInt(secondStr[0]) * 1000;

            int millisecond;
            String fraction = secondStr.length > 1 ? secondStr[1] : "";
            if (fraction.length() >= 3){
                millisecond = Integer.parseInt(fraction.substring(0, 3));
            } else if (fraction.length() == 2){
                millisecond = Integer.parseInt(fraction) * 10;
            } else if (fraction.length() == 1){
                millisecond = Integer.parseInt(fraction) * 100;
            } else {
                millisecond = 0;
            }

            return minute + second + millisecond;
        }catch (Exception err){
            return 0;
        }
    }

    /**
     * 处理歌词字符串
     * @param lyric 歌词字符串
     * @return 歌词 Map
     */
    public static Map<Long, String> lyricToMap(String lyric){
        Map<Long, String> lyricMap = new LinkedHashMap<>();
        for (String lyricRow : lyric.split("\n")) {
            try {
                String[] lyricRows = lyricRow.substring(1).split("]", 2);
                if (lyricRows.length < 2){
                    continue;
                }

                String lyricData = lyricRows[1];
                if (METADATA_LINE.matcher(lyricData).find()){
                    continue;
                }

                lyricMap.put(timeStrToTime(lyricRows[0]), lyricData);
            }catch(Exception err){
                continue;
            }
        }
        return lyricMap;
    }

    public Lyric(JsonObject data){
        String lyric = "";
        if (data != null && data.has("lrc") && data.get("lrc").isJsonObject()) {
            JsonObject lrc = data.getAsJsonObject("lrc");
            if (lrc.has("lyric") && !lrc.get("lyric").isJsonNull()) lyric = lrc.get("lyric").getAsString();
        }
        if(lyric.equals("")){
            this.lyric = new LinkedHashMap<>();
            this.tlyric = this.lyric;
            return;
        }

        this.lyric = lyricToMap(lyric);
        if(!data.has("tlyric") || !data.get("tlyric").isJsonObject()){
            this.tlyric = new LinkedHashMap<>();
            return;
        }

        JsonObject translation = data.getAsJsonObject("tlyric");
        String tlyric = translation.has("lyric") && !translation.get("lyric").isJsonNull()
                ? translation.get("lyric").getAsString() : "";
        if(tlyric.equals("")){
            this.tlyric = new LinkedHashMap<>();
            return;
        }

        this.tlyric = lyricToMap(tlyric);
    }

    public static Lyric fromLrc(String lyric) {
        JsonObject data = new JsonObject();
        JsonObject lrc = new JsonObject();
        lrc.addProperty("lyric", lyric == null ? "" : lyric);
        data.add("lrc", lrc);
        return new Lyric(data);
    }

    public boolean hasLyrics() {
        return !this.lyric.isEmpty();
    }

    @Override
    public void run() {
        if(this.lyric.isEmpty()){
            return;
        }

        MusicPlayer player = MusicCommand.getPlayer();
        List<Map.Entry<Long, String>> entries = new ArrayList<>(this.lyric.entrySet());
        LOGGER.info("[CloudMusic][Lyric] 歌词线程启动, 有效歌词行数={}", entries.size());

        int index = -1;
        while (this.loopIn) {
            synchronized(this){
                while (!this.load && this.loopIn) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }

            if (!this.loopIn) {
                break;
            }

            long time = player.getPlayingProgress();
            // 当前应显示的行 = 时间戳 <= 当前进度的最后一行
            int targetIndex = index;
            if (index < 0 || time < entries.get(index).getKey()) {
                // 刚开始播放或往回调(跳转): 从头找当前应显示的行
                targetIndex = -1;
                while (targetIndex + 1 < entries.size() && time >= entries.get(targetIndex + 1).getKey()) {
                    targetIndex++;
                }
            } else {
                while (targetIndex + 1 < entries.size() && time >= entries.get(targetIndex + 1).getKey()) {
                    targetIndex++;
                }
            }

            if (targetIndex != index && targetIndex >= 0){
                index = targetIndex;
                Map.Entry<Long, String> entry = entries.get(index);
                String tlyric = this.tlyric.get(entry.getKey());
                this.toLyric = (tlyric == null) ? new String[]{entry.getValue()} : new String[]{entry.getValue(), tlyric};
                LOGGER.info("[CloudMusic][Lyric] 显示歌词 (时间={}ms, 行={}): {}", entry.getKey(), index + 1, entry.getValue().length() > 20 ? entry.getValue().substring(0, 20) + "..." : entry.getValue());
            }

            if (!this.loopIn) {
                break;
            }

            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                return;
            }
        }

        LOGGER.info("[CloudMusic][Lyric] 歌词线程结束");
    }

    /**
     * 获取当前滚动到的歌词
     * @return 歌词
     */
    public String[] getToLyric() {
        return toLyric;
    }

    /**
     * Returns a fixed-size lyric window centered on the line active at {@code progressMs}.
     * The music screen uses this snapshot instead of guessing a lyric line from wall-clock time.
     */
    public String[] getWindow(long progressMs, int before, int after) {
        return getWindow(progressMs, before, after, 0);
    }

    public String[] getWindow(long progressMs, int before, int after, int lineOffset) {
        if (lyric.isEmpty()) {
            return new String[0];
        }

        List<Map.Entry<Long, String>> entries = new ArrayList<>(lyric.entrySet());
        int current = currentIndex(entries, progressMs) + lineOffset;

        String[] window = new String[before + after + 1];
        for (int offset = -before; offset <= after; offset++) {
            int index = current + offset;
            if (index >= 0 && index < entries.size()) {
                window[offset + before] = entries.get(index).getValue();
            } else {
                window[offset + before] = "";
            }
        }
        return window;
    }

    public long[] getWindowTimes(long progressMs, int before, int after) {
        return getWindowTimes(progressMs, before, after, 0);
    }

    public long[] getWindowTimes(long progressMs, int before, int after, int lineOffset) {
        if (lyric.isEmpty()) return new long[0];
        List<Map.Entry<Long, String>> entries = new ArrayList<>(lyric.entrySet());
        int current = currentIndex(entries, progressMs) + lineOffset;
        long[] window = new long[before + after + 1];
        for (int offset = -before; offset <= after; offset++) {
            int index = current + offset;
            window[offset + before] = index >= 0 && index < entries.size() ? entries.get(index).getKey() : -1L;
        }
        return window;
    }

    public String[] getTranslationWindow(long progressMs, int before, int after) {
        return getTranslationWindow(progressMs, before, after, 0);
    }

    public String[] getTranslationWindow(long progressMs, int before, int after, int lineOffset) {
        if (lyric.isEmpty()) return new String[0];
        List<Map.Entry<Long, String>> entries = new ArrayList<>(lyric.entrySet());
        int current = currentIndex(entries, progressMs) + lineOffset;
        String[] window = new String[before + after + 1];
        for (int offset = -before; offset <= after; offset++) {
            int index = current + offset;
            if (index < 0 || index >= entries.size()) {
                window[offset + before] = "";
            } else {
                String translation = tlyric.get(entries.get(index).getKey());
                window[offset + before] = translation == null ? "" : translation;
            }
        }
        return window;
    }

    private static int currentIndex(List<Map.Entry<Long, String>> entries, long progressMs) {
        int current = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey() > progressMs) break;
            current = i;
        }
        return current;
    }

    /**
     * 开始歌词滚动
     */
    public void start(){
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.setName("CloudMusicLyric thread");
        thread.start();
    }

    /**
     * 退出歌词滚动
     */
    public void exit(){
        this.loopIn = false;
        synchronized(this){
            this.load = true;
            notifyAll();
        }
    }

    /**
     * 暂停歌词滚动
     */
    public void stop(){
        synchronized(this){
            this.load = false;
            notifyAll();
        }
    }

    /**
     * 继续歌词滚动
     */
    public void continues(){
        synchronized(this){
            this.load = true;
            notifyAll();
        }
    }
}
