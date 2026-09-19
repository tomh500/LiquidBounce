/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.clutch;

import net.ccbluex.liquidbounce.utils.entity.FallingPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** Finds a catch along the fall, rather than waiting until the distant ground is within reach. */
public final class ClutchTargetPlanner {
    private static final double EDGE_EPSILON = 1.0E-4;

    private ClutchTargetPlanner() {
    }

    public record CatchTarget(BlockPos pos, int ticksUntilImpact) {
    }

    public record Plan(CatchTarget target, List<BlockPos> placements) {
    }

    /**
     * A block can catch the player only when their descending feet cross its top surface.
     * Use the footprint before movement: vanilla resolves the downward collision before horizontal travel.
     */
    public static List<CatchTarget> catchTargets(List<FallingPlayer.MovementFrame> frames, double width) {
        var result = new ArrayList<CatchTarget>();
        var seen = new HashSet<BlockPos>();
        double halfWidth = width * 0.5 - EDGE_EPSILON;
        for (var frame : frames) {
            Vec3 before = frame.getBefore();
            Vec3 after = frame.getAfter();
            if (after.y >= before.y) {
                continue;
            }

            int minX = (int) Math.floor(before.x - halfWidth);
            int maxX = (int) Math.floor(before.x + halfWidth);
            int minZ = (int) Math.floor(before.z - halfWidth);
            int maxZ = (int) Math.floor(before.z + halfWidth);
            int highestTop = (int) Math.floor(before.y + EDGE_EPSILON);
            int lowestTop = (int) Math.ceil(after.y - EDGE_EPSILON);
            for (int top = highestTop; top >= lowestTop; top--) {
                var footprint = new ArrayList<BlockPos>();
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        footprint.add(new BlockPos(x, top - 1, z));
                    }
                }
                // Prefer the part of the landing footprint closest to the position after horizontal travel.
                footprint.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(after)));
                for (var pos : footprint) {
                    if (seen.add(pos)) {
                        result.add(new CatchTarget(pos, frame.getTick() + 1));
                    }
                }
            }
        }
        return result;
    }

    /** Keep an achievable catch while aiming, and budget every placement in its support path. */
    public static @Nullable Plan selectPlan(
        List<CatchTarget> targets,
        @Nullable BlockPos preferredTarget,
        int maxPlacements,
        Function<BlockPos, @Nullable List<BlockPos>> findPath
    ) {
        if (preferredTarget != null) {
            for (var target : targets) {
                if (target.pos().equals(preferredTarget)) {
                    var plan = planFor(target, maxPlacements, findPath);
                    if (plan != null) {
                        return plan;
                    }
                    break;
                }
            }
        }
        for (var target : targets) {
            if (target.pos().equals(preferredTarget)) {
                continue;
            }
            var plan = planFor(target, maxPlacements, findPath);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private static @Nullable Plan planFor(
        CatchTarget target, int maxPlacements, Function<BlockPos, @Nullable List<BlockPos>> findPath
    ) {
        var path = findPath.apply(target.pos());
        if (path == null || path.size() > maxPlacements || path.size() > target.ticksUntilImpact()) {
            return null;
        }
        return new Plan(target, List.copyOf(path));
    }
}
