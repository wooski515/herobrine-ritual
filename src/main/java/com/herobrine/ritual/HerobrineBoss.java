package com.herobrine.ritual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Бос Херобрін (10 видів). Спільне: ближній бій, телепорт за спину, друга фаза нижче 50% HP.
 * Своє в кожного виду: див. variantStrike().
 * <p>
 * Коли гравець (або його компаньйон-Херобрін) завдає смертельного удару, бос не помирає,
 * а стає приборканим компаньйоном цього гравця того ж виду й рівня — як «піймати» покемона.
 * <p>
 * Дикі боси (з'являються самі) спочатку лише спостерігають здалеку й нападають, якщо підійти
 * ближче ніж на 10 блоків або вдарити їх. Без гравців поруч вони зникають.
 */
public class HerobrineBoss extends Monster implements HasHerobrineVariant {
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(HerobrineBoss.class, EntityDataSerializers.INT);

    private static final Identifier LEVEL_HEALTH_ID = Identifier.fromNamespaceAndPath(HerobrineMod.MODID, "boss_level_health");
    private static final Identifier LEVEL_ATTACK_ID = Identifier.fromNamespaceAndPath(HerobrineMod.MODID, "boss_level_attack");

    public static final int RITUAL_LEVEL = 10;
    private static final double STALK_TRIGGER_DISTANCE = 10.0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            UUID.randomUUID(), Component.translatable("entity.herobrine_ritual.herobrine_boss"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    private boolean defeated = false;
    private boolean enraged = false;
    private boolean wild = false;
    private boolean provoked = true;
    private int bossLevel = RITUAL_LEVEL;
    private int idleTicks = 0;
    private int strikeCooldown = 100;
    private int teleportCooldown = 160;
    private int healCooldown = 200;

    public HerobrineBoss(EntityType<? extends HerobrineBoss> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        applyLevelStats(); // і заспавнений диспенсером/яйцем у воду бос має характеристики 10 рівня
        this.setHealth(this.getMaxHealth());
    }

    /** Ритуальні боси не зникають; дикі підкоряються звичайним правилам зникнення (далеко від гравців). */
    @Override
    public boolean isPersistenceRequired() {
        return !wild && super.isPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return wild;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Базові значення; більшу частину здоров'я та сили дає рівень (див. setBossLevel)
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    // ---- Вид і рівень ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    public HerobrineVariant getVariant() {
        return HerobrineVariant.byOrdinal(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(HerobrineVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
        updateName();
    }

    public int getBossLevel() {
        return bossLevel;
    }

    /** Рівень визначає здоров'я та силу. Рівень ритуального боса = 10, дикі зазвичай 1..12. */
    public void setBossLevel(int level) {
        this.bossLevel = Math.max(1, Math.min(HerobrineCompanion.MAX_LEVEL, level));
        applyLevelStats();
        this.setHealth(this.getMaxHealth());
        updateName();
    }

    private void applyLevelStats() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrReplacePermanentModifier(new AttributeModifier(
                    LEVEL_HEALTH_ID, 22.0 * bossLevel, AttributeModifier.Operation.ADD_VALUE));
        }
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.addOrReplacePermanentModifier(new AttributeModifier(
                    LEVEL_ATTACK_ID, 0.4 * bossLevel, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void updateName() {
        Component name = Component.translatable(getVariant().translationKey())
                .append(Component.translatable("gui.herobrine_ritual.level_suffix", bossLevel));
        if (wild) {
            name = Component.translatable("message.herobrine_ritual.wild_prefix").append(name);
        }
        this.setCustomName(name);
        this.bossEvent.setName(name);
    }

    public boolean isWild() {
        return wild;
    }

    /** Дикий бос: спочатку не нападає, а спостерігає. */
    public void setWild(boolean wild) {
        this.wild = wild;
        this.provoked = !wild;
        this.bossEvent.setVisible(this.provoked);
        updateName();
    }

    private void provoke() {
        if (!provoked) {
            provoked = true;
            bossEvent.setVisible(true);
            this.playSound(SoundEvents.ENDERMAN_STARE, 1.5F, 0.6F);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getVariant().ordinal());
        output.putInt("BossLevel", this.bossLevel);
        output.putBoolean("Wild", this.wild);
        output.putBoolean("Provoked", this.provoked);
        output.putBoolean("Enraged", this.enraged);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_VARIANT, HerobrineVariant.byOrdinal(input.getIntOr("Variant", 0)).ordinal());
        this.bossLevel = input.getIntOr("BossLevel", RITUAL_LEVEL);
        this.wild = input.getBooleanOr("Wild", false);
        this.provoked = input.getBooleanOr("Provoked", true);
        this.enraged = input.getBooleanOr("Enraged", false);
        applyLevelStats();
        if (input.getFloatOr("Health", -1.0F) < 0.0F) {
            this.setHealth(this.getMaxHealth()); // призвано командою без здоров'я: повне з урахуванням рівня
        }
        this.bossEvent.setVisible(this.provoked);
        if (this.enraged) {
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        }
        updateName();
    }

    // ---- ШІ ----

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return provoked && super.canUse(); // дикий спостерігач стоїть на місці
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> provoked || this.distanceToSqr(target) < STALK_TRIGGER_DISTANCE * STALK_TRIGGER_DISTANCE));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (wild) {
            wildTick(level);
            if (this.isRemoved()) {
                return;
            }
        }

        if (!enraged && this.getHealth() < this.getMaxHealth() * 0.5F) {
            enterPhaseTwo(level);
        }

        // Пасивки видів
        switch (getVariant()) {
            case NETHER, DESERT -> this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
            case OCEAN -> this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
            case FOREST -> {
                if (--healCooldown <= 0) {
                    healCooldown = enraged ? 120 : 200;
                    this.heal(6.0F);
                }
            }
            default -> {
            }
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        provoke();

        if (--strikeCooldown <= 0) {
            strikeCooldown = enraged ? 60 : 110;
            variantStrike(level, target);
        }
        if (--teleportCooldown <= 0) {
            int base = getVariant() == HerobrineVariant.ENDER ? 90 : 180;
            teleportCooldown = enraged ? base / 2 : base;
            teleportBehind(target);
        }
    }

    /** Дикий бос зникає, якщо поруч довго немає гравців. */
    private void wildTick(ServerLevel level) {
        if (level.getNearestPlayer(this, 64.0) == null) {
            if (++idleTicks > HerobrineConfig.WILD_DESPAWN_SECONDS.get() * 20) {
                HerobrineUtil.burst(level, this, ParticleTypes.LARGE_SMOKE, 30);
                this.discard();
            }
        } else {
            idleTicks = 0;
        }
    }

    private void enterPhaseTwo(ServerLevel level) {
        enraged = true;
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, false));
        level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 0.7F);
        for (ServerPlayer player : bossEvent.getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.herobrine_ritual.phase_two"));
        }
    }

    /** Фірмовий удар боса залежно від виду. */
    private void variantStrike(ServerLevel level, LivingEntity target) {
        float k = (enraged ? 1.5F : 1.0F) * (0.6F + bossLevel * 0.04F);
        DamageSource magic = level.damageSources().indirectMagic(this, this);
        target.setInvulnerableTime(0); // фірмовий удар не «згорає» через кадри невразливості після удару рукою
        switch (getVariant()) {
            case CLASSIC -> {
                HerobrineUtil.visualLightning(level, target.getX(), target.getY(), target.getZ());
                target.hurtServer(level, magic, 5.0F * k);
            }
            case NETHER -> {
                target.igniteForSeconds(5.0F);
                HerobrineUtil.burst(level, target, ParticleTypes.FLAME, 25);
                target.hurtServer(level, magic, 4.0F * k);
            }
            case ENDER -> {
                teleportBehind(target);
                target.hurtServer(level, magic, 4.0F * k);
            }
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, enraged ? 2 : 1));
                HerobrineUtil.burst(level, target, ParticleTypes.SNOWFLAKE, 25);
                target.hurtServer(level, level.damageSources().freeze(), 4.0F * k);
            }
            case FOREST -> {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                target.hurtServer(level, magic, 3.0F * k);
            }
            case GHOST -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
                target.hurtServer(level, magic, 4.0F * k);
            }
            case STORM -> {
                // Гроза: три блискавки навколо цілі, шкода всім поруч
                for (int i = 0; i < 3; i++) {
                    HerobrineUtil.visualLightning(level,
                            target.getX() + (random.nextDouble() - 0.5) * 4.0,
                            target.getY(),
                            target.getZ() + (random.nextDouble() - 0.5) * 4.0);
                }
                List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(3.0), e -> e != this && e.isAlive());
                for (LivingEntity e : around) {
                    e.hurtServer(level, magic, (e == target ? 5.0F : 2.5F) * k);
                }
            }
            case DESERT -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
                HerobrineUtil.burst(level, target, ParticleTypes.CLOUD, 30);
                target.hurtServer(level, magic, 4.0F * k);
            }
            case OCEAN -> {
                // Підтягує ціль до себе (як течія) і сповільнює копання
                Vec3 pull = this.position().subtract(target.position()).normalize().scale(1.2);
                target.push(pull.x, 0.35, pull.z);
                target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1));
                HerobrineUtil.burst(level, target, ParticleTypes.SPLASH, 40);
                target.hurtServer(level, level.damageSources().drown(), 4.0F * k);
            }
            case MINER -> {
                // Поштовх: підкидає ціль у повітря
                target.push(0.0, 0.9, 0.0);
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 2, 0.5, 0.1, 0.5, 0.0);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.3F);
                target.hurtServer(level, magic, 5.0F * k);
            }
        }
    }

    private void teleportBehind(LivingEntity target) {
        Vec3 back = target.getLookAngle().multiply(-2.5, 0.0, -2.5);
        this.randomTeleport(target.getX() + back.x, target.getY(), target.getZ() + back.z, true, state -> false);
    }

    // ---- Перемога: бос стає компаньйоном ----

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() != null) {
            provoke();
        }
        if (!defeated && this.getHealth() - amount <= 0.0F
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !wouldIgnoreDamage(level, source, amount)) {
            Player winner = HerobrineUtil.creditedPlayer(source.getEntity());
            if (winner == null) {
                winner = HerobrineUtil.creditedPlayer(this.getLastHurtByMob());
            }
            if (winner != null) {
                defeated = true;
                becomeCompanion(level, winner, source.getEntity());
                return true;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    /** Чи ванільна логіка все одно проігнорує удар (невразливість, вогнестійкість, кадри невразливості). */
    private boolean wouldIgnoreDamage(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableTo(level, source) || this.isDeadOrDying()) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return true;
        }
        return this.getInvulnerableTime() > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN) && amount <= this.lastHurt;
    }

    private void becomeCompanion(ServerLevel level, Player winner, net.minecraft.world.entity.Entity finisher) {
        HerobrineCompanion pet = HerobrineMod.HEROBRINE_COMPANION.get()
                .spawn(level, this.blockPosition(), EntitySpawnReason.TRIGGERED);
        if (pet != null) {
            pet.setYRot(this.getYRot());
            pet.setVariant(getVariant());
            pet.setCompanionLevel(this.bossLevel);
            pet.tame(winner);
            pet.setHealth(pet.getMaxHealth());
            pet.setTarget(null);
        }
        boolean firstCatch = !winner.entityTags().contains(getVariant().caughtTag());
        winner.addTag(getVariant().caughtTag());

        if (finisher instanceof HerobrineCompanion helper && helper != pet) {
            helper.addXp(60 + bossLevel * 10);
            helper.setTarget(null);
        }

        HerobrineUtil.visualLightning(level, this.getX(), this.getY(), this.getZ());
        HerobrineUtil.burst(level, this, ParticleTypes.TOTEM_OF_UNDYING, 60);
        level.playSound(null, this.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, winner.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
        Component name = Component.translatable(getVariant().translationKey()).withStyle(ChatFormatting.GOLD);
        winner.sendSystemMessage(Component.translatable("message.herobrine_ritual.caught", name, bossLevel));
        if (firstCatch) {
            winner.sendSystemMessage(Component.translatable("message.herobrine_ritual.new_dex_entry")
                    .withStyle(ChatFormatting.AQUA));
        }

        bossEvent.removeAllPlayers();
        this.discard();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

}
