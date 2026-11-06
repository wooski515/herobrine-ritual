package com.herobrine.ritual;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Ефекти «при ударі» для видів (і для боса, і для компаньйона) та досвід компаньйонам за перемоги. */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public class VariantEvents {

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !(attacker instanceof HasHerobrineVariant holder)
                || event.getSource().getDirectEntity() != attacker) { // лише ближній удар, не здібності
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == attacker || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        switch (holder.getVariant()) {
            case NETHER -> victim.igniteForSeconds(4.0F);
            case FROST -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
            case GHOST -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            case FOREST -> attacker.heal(2.0F);
            case DESERT -> victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
            case MINER -> victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0));
            case OCEAN -> victim.knockback(0.6, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ(), event.getSource(), 0.0F);
            case STORM -> {
                if (attacker.getRandom().nextFloat() < 0.2F) {
                    HerobrineUtil.visualLightning(level, victim.getX(), victim.getY(), victim.getZ());
                    victim.setInvulnerableTime(0);
                    victim.hurtServer(level, level.damageSources().magic(), 3.0F);
                }
            }
            default -> {
            }
        }
    }

    /** Компаньйон отримує досвід за кожного переможеного (більше за сильніших). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof HerobrineCompanion companion && companion != event.getEntity()) {
            LivingEntity victim = event.getEntity();
            int xp = Math.max(3, (int) (victim.getMaxHealth() / 3.0F));
            if (victim instanceof HerobrineCompanion other) {
                xp += other.getCompanionLevel() * 5;
            }
            companion.addXp(xp);
        }
    }
}
