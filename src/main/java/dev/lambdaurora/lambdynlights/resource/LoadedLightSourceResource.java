/*
 * Copyright © 2024 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the Lambda License. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdynlights.resource;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/**
 * Represents a partially loaded item light source awaiting full load once registries are known.
 *
 * @param id the identifier of the item light source
 * @param data the data to fully load
 * @param silenceError {@code true} if load errors should be silenced, or {@code false} otherwise
 * @author LambdAurora
 * @version 4.0.0
 * @since 3.0.0
 */
public record LoadedLightSourceResource(Identifier id, JsonObject data, boolean silenceError) {}
