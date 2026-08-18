package fengliu.cloudmusic.render;

import fengliu.cloudmusic.CloudMusicClient;
import fengliu.cloudmusic.music163.IMusic;
import fengliu.cloudmusic.util.HttpClient;
import fengliu.cloudmusic.util.PNGConverter;
import fengliu.cloudmusic.util.QRCode;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;

public class MusicIconTexture {
    private static final Minecraft client = Minecraft.getInstance();
    public static Identifier MUSIC_ICON_ID = Identifier.fromNamespaceAndPath(CloudMusicClient.MOD_ID, "texture/music_icon.png");
    public static Identifier MUSIC_CIRCLE_ICON_ID = Identifier.fromNamespaceAndPath(CloudMusicClient.MOD_ID, "texture/music_circle_icon.png");
    public static Identifier LIQUID_BOUNCE_ICON_ID = Identifier.fromNamespaceAndPath(CloudMusicClient.MOD_ID, "texture/liquidbounce_icon.png");
    public static Identifier QR_CODE_ID = Identifier.fromNamespaceAndPath(CloudMusicClient.MOD_ID, "qr.code");
    private static boolean canUseIcon = false;
    private static boolean canUseLiquidBounceIcon = false;

    public static void loadLiquidBounceIcon() {
        if (canUseLiquidBounceIcon) {
            return;
        }

        try (var stream = MusicIconTexture.class.getResourceAsStream("/resources/liquidbounce/icon_64x64.png")) {
            NativeImage image = NativeImage.read(Objects.requireNonNull(stream));
            client.getTextureManager().register(LIQUID_BOUNCE_ICON_ID, new DynamicTexture(() -> "cloudmusic liquidbounce icon", image));
            canUseLiquidBounceIcon = true;
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public static boolean canUseLiquidBounceIcon() {
        return canUseLiquidBounceIcon;
    }

    /**
     * 获取封面并注册材质
     *
     * @param music 歌曲
     */
    public static void getMusicIcon(IMusic music) {
        Thread commandThread = new Thread(() -> {
            NativeImage img;
            NativeImage circularImg;
            try {
                img = NativeImage.read(PNGConverter.convertJPEGtoPNG(HttpClient.downloadStream(music.getPicUrl() + "?param=128y128")));
                circularImg = copy(img);
                cropToCircle(circularImg);
            } catch (Exception err) {
                err.printStackTrace();
                return;
            }

            client.execute(() -> {
                DynamicTexture imageTexture = new DynamicTexture(() -> "cloudmusic texture", img);
                DynamicTexture circularTexture = new DynamicTexture(() -> "cloudmusic circular texture", circularImg);
                client.getTextureManager().register(MUSIC_ICON_ID, imageTexture);
                client.getTextureManager().register(MUSIC_CIRCLE_ICON_ID, circularTexture);
                canUseIcon = true;
            });
        });
        commandThread.setDaemon(true);
        commandThread.setName("CloudMusic getMusicIcon Thread");
        commandThread.start();
    }

    public static void getQRCode(String QRCodeData) {
        NativeImage img;
        try {
            File QRCodeFile = QRCode.generateQRCode(QRCodeData, CloudMusicClient.MC_PATH + "/cloud_music_qrcode.png", 128, 128, "png");
            img = NativeImage.read(new FileInputStream(QRCodeFile));
        } catch (Exception err) {
            err.printStackTrace();
            return;
        }

        client.execute(() -> {
            DynamicTexture imageTexture = new DynamicTexture(() -> "cloudmusic texture", img);
            client.getTextureManager().register(QR_CODE_ID, imageTexture);
        });
    }

    public static boolean canUseIcon() {
        return canUseIcon;
    }

    /**
     * The Dynamic Island uses a real circular album image. Apply the alpha mask
     * before uploading the dynamic texture, so all GUI render paths preserve it.
     */
    private static void cropToCircle(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float radius = Math.min(width, height) / 2.0F;
        float centerX = (width - 1) / 2.0F;
        float centerY = (height - 1) / 2.0F;
        float radiusSquared = radius * radius;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = x - centerX;
                float dy = y - centerY;
                if (dx * dx + dy * dy > radiusSquared) {
                    image.setPixel(x, y, 0);
                }
            }
        }
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage target = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                target.setPixel(x, y, source.getPixel(x, y));
            }
        }
        return target;
    }
}
