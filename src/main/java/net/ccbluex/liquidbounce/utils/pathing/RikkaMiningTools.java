package net.ccbluex.liquidbounce.utils.pathing;

import baritone.utils.ToolSet;
import baritone.api.BaritoneAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/** One tool policy for both path estimates and actual Rikka mining. */
public final class RikkaMiningTools {
    private RikkaMiningTools() { }

    public record Candidate(int slot, boolean harvests, double progress, int fortune, int silk,
                            int materialCost, boolean protectedTool) { }

    public static Candidate choose(Iterable<Candidate> candidates, boolean resource, boolean preferSilk) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.protectedTool || candidate.progress <= 0 || Double.isNaN(candidate.progress)
                || resource && !candidate.harvests) {
                continue;
            }
            if (best == null || better(candidate, best, resource, preferSilk)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(Candidate candidate, Candidate best, boolean resource, boolean preferSilk) {
        if (candidate.harvests != best.harvests) return candidate.harvests;
        boolean candidateEffective = candidate.materialCost != Integer.MAX_VALUE;
        boolean bestEffective = best.materialCost != Integer.MAX_VALUE;
        if (candidateEffective != bestEffective) return candidateEffective;
        if (resource) {
            // Silk explicitly requested: prefer it. Otherwise preserve fortune-compatible drops.
            int candidateHarvest = preferSilk ? candidate.silk : candidate.silk > 0 ? -1 : candidate.fortune;
            int bestHarvest = preferSilk ? best.silk : best.silk > 0 ? -1 : best.fortune;
            if (candidateHarvest != bestHarvest) return candidateHarvest > bestHarvest;
            if (candidate.progress != best.progress) return candidate.progress > best.progress;
        } else {
            // Reserve enchanted harvesting tools when an expendable effective tool is available.
            boolean candidateValuable = candidate.fortune > 0 || candidate.silk > 0;
            boolean bestValuable = best.fortune > 0 || best.silk > 0;
            if (candidateValuable != bestValuable) return !candidateValuable;
        }
        if (candidate.materialCost != best.materialCost) return candidate.materialCost < best.materialCost;
        return candidate.progress > best.progress;
    }

    public static int select(Inventory inventory, BlockState state, boolean resource,
                             boolean preferSilk, boolean saveTools, int reserveDurability) {
        return selectInRange(inventory, state, resource, preferSilk, saveTools, reserveDurability,
            0, Inventory.SELECTION_SIZE);
    }

    /** Inventory indexes match Baritone's non-equipment item list (0..35). */
    public static int selectInventory(Inventory inventory, BlockState state, boolean resource,
                                      boolean preferSilk, boolean saveTools, int reserveDurability) {
        return selectInRange(inventory, state, resource, preferSilk, saveTools, reserveDurability,
            Inventory.SELECTION_SIZE, 36);
    }

    private static int selectInRange(Inventory inventory, BlockState state, boolean resource,
                                     boolean preferSilk, boolean saveTools, int reserveDurability,
                                     int start, int end) {
        var candidates = new java.util.ArrayList<Candidate>();
        double bareHand = ToolSet.calculateSpeedVsBlock(ItemStack.EMPTY, state);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!BaritoneAPI.getSettings().useSwordToMine.value && stack.is(ItemTags.SWORDS)) continue;
            double progress = ToolSet.calculateSpeedVsBlock(stack, state);
            boolean harvests = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
            // Ineffective tools must not beat an empty hand simply because their material is cheap.
            int cost = progress > bareHand ? materialCost(stack) : Integer.MAX_VALUE;
            candidates.add(new Candidate(slot, harvests, progress,
                enchantment(stack, Enchantments.FORTUNE), enchantment(stack, Enchantments.SILK_TOUCH), cost,
                saveTools && stack.isDamageableItem()
                    && stack.getMaxDamage() - stack.getDamageValue() <= reserveDurability));
        }
        Candidate best = choose(candidates, resource, preferSilk);
        return best == null ? -1 : best.slot;
    }

    private static int enchantment(ItemStack stack, ResourceKey<Enchantment> wanted) {
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(wanted)) return stack.getEnchantments().getLevel(enchantment);
        }
        return 0;
    }

    private static int materialCost(ItemStack stack) {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (name.startsWith("wooden_")) return 0;
        if (name.startsWith("stone_")) return 1;
        if (name.startsWith("iron_")) return 2;
        if (name.startsWith("golden_")) return 3;
        if (name.startsWith("diamond_")) return 4;
        if (name.startsWith("netherite_")) return 5;
        return 6;
    }
}
