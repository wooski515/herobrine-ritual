package com.herobrine.ritual;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Компаньйон сам годує тварин їжею зі свого рюкзака, щоб вони розмножувалися.
 * Пропускає малят, тварин на кулдауні розмноження, приручених тварин
 * і ділянки, де вже забагато тварин цього виду (захист від лагів).
 */
public class FeedAnimalsGoal extends Goal {
    private static final double SEARCH_RANGE = 12.0;
    private static final double REACH_SQR = 2.5 * 2.5;
    private static final int MAX_SAME_SPECIES_NEARBY = 12;

    private final HerobrineCompanion companion;
    private Animal target;
    private int scanCooldown = 0;
    private int repathCooldown = 0;

    public FeedAnimalsGoal(HerobrineCompanion companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = 40;
        CompanionTask task = companion.getTask();
        if (!companion.autoFeed || !companion.isTame() || companion.isOrderedToSit()
                || companion.getTarget() != null
                || (task != CompanionTask.FOLLOW && task != CompanionTask.GUARD)) {
            return false;
        }
        if (!companion.hasAnyBackpackItem()) {
            return false;
        }
        List<Animal> candidates = companion.level().getEntitiesOfClass(
                Animal.class,
                companion.getBoundingBox().inflate(SEARCH_RANGE, 4.0, SEARCH_RANGE),
                this::isValidTarget);
        this.target = candidates.stream()
                .min(Comparator.comparingDouble(companion::distanceToSqr))
                .orElse(null);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null
                && target.isAlive()
                && !target.isInLove()
                && !companion.isOrderedToSit()
                && companion.getTarget() == null
                && companion.distanceToSqr(target) < 24.0 * 24.0
                && findFoodSlot(target) >= 0;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        companion.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (companion.distanceToSqr(target) <= REACH_SQR) {
            feed(target);
            target = null;
            scanCooldown = 20;
            return;
        }
        if (--repathCooldown <= 0) {
            repathCooldown = 10;
            companion.getNavigation().moveTo(target, 1.1);
        }
    }

    private void feed(Animal animal) {
        int slot = findFoodSlot(animal);
        if (slot < 0) {
            return;
        }
        ItemStack food = companion.backpack.getItem(slot);
        LivingEntity owner = companion.getOwner();
        animal.setInLove(owner instanceof Player player ? player : null);
        food.shrink(1);
        companion.backpack.setChanged();
        companion.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    private boolean isValidTarget(Animal animal) {
        if (animal == companion || !animal.isAlive() || animal instanceof TamableAnimal) {
            return false;
        }
        if (animal.getAge() != 0 || animal.isInLove() || !animal.canFallInLove()) {
            return false;
        }
        if (findFoodSlot(animal) < 0) {
            return false;
        }
        int sameSpecies = companion.level()
                .getEntitiesOfClass(animal.getClass(), animal.getBoundingBox().inflate(16.0))
                .size();
        return sameSpecies < MAX_SAME_SPECIES_NEARBY;
    }

    /** Індекс слота рюкзака з їжею, яку цей вид їсть, або -1. */
    private int findFoodSlot(Animal animal) {
        for (int i = 0; i < companion.backpack.getContainerSize(); i++) {
            ItemStack stack = companion.backpack.getItem(i);
            if (!stack.isEmpty() && animal.isFood(stack)) {
                return i;
            }
        }
        return -1;
    }
}
