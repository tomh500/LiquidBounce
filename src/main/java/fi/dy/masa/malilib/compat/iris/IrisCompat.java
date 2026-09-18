package fi.dy.masa.malilib.compat.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

/**
 * Iris bridge required by MaLiLib and Litematica mixins.
 *
 * The integrated client owns its render pipelines, so pipeline assignment is
 * intentionally left to the host renderer. Shader state queries remain
 * available to the merged MaLiLib rendering code.
 */
public final class IrisCompat
{
    private IrisCompat()
    {
    }

    public static boolean hasSodium()
    {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    public static boolean hasIris()
    {
        return hasSodium() && FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean isShaderActive()
    {
        return hasIris() && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowPassActive()
    {
        return hasIris() && IrisApi.getInstance().isRenderingShadowPass();
    }

    public static void registerPipelines()
    {
        // LiquidBounce supplies the active render pipeline implementation.
    }
}
