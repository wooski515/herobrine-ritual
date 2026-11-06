package com.herobrine.ritual.item;

import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineMod;
import com.herobrine.ritual.HerobrineUtil;
import com.herobrine.ritual.HerobrineVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.util.function.Consumer;

/**
 * Сфера Херобріна.
 * <ul>
 *   <li>ПКМ порожньою сферою по своєму компаньйону — він ховається в сферу разом зі спорядженням і рюкзаком.</li>
 *   <li>ПКМ повною сферою по блоку — компаньйон виходить. Якщо сферу використав інший гравець,
 *       компаньйон стає його (так можна обмінюватися Херобрінами).</li>
 * </ul>
 */
public class HerobrineOrbItem extends Item {
    private static final String KEY_ENTITY = "HerobrineData";
    private static final String KEY_VARIANT = "Variant";
    private static final String KEY_LEVEL = "Level";
    private static final String KEY_OWNER_NAME = "OwnerName";

    public HerobrineOrbItem(Properties properties) {
        super(properties);
    }

    public static boolean isFilled(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(KEY_ENTITY);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof HerobrineCompanion companion) || isFilled(stack)) {
            return InteractionResult.PASS;
        }
        if (!companion.isOwnedBy(player)) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.orb.not_yours"));
            return InteractionResult.FAIL;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (companion.isInDuel()) {
            HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.orb.busy"));
            return InteractionResult.FAIL;
        }

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        companion.saveWithoutId(output);
        CompoundTag entityTag = output.buildResult();

        CompoundTag data = new CompoundTag();
        data.put(KEY_ENTITY, entityTag);
        data.putInt(KEY_VARIANT, companion.getVariant().ordinal());
        data.putInt(KEY_LEVEL, companion.getCompanionLevel());
        data.putString(KEY_OWNER_NAME, player.getName().getString());

        ItemStack filled = new ItemStack(this);
        filled.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        filled.set(DataComponents.MAX_STACK_SIZE, 1);
        ItemStack held = player.getItemInHand(hand);

        HerobrineUtil.burst(level, companion, ParticleTypes.PORTAL, 50);
        level.playSound(null, companion.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 0.6F);
        companion.discard();

        // Важливо: стак у руці не можна спустошувати — тоді гра після взаємодії сама очищає руку
        // і стирає видану сферу. Тому одну сферу перетворюємо на повну прямо в руці.
        if (player.hasInfiniteMaterials()) {
            give(player, filled);
        } else if (held.getCount() == 1) {
            held.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            held.set(DataComponents.MAX_STACK_SIZE, 1);
        } else {
            held.shrink(1);
            give(player, filled);
        }
        HerobrineUtil.actionBar(player, Component.translatable("message.herobrine_ritual.orb.stored",
                Component.translatable(companion.getVariant().translationKey())));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (!isFilled(stack) || player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        CompoundTag entityTag = data.getCompoundOrEmpty(KEY_ENTITY).copy();
        entityTag.remove("UUID"); // нова сутність отримає свіжий UUID

        HerobrineCompanion pet = HerobrineMod.HEROBRINE_COMPANION.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (pet == null) {
            return InteractionResult.FAIL;
        }
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entityTag);
        pet.load(input);
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        pet.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        pet.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        pet.setTarget(null);
        pet.resetAfterRelease();

        boolean traded = !pet.isOwnedBy(player);
        if (traded) {
            pet.tame(player);
            player.addTag(pet.getVariant().caughtTag());
        }
        level.addFreshEntity(pet);
        HerobrineUtil.burst(level, pet, ParticleTypes.REVERSE_PORTAL, 50);
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.2F);
        if (traded) {
            player.sendSystemMessage(Component.translatable("message.herobrine_ritual.orb.traded",
                    pet.getDisplayName(), data.getStringOr(KEY_OWNER_NAME, "?")).withStyle(ChatFormatting.GOLD));
        }

        if (!player.hasInfiniteMaterials()) {
            if (stack.getCount() == 1) {
                stack.remove(DataComponents.CUSTOM_DATA); // сфера знову порожня
                stack.remove(DataComponents.MAX_STACK_SIZE);
            } else {
                stack.shrink(1);
                give(player, new ItemStack(this));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains(KEY_ENTITY)) {
            CompoundTag tag = data.copyTag();
            HerobrineVariant variant = HerobrineVariant.byOrdinal(tag.getIntOr(KEY_VARIANT, 0));
            return Component.translatable("item.herobrine_ritual.herobrine_orb.filled",
                    Component.translatable(variant.translationKey()), tag.getIntOr(KEY_LEVEL, 1));
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFilled(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        if (isFilled(stack)) {
            CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            builder.accept(Component.translatable("tooltip.herobrine_ritual.orb.owner",
                    tag.getStringOr(KEY_OWNER_NAME, "?")).withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.herobrine_ritual.orb.release").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.herobrine_ritual.orb.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
