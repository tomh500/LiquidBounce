package fi.dy.masa.malilib.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.malilib.event.RenderEventHandler;

@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer
{
	@Shadow @Final private LevelTargetBundle targets;
	@Shadow @Final private RenderBuffers renderBuffers;
	@Shadow @Final private GameRenderer gameRenderer;

	// Effected by Improved Transparency
	@Inject(method = "render",
	        at = @At(value = "INVOKE",
	                 target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
	                 shift = At.Shift.AFTER
	        ))
	private void malilib_onRenderWorldPreWeather(GraphicsResourceAllocator resourceAllocator,
	                                             boolean renderOutline, CameraRenderState cameraState,
	                                             GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
	                                             boolean renderBlockOutline,
	                                             CallbackInfo ci,
	                                             @Local(name = "profiler") ProfilerFiller profiler,
	                                             @Local(name = "frame") FrameGraphBuilder frame)
	{
		((RenderEventHandler) RenderEventHandler.getInstance()).runRenderWorldPreWeather(RenderSystem.getModelViewStack(), Minecraft.getInstance(), frame, this.targets, this.gameRenderer.mainCamera().getCullFrustum(), cameraState, this.renderBuffers, terrainFog, fogColor, profiler);
	}

	// 'addLateDebugPass' clears the Depth Texture
	@Inject(method = "render",
	        at = @At(value = "INVOKE",
	                 target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
	                 shift = At.Shift.BEFORE
	        ))
	private void malilib_onRenderWorldLast(GraphicsResourceAllocator resourceAllocator,
	                                       boolean renderOutline, CameraRenderState cameraState,
	                                       GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
	                                       boolean renderBlockOutline,
	                                       CallbackInfo ci,
	                                       @Local(name = "profiler") ProfilerFiller profiler,
	                                       @Local(name = "frame") FrameGraphBuilder frame)
	{
		((RenderEventHandler) RenderEventHandler.getInstance()).runRenderWorldLast(RenderSystem.getModelViewStack(), Minecraft.getInstance(), frame, this.targets, this.gameRenderer.mainCamera().getCullFrustum(), cameraState, this.renderBuffers, terrainFog, fogColor, profiler);
	}
}
