package com.herobrine.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.function.Predicate;

/**
 * Ритуали виклику Херобріна. Каркас однаковий для всіх видів (вигляд зверху):
 *
 *      [K] [R] [K]          Рівень верхнього блока:
 *      [R] [C] [R]              .  T  .
 *      [K] [R] [K]              T  V  T      V = верхній блок (над C), T = смолоскип (на кожному R)
 *
 *  C = центральний блок, R = кільце (усі 4 обов'язкові), K = кути (щонайменше 2 з кільцевих блоків).
 *  Підпал: ПКМ потрібним предметом по ВЕРХУ верхнього блока. Бос ритуалу має рівень 10.
 *
 *  Вид     | Центр                 | Кільце             | Верх              | Смолоскип | Підпал
 *  Classic | пісок душ / замш.бр.  | золотий блок       | незерак           | червоний  | кресало / вогн. заряд
 *  Nether  | магмовий блок         | незерська цегла    | незерак           | звичайний | вогняний заряд
 *  Ender   | ендерняк              | пурпуровий блок    | обсидіан          | червоний  | око ендера
 *  Frost   | сніговий блок         | пакований лід      | синій лід         | душі      | кресало
 *  Forest  | мох                   | замш. кам. цегла   | гарбуз            | звичайний | кресало
 *  Ghost   | скалк                 | глибосланц. плитка | пісок душ         | душі      | кресало
 *  Storm   | мідний блок (будь-як.)| різана мідь        | громовідвід       | мідний    | кресало
 *  Desert  | пісок                 | різаний пісковик   | різьб. пісковик   | звичайний | кресало
 *  Ocean   | призмарин             | призмаринова цегла | морський ліхтар   | душі      | кристали призмарину
 *  Miner   | блок сирого заліза    | кругл. глибосланець| вугільний блок    | звичайний | кресало
 */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public class RitualHandler {
    private static final int MIN_CORNER_RING = 2;

    /** Частина ритуалу: як перевіряти блок і як його назвати в Херодексі. */
    public record Part(Block display, Predicate<BlockState> test) {
        static Part of(Block... blocks) {
            return new Part(blocks[0], state -> {
                for (Block b : blocks) {
                    if (state.is(b)) {
                        return true;
                    }
                }
                return false;
            });
        }

        static Part tag(Block display, TagKey<Block> tag) {
            return new Part(display, state -> state.is(tag));
        }

        static Part list(Block display, List<Block> blocks) {
            return new Part(display, state -> blocks.stream().anyMatch(state::is));
        }

        public Component name() {
            return Component.translatable(display.getDescriptionId());
        }
    }

    public record Ritual(HerobrineVariant variant, Part center, Part ring, Part top, Part torch,
                         List<Item> ignitionItems) {
        boolean ignitedBy(ItemStack stack) {
            return ignitionItems.stream().anyMatch(stack::is);
        }
    }

    private static final List<Item> FLINT = List.of(Items.FLINT_AND_STEEL);

    public static final List<Ritual> RITUALS = List.of(
            new Ritual(HerobrineVariant.CLASSIC, Part.of(Blocks.SOUL_SAND, Blocks.MOSSY_COBBLESTONE),
                    Part.of(Blocks.GOLD_BLOCK), Part.of(Blocks.NETHERRACK), Part.of(Blocks.REDSTONE_TORCH),
                    List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)),
            new Ritual(HerobrineVariant.NETHER, Part.of(Blocks.MAGMA_BLOCK),
                    Part.of(Blocks.NETHER_BRICKS), Part.of(Blocks.NETHERRACK), Part.of(Blocks.TORCH),
                    List.of(Items.FIRE_CHARGE)),
            new Ritual(HerobrineVariant.ENDER, Part.of(Blocks.END_STONE),
                    Part.of(Blocks.PURPUR_BLOCK), Part.of(Blocks.OBSIDIAN), Part.of(Blocks.REDSTONE_TORCH),
                    List.of(Items.ENDER_EYE)),
            new Ritual(HerobrineVariant.FROST, Part.of(Blocks.SNOW_BLOCK),
                    Part.of(Blocks.PACKED_ICE), Part.of(Blocks.BLUE_ICE), Part.of(Blocks.SOUL_TORCH),
                    FLINT),
            new Ritual(HerobrineVariant.FOREST, Part.of(Blocks.MOSS_BLOCK),
                    Part.of(Blocks.MOSSY_STONE_BRICKS), Part.of(Blocks.PUMPKIN), Part.of(Blocks.TORCH),
                    FLINT),
            new Ritual(HerobrineVariant.GHOST, Part.of(Blocks.SCULK),
                    Part.of(Blocks.DEEPSLATE_TILES), Part.of(Blocks.SOUL_SAND), Part.of(Blocks.SOUL_TORCH),
                    FLINT),
            new Ritual(HerobrineVariant.STORM, Part.tag(Blocks.COPPER_BLOCK.weathering().unaffected(), BlockTags.COPPER),
                    Part.list(Blocks.CUT_COPPER.weathering().unaffected(), Blocks.CUT_COPPER.asList()),
                    Part.tag(Blocks.LIGHTNING_ROD.weathering().unaffected(), BlockTags.LIGHTNING_RODS),
                    Part.of(Blocks.COPPER_TORCH),
                    FLINT),
            new Ritual(HerobrineVariant.DESERT, Part.of(Blocks.SAND, Blocks.RED_SAND),
                    Part.of(Blocks.CUT_SANDSTONE, Blocks.CUT_RED_SANDSTONE),
                    Part.of(Blocks.CHISELED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE), Part.of(Blocks.TORCH),
                    FLINT),
            new Ritual(HerobrineVariant.OCEAN, Part.of(Blocks.PRISMARINE),
                    Part.of(Blocks.PRISMARINE_BRICKS), Part.of(Blocks.SEA_LANTERN), Part.of(Blocks.SOUL_TORCH),
                    List.of(Items.PRISMARINE_CRYSTALS)),
            new Ritual(HerobrineVariant.MINER, Part.of(Blocks.RAW_IRON_BLOCK),
                    Part.of(Blocks.COBBLED_DEEPSLATE), Part.of(Blocks.COAL_BLOCK), Part.of(Blocks.TORCH),
                    FLINT)
    );

    public static Ritual ritualFor(HerobrineVariant variant) {
        for (Ritual ritual : RITUALS) {
            if (ritual.variant() == variant) {
                return ritual;
            }
        }
        return RITUALS.get(0);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getFace() != Direction.UP) {
            return;
        }
        ItemStack stack = event.getItemStack();
        BlockPos pos = event.getPos();

        Ritual match = null;
        for (Ritual ritual : RITUALS) {
            if (ritual.ignitedBy(stack) && isRitualValid(level, pos, ritual)) {
                match = ritual;
                break;
            }
        }
        if (match == null) {
            return;
        }

        Player player = event.getEntity();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_ritual.peaceful"), true);
            }
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);

        // Витрата предмета
        if (stack.is(Items.FLINT_AND_STEEL)) {
            EquipmentSlot slot = event.getHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        } else {
            stack.consume(1, player);
        }

        // Ритуал "згорає": верхній блок і смолоскипи зникають, база та кільце лишаються
        level.destroyBlock(pos, false);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            level.destroyBlock(pos.relative(dir), false);
        }

        HerobrineUtil.visualLightning(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.6F);

        HerobrineBoss boss = HerobrineMod.HEROBRINE_BOSS.get().spawn(level, pos, EntitySpawnReason.TRIGGERED);
        if (boss != null) {
            boss.setVariant(match.variant());
            boss.setBossLevel(HerobrineBoss.RITUAL_LEVEL);
            boss.setTarget(player);
        }
        player.sendSystemMessage(Component.translatable("message.herobrine_ritual.summoned"));
    }

    private static boolean isRitualValid(ServerLevel level, BlockPos topPos, Ritual ritual) {
        if (!ritual.top().test().test(level.getBlockState(topPos))) {
            return false;
        }
        BlockPos base = topPos.below();
        if (!ritual.center().test().test(level.getBlockState(base))) {
            return false;
        }
        // 4 блоки кільця хрестом навколо центру + смолоскипи над ними (навколо верхнього блока)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!ritual.ring().test().test(level.getBlockState(base.relative(dir)))) {
                return false;
            }
            if (!ritual.torch().test().test(level.getBlockState(topPos.relative(dir)))) {
                return false;
            }
        }
        // Кути: щонайменше MIN_CORNER_RING блоків кільця (разом >= 6)
        int corners = 0;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (ritual.ring().test().test(level.getBlockState(base.offset(dx, 0, dz)))) {
                    corners++;
                }
            }
        }
        return corners >= MIN_CORNER_RING;
    }
}
