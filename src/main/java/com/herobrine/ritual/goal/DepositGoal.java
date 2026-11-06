package com.herobrine.ritual.goal;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Несе вміст рюкзака в сундук-склад: коли рюкзак повний (видобуток) або коли більше нічого збирати (перенесення).
 * Ендерський Херобрін не ходить, а телепортує речі в сундук на відстані до 48 блоків.
 */
public class DepositGoal extends Goal {
    private static final double ENDER_RANGE = 48.0;

    private final HerobrineCompanion companion;
    private @Nullable BlockPos storage;
    private int repath;
    private int stuck;
    private boolean done;
    private boolean teleported;
    private int retryAt;

    public DepositGoal(HerobrineCompanion companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private void teleportToStorage(ServerLevel level) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos spot = storage.relative(dir);
            if (level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
                    && level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()
                    && level.getFluidState(spot).isEmpty()) {
                level.sendParticles(ParticleTypes.PORTAL, companion.getX(), companion.getY() + 1, companion.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
                companion.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                companion.getNavigation().stop();
                return;
            }
        }
    }

    private boolean wantsToDeposit() {
        CompanionTask task = companion.getTask();
        if (!companion.hasAnyBackpackItem()) {
            return false;
        }
        if (task == CompanionTask.MINE) {
            return !companion.hasFreeBackpackSlot() || companion.mineDone;
        }
        if (task == CompanionTask.HAUL) {
            return !companion.hasFreeBackpackSlot() || companion.haulIdle;
        }
        return false;
    }

    @Override
    public boolean canUse() {
        if (companion.tickCount < retryAt || companion.getTarget() != null || companion.isOrderedToSit()
                || !wantsToDeposit()) {
            return false;
        }
        storage = JobUtil.resolveStorage(companion);
        if (storage == null) {
            companion.notifyOwner(Component.translatable("message.herobrine_ritual.job.no_storage"));
            retryAt = companion.tickCount + 100;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && storage != null && companion.getTarget() == null
                && (companion.getTask() == CompanionTask.MINE || companion.getTask() == CompanionTask.HAUL)
                && JobUtil.containerAt(companion.level(), storage) != null && stuck < 240;
    }

    @Override
    public void start() {
        repath = 0;
        stuck = 0;
        done = false;
        teleported = false;
    }

    @Override
    public void stop() {
        if (!done && stuck >= 240) {
            companion.notifyOwner(Component.translatable("message.herobrine_ritual.job.cant_reach"));
            retryAt = companion.tickCount + 200;
        }
        companion.getNavigation().stop();
        storage = null;
    }

    @Override
    public void tick() {
        if (storage == null || !(companion.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(storage);
        double dist = companion.distanceToSqr(center);

        boolean ender = companion.getVariant() == HerobrineVariant.ENDER && dist < ENDER_RANGE * ENDER_RANGE;
        if (ender || dist < 3.0 * 3.0) {
            companion.getLookControl().setLookAt(center.x, center.y, center.z);
            Container container = JobUtil.containerAt(level, storage);
            if (container != null) {
                boolean moved = companion.depositInto(container);
                if (moved) {
                    if (ender) {
                        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 30, 0.4, 0.4, 0.4, 0.4);
                        level.sendParticles(ParticleTypes.PORTAL, companion.getX(), companion.getY() + 1, companion.getZ(), 30, 0.3, 0.6, 0.3, 0.4);
                        level.playSound(null, storage, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.4F);
                    } else {
                        level.playSound(null, storage, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6F, 1.0F);
                        companion.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                    }
                }
                companion.storageFull = companion.hasAnyBackpackItem() && !moved || !companion.hasFreeBackpackSlot();
                if (companion.storageFull) {
                    companion.notifyOwner(Component.translatable("message.herobrine_ritual.job.storage_full"));
                    retryAt = companion.tickCount + 200;
                }
            }
            companion.haulIdle = false;
            done = true;
            return;
        }

        stuck++;
        if (stuck >= 100 && !teleported) {
            teleported = true;
            teleportToStorage(level); // застряг у кар'єрі чи ямі — телепортується до сундука
        }
        if (--repath <= 0) {
            repath = 10;
            if (!companion.getNavigation().moveTo(center.x, storage.getY(), center.z, 1, 1.1)) {
                stuck += 10;
            }
        }
    }
}
