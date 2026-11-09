package dev.magomc.orbital;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone, server-side orbital TNT attacks. No OrbitalStrike+ dependency. */
public final class OrbitalMod implements ModInitializer {
    private static final String OWNER = "MaGoMC";
    private static final Random RNG = new Random();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private record Strike(ServerWorld world, Vec3d center, boolean nuke, int tick) {}

    private static String rodType(ItemStack stack) {
        if (!stack.isOf(Items.FISHING_ROD)) return "";
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return "";
        NbtCompound nbt = component.copyNbt();
        return nbt.getString("mago_orbital").orElse("");
    }

    @Override public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            String type = rodType(stack);
            if (!type.equals("nuke") && !type.equals("stab")) return ActionResult.PASS;
            if (player.isSneaking() && player.getGameProfile().name().equalsIgnoreCase(OWNER)) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                if (!player.getInventory().insertStack(copy)) player.dropItem(copy, false);
                return ActionResult.SUCCESS;
            }
            Vec3d eye = player.getEyePos();
            Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(160));
            BlockHitResult hit = sw.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
            STRIKES.add(new Strike(sw, target, type.equals("nuke"), 0));
            if (!player.isCreative()) stack.decrement(1);
            sw.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.HOSTILE, 2f, 0.7f);
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<Strike> updated = new ArrayList<>();
            for (Strike strike : STRIKES) {
                renderAndStrike(strike);
                int next = strike.tick() + 1;
                if (next < (strike.nuke() ? 42 : 15)) {
                    updated.add(new Strike(strike.world(), strike.center(), strike.nuke(), next));
                }
            }
            STRIKES.clear();
            STRIKES.addAll(updated);
        });
    }

    private static void renderAndStrike(Strike strike) {
        int t = strike.tick();
        Vec3d c = strike.center();
        ServerWorld world = strike.world();
        if (strike.nuke()) {
            // Thirteen distinct, irregular, descending TNT rings. No decorative flame circles.
            // Spawning in staggered groups keeps the effect visibly moving and reduces lag spikes.
            if (t % 3 != 0 || t > 36) return;
            int ring = t / 3;
            double radius = 3.5 + ring * 2.8;
            int count = 18 + ring * 3;
            double phase = ring * 0.61;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count + phase;
                double uneven = 1 + 0.13 * Math.sin(i * 2.9 + ring) + (RNG.nextDouble() - .5) * .14;
                double x = c.x + Math.cos(angle) * radius * uneven;
                double z = c.z + Math.sin(angle) * radius * uneven;
                // Prime TNT high above the target, falling with a strong initial downward speed.
                spawnFallingTnt(world, x, c.y + 43 + RNG.nextDouble() * 6, z,
                    -1.10 - RNG.nextDouble() * .15, 48 + RNG.nextInt(9));
            }
        } else {
            // Dense vertical TNT lance rather than a particle animation.
            if (t % 2 != 0 || t > 12) return;
            for (int i = 0; i < 12; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double radius = Math.sqrt(RNG.nextDouble()) * 1.6;
                double x = c.x + Math.cos(angle) * radius;
                double z = c.z + Math.sin(angle) * radius;
                spawnFallingTnt(world, x, c.y + 40 + RNG.nextDouble() * 10, z,
                    -1.45 - RNG.nextDouble() * .3, 37 + RNG.nextInt(8));
            }
        }
    }

    private static void spawnFallingTnt(ServerWorld world, double x, double y, double z,
                                        double fallSpeed, int fuse) {
        TntEntity tnt = new TntEntity(world, x, y, z, null);
        tnt.setFuse(fuse);
        tnt.setVelocity((RNG.nextDouble() - .5) * .05, fallSpeed,
            (RNG.nextDouble() - .5) * .05);
        world.spawnEntity(tnt);
    }
}
