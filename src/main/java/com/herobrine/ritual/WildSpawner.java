package com.herobrine.ritual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Природний спавн диких Херобрінів. Кожен вид має свої умови:
 * <pre>
 *  Класичний  — поверхня Верхнього світу, ніч
 *  Незерський — Незер
 *  Ендерський — Енд
 *  Крижаний   — сніжні біоми (поверхня)
 *  Лісовий    — ліси, джунглі, тайга (поверхня)
 *  Привид     — глибоко під землею (нижче Y 0) або Глибока темрява
 *  Грозовий   — поверхня під час грози
 *  Пустельний — пустеля або безплідні землі, вдень
 *  Морський   — океан, пляж, річка
 *  Шахтар     — печери між Y 0 і Y 50
 * </pre>
 * Дикий бос має рівень 1..12, спочатку лише спостерігає й нападає, якщо підійти ближче.
 */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public class WildSpawner {
    private static final Map<UUID, Long> LAST_SPAWN = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!HerobrineConfig.WILD_SPAWNS.get()) {
            return;
        }
        if (++tickCounter < HerobrineConfig.WILD_CHECK_INTERVAL_SECONDS.get() * 20) {
            return;
        }
        tickCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isSpectator() && player.isAlive()) {
                trySpawnNear(player);
            }
        }
    }

    private static void trySpawnNear(ServerPlayer player) {
        ServerLevel level = player.level();
        RandomSource random = level.getRandom();
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_SPAWN.get(player.getUUID());
        long cooldown = HerobrineConfig.WILD_PLAYER_COOLDOWN_MINUTES.get() * 60L * 20L;
        if (last != null && now - last < cooldown && now >= last) {
            return;
        }
        if (random.nextDouble() >= HerobrineConfig.WILD_SPAWN_CHANCE.get()) {
            return;
        }
        // Не більше одного боса поблизу
        if (!level.getEntitiesOfClass(HerobrineBoss.class, new AABB(player.blockPosition()).inflate(160.0)).isEmpty()) {
            return;
        }

        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 20.0 + random.nextDouble() * 20.0;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos pos = findSpawnPos(level, player, x, z);
            if (pos == null) {
                continue;
            }
            List<HerobrineVariant> eligible = new ArrayList<>();
            for (HerobrineVariant variant : HerobrineVariant.values()) {
                if (canSpawnAt(variant, level, pos)) {
                    eligible.add(variant);
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }
            HerobrineVariant variant = eligible.get(random.nextInt(eligible.size()));
            int bossLevel = 1 + random.nextInt(8) + (level.dimension() != Level.OVERWORLD ? 4 : 0);
            if (spawnWild(level, pos, variant, bossLevel, player) != null) {
                LAST_SPAWN.put(player.getUUID(), now);
            }
            return;
        }
    }

    private static boolean isUnderground(ServerLevel level, ServerPlayer player) {
        return !level.canSeeSky(player.blockPosition()) && player.getY() < level.getSeaLevel() - 4;
    }

    private static @Nullable BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, int x, int z) {
        BlockPos column = new BlockPos(x, player.getBlockY(), z);
        if (!level.isLoaded(column)) {
            return null;
        }
        if (level.dimension() == Level.NETHER || isUnderground(level, player)) {
            // Печера: шукаємо порожнину з підлогою поблизу висоти гравця
            for (int i = 0; i <= 16; i++) {
                int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
                BlockPos pos = new BlockPos(x, player.getBlockY() + dy, z);
                if (isStandable(level, pos)) {
                    return pos;
                }
            }
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (y <= level.getMinY() || level.getBlockState(pos.below()).isAir()) {
            return null; // порожнеча (Енд) — тут нема на чому стояти
        }
        return level.noCollision(HerobrineMod.HEROBRINE_BOSS.get().getSpawnAABB(x + 0.5, y, z + 0.5)) ? pos : null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        return floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(pos).getFluidState().isEmpty()
                && level.noCollision(HerobrineMod.HEROBRINE_BOSS.get().getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    /** Умови природного спавну кожного виду. */
    public static boolean canSpawnAt(HerobrineVariant variant, ServerLevel level, BlockPos pos) {
        boolean overworld = level.dimension() == Level.OVERWORLD;
        Holder<Biome> biome = level.getBiome(pos);
        boolean sky = level.canSeeSky(pos);
        return switch (variant) {
            case CLASSIC -> overworld && sky && level.isDarkOutside();
            case NETHER -> level.dimension() == Level.NETHER;
            case ENDER -> level.dimension() == Level.END;
            case FROST -> overworld && sky && biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
            case FOREST -> overworld && sky
                    && (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA));
            case GHOST -> overworld && !sky && (pos.getY() < 0 || biome.is(Biomes.DEEP_DARK));
            case STORM -> overworld && sky && level.isThundering();
            case DESERT -> overworld && sky && level.isBrightOutside()
                    && (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS));
            case OCEAN -> overworld
                    && (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER));
            case MINER -> overworld && !sky && pos.getY() >= 0 && pos.getY() < 50;
        };
    }

    /** Створює дикого боса. player (якщо є) отримує моторошне попередження. */
    public static @Nullable HerobrineBoss spawnWild(ServerLevel level, BlockPos pos, HerobrineVariant variant,
                                                    int bossLevel, @Nullable ServerPlayer player) {
        HerobrineBoss boss = HerobrineMod.HEROBRINE_BOSS.get().spawn(level, pos, EntitySpawnReason.NATURAL);
        if (boss == null) {
            return null;
        }
        boss.setVariant(variant);
        boss.setWild(true);
        boss.setBossLevel(bossLevel);

        if (player != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
            player.sendSystemMessage(Component.translatable("message.herobrine_ritual.wild_appeared",
                            Component.translatable(variant.translationKey()).withStyle(ChatFormatting.GOLD), bossLevel)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return boss;
    }
}
