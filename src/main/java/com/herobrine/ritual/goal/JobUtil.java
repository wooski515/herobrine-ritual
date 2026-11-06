package com.herobrine.ritual.goal;

import com.herobrine.ritual.HerobrineCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/** Спільні перевірки для робіт компаньйона (видобуток, перенесення). */
public final class JobUtil {
    private JobUtil() {
    }

    public static @Nullable Container containerAt(Level level, @Nullable BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return HopperBlockEntity.getContainerAt(level, pos);
    }

    public static boolean isStorageBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    /**
     * Сундук-склад: прив'язаний посохом або найближчий сундук/бочка в радіусі 10 від точки роботи.
     * Знайдений автоматично запам'ятовується.
     */
    public static @Nullable BlockPos resolveStorage(HerobrineCompanion c) {
        Level level = c.level();
        BlockPos linked = c.getStoragePos();
        if (linked != null && containerAt(level, linked) != null) {
            return linked;
        }
        BlockPos center = c.getWorkPos() != null ? c.getWorkPos() : c.blockPosition();
        int r = 10;
        int down = 4;
        if (c.hasArea()) {
            r = Math.max(10, Math.max(c.getAreaMax().getX() - c.getAreaMin().getX(), c.getAreaMax().getZ() - c.getAreaMin().getZ()) / 2 + 6);
            down = Math.max(4, c.getAreaMax().getY() - c.getAreaMin().getY() + 4);
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -down, -r), center.offset(r, 4, r))) {
            if (!p.equals(c.getSourcePos()) && isStorageBlock(level, p)) {
                double d = p.distSqr(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) {
            c.setStoragePos(best);
        }
        return best;
    }

    /**
     * Чи може власник зламати цей блок: та сама перевірка, що й для гравця (захист спавну, пригодницький
     * режим, межа світу, моди приватів через BreakBlockEvent). Нічий Херобрін (без власника) копає вільно.
     */
    public static boolean mayBreak(HerobrineCompanion c, BlockPos pos, BlockState state) {
        if (!(c.level() instanceof ServerLevel level) || c.getOwnerReference() == null) {
            return true;
        }
        if (!(c.getOwner() instanceof ServerPlayer owner)) {
            return false; // власник не в грі — без перевірки не копаємо
        }
        if (owner.blockActionRestricted(level, pos, owner.gameMode())) {
            return false;
        }
        BreakBlockEvent event = new BreakBlockEvent(level, pos, state, owner);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public static boolean isOre(BlockState state) {
        return state.is(Tags.BlockItems.ORES.block());
    }

    /** Колода дерева (а не стіна будинку): поруч є природне листя. */
    public static boolean isNaturalLog(Level level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 6, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** Природні блоки, які можна прокопувати штольнею (не будівельні: дошки, цегла, скло не чіпаємо). */
    public static boolean isTunnelDiggable(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.END_STONE) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.CLAY) || isOre(state);
    }

    /** Блок взагалі можна зламати: не повітря, не рідина, без блок-сутності, не незламний і не обсидіан. */
    public static boolean canBreak(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness < 50.0F;
    }

    /** Поруч немає води чи лави (щоб не затопити шахту й не втопити компаньйона). */
    public static boolean isSafeToOpen(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Хоча б одна сторона блока відкрита (повітря), тобто до нього можна дістатися. */
    public static boolean isExposed(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            BlockState s = level.getBlockState(n);
            if (s.getCollisionShape(level, n).isEmpty() && s.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Переплавляє сировину (raw iron → iron ingot тощо). Використовує Незерський Херобрін. */
    public static ItemStack smelt(ServerLevel level, ItemStack stack) {
        if (!stack.is(Tags.Items.RAW_MATERIALS)) {
            return stack;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> {
                    ItemStack out = holder.value().assemble(input).copy();
                    out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * stack.getCount()));
                    return out;
                })
                .filter(out -> !out.isEmpty())
                .orElse(stack);
    }
}
