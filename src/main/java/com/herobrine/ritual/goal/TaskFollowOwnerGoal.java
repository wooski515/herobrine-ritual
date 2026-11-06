package com.herobrine.ritual.goal;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;

/** Іти за власником (і телепортуватися до нього) лише в режимі «Іти за мною». */
public class TaskFollowOwnerGoal extends FollowOwnerGoal {
    private final HerobrineCompanion companion;

    public TaskFollowOwnerGoal(HerobrineCompanion companion, double speed, float startDistance, float stopDistance) {
        super(companion, speed, startDistance, stopDistance);
        this.companion = companion;
    }

    @Override
    public boolean canUse() {
        return companion.getTask() == CompanionTask.FOLLOW && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return companion.getTask() == CompanionTask.FOLLOW && super.canContinueToUse();
    }
}
