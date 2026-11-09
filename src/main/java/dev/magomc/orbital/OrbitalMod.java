package dev.magomc.orbital;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void duplicate(ServerPlayerEntity player, ItemStack original) {
        ItemStack copy = original.copy();
        copy.setCount(1);
        if (!player.getInventory().insertStack(copy)) {
            player.dropItem(copy, false);
        }
    }

    @Override
    public void onInitialize() {
        try {
            Class<?> logic = Class.forName("com.mcclip.orbitalstrike.OrbitalStrikeLogic");
            nuke = logic.getMethod("summonNuke", ServerWorld.class,
                    int.class, int.class, int.class, int.class);
            stab = logic.getMethod("summonStab", ServerWorld.class,
                    int.class, int.class);
            logic.getMethod("registerSlamPhysics").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bundled OrbitalStrike+ attack code could not be loaded", e);
        }

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)
                    || !(world instanceof ServerWorld sw)) {
                return ActionResult.PASS;
            }

            boolean ownerSneaking = player.isSneaking()
                    && sp.getGameProfile().name().equalsIgnoreCase(OWNER);

            // Duplicate a shulker box held in the offhand when crouching and
            // right-clicking. MAIN_HAND is checked first for better reliability.
            if (ownerSneaking && hand == Hand.MAIN_HAND
                    && isShulkerBox(player.getOffHandStack())) {
                duplicate(sp, player.getOffHandStack());
                return ActionResult.SUCCESS;
            }

            ItemStack stack = player.getStackInHand(hand);
            String type = rodType(stack);
            if (!type.equals("nuke") && !type.equals("stab")) {
                return ActionResult.PASS;
            }

            // Preserve MaGoMC's original Nuke/Stab rod duplication.
            if (ownerSneaking) {
                duplicate(sp, stack);
                return ActionResult.SUCCESS;
            }

            Vec3d eye = player.getEyePos();
            Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(160));
            BlockHitResult hit = sw.raycast(new RaycastContext(
                    eye, end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player));
            Vec3d target = hit.getPos();
            BlockPos pos = BlockPos.ofFloored(target);

            try {
                if (type.equals("nuke")) {
                    // Retains the previous sky-height adjustment.
                    int spawnY = Math.min(sw.getTopYInclusive() - 4,
                            pos.getY() + 75);
                    nuke.invoke(null, sw, pos.getX(), spawnY, pos.getZ(), 13);
                } else {
                    stab.invoke(null, sw, pos.getX(), pos.getZ());
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Orbital attack failed", e);
            }

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return ActionResult.SUCCESS;
        });
    }
}
