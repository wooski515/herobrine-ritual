package com.herobrine.ritual.item;

import com.herobrine.ritual.HerobrineVariant;
import com.herobrine.ritual.RitualHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Херодекс — книга-довідник (як покедекс). Сторінки генеруються щоразу при відкритті:
 * які види вже піймані, де шукати кожен вид, його здібності, робота та точний рецепт ритуалу.
 */
public class HerodexItem extends Item {
    public HerodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                    Filterable.passThrough("Herodex"), "Herobrine", 0, buildPages(player), false));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverPlayer.openItemGui(stack, hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean caught(Player player, HerobrineVariant variant) {
        return player.entityTags().contains(variant.caughtTag()) || player.entityTags().contains(variant.legacyOwnerTag());
    }

    private static List<Filterable<Component>> buildPages(Player player) {
        List<Filterable<Component>> pages = new ArrayList<>();
        int total = HerobrineVariant.values().length;
        int count = 0;
        for (HerobrineVariant v : HerobrineVariant.values()) {
            if (caught(player, v)) {
                count++;
            }
        }

        // Титульна сторінка
        pages.add(page(Component.empty()
                .append(Component.translatable("dex.herobrine_ritual.title").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED))
                .append("\n\n")
                .append(Component.translatable("dex.herobrine_ritual.progress", count, total))
                .append("\n\n")
                .append(Component.translatable("dex.herobrine_ritual.intro"))));
        // Як ловити та командувати
        pages.add(page(Component.translatable("dex.herobrine_ritual.howto")));
        pages.add(page(Component.translatable("dex.herobrine_ritual.staff_help")));

        for (HerobrineVariant v : HerobrineVariant.values()) {
            boolean has = caught(player, v);
            MutableComponent name = Component.translatable(v.translationKey())
                    .withStyle(s -> s.withBold(true).withColor(TextColor.fromRgb(darken(v.primaryColor()))));
            String id = v.id();
            pages.add(page(Component.empty()
                    .append(name)
                    .append("\n")
                    .append(Component.translatable(has ? "dex.herobrine_ritual.caught" : "dex.herobrine_ritual.not_caught")
                            .withStyle(has ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY))
                    .append("\n\n")
                    .append(label("dex.herobrine_ritual.where")).append(Component.translatable("dex.herobrine_ritual.where." + id))
                    .append("\n\n")
                    .append(label("dex.herobrine_ritual.skill")).append(Component.translatable("dex.herobrine_ritual.skill." + id))
                    .append("\n\n")
                    .append(label("dex.herobrine_ritual.passive")).append(Component.translatable("dex.herobrine_ritual.passive." + id))));

            RitualHandler.Ritual r = RitualHandler.ritualFor(v);
            MutableComponent ignition = Component.empty();
            for (int i = 0; i < r.ignitionItems().size(); i++) {
                if (i > 0) {
                    ignition.append(" / ");
                }
                ignition.append(Component.translatable(r.ignitionItems().get(i).getDescriptionId()));
            }
            pages.add(page(Component.empty()
                    .append(label("dex.herobrine_ritual.job")).append(Component.translatable("dex.herobrine_ritual.job." + id))
                    .append("\n\n")
                    .append(Component.translatable("dex.herobrine_ritual.ritual").withStyle(ChatFormatting.BOLD))
                    .append("\n")
                    .append(label("dex.herobrine_ritual.r_center")).append(r.center().name()).append("\n")
                    .append(label("dex.herobrine_ritual.r_ring")).append(r.ring().name()).append("\n")
                    .append(label("dex.herobrine_ritual.r_top")).append(r.top().name()).append("\n")
                    .append(label("dex.herobrine_ritual.r_torch")).append(r.torch().name()).append("\n")
                    .append(label("dex.herobrine_ritual.r_ignite")).append(ignition)));
        }
        return pages;
    }

    private static Filterable<Component> page(Component content) {
        return Filterable.passThrough(content);
    }

    private static Component label(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_BLUE).append(" ");
    }

    /** Колір виду, затемнений, щоб читався на папері книги. */
    private static int darken(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.6);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.6);
        int b = (int) ((rgb & 0xFF) * 0.6);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.herobrine_ritual.herodex").withStyle(ChatFormatting.GRAY));
    }
}
