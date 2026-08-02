/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the Lambda License. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdynlights.api;

/**
 * Represents the entrypoint for LambDynamicLights' API.
 *
 * @author LambdAurora
 * @version 4.0.0
 * @since 1.3.2
 */
public interface DynamicLightsInitializer {
	/**
	 * The entrypoint key for LambDynamicLights' API, whose value is {@value}.
	 *
	 * @since 4.0.0
	 */
	String ENTRYPOINT_KEY = "lambdynlights:initializer";

	/**
	 * Called when LambDynamicLights is initialized to register various objects related to dynamic lighting such as:
	 * <ul>
	 *     <li>entity luminance providers;</li>
	 *     <li>item and entity light sources;</li>
	 *     <li>custom dynamic lighting behavior.</li>
	 * </ul>
	 *
	 * @param context the dynamic lights context, containing references to managers for each source type provided by the API
	 */
	void onInitializeDynamicLights(DynamicLightsContext context);
}
