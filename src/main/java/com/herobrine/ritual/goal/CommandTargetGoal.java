package com.herobrine.ritual.goal;

import com.herobrine.ritual.HerobrineCompanion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Найвищий пріоритет цілі: суперник у дуелі або ціль, яку наказали атакувати посохом. */
public class CommandTargetGoal extends Goal {
    private final HerobrineCompanion companion;
    private @Nullable LivingEntity target;

    public CommandTargetGoal(HerobrineCompanion companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!companion.hasCommandTarget()) {
            return false;
        }
        target = companion.resolveCommandTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity current = companion.resolveCommandTarget();
        return current != null && current == target;
    }

    @Override
    public void start() {
        companion.setTarget(target);
    }

    @Override
    public void tick() {
        if (target != null && companion.getTarget() != target) {
            companion.setTarget(target);
        }
    }

    @Override
    public void stop() {
        if (companion.getTarget() == target) {
            companion.setTarget(null);
        }
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
