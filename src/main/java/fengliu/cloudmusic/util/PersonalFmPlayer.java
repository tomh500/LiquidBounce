package fengliu.cloudmusic.util;

import fengliu.cloudmusic.command.MusicCommand;
import fengliu.cloudmusic.music163.IMusic;
import java.util.ArrayList;
import java.util.List;

public final class PersonalFmPlayer extends MusicPlayer {
    public PersonalFmPlayer(List<IMusic> initial) { super(new ArrayList<>(initial)); }

    @Override
    protected void extendQueue() {
        if (!notExitFlag) return;
        try {
            var batch = MusicCommand.getMy(false).fm();
            if (!notExitFlag) return;
            if (!batch.isEmpty()) {
                // Keep only the finished track, so continuous FM has bounded memory.
                IMusic finished = playList.get(playIn);
                playList.clear();
                playList.add(finished);
                playList.addAll(batch);
                playIn = 0;
                playListSize = playList.size();
            } else loopPlayIn = false;
        } catch (RuntimeException error) {
            loopPlayIn = false;
            fengliu.cloudmusic.CloudMusicClient.LOGGER.warn("[RikkaMusic] Personal FM could not fetch the next batch");
        }
    }
}
