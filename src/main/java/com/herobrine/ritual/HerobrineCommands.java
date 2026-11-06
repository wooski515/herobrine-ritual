package com.herobrine.ritual;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * Команди для адмінів (рівень 2):
 * <pre>
 *  /herobrine duel &lt;Херобрін1&gt; &lt;Херобрін2&gt;   — дуель на арені між будь-якими двома компаньйонами
 *  /herobrine wild &lt;вид&gt; [рівень]            — дикий бос-Херобрін поруч (для подій і тестів)
 * </pre>
 */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public class HerobrineCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("herobrine")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("duel")
                        .then(Commands.argument("first", EntityArgument.entity())
                                .then(Commands.argument("second", EntityArgument.entity())
                                        .executes(HerobrineCommands::duel))))
                .then(Commands.literal("wild")
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(HerobrineVariant.values()).map(HerobrineVariant::id), builder))
                                .executes(ctx -> wild(ctx, 5))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, HerobrineCompanion.MAX_LEVEL))
                                        .executes(ctx -> wild(ctx, IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    private static int duel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity a = EntityArgument.getEntity(ctx, "first");
        Entity b = EntityArgument.getEntity(ctx, "second");
        if (!(a instanceof HerobrineCompanion first) || !(b instanceof HerobrineCompanion second)) {
            ctx.getSource().sendFailure(Component.translatable("commands.herobrine_ritual.duel.not_companions"));
            return 0;
        }
        if (!HerobrineCompanion.startDuel(first, second)) {
            ctx.getSource().sendFailure(Component.translatable("message.herobrine_ritual.staff.duel_busy"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.herobrine_ritual.duel_start",
                first.getDisplayName(), second.getDisplayName()), true);
        return 1;
    }

    private static int wild(CommandContext<CommandSourceStack> ctx, int bossLevel) {
        String id = StringArgumentType.getString(ctx, "variant");
        HerobrineVariant variant = Arrays.stream(HerobrineVariant.values())
                .filter(v -> v.id().equals(id)).findFirst().orElse(null);
        if (variant == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.herobrine_ritual.unknown_variant", id));
            return 0;
        }
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        HerobrineBoss boss = WildSpawner.spawnWild(ctx.getSource().getLevel(), pos, variant, bossLevel, null);
        if (boss == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.herobrine_ritual.spawn_failed"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> boss.getDisplayName().copy(), true);
        return 1;
    }
}
