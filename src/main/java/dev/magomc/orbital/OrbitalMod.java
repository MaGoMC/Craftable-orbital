package dev.magomc.orbital;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.lang.reflect.Method;

public final class OrbitalMod implements ModInitializer {
    private static final String OWNER = "MaGoMC";
    private static Method nuke;
    private static Method stab;

    private static String rodType(ItemStack stack) {
        if (!stack.isOf(Items.FISHING_ROD)) return "";
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return "";
        NbtCompound nbt = component.copyNbt();
        return nbt.getString("mago_orbital").orElse("");
    }

    @Override public void onInitialize() {
        try {
            Class<?> logic = Class.forName("com.mcclip.orbitalstrike.OrbitalStrikeLogic");
            nuke = logic.getMethod("summonNuke", ServerWorld.class, int.class, int.class, int.class, int.class);
            stab = logic.getMethod("summonStab", ServerWorld.class, int.class, int.class);
            logic.getMethod("registerSlamPhysics").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bundled OrbitalStrike+ attack code could not be loaded", e);
        }
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
            Vec3d target = hit.getPos();
            BlockPos pos = BlockPos.ofFloored(target);
            try {
                if (type.equals("nuke")) {
                    // Actual bundled OrbitalStrike+ method; 13 original TNT rings.
                    nuke.invoke(null, sw, pos.getX(), Math.min(sw.getTopYInclusive() - 4, pos.getY() + 75), pos.getZ(), 13);
                } else {
                    // Actual bundled OrbitalStrike+ Stab method, using its native x/z arguments.
                    stab.invoke(null, sw, pos.getX(), pos.getZ());
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Orbital attack failed", e);
            }
            if (!player.isCreative()) stack.decrement(1);
            return ActionResult.SUCCESS;
        });
    }
}
