/*
 * Copyright © 2024 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the Lambda License. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdynlights.mixin.lightsource;

import dev.lambdaurora.lambdynlights.LambDynLights;
import dev.lambdaurora.lambdynlights.echo.TheEndGatewayBeamLightBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndGatewayBlockEntity.class)
public class TheEndGatewayBlockEntityMixin extends BlockEntity {
	@Unique
	private TheEndGatewayBeamLightBehavior dynamicLightBeam;

	public TheEndGatewayBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "beamAnimationTick", at = @At("RETURN"))
	private static void lambdynlights$onBeamAnimationTick(
			Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity gateway, CallbackInfo ci
	) {
		var specialGateway = (TheEndGatewayBlockEntityMixin) (Object) gateway;

		if (!LambDynLights.get().config.getBeamLighting().get()) {
			if (specialGateway.dynamicLightBeam != null) {
				LambDynLights.get().dynamicLightBehaviorManager().remove(specialGateway.dynamicLightBeam);
				specialGateway.dynamicLightBeam = null;
			}

			return;
		}

		boolean beamExists = gateway.isSpawning() || gateway.isCoolingDown();

		if (beamExists && specialGateway.dynamicLightBeam == null) {
			specialGateway.dynamicLightBeam = new TheEndGatewayBeamLightBehavior(gateway, level);
			LambDynLights.get().dynamicLightBehaviorManager().add(specialGateway.dynamicLightBeam);
		} else if (!beamExists && specialGateway.dynamicLightBeam != null) {
			LambDynLights.get().dynamicLightBehaviorManager().remove(specialGateway.dynamicLightBeam);
			specialGateway.dynamicLightBeam = null;
		}
	}
}
