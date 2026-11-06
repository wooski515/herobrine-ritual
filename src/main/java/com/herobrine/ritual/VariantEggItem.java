package com.herobrine.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Яйце виклику: або боса певного виду, або одразу приборканого компаньйона цього виду
 * (власник: той, хто використав яйце). Призначене для творчого режиму.
 */
public class VariantEggItem extends SpawnEggItem {
    private final HerobrineVariant variant;
    private final boolean boss;

    public VariantEggItem(Item.Properties properties, HerobrineVariant variant, boolean boss) {
        super(properties);
        this.variant = variant;
        this.boss = boss;
    }

    /** Ванільний спавн у воду обходить налаштування виду/рівня/власника — вимикаємо його. */
    @Override
    public InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        if (boss) {
            HerobrineBoss b = HerobrineMod.HEROBRINE_BOSS.get()
                    .spawn(serverLevel, pos, EntitySpawnReason.TRIGGERED);
            if (b == null) {
                return InteractionResult.FAIL;
            }
            b.setVariant(variant);
            b.setBossLevel(HerobrineBoss.RITUAL_LEVEL);
        } else {
            HerobrineCompanion pet = HerobrineMod.HEROBRINE_COMPANION.get()
                    .spawn(serverLevel, pos, EntitySpawnReason.TRIGGERED);
            if (pet == null) {
                return InteractionResult.FAIL;
            }
            pet.setVariant(variant);
            pet.setCompanionLevel(5);
            pet.setHealth(pet.getMaxHealth());
            if (player != null) {
                pet.tame(player);
            }
        }
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
