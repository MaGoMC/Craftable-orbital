package dev.magomc.orbital;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class OrbitalMod implements ModInitializer {
    private static final String OWNER = "MaGoMC";
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
                ItemStack copy = stack.copy(); copy.setCount(1);
                if (!player.getInventory().insertStack(copy)) player.dropItem(copy, false);
                return ActionResult.SUCCESS;
            }
            Vec3d eye = player.getEyePos();
            Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(160));
            BlockHitResult hit = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
            STRIKES.add(new Strike(sw, target, type.equals("nuke"), 0));
            if (!player.isCreative()) stack.decrement(1);
            sw.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2f, 0.7f);
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            synchronized (STRIKES) {
                List<Strike> updated = new ArrayList<>();
                for (Strike s : STRIKES) {
                    if (s.tick() >= (s.nuke() ? 75 : 30)) continue;
                    renderAndStrike(s);
                    updated.add(new Strike(s.world(), s.center(), s.nuke(), s.tick() + 1));
                }
                STRIKES.clear(); STRIKES.addAll(updated);
            }
        });
    }

    private static void renderAndStrike(Strike s) {
        ServerWorld world = s.world();
        Vec3d c = s.center();
        int t = s.tick();
        // Uneven, offset, broken-looking TNT/flash particle arcs rather than geometric perfect circles.
        int rings = s.nuke() ? 13 : 1;
        if (t % 3 == 0) {
            for (int r = 0; r < rings; r++) {
                double baseRadius = 3.0 + r * 2.2;
                double y = c.y + 35 + r * 2.0 - t * 0.65;
                for (int k = 0; k < 34; k++) {
                    double a = (Math.PI * 2 * k / 34.0) + t * .07 + r * .3;
                    if ((k + r * 7 + t / 3) % 9 == 0) continue;
                    double rough = 1.0 + 0.17 * Math.sin(k * 2.7 + r * 1.9) + 0.12 * Math.cos(k * 1.3 + t);
                    double x = c.x + Math.cos(a) * baseRadius * rough + Math.sin(r * 2.1) * 1.2;
                    double z = c.z + Math.sin(a) * baseRadius * rough + Math.cos(r * 1.8) * 1.2;
                    world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, .12, .12, .12, .015);
                    if (k % 3 == 0) world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, .3, .1, .3, .01);
                }
            }
        }
        // TNT-based destructive impact. Tune strength/performance to suit your server.
        if (s.nuke() && t >= 40 && t <= 65 && t % 5 == 0) {
            int wave = (t - 40) / 5;
            for (int i = 0; i < 13; i++) {
                double a = 2 * Math.PI * i / 13.0 + wave * .31;
                double radius = 2.5 + wave * 4.5;
                spawnTnt(world, c.x + Math.cos(a) * radius, c.y + 3, c.z + Math.sin(a) * radius, 1);
            }
        } else if (!s.nuke() && t == 20) {
            for (int i = 0; i < 8; i++) spawnTnt(world, c.x + (i % 3 - 1) * 1.2, c.y + 2 + i * 2, c.z + (i / 3 - 1) * 1.2, 1);
        }
    }
    private static void spawnTnt(ServerWorld world, double x, double y, double z, int fuse) {
        TntEntity tnt = new TntEntity(world, x, y, z, null);
        tnt.setFuse(fuse);
        world.spawnEntity(tnt);
    }
}
