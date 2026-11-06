package com.herobrine.ritual;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Дрібні спільні функції. */
public final class HerobrineUtil {
    private HerobrineUtil() {
    }

    /** Блискавка лише для краси (не підпалює і не шкодить). */
    public static void visualLightning(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** Хмарка частинок навколо сутності. */
    public static void burst(ServerLevel level, Entity entity, ParticleOptions particle, int count) {
        level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                count, entity.getBbWidth() * 0.8, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.8, 0.05);
    }

    /** Гравець, якому «належить» удар: сам гравець або власник компаньйона-Херобріна. */
    public static @Nullable Player creditedPlayer(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof HerobrineCompanion companion && companion.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    public static void actionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    public static void chat(@Nullable Entity player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message);
        }
    }
}
