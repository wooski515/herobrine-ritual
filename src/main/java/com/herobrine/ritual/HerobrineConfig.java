package com.herobrine.ritual;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Налаштування мода (config/herobrine_ritual-local.toml). */
public final class HerobrineConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue WILD_SPAWNS;
    public static final ModConfigSpec.IntValue WILD_CHECK_INTERVAL_SECONDS;
    public static final ModConfigSpec.DoubleValue WILD_SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue WILD_PLAYER_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WILD_DESPAWN_SECONDS;
    public static final ModConfigSpec.BooleanValue MINING_ENABLED;
    public static final ModConfigSpec.IntValue MAX_TUNNEL_LENGTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Wild Herobrines that appear on their own (each kind has its own place and conditions)")
                .push("wild_spawns");
        WILD_SPAWNS = b.comment("Enable natural spawning of wild Herobrine bosses")
                .define("enabled", true);
        WILD_CHECK_INTERVAL_SECONDS = b.comment("How often (seconds) the game rolls a wild spawn for each player")
                .defineInRange("checkIntervalSeconds", 30, 5, 3600);
        WILD_SPAWN_CHANCE = b.comment("Chance per roll that a wild Herobrine appears near a player (if conditions fit)")
                .defineInRange("chance", 0.06, 0.0, 1.0);
        WILD_PLAYER_COOLDOWN_MINUTES = b.comment("Minimum minutes between two wild Herobrines for the same player")
                .defineInRange("playerCooldownMinutes", 8, 0, 1440);
        WILD_DESPAWN_SECONDS = b.comment("A wild Herobrine with no player within 64 blocks vanishes after this many seconds")
                .defineInRange("despawnSeconds", 120, 10, 3600);
        b.pop();

        b.comment("Companion jobs").push("jobs");
        MINING_ENABLED = b.comment("Allow companions to break blocks with the Mine task")
                .define("miningEnabled", true);
        MAX_TUNNEL_LENGTH = b.comment("Maximum length of the tunnel a mining companion digs from its work point")
                .defineInRange("maxTunnelLength", 48, 4, 256);
        b.pop();

        SPEC = b.build();
    }

    private HerobrineConfig() {
    }
}
