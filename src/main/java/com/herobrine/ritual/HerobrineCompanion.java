package com.herobrine.ritual;

import com.herobrine.ritual.goal.CommandTargetGoal;
import com.herobrine.ritual.goal.DepositGoal;
import com.herobrine.ritual.goal.HaulGoal;
import com.herobrine.ritual.goal.MineGoal;
import com.herobrine.ritual.goal.TaskFollowOwnerGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Приручений Херобрін — твій «покемон».
 * <ul>
 *   <li>Має вид (здібності), рівень і досвід: росте від перемог над мобами, босами та в дуелях.</li>
 *   <li>Виконує завдання: іти за тобою, стояти, охороняти точку, добувати руду, носити ресурси.</li>
 *   <li>Атакує ціль за наказом посоха, б'ється в дуелях з іншими Херобрінами (без смерті).</li>
 *   <li>Має спорядження, рюкзак і активну здібність (вручну з меню або автоматично в бою).</li>
 * </ul>
 */
public class HerobrineCompanion extends TamableAnimal implements HasHerobrineVariant {

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.INT);
    /** Дані роботи, що синхронізуються на клієнт (для сітки області, яку видно з посохом у руці). */
    private static final EntityDataAccessor<Integer> DATA_TASK =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_WORK_POS =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MIN =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MAX =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_STORAGE_POS =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_SOURCE_POS =
            SynchedEntityData.defineId(HerobrineCompanion.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private static final Identifier LEVEL_HEALTH_ID = Identifier.fromNamespaceAndPath(HerobrineMod.MODID, "level_health");
    private static final Identifier LEVEL_ATTACK_ID = Identifier.fromNamespaceAndPath(HerobrineMod.MODID, "level_attack");

    public static final int MAX_LEVEL = 50;
    public static final int GUARD_RADIUS = 14;
    /** Найбільший розмір виділеної області по кожній осі. */
    public static final int MAX_AREA_SIZE = 48;
    private static final int DUEL_SECONDS = 90;
    private static final int ORDER_SECONDS = 60;
    private static final long NO_POS = Long.MIN_VALUE;

    /** Слоти спорядження: порядок збігається зі слотами в CompanionMenu. */
    public static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** Максимальний розмір рюкзака (4 рядки по 9). Скільки рядків відкрито, залежить від апгрейдів. */
    public static final int BACKPACK_SIZE = 36;
    public static final int MAX_BACKPACK_ROWS = 4;

    /** Рюкзак: сюди складається видобуте й зібране; звідси береться їжа для тварин. */
    public final SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
    public int backpackRows = 1;

    // ---- Налаштування з меню ----
    public boolean autoAttack = true;
    public boolean autoFeed = true;
    public boolean autoSkill = true;

    // ---- Завдання ----
    private CompanionTask task = CompanionTask.FOLLOW;
    private @Nullable BlockPos workPos;
    private @Nullable BlockPos storagePos;
    private @Nullable BlockPos sourcePos;
    private Direction tunnelDir = Direction.NORTH;
    /** Виділена посохом область роботи (кути включно) або null — тоді робота «в точці». */
    private @Nullable BlockPos areaMin;
    private @Nullable BlockPos areaMax;
    /** Поточний шар кар'єру (видобуток в області йде зверху вниз). */
    public int mineLayer = Integer.MIN_VALUE;
    /** Скільки блоків прокопано штольнею (0 = на цьому місці штольні копати нічого). */
    public int tunnelDug = 0;
    /** Область видобутку повністю викопана: віднести залишки в сундук і повернутися до власника. */
    public boolean mineDone = false;
    /** Сундук-склад заповнений: DepositGoal не зміг вивантажити все. */
    public boolean storageFull = false;
    /** HaulGoal виставляє true, коли збирати більше нічого (тоді DepositGoal несе все в сундук). */
    public boolean haulIdle = false;
    private @Nullable Component lastNotice;
    private int lastNoticeTick = -100000;

    // ---- Рівень ----
    private int companionLevel = 1;
    private int xp = 0;
    private boolean playerNamed = false;
    private boolean settingAutoName = false;

    // ---- Бій ----
    private int skillCooldown = 0;
    private @Nullable UUID orderedTargetId;
    private int orderedTicks = 0;
    private @Nullable UUID duelOpponentId;
    private int duelTicks = 0;

    public HerobrineCompanion(EntityType<? extends HerobrineCompanion> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // Усе спорядження гарантовано випадає при смерті (і не псується), хто б не вбив компаньйона
        for (EquipmentSlot slot : GEAR_SLOTS) {
            this.setGuaranteedDrop(slot);
        }
        applyLevelStats(); // щоб і заспавнений диспенсером мав коректні характеристики
        this.setHealth(this.getMaxHealth());
    }

    /** Базові значення невеликі: решту дає рівень, зброя й броня. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 0.0);
    }

    // =====================================================================
    // ШІ
    // =====================================================================

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // Ванільна ціль садить приручених, коли власник офлайн або в іншому вимірі — тоді робота
        // (видобуток, перенесення, охорона) зупинялася б. Сидимо лише за наказом «Стояти».
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this) {
            @Override
            public boolean canUse() {
                return isOrderedToSit() && super.canUse();
            }
        });
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(5, new TaskFollowOwnerGoal(this, 1.1, 10.0F, 2.0F));
        this.goalSelector.addGoal(5, new DepositGoal(this));
        this.goalSelector.addGoal(6, new MineGoal(this));
        this.goalSelector.addGoal(6, new HaulGoal(this));
        this.goalSelector.addGoal(7, new FeedAnimalsGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return (task == CompanionTask.FOLLOW || task == CompanionTask.GUARD) && super.canUse();
            }
        });
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new CommandTargetGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity attacker = mob.getLastHurtByMob();
                return attacker != null && !isFriendly(attacker) && super.canUse();
            }
        });
        // Охорона: будь-який ворожий моб біля поста
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this, Monster.class, 10, false, false,
                (target, level) -> task == CompanionTask.GUARD && workPos != null
                        && !(target instanceof Creeper) && isInGuardZone(target.position())));
        // Автоатака ворожих мобів поблизу (не кріпери і не боси — на боса треба наказ або удар власника)
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(
                this, Monster.class, 10, true, false,
                (target, level) -> this.autoAttack && task != CompanionTask.STAY
                        && !(target instanceof Creeper) && !(target instanceof HerobrineBoss)
                        && target.distanceToSqr(this) < 14 * 14));
    }

    /** Свої: власник, його компаньйони та приручені тварини (по них не б'ємо у відповідь). */
    public boolean isFriendly(LivingEntity entity) {
        if (entity == this.getOwner()) {
            return true;
        }
        if (entity instanceof HerobrineCompanion other && entity != getDuelOpponent()) {
            return other.getOwner() != null && other.getOwner() == this.getOwner();
        }
        return entity instanceof TamableAnimal tamable && tamable.getOwner() != null && tamable.getOwner() == this.getOwner();
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !isFriendly(target) && pvpAllows(target);
    }

    /** Як у вовків: на гравців нападаємо, лише якщо на сервері дозволено PvP (і власник може їх бити). */
    public boolean pvpAllows(LivingEntity target) {
        return !(target instanceof Player victim) || !(this.getOwner() instanceof Player owner) || owner.canHarmPlayer(victim);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        // Охорона: «прив'язка» до поста або центру області (не діє, поки виконується наказ або дуель)
        boolean wantHome = task == CompanionTask.GUARD && workPos != null && !hasCommandTarget();
        if (wantHome) {
            BlockPos home = guardCenter();
            int radius = guardRadius();
            if (!this.hasHome() || !this.getHomePosition().equals(home) || this.getHomeRadius() != radius) {
                this.setHomeTo(home, radius);
            }
        } else if (this.hasHome()) {
            this.clearHome();
        }

        // Кар'єр викопано: залишки вже в сундуку (або нема куди нести) — повертаємось до власника
        if (task == CompanionTask.MINE && mineDone && this.tickCount % 20 == 0
                && (!hasAnyBackpackItem() || storageFull || com.herobrine.ritual.goal.JobUtil.resolveStorage(this) == null)) {
            setTask(CompanionTask.FOLLOW, null, null);
        }

        // Наказ атакувати має термін дії
        if (orderedTargetId != null && --orderedTicks <= 0) {
            orderedTargetId = null;
        }
        if (duelOpponentId != null) {
            duelTick(level);
        }

        if (autoSkill && skillCooldown == 0 && this.tickCount % 10 == 0) {
            tryAutoSkill();
        }
    }

    // =====================================================================
    // Взаємодія
    // =====================================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Нічий Херобрін (заспавнений командою чи диспенсером): ПКМ порожньою рукою — забрати собі
        if (!this.isTame() && this.getOwnerReference() == null && stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (this.level() instanceof ServerLevel level) {
                this.tame(player);
                player.addTag(getVariant().caughtTag());
                HerobrineUtil.burst(level, this, ParticleTypes.HEART, 8);
                HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.claimed", this.getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }

        if (this.isTame() && this.isOwnedBy(player)) {
            // Посох і сфера мають власну логіку (interactLivingEntity у предметі)
            if (stack.is(HerobrineMod.COMMAND_STAFF.get()) || stack.is(HerobrineMod.HEROBRINE_ORB.get())) {
                return InteractionResult.PASS;
            }
            // Апгрейд рюкзака: ПКМ предметом по компаньйону
            if (stack.is(HerobrineMod.BACKPACK_UPGRADE.get())) {
                if (this.level() instanceof ServerLevel) {
                    if (this.backpackRows < MAX_BACKPACK_ROWS) {
                        this.backpackRows++;
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        player.sendSystemMessage(Component.translatable(
                                "message.herobrine_ritual.backpack_upgraded", this.backpackRows * 9));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.herobrine_ritual.backpack_max"));
                    }
                }
                return InteractionResult.SUCCESS;
            }
            // Shift + ПКМ: відкрити меню (завдання, броня, зброя, рюкзак)
            if (player.isSecondaryUseActive()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    openMenu(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            if (isHealingFood(stack) && this.getHealth() < this.getMaxHealth()) {
                if (this.level() instanceof ServerLevel) {
                    this.heal(stack.is(Items.GOLDEN_APPLE) ? 20.0F : 8.0F);
                    stack.consume(1, player);
                }
                return InteractionResult.SUCCESS;
            }
            if (hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
                if (this.level() instanceof ServerLevel) {
                    setTask(task == CompanionTask.STAY ? CompanionTask.FOLLOW : CompanionTask.STAY, null, null);
                    HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.task_set",
                            this.getDisplayName(), Component.translatable(task.translationKey())));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    public void openMenu(ServerPlayer player) {
        final HerobrineCompanion self = this;
        player.openMenu(
                new SimpleMenuProvider((id, inv, p) -> new CompanionMenu(
                        id, inv, self, self.backpackRows, self.createMenuData()),
                        this.getDisplayName()),
                buf -> {
                    buf.writeVarInt(self.getId());
                    buf.writeVarInt(self.backpackRows);
                });
    }

    private static boolean isHealingFood(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_MUTTON) || stack.is(Items.COOKED_CHICKEN) || stack.is(Items.BREAD);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false; // не розмножується
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // =====================================================================
    // Вид, ім'я, рівень
    // =====================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_TASK, 0);
        builder.define(DATA_WORK_POS, Optional.empty());
        builder.define(DATA_AREA_MIN, Optional.empty());
        builder.define(DATA_AREA_MAX, Optional.empty());
        builder.define(DATA_STORAGE_POS, Optional.empty());
        builder.define(DATA_SOURCE_POS, Optional.empty());
    }

    @Override
    public HerobrineVariant getVariant() {
        return HerobrineVariant.byOrdinal(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(HerobrineVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
        updateName();
    }

    public int getCompanionLevel() {
        return companionLevel;
    }

    public int getXp() {
        return xp;
    }

    public int xpToNextLevel() {
        return 30 + companionLevel * 20;
    }

    public void setCompanionLevel(int level) {
        this.companionLevel = Math.max(1, Math.min(MAX_LEVEL, level));
        this.xp = 0;
        applyLevelStats();
        updateName();
    }

    public void addXp(int amount) {
        if (companionLevel >= MAX_LEVEL || amount <= 0 || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        xp += amount;
        boolean leveled = false;
        while (companionLevel < MAX_LEVEL && xp >= xpToNextLevel()) {
            xp -= xpToNextLevel();
            companionLevel++;
            leveled = true;
        }
        if (companionLevel >= MAX_LEVEL) {
            xp = 0;
        }
        if (leveled) {
            applyLevelStats();
            this.setHealth(this.getMaxHealth());
            updateName();
            HerobrineUtil.burst(level, this, ParticleTypes.HAPPY_VILLAGER, 25);
            level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8F, 0.8F);
            HerobrineUtil.chat(this.getOwner(), Component.translatable("message.herobrine_ritual.level_up",
                    Component.translatable(getVariant().translationKey()).withStyle(ChatFormatting.GOLD), companionLevel));
        }
    }

    private void applyLevelStats() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrReplacePermanentModifier(new AttributeModifier(
                    LEVEL_HEALTH_ID, 4.0 * (companionLevel - 1), AttributeModifier.Operation.ADD_VALUE));
        }
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.addOrReplacePermanentModifier(new AttributeModifier(
                    LEVEL_ATTACK_ID, 0.35 * (companionLevel - 1), AttributeModifier.Operation.ADD_VALUE));
        }
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    /** Ім'я «Вид · Рів. N», якщо гравець не перейменував бирку. */
    private void updateName() {
        if (playerNamed) {
            return;
        }
        settingAutoName = true;
        this.setCustomName(Component.translatable(getVariant().translationKey())
                .append(Component.translatable("gui.herobrine_ritual.level_suffix", companionLevel)));
        settingAutoName = false;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (!settingAutoName && this.level() != null && !this.level().isClientSide()) {
            playerNamed = name != null; // перейменовано биркою
        }
    }

    // =====================================================================
    // Завдання
    // =====================================================================

    public CompanionTask getTask() {
        return task;
    }

    public @Nullable BlockPos getWorkPos() {
        return workPos;
    }

    public Direction getTunnelDir() {
        return tunnelDir;
    }

    public @Nullable BlockPos getStoragePos() {
        return storagePos;
    }

    public void setStoragePos(@Nullable BlockPos pos) {
        this.storagePos = pos == null ? null : pos.immutable();
        syncJobData();
    }

    public @Nullable BlockPos getSourcePos() {
        return sourcePos;
    }

    public void setSourcePos(@Nullable BlockPos pos) {
        this.sourcePos = pos == null ? null : pos.immutable();
        syncJobData();
    }

    /**
     * Змінює завдання «в точці». Для охорони/видобутку/перенесення точка роботи = pos (або поточне місце),
     * напрямок штольні = facing (або куди дивиться компаньйон). Виділена область скидається.
     */
    public void setTask(CompanionTask newTask, @Nullable BlockPos pos, @Nullable Direction facing) {
        this.task = newTask;
        this.setOrderedToSit(newTask == CompanionTask.STAY);
        if (newTask == CompanionTask.GUARD || newTask == CompanionTask.MINE || newTask == CompanionTask.HAUL) {
            this.workPos = (pos != null ? pos : this.blockPosition()).immutable();
        }
        if (newTask == CompanionTask.MINE) {
            Direction dir = facing != null ? facing : this.getDirection();
            this.tunnelDir = dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
        }
        this.areaMin = null;
        this.areaMax = null;
        resetJobState();
        if (pos != null && newTask != CompanionTask.STAY && this.level() instanceof ServerLevel) {
            this.navigation.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
        }
        syncJobData();
    }

    /** Після виходу зі сфери: старі точки роботи й сундуки можуть бути за кілометри — усе скидаємо. */
    public void resetAfterRelease() {
        this.storagePos = null;
        this.sourcePos = null;
        this.orderedTargetId = null;
        clearDuelState();
        setTask(CompanionTask.FOLLOW, null, null);
    }

    /** Завдання в області між двома кутами (кути включно). */
    public void setTaskInArea(CompanionTask newTask, BlockPos a, BlockPos b) {
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        this.task = newTask;
        this.setOrderedToSit(false);
        this.areaMin = min.immutable();
        this.areaMax = max.immutable();
        this.workPos = new BlockPos((min.getX() + max.getX()) / 2, max.getY() + 1, (min.getZ() + max.getZ()) / 2);
        resetJobState();
        if (this.level() instanceof ServerLevel) {
            this.navigation.moveTo(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5, 1.1);
        }
        syncJobData();
    }

    private void resetJobState() {
        this.haulIdle = false;
        this.mineDone = false;
        this.storageFull = false;
        this.tunnelDug = 0;
        this.mineLayer = areaMax != null ? areaMax.getY() : Integer.MIN_VALUE;
        this.lastNotice = null;
        this.navigation.stop();
        this.setTarget(null);
        if (this.hasHome()) {
            this.clearHome();
        }
    }

    public boolean hasArea() {
        return areaMin != null && areaMax != null;
    }

    public @Nullable BlockPos getAreaMin() {
        return areaMin;
    }

    public @Nullable BlockPos getAreaMax() {
        return areaMax;
    }

    /** Чи точка всередині області (з запасом margin блоків). */
    public boolean isInArea(Vec3 pos, double margin) {
        return hasArea()
                && pos.x >= areaMin.getX() - margin && pos.x <= areaMax.getX() + 1 + margin
                && pos.y >= areaMin.getY() - margin - 2 && pos.y <= areaMax.getY() + 3 + margin
                && pos.z >= areaMin.getZ() - margin && pos.z <= areaMax.getZ() + 1 + margin;
    }

    private BlockPos guardCenter() {
        return workPos;
    }

    private int guardRadius() {
        if (!hasArea()) {
            return GUARD_RADIUS;
        }
        int half = Math.max(areaMax.getX() - areaMin.getX(), areaMax.getZ() - areaMin.getZ()) / 2;
        return half + 3;
    }

    /** Зона охорони: виділена область (+2 блоки) або коло радіусом GUARD_RADIUS навколо поста. */
    public boolean isInGuardZone(Vec3 pos) {
        if (hasArea()) {
            return isInArea(pos, 2.0);
        }
        return workPos != null && pos.distanceToSqr(Vec3.atCenterOf(workPos)) < (GUARD_RADIUS + 2) * (GUARD_RADIUS + 2);
    }

    private void syncJobData() {
        this.entityData.set(DATA_TASK, task.ordinal());
        this.entityData.set(DATA_WORK_POS, Optional.ofNullable(workPos));
        this.entityData.set(DATA_AREA_MIN, Optional.ofNullable(areaMin));
        this.entityData.set(DATA_AREA_MAX, Optional.ofNullable(areaMax));
        this.entityData.set(DATA_STORAGE_POS, Optional.ofNullable(storagePos));
        this.entityData.set(DATA_SOURCE_POS, Optional.ofNullable(sourcePos));
    }

    // ---- Для клієнта (малювання сітки) ----

    public CompanionTask clientTask() {
        return CompanionTask.byOrdinal(this.entityData.get(DATA_TASK));
    }

    public Optional<BlockPos> clientWorkPos() {
        return this.entityData.get(DATA_WORK_POS);
    }

    public Optional<BlockPos> clientAreaMin() {
        return this.entityData.get(DATA_AREA_MIN);
    }

    public Optional<BlockPos> clientAreaMax() {
        return this.entityData.get(DATA_AREA_MAX);
    }

    public Optional<BlockPos> clientStoragePos() {
        return this.entityData.get(DATA_STORAGE_POS);
    }

    public Optional<BlockPos> clientSourcePos() {
        return this.entityData.get(DATA_SOURCE_POS);
    }

    /**
     * Повідомлення власнику з робочих цілей. Те саме повідомлення не повторюється частіше ніж раз на хвилину,
     * а будь-які — не частіше ніж раз на 5 секунд.
     */
    public void notifyOwner(Component message) {
        int since = this.tickCount - lastNoticeTick;
        if (since < 100 || (message.equals(lastNotice) && since < 1200)) {
            return;
        }
        lastNotice = message;
        lastNoticeTick = this.tickCount;
        HerobrineUtil.chat(this.getOwner(), Component.empty()
                .append(this.getDisplayName().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": "))
                .append(message));
    }

    // ---- Рюкзак (з урахуванням відкритих рядків) ----

    public int unlockedSlots() {
        return backpackRows * 9;
    }

    public boolean hasAnyBackpackItem() {
        for (int i = 0; i < unlockedSlots(); i++) {
            if (!backpack.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFreeBackpackSlot() {
        for (int i = 0; i < unlockedSlots(); i++) {
            if (backpack.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Кладе предмет у відкриті слоти рюкзака. Повертає те, що не вмістилося. */
    public ItemStack insertIntoBackpack(ItemStack stack) {
        ItemStack rest = stack.copy();
        int slots = unlockedSlots();
        for (int i = 0; i < slots && !rest.isEmpty(); i++) {
            ItemStack existing = backpack.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, rest)) {
                int move = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    rest.shrink(move);
                }
            }
        }
        for (int i = 0; i < slots && !rest.isEmpty(); i++) {
            if (backpack.getItem(i).isEmpty()) {
                backpack.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        backpack.setChanged();
        return rest;
    }

    /** Чи влізе хоч щось із цього предмета. */
    public boolean canFit(ItemStack stack) {
        for (int i = 0; i < unlockedSlots(); i++) {
            ItemStack existing = backpack.getItem(i);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Вивантажує рюкзак у контейнер. Повертає true, якщо хоч щось перенесено. */
    public boolean depositInto(Container container) {
        boolean moved = false;
        for (int i = 0; i < unlockedSlots(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = HopperBlockEntity.addItem(null, container, stack.copy(), null);
            if (rest.getCount() != before) {
                moved = true;
            }
            backpack.setItem(i, rest);
        }
        if (moved) {
            container.setChanged();
            backpack.setChanged();
        }
        return moved;
    }

    // =====================================================================
    // Накази та дуелі
    // =====================================================================

    public boolean hasCommandTarget() {
        return orderedTargetId != null || duelOpponentId != null;
    }

    /** Ціль, яку компаньйон мусить атакувати: суперник у дуелі або ціль наказу. */
    public @Nullable LivingEntity resolveCommandTarget() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        if (duelOpponentId != null) {
            return getDuelOpponent();
        }
        if (orderedTargetId != null) {
            Entity e = level.getEntity(orderedTargetId);
            if (e instanceof LivingEntity living && living.isAlive() && living != this.getOwner()
                    && living.distanceToSqr(this) < 64 * 64) {
                return living;
            }
            orderedTargetId = null;
        }
        return null;
    }

    /** Наказ «атакувати ціль» (посох). */
    public void orderAttack(LivingEntity target) {
        if (target == this || target == this.getOwner()) {
            return;
        }
        if (task == CompanionTask.STAY) {
            setTask(CompanionTask.FOLLOW, null, null);
        }
        this.orderedTargetId = target.getUUID();
        this.orderedTicks = ORDER_SECONDS * 20;
        this.setTarget(target);
    }

    public boolean isInDuel() {
        return duelOpponentId != null;
    }

    public @Nullable HerobrineCompanion getDuelOpponent() {
        if (duelOpponentId == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity e = level.getEntity(duelOpponentId);
        return e instanceof HerobrineCompanion c && c.isAlive() ? c : null;
    }

    /** Тренувальна дуель двох Херобрінів: б'ються до ~1 HP, ніхто не гине. */
    public static boolean startDuel(HerobrineCompanion a, HerobrineCompanion b) {
        if (a == b || a.isInDuel() || b.isInDuel() || !(a.level() instanceof ServerLevel level)) {
            return false;
        }
        for (HerobrineCompanion c : new HerobrineCompanion[]{a, b}) {
            c.orderedTargetId = null;
            c.setOrderedToSit(false);
            c.duelTicks = DUEL_SECONDS * 20;
            if (c.hasHome()) {
                c.clearHome();
            }
        }
        a.duelOpponentId = b.getUUID();
        b.duelOpponentId = a.getUUID();
        a.setTarget(b);
        b.setTarget(a);
        Component msg = Component.translatable("message.herobrine_ritual.duel_start", a.getDisplayName(), b.getDisplayName())
                .withStyle(ChatFormatting.YELLOW);
        HerobrineUtil.chat(a.getOwner(), msg);
        if (b.getOwner() != a.getOwner()) {
            HerobrineUtil.chat(b.getOwner(), msg);
        }
        level.playSound(null, a.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 1.0F, 0.8F);
        HerobrineUtil.burst(level, a, ParticleTypes.CRIT, 20);
        HerobrineUtil.burst(level, b, ParticleTypes.CRIT, 20);
        return true;
    }

    private void duelTick(ServerLevel level) {
        HerobrineCompanion opponent = getDuelOpponent();
        if (opponent == null || opponent.distanceToSqr(this) > 40 * 40) {
            clearDuelState();
            return;
        }
        if (--duelTicks <= 0) {
            finishDuel(opponent, null);
        }
    }

    /** Завершує дуель обох учасників. winner == null означає нічию. */
    private void finishDuel(HerobrineCompanion opponent, @Nullable HerobrineCompanion winner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (HerobrineCompanion c : new HerobrineCompanion[]{this, opponent}) {
            c.clearDuelState();
            c.setTarget(null);
            c.setLastHurtByMob(null);
            c.clearFire();
            c.removeEffect(MobEffects.POISON);
            c.removeEffect(MobEffects.WITHER);
            c.removeEffect(MobEffects.SLOWNESS);
            c.removeEffect(MobEffects.BLINDNESS);
            c.removeEffect(MobEffects.WEAKNESS);
            c.heal(c.getMaxHealth() * 0.3F);
            c.setOrderedToSit(c.task == CompanionTask.STAY);
        }
        Component msg;
        if (winner == null) {
            msg = Component.translatable("message.herobrine_ritual.duel_draw", this.getDisplayName(), opponent.getDisplayName());
            this.addXp(20);
            opponent.addXp(20);
        } else {
            HerobrineCompanion loser = winner == this ? opponent : this;
            msg = Component.translatable("message.herobrine_ritual.duel_won", winner.getDisplayName(), loser.getDisplayName());
            winner.addXp(40 + loser.companionLevel * 3);
            loser.addXp(15);
            HerobrineUtil.burst(level, winner, ParticleTypes.TOTEM_OF_UNDYING, 30);
            level.playSound(null, winner.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.4F);
        }
        msg = msg.copy().withStyle(ChatFormatting.YELLOW);
        HerobrineUtil.chat(this.getOwner(), msg);
        if (opponent.getOwner() != this.getOwner()) {
            HerobrineUtil.chat(opponent.getOwner(), msg);
        }
    }

    private void clearDuelState() {
        duelOpponentId = null;
        duelTicks = 0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInDuel()) {
            HerobrineCompanion opponent = getDuelOpponent();
            if (opponent == null) {
                clearDuelState();
            } else {
                Entity attacker = source.getEntity();
                if (attacker != null && attacker != opponent) {
                    // Хтось сторонній втрутився: дуель скасовується, і обидва знову можуть захищатися
                    interruptDuel(opponent);
                } else if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                        && !wouldIgnoreDamage(level, source, amount)
                        && this.getHealth() - amount <= 2.0F) {
                    // У дуелі не можна загинути: від удару суперника або від вогню/отрути під час бою
                    this.setHealth(Math.max(1.0F, Math.min(this.getHealth(), 2.0F)));
                    finishDuel(opponent, opponent);
                    return false;
                }
            }
        }
        return super.hurtServer(level, source, amount);
    }

    /** Чи ванільна логіка все одно проігнорує цей удар (невразливість, вогнестійкість, кадри невразливості). */
    public boolean wouldIgnoreDamage(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableTo(level, source) || this.isDeadOrDying()) {
            return true;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return true;
        }
        return this.getInvulnerableTime() > 10 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)
                && amount <= this.lastHurt;
    }

    private void interruptDuel(HerobrineCompanion opponent) {
        for (HerobrineCompanion c : new HerobrineCompanion[]{this, opponent}) {
            c.clearDuelState();
            if (c.getTarget() == this || c.getTarget() == opponent) {
                c.setTarget(null);
            }
        }
        Component msg = Component.translatable("message.herobrine_ritual.duel_interrupted",
                this.getDisplayName(), opponent.getDisplayName()).withStyle(ChatFormatting.GRAY);
        HerobrineUtil.chat(this.getOwner(), msg);
        if (opponent.getOwner() != this.getOwner()) {
            HerobrineUtil.chat(opponent.getOwner(), msg);
        }
    }

    // =====================================================================
    // Пасивки та активні здібності
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (this.skillCooldown > 0) {
            this.skillCooldown--;
        }
        if (this.tickCount % 40 == 0) {
            applyPassive();
        }
    }

    /** Пасивні ефекти виду (оновлюються кожні 2 секунди). */
    private void applyPassive() {
        LivingEntity owner = this.getOwner();
        boolean ownerNear = owner != null && owner.distanceToSqr(this) < 10.0 * 10.0;
        switch (getVariant()) {
            case CLASSIC -> selfEffect(MobEffects.STRENGTH, 0);
            case NETHER -> {
                selfEffect(MobEffects.FIRE_RESISTANCE, 0);
                if (ownerNear) {
                    owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false));
                }
            }
            case ENDER -> selfEffect(MobEffects.SLOW_FALLING, 0);
            case FROST -> selfEffect(MobEffects.SPEED, 0);
            case FOREST -> selfEffect(MobEffects.REGENERATION, 0);
            case GHOST -> selfEffect(MobEffects.RESISTANCE, 0);
            case STORM -> {
                selfEffect(MobEffects.SPEED, 0);
                if (this.level().isThundering()) {
                    selfEffect(MobEffects.STRENGTH, 1);
                }
            }
            case DESERT -> {
                selfEffect(MobEffects.FIRE_RESISTANCE, 0);
                selfEffect(MobEffects.JUMP_BOOST, 0);
                if (ownerNear) {
                    owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0, true, false));
                }
            }
            case OCEAN -> {
                selfEffect(MobEffects.WATER_BREATHING, 0);
                selfEffect(MobEffects.DOLPHINS_GRACE, 0);
                if (ownerNear && owner.isInWater()) {
                    owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false));
                    owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 0, true, false));
                    owner.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, false));
                }
            }
            case MINER -> {
                selfEffect(MobEffects.HASTE, 1);
                if (ownerNear) {
                    owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 100, 0, true, false));
                    if (!this.level().canSeeSky(owner.blockPosition())) {
                        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
                    }
                }
            }
        }
    }

    private void selfEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        this.addEffect(new MobEffectInstance(effect, 100, amplifier, true, false));
    }

    /** Удар здібністю: скидаємо кадри невразливості, інакше здібність одразу після удару рукою «згорає». */
    private void skillHit(LivingEntity victim, DamageSource dmg, float amount) {
        if (this.level() instanceof ServerLevel level) {
            victim.setInvulnerableTime(0);
            victim.hurtServer(level, dmg, amount);
        }
    }

    public int getSkillCooldown() {
        return skillCooldown;
    }

    private void tryAutoSkill() {
        LivingEntity owner = this.getOwner();
        if (getVariant() == HerobrineVariant.FOREST) {
            boolean ownerHurt = owner != null && owner.distanceToSqr(this) < 12 * 12
                    && owner.getHealth() < owner.getMaxHealth() * 0.5F;
            if (ownerHurt || this.getHealth() < this.getMaxHealth() * 0.4F) {
                useSkill(true);
            }
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && target.distanceToSqr(this) < 8 * 8) {
            useSkill(true);
        }
    }

    /** Вороги в радіусі: ворожі моби, поточна ціль, суперник у дуелі. Без своїх і без чужих гравців. */
    private List<LivingEntity> gatherEnemies(ServerLevel level, double radius) {
        LivingEntity target = this.getTarget();
        HerobrineCompanion duel = getDuelOpponent();
        List<LivingEntity> result = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius, 4.0, radius),
                e -> e != this && e.isAlive()
                        && (e == target || e == duel || (e instanceof Monster && !isFriendly(e)))));
        result.sort(Comparator.comparingDouble(this::distanceToSqr));
        return result;
    }

    /**
     * Активна здібність виду. silent = автоматичне використання (без повідомлень).
     * Перезарядка витрачається лише при успіху.
     */
    public boolean useSkill(boolean silent) {
        if (!(this.level() instanceof ServerLevel level) || this.skillCooldown > 0) {
            if (!silent) {
                HerobrineUtil.chat(this.getOwner(), Component.translatable("message.herobrine_ritual.skill_no_target"));
            }
            return false;
        }
        LivingEntity owner = this.getOwner();
        List<LivingEntity> enemies = gatherEnemies(level, 8.0);
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || target.distanceToSqr(this) > 16 * 16) {
            target = enemies.isEmpty() ? null : enemies.get(0);
        }
        float power = 1.0F + companionLevel * 0.03F;
        DamageSource dmg = level.damageSources().mobAttack(this);

        boolean ok = switch (getVariant()) {
            case CLASSIC -> {
                if (target == null) {
                    yield false;
                }
                HerobrineUtil.visualLightning(level, target.getX(), target.getY(), target.getZ());
                skillHit(target, dmg, 12.0F * power);
                yield true;
            }
            case NETHER -> {
                if (enemies.isEmpty()) {
                    yield false;
                }
                for (LivingEntity e : enemies) {
                    e.igniteForSeconds(6.0F);
                    HerobrineUtil.burst(level, e, ParticleTypes.FLAME, 15);
                    skillHit(e, dmg, 6.0F * power);
                }
                yield true;
            }
            case ENDER -> {
                if (target == null) {
                    yield false;
                }
                Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.5, 0, 1.5));
                level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0.3);
                this.randomTeleport(behind.x, target.getY(), behind.z, true, state -> false);
                skillHit(target, dmg, 10.0F * power);
                yield true;
            }
            case FROST -> {
                if (enemies.isEmpty()) {
                    yield false;
                }
                for (LivingEntity e : enemies) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 2));
                    HerobrineUtil.burst(level, e, ParticleTypes.SNOWFLAKE, 15);
                    skillHit(e, dmg, 4.0F * power);
                }
                yield true;
            }
            case FOREST -> {
                if (owner != null && owner.distanceToSqr(this) < 16 * 16) {
                    owner.heal(10.0F * power);
                    owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                    HerobrineUtil.burst(level, owner, ParticleTypes.HEART, 8);
                }
                this.heal(10.0F * power);
                HerobrineUtil.burst(level, this, ParticleTypes.HAPPY_VILLAGER, 15);
                yield true;
            }
            case GHOST -> {
                this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, true, false));
                this.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1, true, false));
                for (LivingEntity e : enemies) {
                    if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this) {
                        mob.setTarget(null);
                    }
                }
                HerobrineUtil.burst(level, this, ParticleTypes.SOUL, 20);
                yield true;
            }
            case STORM -> {
                // Ланцюгова блискавка: до 4 ворогів у радіусі 10
                List<LivingEntity> chain = gatherEnemies(level, 10.0);
                if (chain.isEmpty()) {
                    yield false;
                }
                int hits = 0;
                for (LivingEntity e : chain) {
                    if (hits++ >= 4) {
                        break;
                    }
                    HerobrineUtil.visualLightning(level, e.getX(), e.getY(), e.getZ());
                    skillHit(e, dmg, 8.0F * power);
                }
                yield true;
            }
            case DESERT -> {
                // Піщана буря
                if (enemies.isEmpty()) {
                    yield false;
                }
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 80, 4.0, 1.0, 4.0, 0.05);
                level.playSound(null, this.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.NEUTRAL, 1.5F, 0.6F);
                for (LivingEntity e : enemies) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                    skillHit(e, dmg, 5.0F * power);
                }
                yield true;
            }
            case OCEAN -> {
                // Хвиля: відкидає всіх ворогів і дає власнику дихання під водою
                if (enemies.isEmpty()) {
                    yield false;
                }
                level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 120, 4.0, 0.5, 4.0, 0.2);
                for (LivingEntity e : enemies) {
                    Vec3 away = e.position().subtract(this.position()).normalize().scale(1.6);
                    e.push(away.x, 0.45, away.z);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                    skillHit(e, dmg, 6.0F * power);
                }
                if (owner != null) {
                    owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0));
                    owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 600, 0));
                }
                yield true;
            }
            case MINER -> {
                // Землетрус: підкидає ворогів, а власник отримує Поспіх II
                if (enemies.isEmpty()) {
                    yield false;
                }
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 4, 2.0, 0.1, 2.0, 0.0);
                level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
                for (LivingEntity e : enemies) {
                    e.push(0.0, 0.9, 0.0);
                    skillHit(e, dmg, 7.0F * power);
                }
                if (owner != null) {
                    owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 1));
                }
                yield true;
            }
        };

        if (ok) {
            this.skillCooldown = getVariant().skillCooldownTicks();
            this.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        } else if (!silent) {
            HerobrineUtil.chat(owner, Component.translatable("message.herobrine_ritual.skill_no_target"));
        }
        return ok;
    }

    // =====================================================================
    // Команди з меню
    // =====================================================================

    public static final int CMD_CYCLE_TASK = 0;
    public static final int CMD_COME = 1;
    public static final int CMD_TOGGLE_ATTACK = 2;
    public static final int CMD_TOGGLE_FEED = 3;
    public static final int CMD_SKILL = 4;
    public static final int CMD_TOGGLE_AUTOSKILL = 5;

    /** Виконує команду з меню. Викликається на сервері. */
    public void runCommand(int command, Player owner) {
        switch (command) {
            case CMD_CYCLE_TASK -> {
                CompanionTask next = task.next();
                setTask(next, null, owner.getDirection());
                if (next == CompanionTask.MINE || next == CompanionTask.HAUL || next == CompanionTask.GUARD) {
                    HerobrineUtil.actionBar(owner, Component.translatable("message.herobrine_ritual.task_here." + next.id()));
                }
            }
            case CMD_COME -> {
                setTask(CompanionTask.FOLLOW, null, null);
                this.orderedTargetId = null;
                this.randomTeleport(owner.getX() + 1.0, owner.getY(), owner.getZ() + 1.0, true, state -> false);
            }
            case CMD_TOGGLE_ATTACK -> {
                this.autoAttack = !this.autoAttack;
                if (!this.autoAttack) {
                    this.setTarget(null);
                }
            }
            case CMD_TOGGLE_FEED -> this.autoFeed = !this.autoFeed;
            case CMD_SKILL -> this.useSkill(false);
            case CMD_TOGGLE_AUTOSKILL -> this.autoSkill = !this.autoSkill;
            default -> {
            }
        }
    }

    /** Стан для меню (синхронізується на клієнт; значення мають влазити в short). */
    public ContainerData createMenuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                HerobrineCompanion c = HerobrineCompanion.this;
                return switch (index) {
                    case 0 -> c.task.ordinal();
                    case 1 -> c.autoAttack ? 1 : 0;
                    case 2 -> c.autoFeed ? 1 : 0;
                    case 3 -> (c.skillCooldown + 19) / 20; // секунди до готовності
                    case 4 -> c.getVariant().ordinal();
                    case 5 -> c.companionLevel;
                    case 6 -> c.xp;
                    case 7 -> c.xpToNextLevel();
                    case 8 -> c.autoSkill ? 1 : 0;
                    case 9 -> (int) Math.ceil(c.getHealth());
                    case 10 -> (int) Math.ceil(c.getMaxHealth());
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return CompanionMenu.DATA_COUNT;
            }
        };
    }

    // =====================================================================
    // Збереження
    // =====================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getVariant().ordinal());
        output.putInt("BackpackRows", this.backpackRows);
        output.putBoolean("AutoAttack", this.autoAttack);
        output.putBoolean("AutoFeed", this.autoFeed);
        output.putBoolean("AutoSkill", this.autoSkill);
        output.putInt("Task", this.task.ordinal());
        output.putLong("WorkPos", workPos == null ? NO_POS : workPos.asLong());
        output.putLong("StoragePos", storagePos == null ? NO_POS : storagePos.asLong());
        output.putLong("SourcePos", sourcePos == null ? NO_POS : sourcePos.asLong());
        output.putLong("AreaMin", areaMin == null ? NO_POS : areaMin.asLong());
        output.putLong("AreaMax", areaMax == null ? NO_POS : areaMax.asLong());
        output.putInt("MineLayer", this.mineLayer);
        output.putInt("TunnelDug", this.tunnelDug);
        output.putBoolean("MineDone", this.mineDone);
        output.putInt("TunnelDir", this.tunnelDir.get3DDataValue());
        output.putInt("CompanionLevel", this.companionLevel);
        output.putInt("Xp", this.xp);
        output.putBoolean("PlayerNamed", this.playerNamed);
        ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list("Backpack", ItemStackWithSlot.CODEC);
        for (int i = 0; i < this.backpack.getContainerSize(); i++) {
            ItemStack stack = this.backpack.getItem(i);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(i, stack));
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Mob при завантаженні перезаписує шанси випадіння (зі збереження або 8,5% за замовчуванням)
        for (EquipmentSlot slot : GEAR_SLOTS) {
            this.setGuaranteedDrop(slot);
        }
        this.entityData.set(DATA_VARIANT, HerobrineVariant.byOrdinal(input.getIntOr("Variant", 0)).ordinal());
        this.backpackRows = Math.max(1, Math.min(MAX_BACKPACK_ROWS, input.getIntOr("BackpackRows", 1)));
        this.autoAttack = input.getBooleanOr("AutoAttack", true);
        this.autoFeed = input.getBooleanOr("AutoFeed", true);
        this.autoSkill = input.getBooleanOr("AutoSkill", true);
        // Старі збереження (1.x) не мають завдання: беремо з того, чи сидить
        this.task = CompanionTask.byOrdinal(input.getIntOr("Task", this.isOrderedToSit() ? 1 : 0));
        this.workPos = readPos(input, "WorkPos");
        this.storagePos = readPos(input, "StoragePos");
        this.sourcePos = readPos(input, "SourcePos");
        this.areaMin = readPos(input, "AreaMin");
        this.areaMax = readPos(input, "AreaMax");
        if (this.areaMin == null || this.areaMax == null) {
            this.areaMin = null;
            this.areaMax = null;
        }
        this.mineLayer = input.getIntOr("MineLayer", areaMax != null ? areaMax.getY() : Integer.MIN_VALUE);
        this.tunnelDug = input.getIntOr("TunnelDug", 0);
        this.mineDone = input.getBooleanOr("MineDone", false);
        Direction dir = Direction.from3DDataValue(input.getIntOr("TunnelDir", Direction.NORTH.get3DDataValue()));
        this.tunnelDir = dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
        this.companionLevel = Math.max(1, Math.min(MAX_LEVEL, input.getIntOr("CompanionLevel", 5)));
        this.xp = input.getIntOr("Xp", 0);
        this.playerNamed = input.getBooleanOr("PlayerNamed", false);
        this.backpack.clearContent();
        for (ItemStackWithSlot entry : input.listOrEmpty("Backpack", ItemStackWithSlot.CODEC)) {
            if (entry.isValidInContainer(BACKPACK_SIZE)) {
                this.backpack.setItem(entry.slot(), entry.stack());
            }
        }
        applyLevelStats();
        if (input.getFloatOr("Health", -1.0F) < 0.0F) {
            this.setHealth(this.getMaxHealth()); // призвано командою без здоров'я: повне з урахуванням рівня
        }
        updateName();
        syncJobData();
    }

    private static @Nullable BlockPos readPos(ValueInput input, String key) {
        long value = input.getLongOr(key, NO_POS);
        return value == NO_POS ? null : BlockPos.of(value);
    }

    /** При смерті вміст рюкзака випадає на землю. */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel serverLevel && !this.backpack.isEmpty()) {
            Containers.dropContents(serverLevel, this.blockPosition(), this.backpack);
            this.backpack.clearContent();
        }
        super.die(source);
    }
}
