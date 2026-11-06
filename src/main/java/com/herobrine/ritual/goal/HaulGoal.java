package com.herobrine.ritual.goal;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Перенесення ресурсів:
 * 1) збирає предмети, що лежать на землі біля точки роботи;
 * 2) якщо задано сундук-джерело — бере з нього речі;
 * усе зібране DepositGoal несе в сундук-склад. Пустельний збирає в більшому радіусі.
 */
public class HaulGoal extends Goal {
    private final HerobrineCompanion companion;
    private @Nullable ItemEntity item;
    private @Nullable BlockPos source;
    private int repath;
    private int stuck;
    private int scanCooldown;

    public HaulGoal(HerobrineCompanion companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private int radius() {
        return companion.getVariant() == HerobrineVariant.DESERT ? 18 : 12;
    }

    @Override
    public boolean canUse() {
        if (companion.getTask() != CompanionTask.HAUL || companion.getWorkPos() == null
                || companion.getTarget() != null || companion.isOrderedToSit()
                || !companion.hasFreeBackpackSlot()) {
            return false;
        }
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = 20;
        item = findItem();
        source = item == null ? findSource() : null;
        companion.haulIdle = item == null && source == null;
        return !companion.haulIdle;
    }

    @Override
    public boolean canContinueToUse() {
        if (companion.getTask() != CompanionTask.HAUL || companion.getTarget() != null || stuck > 200
                || !companion.hasFreeBackpackSlot()) {
            return false;
        }
        if (item != null) {
            return item.isAlive() && !item.getItem().isEmpty();
        }
        return source != null;
    }

    @Override
    public void start() {
        repath = 0;
        stuck = 0;
    }

    @Override
    public void stop() {
        item = null;
        source = null;
        companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        if (item != null) {
            companion.getLookControl().setLookAt(item, 30.0F, 30.0F);
            if (companion.distanceToSqr(item) < 1.8 * 1.8) {
                ItemStack stack = item.getItem();
                ItemStack rest = companion.insertIntoBackpack(stack);
                int taken = stack.getCount() - rest.getCount();
                if (taken > 0) {
                    companion.take(item, taken);
                    if (rest.isEmpty()) {
                        item.discard();
                    } else {
                        item.setItem(rest);
                    }
                }
                item = null;
                scanCooldown = 0;
                return;
            }
            moveTo(item.position());
            return;
        }
        if (source != null) {
            Vec3 center = Vec3.atCenterOf(source);
            if (companion.distanceToSqr(center) < 3.0 * 3.0) {
                Container container = JobUtil.containerAt(level, source);
                if (container != null) {
                    takeFrom(container);
                    level.playSound(null, source, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
                }
                source = null;
                companion.haulIdle = true; // одразу несемо на склад
                return;
            }
            moveTo(center);
        }
    }

    private void moveTo(Vec3 pos) {
        stuck++;
        if (--repath <= 0) {
            repath = 10;
            if (!companion.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.1)) {
                stuck += 10;
            }
        }
    }

    private void takeFrom(Container container) {
        for (int i = 0; i < container.getContainerSize() && companion.hasFreeBackpackSlot(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = companion.insertIntoBackpack(stack);
            container.setItem(i, rest);
        }
        container.setChanged();
    }

    private @Nullable ItemEntity findItem() {
        BlockPos work = companion.getWorkPos();
        int r = radius();
        AABB box = companion.hasArea()
                ? new AABB(Vec3.atLowerCornerOf(companion.getAreaMin()), Vec3.atLowerCornerOf(companion.getAreaMax()).add(1, 3, 1)).inflate(1.0)
                : new AABB(work).inflate(r, 6, r);
        List<ItemEntity> items = companion.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && companion.canFit(e.getItem()));
        return items.stream().min(Comparator.comparingDouble(companion::distanceToSqr)).orElse(null);
    }

    private @Nullable BlockPos findSource() {
        BlockPos src = companion.getSourcePos();
        if (src == null || src.equals(companion.getStoragePos())) {
            return null;
        }
        if (JobUtil.resolveStorage(companion) == null) {
            return null; // нема куди нести
        }
        Container container = JobUtil.containerAt(companion.level(), src);
        if (container == null || container.isEmpty()) {
            return null;
        }
        return src;
    }
}
