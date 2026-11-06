package com.herobrine.ritual.goal;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineConfig;
import com.herobrine.ritual.HerobrineVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Видобуток. Компаньйон шукає відкриту руду поруч (Лісовий — ще й дерева), а коли руди немає,
 * прокладає штольню 1×2 від точки роботи в заданому напрямку — так відкриваються нові жили.
 * Знахідки складаються в рюкзак; коли він повний, DepositGoal несе їх у сундук.
 * <p>
 * Бонуси видів: Шахтар копає в 2,5 рази швидше і інколи подвоює руду; Незерський одразу переплавляє сировину.
 */
public class MineGoal extends Goal {
    private static final double REACH = 4.5;

    private final HerobrineCompanion companion;
    private @Nullable BlockPos target;
    private float progress;
    private int stuck;
    private int repath;
    private int scanCooldown;
    private int lastStage = -1;
    private boolean teleported;
    /** Блоки, до яких не вдалося дійти: тимчасово ігноруємо (позиція → тік, до якого ігнорувати). */
    private final Map<Long, Integer> blacklist = new HashMap<>();

    public MineGoal(HerobrineCompanion companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (companion.getTask() != CompanionTask.MINE || companion.getWorkPos() == null
                || !HerobrineConfig.MINING_ENABLED.get()
                || companion.getTarget() != null || companion.isOrderedToSit()
                || !companion.hasFreeBackpackSlot() || companion.mineDone) {
            return false;
        }
        if (companion.getOwnerReference() != null && companion.getOwner() == null) {
            return false; // власник вийшов з гри: видобуток на паузі (без нього не перевірити привати)
        }
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = 20;
        for (int attempt = 0; attempt < 16; attempt++) {
            target = findTarget();
            if (target == null || JobUtil.mayBreak(companion, target, companion.level().getBlockState(target))) {
                break;
            }
            blacklist.put(target.asLong(), companion.tickCount + 6000); // захищений блок — пропускаємо
            target = null;
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && companion.getTask() == CompanionTask.MINE && companion.getTarget() == null
                && !companion.isOrderedToSit() && companion.hasFreeBackpackSlot() && stuck < 160
                && JobUtil.canBreak(companion.level(), target, companion.level().getBlockState(target));
    }

    @Override
    public void start() {
        progress = 0.0F;
        stuck = 0;
        teleported = false;
        repath = 0;
        lastStage = -1;
    }

    @Override
    public void stop() {
        if (target != null) {
            if (companion.level() instanceof ServerLevel level) {
                level.destroyBlockProgress(companion.getId(), target, -1);
            }
            if (stuck >= 160) {
                blacklist.put(target.asLong(), companion.tickCount + 1200);
            }
        }
        target = null;
        companion.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null || !(companion.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        companion.getLookControl().setLookAt(center.x, center.y, center.z);

        if (companion.getEyePosition().distanceToSqr(center) > REACH * REACH) {
            stuck++;
            if (stuck > 80 && !teleported) {
                teleported = true;
                teleportNear(level, target); // Херобрін же: не дійшов — телепортувався
            }
            if (--repath <= 0) {
                repath = 10;
                if (!companion.getNavigation().moveTo(center.x, target.getY(), center.z, 1, 1.0)) {
                    stuck += 15;
                }
            }
            return;
        }

        companion.getNavigation().stop();
        BlockState state = level.getBlockState(target);
        ItemStack tool = companion.getMainHandItem();
        progress += breakSpeed(level, target, state, tool);
        if (companion.tickCount % 5 == 0) {
            companion.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != lastStage) {
            lastStage = stage;
            level.destroyBlockProgress(companion.getId(), target, stage);
        }
        if (progress >= 1.0F) {
            breakTarget(level, state, tool);
        }
    }

    /** Частка «міцності», знята за тік. Кирка в руці прискорює (як у гравця). */
    private float breakSpeed(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {
            return 1.0F;
        }
        float toolSpeed = Math.max(1.0F, tool.getDestroySpeed(state));
        float bonus = companion.getVariant() == HerobrineVariant.MINER ? 2.5F : 1.0F;
        float ticks = Math.max(4.0F, hardness * 30.0F / (toolSpeed * bonus));
        return 1.0F / ticks;
    }

    private void breakTarget(ServerLevel level, BlockState state, ItemStack tool) {
        BlockPos pos = target;
        boolean ore = JobUtil.isOre(state);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, companion, tool);
        level.destroyBlockProgress(companion.getId(), pos, -1);
        level.destroyBlock(pos, false, companion);

        boolean doubled = ore && companion.getVariant() == HerobrineVariant.MINER && companion.getRandom().nextFloat() < 0.25F;
        for (ItemStack drop : drops) {
            ItemStack stack = drop;
            if (doubled) {
                stack = stack.copyWithCount(Math.min(stack.getMaxStackSize(), stack.getCount() * 2));
            }
            if (companion.getVariant() == HerobrineVariant.NETHER) {
                stack = JobUtil.smelt(level, stack);
            }
            ItemStack rest = companion.insertIntoBackpack(stack);
            if (!rest.isEmpty()) {
                Block.popResource(level, pos, rest);
            }
        }
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, companion, EquipmentSlot.MAINHAND);
        }
        if (ore) {
            companion.addXp(2);
        }
        if (!companion.hasArea()) {
            companion.tunnelDug++;
        }
        target = null;
        progress = 0.0F;
        scanCooldown = 0;
    }

    // ---- Пошук цілі ----

    private @Nullable BlockPos findTarget() {
        Level level = companion.level();
        BlockPos work = companion.getWorkPos();
        int maxLen = HerobrineConfig.MAX_TUNNEL_LENGTH.get();
        blacklist.values().removeIf(until -> until < companion.tickCount);
        if (companion.hasArea()) {
            return findAreaTarget(level);
        }

        // 1) Відкрита руда (і дерева для Лісового) біля компаньйона та біля точки роботи
        BlockPos found = findResource(level, companion.blockPosition(), 6, work, maxLen + 8);
        if (found == null) {
            found = findResource(level, work, 8, work, maxLen + 8);
        }
        if (found != null) {
            return found;
        }
        // 2) Штольня
        return nextTunnelBlock(level, work, maxLen);
    }

    private @Nullable BlockPos findResource(Level level, BlockPos center, int radius, BlockPos work, int maxFromWork) {
        boolean lumberjack = companion.getVariant() == HerobrineVariant.FOREST;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 5, radius))) {
            if (blacklist.containsKey(p.asLong()) || p.distSqr(work) > (double) maxFromWork * maxFromWork) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            boolean wanted = JobUtil.isOre(s) || (lumberjack && JobUtil.isNaturalLog(level, p, s));
            if (!wanted || !JobUtil.canBreak(level, p, s) || !JobUtil.isExposed(level, p) || !JobUtil.isSafeToOpen(level, p)) {
                continue;
            }
            double d = p.distSqr(companion.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /**
     * Кар'єр: копаємо виділену область шар за шаром зверху вниз. У шарі беремо найближчий блок,
     * до якого можна дістатися. Блоки біля води/лави та з блок-сутностями (скрині тощо) не чіпаємо.
     */
    private @Nullable BlockPos findAreaTarget(Level level) {
        BlockPos min = companion.getAreaMin();
        BlockPos max = companion.getAreaMax();
        if (companion.mineLayer == Integer.MIN_VALUE || companion.mineLayer > max.getY()) {
            companion.mineLayer = max.getY();
        }
        // За один пошук переглядаємо не більше 6 шарів, щоб не навантажувати сервер
        for (int step = 0; step < 6 && companion.mineLayer >= min.getY(); step++) {
            int y = companion.mineLayer;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(min.getX(), y, min.getZ(), max.getX(), y, max.getZ())) {
                if (blacklist.containsKey(p.asLong())) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (!JobUtil.canBreak(level, p, s) || !JobUtil.isSafeToOpen(level, p)) {
                    continue;
                }
                double d = p.distSqr(companion.blockPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
            if (best != null) {
                return best;
            }
            companion.mineLayer--; // шар порожній — нижче
        }
        if (companion.mineLayer < min.getY()) {
            companion.mineDone = true;
            companion.notifyOwner(Component.translatable("message.herobrine_ritual.job.area_done"));
        }
        return null;
    }

    /** Телепорт на вільне місце поруч із блоком (зверху або збоку). */
    private void teleportNear(ServerLevel level, BlockPos pos) {
        BlockPos[] spots = {pos.above(), pos.north(), pos.south(), pos.east(), pos.west(),
                pos.north().above(), pos.south().above(), pos.east().above(), pos.west().above()};
        for (BlockPos spot : spots) {
            if (level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
                    && level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()
                    && level.getFluidState(spot).isEmpty()
                    && !level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty()) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        companion.getX(), companion.getY() + 1, companion.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
                companion.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                companion.getNavigation().stop();
                stuck = 0;
                return;
            }
        }
    }

    private @Nullable BlockPos nextTunnelBlock(Level level, BlockPos work, int maxLen) {
        for (int i = 1; i <= maxLen; i++) {
            BlockPos base = work.relative(companion.getTunnelDir(), i);
            for (BlockPos p : new BlockPos[]{base, base.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty()) {
                    continue; // уже прокопано
                }
                if (blacklist.containsKey(p.asLong())) {
                    return null;
                }
                if (!JobUtil.isTunnelDiggable(s) || !JobUtil.canBreak(level, p, s) || !JobUtil.isSafeToOpen(level, p)) {
                    companion.notifyOwner(Component.translatable("message.herobrine_ritual.job.tunnel_blocked", i));
                    return null;
                }
                return p.immutable();
            }
        }
        companion.notifyOwner(companion.tunnelDug == 0
                ? Component.translatable("message.herobrine_ritual.job.nothing_here")
                : Component.translatable("message.herobrine_ritual.job.tunnel_done", maxLen));
        return null;
    }
}
