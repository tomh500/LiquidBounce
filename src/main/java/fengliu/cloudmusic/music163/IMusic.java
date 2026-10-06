package fengliu.cloudmusic.music163;

import fengliu.cloudmusic.util.Time;

public interface IMusic extends IPrint{
    long getId();
    String getName();
    String getPicUrl();
    String getPlayUrl();
    long getDuration();

    default String getSource() { return "netease"; }
    default String getSourceId() { return Long.toString(getId()); }
    default String getKey() { return getSource() + ":" + getSourceId(); }

    /**
     * 获取歌曲长度 (秒)
     * @return 秒
     */
    default int getDurationSecond(){
        return (int) (getDuration() / 1000);
    }

    /**
     * 获取歌曲长度 (字符串)
     * @return 歌曲长度字符串 (格式 "分:秒")
     */
    default String getDurationToString(){
        return Time.secondToString(this.getDurationSecond());
    }
}
