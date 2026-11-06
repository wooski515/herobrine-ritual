package com.herobrine.ritual.item;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineUtil;
import com.herobrine.ritual.goal.JobUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Посох наказів. Працює на відстані до 32 блоків.
 * <ul>
 *   <li>Shift + ПКМ у повітря — змінити режим.</li>
 *   <li>ПКМ по своєму Херобріну — вибрати його (у режимі «Дуель» другий клік по іншому Херобріну починає дуель).</li>
 *   <li>ПКМ по будь-якій іншій істоті — атакувати її (вибраний або всі твої Херобріни поруч).</li>
 *   <li>Охорона / Видобуток / Перенесення: ПКМ по блоку — вибраний Херобрін іде туди працювати.
 *       У перенесенні ПКМ по сундуку — склад, Shift + ПКМ по сундуку — джерело.</li>
 *   <li>«До мене» — ПКМ будь-куди: усі твої Херобріни поруч ідуть за тобою.</li>
 * </ul>
 */
public class CommandStaffItem extends Item {
    private static final double RANGE = 32.0;
    private static final String KEY_MODE = "Mode";
    private static final String KEY_SELECTED = "Selected";
    private static final String KEY_CORNER = "CornerA";
    /** Глибина кар'єру за замовчуванням, якщо обидва кути на одній висоті. */
    private static final int DEFAULT_MINE_DEPTH = 8;

    public enum Mode {
        ATTACK("attack"), DUEL("duel"), GUARD("guard"), MINE("mine"), HAUL("haul"), RECALL("recall");

        final String id;

        Mode(String id) {
            this.id = id;
        }

        Component label() {
            return Component.translatable("staff.herobrine_ritual.mode." + id);
        }
    }

    public CommandStaffItem(Properties properties) {
        super(properties);
    }

    // ---- Дані на предметі ----

    private static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static Mode getMode(ItemStack stack) {
        int ordinal = data(stack).getIntOr(KEY_MODE, 0);
        Mode[] modes = Mode.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.ATTACK;
    }

    private static void setMode(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(KEY_MODE, mode.ordinal());
            tag.remove(KEY_CORNER);
        });
    }

    /** Перший кут області, що зараз виділяється (або null). Читається й на клієнті для сітки. */
    public static @Nullable BlockPos getCorner(ItemStack stack) {
        long raw = data(stack).getLongOr(KEY_CORNER, Long.MIN_VALUE);
        return raw == Long.MIN_VALUE ? null : BlockPos.of(raw);
    }

    private static void setCorner(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(KEY_CORNER);
            } else {
                tag.putLong(KEY_CORNER, pos.asLong());
            }
        });
    }

    /** UUID вибраного Херобріна (для підсвітки на клієнті). */
    public static @Nullable UUID getSelectedId(ItemStack stack) {
        String raw = data(stack).getStringOr(KEY_SELECTED, "");
        try {
            return raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void setSelected(ItemStack stack, @Nullable UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(KEY_CORNER);
            if (id == null) {
                tag.remove(KEY_SELECTED);
            } else {
                tag.putString(KEY_SELECTED, id.toString());
            }
        });
    }

    private static @Nullable HerobrineCompanion getSelected(ItemStack stack, Player player) {
        String raw = data(stack).getStringOr(KEY_SELECTED, "");
        if (raw.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        try {
            Entity e = level.getEntity(UUID.fromString(raw));
            if (e instanceof HerobrineCompanion c && c.isAlive() && c.isOwnedBy(player)) {
                return c;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    // ---- Події предмета ----

    /** ПКМ по блоку в межах досяжності: обробляємо раніше за сам блок (щоб сундук не відкривався). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel) {
            handleBlock(player, player.getItemInHand(context.getHand()), context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.SUCCESS; // уже оброблено в onItemUseFirst
    }

    /** ПКМ по істоті поруч. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level() instanceof ServerLevel) {
            handleEntity(player, player.getItemInHand(hand), target);
        }
        return InteractionResult.SUCCESS;
    }

    /** ПКМ у повітря: промінь до 32 блоків. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, entityEnd,
                new AABB(eye, entityEnd).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
                eye.distanceToSqr(entityEnd));

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            handleEntity(player, stack, living);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            handleBlock(player, stack, blockHit.getBlockPos(), blockHit.getDirection());
        } else if (player.isSecondaryUseActive()) {
            cycleMode(player, stack);
        } else if (getMode(stack) == Mode.RECALL) {
            recall(player);
        } else {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.no_target"));
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Логіка ----

    private static void cycleMode(Player player, ItemStack stack) {
        Mode[] modes = Mode.values();
        Mode next = modes[(getMode(stack).ordinal() + 1) % modes.length];
        setMode(stack, next);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.mode",
                next.label().copy().withStyle(ChatFormatting.GOLD)));
    }

    private static void handleEntity(Player player, ItemStack stack, LivingEntity target) {
        Mode mode = getMode(stack);
        if (player.isSecondaryUseActive()) {
            cycleMode(player, stack);
            return;
        }
        HerobrineCompanion selected = getSelected(stack, player);

        if (target instanceof HerobrineCompanion companion && companion.isOwnedBy(player)) {
            if (mode == Mode.DUEL && selected != null && selected != companion) {
                duel(player, stack, selected, companion);
                return;
            }
            setSelected(stack, companion.getUUID());
            companion.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.selected",
                    companion.getDisplayName()));
            return;
        }

        if (mode == Mode.DUEL) {
            if (target instanceof HerobrineCompanion other && selected != null) {
                duel(player, stack, selected, other);
            } else {
                HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.duel_hint"));
            }
            return;
        }

        // PvP вимкнено на сервері — на гравців не нацьковуємо
        if (target instanceof Player victim && !player.canHarmPlayer(victim)) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.pvp_off"));
            return;
        }

        // Будь-який інший режим: ПКМ по чужій істоті = наказ атакувати
        List<HerobrineCompanion> squad = selected != null ? List.of(selected) : nearbyCompanions(player);
        if (squad.isEmpty()) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.none_nearby"));
            return;
        }
        for (HerobrineCompanion c : squad) {
            c.orderAttack(target);
        }
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6F, 1.5F);
        HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.attack",
                squad.size(), target.getDisplayName()));
    }

    /** Виклики на дуель чужих Херобрінів: Херобрін, якого викликали → (хто викликав, до якого тіку чинний). */
    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();

    private record Challenge(UUID challenger, long expiresAt) {
    }

    /**
     * Дуель своїх — одразу. Дуель із чужим Херобріном — лише за згодою: це виклик, який власник іншого
     * Херобріна приймає так само (режим «Дуель», вибрати свого, ПКМ по Херобріну того, хто викликав).
     */
    private static void duel(Player player, ItemStack stack, HerobrineCompanion mine, HerobrineCompanion other) {
        long now = player.level().getGameTime();
        CHALLENGES.values().removeIf(c -> c.expiresAt() < now);
        boolean foreign = other.getOwner() instanceof Player otherOwner && otherOwner != player;
        if (foreign) {
            Challenge pending = CHALLENGES.get(mine.getUUID());
            boolean accepting = pending != null && pending.challenger().equals(other.getUUID());
            if (!accepting) {
                CHALLENGES.put(other.getUUID(), new Challenge(mine.getUUID(), now + 60 * 20));
                HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.challenge_sent",
                        other.getDisplayName()));
                HerobrineUtil.chat(other.getOwner(), Component.translatable("message.herobrine_ritual.staff.challenge_received",
                        player.getDisplayName(), other.getDisplayName(), mine.getDisplayName()).withStyle(ChatFormatting.YELLOW));
                return;
            }
            CHALLENGES.remove(mine.getUUID());
        }
        if (HerobrineCompanion.startDuel(mine, other)) {
            setSelected(stack, null);
        } else {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.duel_busy"));
        }
    }

    private static void handleBlock(Player player, ItemStack stack, BlockPos pos, Direction face) {
        Mode mode = getMode(stack);
        Level level = player.level();
        boolean container = JobUtil.containerAt(level, pos) != null;

        if (player.isSecondaryUseActive()) {
            if (mode == Mode.HAUL && container) {
                HerobrineCompanion selected = requireSelected(player, stack);
                if (selected != null) {
                    selected.setSourcePos(pos);
                    feedback(player, pos, Component.translatable("message.herobrine_ritual.staff.source_set", selected.getDisplayName()));
                }
            } else {
                cycleMode(player, stack);
            }
            return;
        }
        if (mode == Mode.RECALL) {
            recall(player);
            return;
        }
        if (mode == Mode.ATTACK || mode == Mode.DUEL) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.point_creature"));
            return;
        }
        HerobrineCompanion selected = requireSelected(player, stack);
        if (selected == null) {
            return;
        }
        if (mode == Mode.HAUL && container) {
            selected.setStoragePos(pos);
            feedback(player, pos, Component.translatable("message.herobrine_ritual.staff.storage_set", selected.getDisplayName()));
            return;
        }

        // Виділення області двома кутами. Той самий блок двічі — робота «в точці».
        BlockPos corner = getCorner(stack);
        if (corner == null) {
            setCorner(stack, pos);
            feedback(player, pos, Component.translatable("message.herobrine_ritual.staff.corner1",
                    pos.getX(), pos.getY(), pos.getZ()));
            return;
        }
        setCorner(stack, null);
        CompanionTask task = mode == Mode.GUARD ? CompanionTask.GUARD : mode == Mode.MINE ? CompanionTask.MINE : CompanionTask.HAUL;

        if (corner.equals(pos)) {
            BlockPos stand = face == Direction.UP ? pos.above() : pos.relative(face);
            selected.setTask(task, stand, player.getDirection());
            String key = switch (task) {
                case GUARD -> "message.herobrine_ritual.staff.guard";
                case MINE -> "message.herobrine_ritual.staff.mine";
                default -> "message.herobrine_ritual.staff.haul";
            };
            feedback(player, stand, task == CompanionTask.MINE
                    ? Component.translatable(key, selected.getDisplayName(),
                            Component.translatable("direction.herobrine_ritual." + player.getDirection().getName()))
                    : Component.translatable(key, selected.getDisplayName()));
            return;
        }

        BlockPos a = corner;
        BlockPos b = pos;
        if (task == CompanionTask.MINE && a.getY() == b.getY()) {
            b = b.below(DEFAULT_MINE_DEPTH - 1); // обидва кути на поверхні — копаємо на 8 блоків углиб
        }
        int sx = Math.abs(a.getX() - b.getX()) + 1;
        int sy = Math.abs(a.getY() - b.getY()) + 1;
        int sz = Math.abs(a.getZ() - b.getZ()) + 1;
        int max = HerobrineCompanion.MAX_AREA_SIZE;
        if (sx > max || sy > max || sz > max) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.area_too_big", max));
            return;
        }
        selected.setTaskInArea(task, a, b);
        String key = switch (task) {
            case GUARD -> "message.herobrine_ritual.staff.area_guard";
            case MINE -> "message.herobrine_ritual.staff.area_mine";
            default -> "message.herobrine_ritual.staff.area_haul";
        };
        feedback(player, pos, Component.translatable(key, selected.getDisplayName(), sx, sy, sz));
    }

    private static @Nullable HerobrineCompanion requireSelected(Player player, ItemStack stack) {
        HerobrineCompanion selected = getSelected(stack, player);
        if (selected == null) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.select_first"));
        }
        return selected;
    }

    private static void feedback(Player player, BlockPos pos, Component message) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.4, 0.4, 0.4, 0.0);
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        HerobrineUtil.actionBar(player, message);
    }

    private static List<HerobrineCompanion> nearbyCompanions(Player player) {
        return player.level().getEntitiesOfClass(HerobrineCompanion.class,
                player.getBoundingBox().inflate(RANGE),
                c -> c.isAlive() && c.isOwnedBy(player) && !c.isInDuel()
                        && (c.getTask() == CompanionTask.FOLLOW || c.getTask() == CompanionTask.GUARD));
    }

    private static void recall(Player player) {
        List<HerobrineCompanion> all = player.level().getEntitiesOfClass(HerobrineCompanion.class,
                player.getBoundingBox().inflate(64.0), c -> c.isAlive() && c.isOwnedBy(player));
        for (HerobrineCompanion c : all) {
            c.setTask(CompanionTask.FOLLOW, null, null);
        }
        HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.staff.recall", all.size()));
    }

    // ---- Вигляд ----

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.herobrine_ritual.command_staff")
                .append(Component.literal(" ["))
                .append(getMode(stack).label())
                .append(Component.literal("]"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.herobrine_ritual.staff.mode." + getMode(stack).id)
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.herobrine_ritual.staff.select").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.herobrine_ritual.staff.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
