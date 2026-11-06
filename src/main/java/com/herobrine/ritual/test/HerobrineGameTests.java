package com.herobrine.ritual.test;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineBoss;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineConfig;
import com.herobrine.ritual.HerobrineMod;
import com.herobrine.ritual.HerobrineVariant;
import com.herobrine.ritual.RitualHandler;
import com.herobrine.ritual.item.HerobrineOrbItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Автоматичні ігрові тести (GameTest). Запуск: {@code gradlew runGameTestServer} — сервер проганяє всі тести
 * з віртуальним гравцем і завершується зі звітом. У звичайній грі (не в середовищі розробки) не реєструються.
 */
public final class HerobrineGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, HerobrineMod.MODID);
    private static final Map<String, Integer> TESTS = new LinkedHashMap<>();

    private static void add(String name, int maxTicks, Consumer<GameTestHelper> test) {
        FUNCTIONS.register(name, () -> test);
        TESTS.put(name, maxTicks);
    }

    static {
        add("orb_store_release_single", 60, HerobrineGameTests::orbStoreReleaseSingle);
        add("orb_store_stack_of_two", 60, HerobrineGameTests::orbStoreStackOfTwo);
        add("orb_trade_changes_owner", 60, HerobrineGameTests::orbTrade);
        add("boss_capture_by_player", 60, HerobrineGameTests::bossCaptureByPlayer);
        add("boss_capture_by_companion", 60, HerobrineGameTests::bossCaptureByCompanion);
        add("boss_fire_resistant_not_captured", 60, HerobrineGameTests::bossFireResistantNotCaptured);
        add("boss_kill_command_not_captured", 60, HerobrineGameTests::bossKillCommand);
        add("boss_persistence_rules", 40, HerobrineGameTests::bossPersistenceRules);
        add("rituals_all_variants", 80, HerobrineGameTests::ritualsAllVariants);
        add("staff_mode_select_and_area", 60, HerobrineGameTests::staffSelectAndArea);
        add("staff_foreign_duel_needs_accept", 60, HerobrineGameTests::staffForeignDuel);
        add("staff_pvp_off_blocks_attack", 60, HerobrineGameTests::staffPvpOff);
        add("herodex_builds_pages", 40, HerobrineGameTests::herodexPages);
        add("mine_area_quarry", 1400, HerobrineGameTests::mineAreaQuarry);
        add("mine_paused_owner_offline", 200, HerobrineGameTests::minePausedOwnerOffline);
        add("haul_items_to_chest", 600, HerobrineGameTests::haulToChest);
        add("guard_kills_intruder", 600, HerobrineGameTests::guardKillsIntruder);
        add("duel_non_lethal", 1200, HerobrineGameTests::duelNonLethal);
        add("duel_interrupted_by_outsider", 40, HerobrineGameTests::duelInterrupted);
        add("gear_drops_after_reload", 60, HerobrineGameTests::gearDropsAfterReload);
        add("level_up_stats", 40, HerobrineGameTests::levelUpStats);
        add("claim_ownerless_companion", 40, HerobrineGameTests::claimOwnerless);
        add("spawn_eggs_setup", 60, HerobrineGameTests::spawnEggs);
        add("skills_all_variants", 80, HerobrineGameTests::skillsAllVariants);
        add("nether_smelts_ores", 1200, HerobrineGameTests::netherSmeltsOres);
        add("forest_chops_trees", 1200, HerobrineGameTests::forestChopsTrees);
        add("haul_source_to_storage", 800, HerobrineGameTests::haulSourceToStorage);
        add("guard_area_boundary", 500, HerobrineGameTests::guardAreaBoundary);
        add("mine_point_tunnel", 1200, HerobrineGameTests::minePointTunnel);
        add("mine_full_chest_returns", 800, HerobrineGameTests::mineFullChestReturns);
        add("orb_refuses_during_duel", 40, HerobrineGameTests::orbRefusesDuringDuel);
        add("death_drops_backpack", 60, HerobrineGameTests::deathDropsBackpack);
        add("backpack_upgrade", 40, HerobrineGameTests::backpackUpgrade);
        add("menu_commands", 40, HerobrineGameTests::menuCommands);
        add("staff_links_haul_chests", 60, HerobrineGameTests::staffLinksHaulChests);
    }

    private HerobrineGameTests() {
    }

    public static void init(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(HerobrineGameTests::registerTests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HerobrineMod.MODID, path);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"));
        for (Map.Entry<String, Integer> e : TESTS.entrySet()) {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, id(e.getKey()));
            event.registerTest(id(e.getKey()), new FunctionGameTestInstance(key,
                    new TestData<>(env, id("arena"), e.getValue(), 0, true)));
        }
    }

    // =====================================================================
    // Допоміжне
    // =====================================================================

    private static ServerPlayer player(GameTestHelper h, GameType mode, int x, int z) {
        HerobrineConfig.WILD_SPAWNS.set(false); // дикі боси не повинні втручатися в тести
        @SuppressWarnings("removal")
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        BlockPos pos = h.absolutePos(new BlockPos(x, 1, z));
        p.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        return p;
    }

    private static void removePlayers(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static HerobrineCompanion companion(GameTestHelper h, Player owner, HerobrineVariant v, int level, int x, int z) {
        HerobrineCompanion c = h.spawn(HerobrineMod.HEROBRINE_COMPANION.get(), new BlockPos(x, 1, z));
        c.setVariant(v);
        c.setCompanionLevel(level);
        c.setHealth(c.getMaxHealth());
        if (owner != null) {
            c.tame(owner);
        }
        return c;
    }

    private static HerobrineBoss boss(GameTestHelper h, HerobrineVariant v, int level, int x, int z) {
        HerobrineBoss b = h.spawn(HerobrineMod.HEROBRINE_BOSS.get(), new BlockPos(x, 1, z));
        b.setVariant(v);
        b.setBossLevel(level);
        return b;
    }

    private static List<HerobrineCompanion> companionsNear(GameTestHelper h, int x, int z, double r) {
        return h.getLevel().getEntitiesOfClass(HerobrineCompanion.class,
                new AABB(h.absolutePos(new BlockPos(x, 1, z))).inflate(r), LivingEntity::isAlive);
    }

    private static BlockHitResult topOf(GameTestHelper h, int x, int y, int z) {
        BlockPos pos = h.absolutePos(new BlockPos(x, y, z));
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    private static void clickBlock(GameTestHelper h, ServerPlayer p, int x, int y, int z) {
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, topOf(h, x, y, z));
    }

    private static void clickEntity(ServerPlayer p, LivingEntity target) {
        p.interactOn(target, InteractionHand.MAIN_HAND, target.position());
    }

    /** Shift + ПКМ у повітря n разів (зміна режиму посоха). */
    private static void cycleStaff(GameTestHelper h, ServerPlayer p, int times) {
        p.setXRot(-90.0F); // дивимося в небо — промінь нічого не зачепить
        p.setShiftKeyDown(true);
        for (int i = 0; i < times; i++) {
            p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        }
        p.setShiftKeyDown(false);
        p.setXRot(0.0F);
    }

    private static int countInContainer(GameTestHelper h, int x, int y, int z, net.minecraft.world.item.Item item) {
        Container c = HopperBlockEntity.getContainerAt(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)));
        int n = 0;
        if (c != null) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).is(item)) {
                    n += c.getItem(i).getCount();
                }
            }
        }
        return n;
    }

    // =====================================================================
    // Сфера
    // =====================================================================

    /** Одна сфера в руці (виживання): повна сфера має лишитися в руці, а потім знову стати порожньою. */
    private static void orbStoreReleaseSingle(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.MINER, 7, 4, 4);
        c.setTaskInArea(CompanionTask.MINE, h.absolutePos(new BlockPos(8, 1, 8)), h.absolutePos(new BlockPos(10, 2, 10)));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.HEROBRINE_ORB.get()));

        clickEntity(p, c);
        ItemStack held = p.getMainHandItem();
        h.assertTrue(held.is(HerobrineMod.HEROBRINE_ORB.get()) && HerobrineOrbItem.isFilled(held), "Повна сфера мала лишитися в руці");
        h.assertTrue(c.isRemoved(), "Компаньйон мав сховатися у сферу");

        clickBlock(h, p, 5, 0, 5);
        List<HerobrineCompanion> found = companionsNear(h, 5, 5, 3);
        h.assertTrue(found.size() == 1, "Після випуску має бути рівно один компаньйон, є " + found.size());
        HerobrineCompanion r = found.get(0);
        h.assertTrue(r.isOwnedBy(p), "Власник мав зберегтися");
        h.assertTrue(r.getVariant() == HerobrineVariant.MINER && r.getCompanionLevel() == 7, "Вид і рівень мали зберегтися");
        h.assertTrue(r.getTask() == CompanionTask.FOLLOW && !r.hasArea() && r.getStoragePos() == null,
                "Після випуску стара робота мала скинутися");
        ItemStack after = p.getMainHandItem();
        h.assertTrue(after.is(HerobrineMod.HEROBRINE_ORB.get()) && !HerobrineOrbItem.isFilled(after), "Сфера мала знову стати порожньою");
        removePlayers(h, p);
        h.succeed();
    }

    /** Дві сфери в руці: одна лишається порожньою в руці, повна — в інвентарі. */
    private static void orbStoreStackOfTwo(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.FROST, 3, 4, 4);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.HEROBRINE_ORB.get(), 2));
        clickEntity(p, c);
        ItemStack held = p.getMainHandItem();
        h.assertTrue(held.getCount() == 1 && !HerobrineOrbItem.isFilled(held), "У руці мала лишитися 1 порожня сфера");
        boolean filledInInventory = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (HerobrineOrbItem.isFilled(p.getInventory().getItem(i))) {
                filledInInventory = true;
            }
        }
        h.assertTrue(filledInInventory, "Повна сфера мала з'явитися в інвентарі");
        removePlayers(h, p);
        h.succeed();
    }

    /** Обмін: сферу випускає інший гравець — компаньйон стає його. */
    private static void orbTrade(GameTestHelper h) {
        ServerPlayer a = player(h, GameType.SURVIVAL, 2, 2);
        ServerPlayer b = player(h, GameType.SURVIVAL, 3, 2);
        HerobrineCompanion c = companion(h, a, HerobrineVariant.OCEAN, 9, 4, 4);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.HEROBRINE_ORB.get()));
        clickEntity(a, c);
        b.setItemInHand(InteractionHand.MAIN_HAND, a.getMainHandItem().copy());
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickBlock(h, b, 6, 0, 6);
        List<HerobrineCompanion> found = companionsNear(h, 6, 6, 3);
        h.assertTrue(found.size() == 1 && found.get(0).isOwnedBy(b), "Після обміну власником мав стати інший гравець");
        h.assertTrue(b.entityTags().contains(HerobrineVariant.OCEAN.caughtTag()), "Отриманий вид мав потрапити в Херодекс");
        removePlayers(h, a, b);
        h.succeed();
    }

    // =====================================================================
    // Бос і ловля
    // =====================================================================

    private static void bossCaptureByPlayer(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        HerobrineBoss b = boss(h, HerobrineVariant.FROST, 2, 6, 6);
        b.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 10000.0F);
        h.assertTrue(b.isRemoved(), "Бос мав зникнути після останнього удару");
        List<HerobrineCompanion> found = companionsNear(h, 6, 6, 3);
        h.assertTrue(found.size() == 1, "Мав з'явитися компаньйон");
        HerobrineCompanion c = found.get(0);
        h.assertTrue(c.isOwnedBy(p) && c.getVariant() == HerobrineVariant.FROST && c.getCompanionLevel() == 2,
                "Компаньйон має належати гравцю й мати вид і рівень боса");
        h.assertTrue(p.entityTags().contains(HerobrineVariant.FROST.caughtTag()), "Вид мав потрапити в Херодекс");
        removePlayers(h, p);
        h.succeed();
    }

    private static void bossCaptureByCompanion(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        HerobrineCompanion helper = companion(h, p, HerobrineVariant.CLASSIC, 5, 4, 6);
        helper.setTask(CompanionTask.STAY, null, null);
        HerobrineBoss b = boss(h, HerobrineVariant.STORM, 3, 8, 6);
        int xpBefore = helper.getXp() + helper.getCompanionLevel() * 100000;
        b.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(helper), 10000.0F);
        List<HerobrineCompanion> caught = companionsNear(h, 8, 6, 2).stream().filter(c -> c != helper).toList();
        h.assertTrue(caught.size() == 1 && caught.get(0).isOwnedBy(p), "Боса, добитого компаньйоном, отримує власник компаньйона");
        h.assertTrue(helper.getXp() + helper.getCompanionLevel() * 100000 > xpBefore, "Компаньйон-помічник мав отримати досвід");
        removePlayers(h, p);
        h.succeed();
    }

    private static void bossFireResistantNotCaptured(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        HerobrineBoss b = boss(h, HerobrineVariant.NETHER, 1, 6, 6);
        b.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
        b.setHealth(1.0F);
        b.setLastHurtByPlayer(p, 100);
        b.setLastHurtByMob(p);
        b.hurtServer(h.getLevel(), h.getLevel().damageSources().inFire(), 5.0F);
        h.assertTrue(!b.isRemoved() && b.isAlive(), "Вогнестійкий бос у вогні не мав бути «спійманий»");
        h.assertTrue(companionsNear(h, 6, 6, 3).isEmpty(), "Компаньйон не мав з'явитися");
        b.discard();
        removePlayers(h, p);
        h.succeed();
    }

    private static void bossKillCommand(GameTestHelper h) {
        HerobrineBoss b = boss(h, HerobrineVariant.GHOST, 1, 6, 6);
        b.kill(h.getLevel());
        h.runAfterDelay(5, () -> {
            h.assertTrue(companionsNear(h, 6, 6, 4).isEmpty(), "/kill не повинен перетворювати боса на компаньйона");
            h.succeed();
        });
    }

    private static void bossPersistenceRules(GameTestHelper h) {
        HerobrineBoss ritual = boss(h, HerobrineVariant.CLASSIC, 10, 4, 4);
        HerobrineBoss wild = boss(h, HerobrineVariant.DESERT, 3, 8, 8);
        wild.setWild(true);
        h.assertTrue(ritual.isPersistenceRequired() && !ritual.removeWhenFarAway(10000), "Ритуальний бос не повинен зникати");
        h.assertTrue(!wild.isPersistenceRequired() && wild.removeWhenFarAway(10000), "Дикий бос має зникати далеко від гравців");
        h.assertTrue(Math.abs(wild.getMaxHealth() - (100 + 22 * 3)) < 0.01, "HP дикого боса 3 рівня має бути 166, є " + wild.getMaxHealth());
        ritual.discard();
        wild.discard();
        h.succeed();
    }

    /** Будуємо всі 10 ритуалів і підпалюємо кожен — має з'явитися бос потрібного виду 10 рівня. */
    private static void ritualsAllVariants(GameTestHelper h) {
        h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer p = player(h, GameType.CREATIVE, 1, 1);
        HerobrineVariant[] variants = HerobrineVariant.values();
        for (int i = 0; i < variants.length; i++) {
            RitualHandler.Ritual r = RitualHandler.ritualFor(variants[i]);
            int bx = 3 + (i % 5) * 4;
            int bz = 3 + (i / 5) * 8;
            set(h, bx, 1, bz, r.center().display());
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
                set(h, bx + d[0], 1, bz + d[1], r.ring().display());
            }
            set(h, bx, 2, bz, r.top().display());
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                set(h, bx + d[0], 2, bz + d[1], r.torch().display());
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(r.ignitionItems().get(0)));
            clickBlock(h, p, bx, 2, bz);
            final int fi = i;
            List<HerobrineBoss> bosses = h.getLevel().getEntitiesOfClass(HerobrineBoss.class,
                    new AABB(h.absolutePos(new BlockPos(bx, 2, bz))).inflate(2.5), b -> b.getVariant() == variants[fi]);
            h.assertTrue(bosses.size() == 1, "Ритуал " + variants[i].id() + " не викликав боса");
            h.assertTrue(bosses.get(0).getBossLevel() == HerobrineBoss.RITUAL_LEVEL, "Ритуальний бос має бути 10 рівня");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(bx, 2, bz))).isAir(), "Верхній блок ритуалу мав згоріти");
            bosses.get(0).discard();
        }
        removePlayers(h, p);
        h.succeed();
    }

    private static void set(GameTestHelper h, int x, int y, int z, Block block) {
        h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
    }

    // =====================================================================
    // Посох
    // =====================================================================

    private static void staffSelectAndArea(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.MINER, 5, 4, 4);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMMAND_STAFF.get()));
        cycleStaff(h, p, 3); // Атака → Дуель → Охорона → Видобуток
        clickEntity(p, c);    // вибрати
        clickBlock(h, p, 8, 0, 8);
        clickBlock(h, p, 11, 0, 11);
        h.assertTrue(c.getTask() == CompanionTask.MINE && c.hasArea(), "Після двох кутів має бути видобуток в області");
        h.assertTrue(c.getAreaMin().equals(h.absolutePos(new BlockPos(8, -7, 8))) && c.getAreaMax().equals(h.absolutePos(new BlockPos(11, 0, 11))),
                "Область має бути 4×8×4 вглиб від кутів на одній висоті, є " + c.getAreaMin() + " .. " + c.getAreaMax());
        c.setTask(CompanionTask.STAY, null, null);
        // Той самий блок двічі — робота «в точці»
        cycleStaff(h, p, 5); // Видобуток → … → Охорона (через повне коло: Перенесення, До мене, Атака, Дуель, Охорона)
        clickBlock(h, p, 6, 0, 6);
        clickBlock(h, p, 6, 0, 6);
        h.assertTrue(c.getTask() == CompanionTask.GUARD && !c.hasArea(), "Той самий блок двічі має дати охорону в точці");
        removePlayers(h, p);
        h.succeed();
    }

    private static void staffForeignDuel(GameTestHelper h) {
        ServerPlayer pa = player(h, GameType.CREATIVE, 2, 2);
        ServerPlayer pb = player(h, GameType.CREATIVE, 2, 8);
        HerobrineCompanion a = companion(h, pa, HerobrineVariant.CLASSIC, 5, 5, 4);
        HerobrineCompanion b = companion(h, pb, HerobrineVariant.FROST, 5, 5, 8);
        a.setTask(CompanionTask.STAY, null, null);
        b.setTask(CompanionTask.STAY, null, null);
        pa.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMMAND_STAFF.get()));
        pb.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMMAND_STAFF.get()));
        cycleStaff(h, pa, 1);
        cycleStaff(h, pb, 1);
        clickEntity(pa, a);
        clickEntity(pa, b);
        h.assertTrue(!a.isInDuel() && !b.isInDuel(), "Дуель із чужим Херобріном не повинна починатися без згоди");
        clickEntity(pb, b);
        clickEntity(pb, a);
        h.assertTrue(a.isInDuel() && b.isInDuel(), "Після прийняття виклику дуель має початися");
        a.discard();
        b.discard();
        removePlayers(h, pa, pb);
        h.succeed();
    }

    private static void staffPvpOff(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        level.getGameRules().set(GameRules.PVP, false, level.getServer());
        ServerPlayer pa = player(h, GameType.SURVIVAL, 2, 2);
        ServerPlayer pb = player(h, GameType.SURVIVAL, 8, 8);
        HerobrineCompanion a = companion(h, pa, HerobrineVariant.CLASSIC, 5, 4, 4);
        pa.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMMAND_STAFF.get()));
        clickEntity(pa, pb);
        boolean ordered = a.hasCommandTarget();
        boolean wants = a.wantsToAttack(pb, pa);
        level.getGameRules().set(GameRules.PVP, true, level.getServer());
        boolean wantsWithPvp = a.wantsToAttack(pb, pa);
        removePlayers(h, pa, pb);
        h.assertTrue(!ordered, "З вимкненим PvP посох не повинен нацьковувати на гравця");
        h.assertTrue(!wants, "З вимкненим PvP компаньйон не повинен хотіти атакувати гравця");
        h.assertTrue(wantsWithPvp, "З увімкненим PvP компаньйон може захищати власника від гравця");
        h.succeed();
    }

    private static void herodexPages(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        p.addTag(HerobrineVariant.STORM.caughtTag());
        p.addTag(HerobrineVariant.MINER.caughtTag());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.HERODEX.get()));
        p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        WrittenBookContent content = p.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(content != null, "Херодекс має отримати сторінки книги");
        int expected = 3 + HerobrineVariant.values().length * 2;
        h.assertTrue(content.pages().size() == expected, "Сторінок має бути " + expected + ", є " + content.pages().size());
        removePlayers(h, p);
        h.succeed();
    }

    // =====================================================================
    // Роботи
    // =====================================================================

    private static void mineAreaQuarry(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 1, 1);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.MINER, 20, 4, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int x = 8; x <= 11; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 8; z <= 11; z++) {
                    set(h, x, y, z, y == 2 && x == 9 && z == 9 ? Blocks.IRON_ORE : Blocks.STONE);
                }
            }
        }
        set(h, 3, 1, 8, Blocks.CHEST);
        c.setTaskInArea(CompanionTask.MINE, h.absolutePos(new BlockPos(8, 1, 8)), h.absolutePos(new BlockPos(11, 3, 11)));
        h.succeedWhen(() -> {
            for (int x = 8; x <= 11; x++) {
                for (int y = 1; y <= 3; y++) {
                    for (int z = 8; z <= 11; z++) {
                        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, z))).isAir(), "Область ще не викопана");
                    }
                }
            }
            h.assertTrue(countInContainer(h, 3, 1, 8, Items.COBBLESTONE) >= 40, "Бруківка мала опинитися в сундуку");
            h.assertTrue(countInContainer(h, 3, 1, 8, Items.RAW_IRON) >= 1, "Руда мала опинитися в сундуку");
            h.assertTrue(c.getTask() == CompanionTask.FOLLOW, "Після роботи компаньйон мав повернутися до власника");
            removePlayers(h, p);
        });
    }

    private static void minePausedOwnerOffline(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.MINER, 20, 4, 4);
        c.setOwnerReference(EntityReference.of(UUID.randomUUID()));
        c.setTame(true, false);
        set(h, 8, 1, 8, Blocks.STONE);
        c.setTaskInArea(CompanionTask.MINE, h.absolutePos(new BlockPos(8, 1, 8)), h.absolutePos(new BlockPos(8, 1, 8)));
        h.runAfterDelay(150, () -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8, 1, 8))).is(Blocks.STONE),
                    "Поки власника немає в грі, видобуток має стояти на паузі");
            h.assertTrue(!c.isInSittingPose(), "Компаньйон не повинен сідати лише через те, що власник не в грі");
            h.succeed();
        });
    }

    private static void haulToChest(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.CLASSIC, 5, 4, 4);
        set(h, 2, 1, 2, Blocks.CHEST);
        c.setTaskInArea(CompanionTask.HAUL, h.absolutePos(new BlockPos(3, 1, 3)), h.absolutePos(new BlockPos(14, 1, 14)));
        c.setStoragePos(h.absolutePos(new BlockPos(2, 1, 2)));
        ItemEntity a = h.spawn(EntityTypes.ITEM, new BlockPos(10, 1, 10));
        a.setItem(new ItemStack(Items.DIAMOND, 5));
        ItemEntity b = h.spawn(EntityTypes.ITEM, new BlockPos(12, 1, 6));
        b.setItem(new ItemStack(Items.EMERALD, 7));
        h.succeedWhen(() -> {
            h.assertTrue(countInContainer(h, 2, 1, 2, Items.DIAMOND) == 5, "Алмази мали опинитися в сундуку");
            h.assertTrue(countInContainer(h, 2, 1, 2, Items.EMERALD) == 7, "Смарагди мали опинитися в сундуку");
        });
    }

    private static void guardKillsIntruder(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.FROST, 15, 4, 4);
        c.setTask(CompanionTask.GUARD, h.absolutePos(new BlockPos(4, 1, 4)), null);
        c.autoAttack = false; // б'є лише як охоронець
        Monster husk = h.spawn(EntityTypes.HUSK, new BlockPos(10, 1, 10));
        husk.setPersistenceRequired();
        h.succeedWhen(() -> h.assertTrue(!husk.isAlive(), "Охоронець мав знищити чужака"));
    }

    private static void duelNonLethal(GameTestHelper h) {
        HerobrineCompanion a = companion(h, null, HerobrineVariant.NETHER, 25, 4, 4);
        HerobrineCompanion b = companion(h, null, HerobrineVariant.FROST, 1, 7, 4);
        h.assertTrue(HerobrineCompanion.startDuel(a, b), "Дуель мала початися");
        h.succeedWhen(() -> {
            h.assertTrue(b.isAlive() && a.isAlive(), "У дуелі ніхто не повинен загинути");
            h.assertTrue(!a.isInDuel() && !b.isInDuel(), "Дуель ще триває");
            h.assertTrue(a.getXp() > 0, "Переможець мав отримати досвід");
        });
    }

    private static void duelInterrupted(GameTestHelper h) {
        HerobrineCompanion a = companion(h, null, HerobrineVariant.CLASSIC, 10, 4, 4);
        HerobrineCompanion b = companion(h, null, HerobrineVariant.FROST, 10, 7, 4);
        Monster zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(10, 1, 10));
        HerobrineCompanion.startDuel(a, b);
        a.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(zombie), 1.0F);
        h.assertTrue(!a.isInDuel() && !b.isInDuel(), "Втручання стороннього має скасувати дуель");
        zombie.discard();
        h.succeed();
    }

    // =====================================================================
    // Різне
    // =====================================================================

    /** Спорядження має випадати навіть після перезавантаження світу (Mob перезаписує шанси випадіння). */
    private static void gearDropsAfterReload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        HerobrineCompanion original = companion(h, null, HerobrineVariant.CLASSIC, 3, 4, 4);
        original.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        original.saveWithoutId(out);
        original.discard();
        HerobrineCompanion reloaded = HerobrineMod.HEROBRINE_COMPANION.get().create(level, EntitySpawnReason.LOAD);
        net.minecraft.nbt.CompoundTag tag = out.buildResult();
        tag.remove("UUID");
        // Як у старих збереженнях і в призваних командою: шансів випадіння в даних немає
        tag.remove("drop_chances");
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        reloaded.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        level.addFreshEntity(reloaded);
        Monster zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(9, 1, 9));
        reloaded.hurtServer(level, level.damageSources().mobAttack(zombie), 100000.0F);
        h.runAfterDelay(3, () -> {
            List<ItemEntity> helmets = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4),
                    e -> e.getItem().is(Items.DIAMOND_HELMET));
            h.assertTrue(helmets.size() == 1, "Шолом мав випасти");
            h.assertTrue(helmets.get(0).getItem().getDamageValue() == 0, "Шолом мав випасти цілим");
            zombie.discard();
            h.succeed();
        });
    }

    private static void levelUpStats(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.CLASSIC, 1, 4, 4);
        c.addXp(10_000_000);
        h.assertTrue(c.getCompanionLevel() == HerobrineCompanion.MAX_LEVEL, "Рівень мав стати максимальним");
        h.assertTrue(Math.abs(c.getMaxHealth() - (70 + 4 * 49)) < 0.01, "HP на 50 рівні має бути 266, є " + c.getMaxHealth());
        h.succeed();
    }

    private static void claimOwnerless(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion c = companion(h, null, HerobrineVariant.GHOST, 4, 4, 4);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickEntity(p, c);
        h.assertTrue(c.isOwnedBy(p) && c.isTame(), "Нічийого Херобріна мало бути можна забрати порожньою рукою");
        removePlayers(h, p);
        h.succeed();
    }

    private static void spawnEggs(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMPANION_EGGS.get(HerobrineVariant.DESERT).get()));
        clickBlock(h, p, 5, 0, 5);
        List<HerobrineCompanion> pets = companionsNear(h, 5, 5, 2);
        h.assertTrue(pets.size() == 1, "Яйце компаньйона мало заспавнити компаньйона");
        HerobrineCompanion pet = pets.get(0);
        h.assertTrue(pet.isOwnedBy(p) && pet.getVariant() == HerobrineVariant.DESERT && pet.getCompanionLevel() == 5,
                "Компаньйон з яйця: власник, вид і рівень 5");
        h.assertTrue(pet.getHealth() >= pet.getMaxHealth() - 0.01, "Компаньйон з яйця має бути з повним здоров'ям");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.BOSS_EGGS.get(HerobrineVariant.ENDER).get()));
        clickBlock(h, p, 10, 0, 10);
        List<HerobrineBoss> bosses = h.getLevel().getEntitiesOfClass(HerobrineBoss.class,
                new AABB(h.absolutePos(new BlockPos(10, 1, 10))).inflate(2));
        h.assertTrue(bosses.size() == 1 && bosses.get(0).getVariant() == HerobrineVariant.ENDER
                && bosses.get(0).getBossLevel() == HerobrineBoss.RITUAL_LEVEL, "Яйце боса: вид і рівень 10");
        h.assertTrue(Math.abs(bosses.get(0).getHealth() - 320.0F) < 0.01, "Бос 10 рівня має мати 320 HP");
        bosses.forEach(net.minecraft.world.entity.Entity::discard);
        removePlayers(h, p);
        h.succeed();
    }

    /** Кожна активна здібність спрацьовує і (крім лікування/невидимості) завдає шкоди ворогу. */
    private static void skillsAllVariants(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 1, 1);
        HerobrineVariant[] variants = HerobrineVariant.values();
        for (int i = 0; i < variants.length; i++) {
            int x = 3 + (i % 5) * 4;
            int z = 3 + (i / 5) * 9;
            HerobrineCompanion c = companion(h, p, variants[i], 10, x, z);
            c.setTask(CompanionTask.STAY, null, null);
            Monster zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(x, 1, z + 2));
            zombie.setNoAi(true);
            c.setTarget(zombie);
            float before = zombie.getHealth();
            boolean ok = c.useSkill(true);
            h.assertTrue(ok, "Здібність " + variants[i].id() + " не спрацювала");
            boolean damaging = variants[i] != HerobrineVariant.FOREST && variants[i] != HerobrineVariant.GHOST;
            if (damaging) {
                h.assertTrue(zombie.getHealth() < before || !zombie.isAlive(), "Здібність " + variants[i].id() + " не завдала шкоди");
            }
            h.assertTrue(c.getSkillCooldown() > 0, "Після здібності має бути перезарядка");
            zombie.discard();
            c.discard();
        }
        removePlayers(h, p);
        h.succeed();
    }

    // =====================================================================
    // Додаткові тести робіт і предметів
    // =====================================================================

    private static int countInBackpack(HerobrineCompanion c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.backpack.getContainerSize(); i++) {
            if (c.backpack.getItem(i).is(item)) {
                n += c.backpack.getItem(i).getCount();
            }
        }
        return n;
    }

    private static boolean hasRaw(GameTestHelper h, HerobrineCompanion c, int cx, int cy, int cz) {
        for (net.minecraft.world.item.Item raw : new net.minecraft.world.item.Item[]{Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER}) {
            if (countInBackpack(c, raw) > 0 || countInContainer(h, cx, cy, cz, raw) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Незерський одразу переплавляє руду. */
    private static void netherSmeltsOres(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.NETHER, 20, 4, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        set(h, 8, 1, 8, Blocks.IRON_ORE);
        set(h, 9, 1, 8, Blocks.GOLD_ORE);
        set(h, 8, 1, 9, Blocks.COPPER_ORE);
        set(h, 9, 1, 9, Blocks.DEEPSLATE_IRON_ORE);
        set(h, 3, 1, 8, Blocks.CHEST);
        c.setTaskInArea(CompanionTask.MINE, h.absolutePos(new BlockPos(8, 1, 8)), h.absolutePos(new BlockPos(9, 1, 9)));
        h.succeedWhen(() -> {
            int iron = countInBackpack(c, Items.IRON_INGOT) + countInContainer(h, 3, 1, 8, Items.IRON_INGOT);
            int gold = countInBackpack(c, Items.GOLD_INGOT) + countInContainer(h, 3, 1, 8, Items.GOLD_INGOT);
            int copper = countInBackpack(c, Items.COPPER_INGOT) + countInContainer(h, 3, 1, 8, Items.COPPER_INGOT);
            h.assertTrue(iron >= 2 && gold >= 1 && copper >= 2, "Ще не всі злитки: залізо " + iron + ", золото " + gold + ", мідь " + copper);
            h.assertTrue(!hasRaw(h, c, 3, 1, 8), "Сировина мала переплавитися");
        });
    }

    /** Лісовий рубає дерево (колоди поруч із природним листям). */
    private static void forestChopsTrees(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.FOREST, 20, 6, 10);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        for (int y = 1; y <= 4; y++) {
            set(h, 10, y, 10, Blocks.OAK_LOG);
        }
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                for (int y = 4; y <= 5; y++) {
                    if (!(x == 10 && z == 10 && y == 4)) {
                        set(h, x, y, z, Blocks.OAK_LEAVES);
                    }
                }
            }
        }
        c.setTask(CompanionTask.MINE, h.absolutePos(new BlockPos(6, 1, 10)), Direction.WEST);
        h.succeedWhen(() -> h.assertTrue(countInBackpack(c, Items.OAK_LOG) >= 4,
                "Лісовий мав зрубати 4 колоди, є " + countInBackpack(c, Items.OAK_LOG)));
    }

    /** Перенесення з сундука-джерела в сундук-склад. */
    private static void haulSourceToStorage(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.CLASSIC, 5, 6, 6);
        set(h, 3, 1, 3, Blocks.CHEST);
        set(h, 12, 1, 12, Blocks.CHEST);
        Container source = HopperBlockEntity.getContainerAt(h.getLevel(), h.absolutePos(new BlockPos(12, 1, 12)));
        source.setItem(0, new ItemStack(Items.BREAD, 10));
        source.setItem(5, new ItemStack(Items.IRON_INGOT, 20));
        c.setTask(CompanionTask.HAUL, h.absolutePos(new BlockPos(6, 1, 6)), null);
        c.setStoragePos(h.absolutePos(new BlockPos(3, 1, 3)));
        c.setSourcePos(h.absolutePos(new BlockPos(12, 1, 12)));
        h.succeedWhen(() -> {
            h.assertTrue(countInContainer(h, 3, 1, 3, Items.BREAD) == 10 && countInContainer(h, 3, 1, 3, Items.IRON_INGOT) == 20,
                    "Речі мали переїхати на склад");
            h.assertTrue(source.isEmpty(), "Джерело мало спорожніти");
        });
    }

    /** Охорона області: б'є чужака всередині, не чіпає того, хто далеко за межами. */
    private static void guardAreaBoundary(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.FROST, 15, 5, 5);
        c.autoAttack = false;
        c.setTaskInArea(CompanionTask.GUARD, h.absolutePos(new BlockPos(2, 1, 2)), h.absolutePos(new BlockPos(8, 1, 8)));
        Monster inside = h.spawn(EntityTypes.HUSK, new BlockPos(7, 1, 7));
        inside.setPersistenceRequired();
        Monster outside = h.spawn(EntityTypes.HUSK, new BlockPos(20, 1, 20));
        outside.setPersistenceRequired();
        outside.setNoAi(true);
        h.runAfterDelay(400, () -> {
            h.assertTrue(!inside.isAlive(), "Чужак усередині області мав загинути");
            h.assertTrue(outside.isAlive() && outside.getHealth() >= outside.getMaxHealth(), "Мобa за межами області не можна чіпати");
            outside.discard();
            h.succeed();
        });
    }

    /** Видобуток «в точці»: штольня 1×2 у стіні в напрямку погляду. */
    private static void minePointTunnel(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.MINER, 20, 6, 10);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int x = 8; x <= 14; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 9; z <= 11; z++) {
                    set(h, x, y, z, Blocks.STONE);
                }
            }
        }
        c.setTask(CompanionTask.MINE, h.absolutePos(new BlockPos(6, 1, 10)), Direction.EAST);
        h.succeedWhen(() -> {
            for (int x = 8; x <= 12; x++) {
                for (int y = 1; y <= 2; y++) {
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, 10))).isAir(), "Штольня ще не прокопана");
                }
            }
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(10, 3, 10))).is(Blocks.STONE), "Стеля штольні має лишитися");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(10, 1, 9))).is(Blocks.STONE), "Стіни штольні мають лишитися");
        });
    }

    /** Область викопано, а сундук повний — компаньйон однаково повертається (не стоїть вічно). */
    private static void mineFullChestReturns(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.MINER, 20, 4, 4);
        set(h, 8, 1, 8, Blocks.STONE);
        set(h, 3, 1, 8, Blocks.CHEST);
        Container chest = HopperBlockEntity.getContainerAt(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 8)));
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        c.setTaskInArea(CompanionTask.MINE, h.absolutePos(new BlockPos(8, 1, 8)), h.absolutePos(new BlockPos(8, 1, 8)));
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8, 1, 8))).isAir(), "Блок ще не викопано");
            h.assertTrue(c.getTask() == CompanionTask.FOLLOW, "З повним сундуком компаньйон усе одно мав повернутися");
            h.assertTrue(countInBackpack(c, Items.COBBLESTONE) == 1, "Бруківка мала лишитися в рюкзаку");
        });
    }

    private static void orbRefusesDuringDuel(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion a = companion(h, p, HerobrineVariant.CLASSIC, 5, 4, 4);
        HerobrineCompanion b = companion(h, p, HerobrineVariant.FROST, 5, 7, 4);
        HerobrineCompanion.startDuel(a, b);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.HEROBRINE_ORB.get()));
        clickEntity(p, a);
        boolean stored = a.isRemoved();
        boolean filled = HerobrineOrbItem.isFilled(p.getMainHandItem());
        a.discard();
        b.discard();
        removePlayers(h, p);
        h.assertTrue(!stored && !filled, "Компаньйона в дуелі не можна ховати у сферу");
        h.succeed();
    }

    private static void deathDropsBackpack(GameTestHelper h) {
        HerobrineCompanion c = companion(h, null, HerobrineVariant.CLASSIC, 3, 6, 6);
        c.insertIntoBackpack(new ItemStack(Items.EMERALD, 12));
        Monster zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(12, 1, 12));
        c.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(zombie), 100000.0F);
        h.runAfterDelay(3, () -> {
            int emeralds = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(6, 1, 6))).inflate(4),
                    e -> e.getItem().is(Items.EMERALD)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            zombie.discard();
            h.assertTrue(emeralds == 12, "Вміст рюкзака мав випасти, випало " + emeralds);
            h.succeed();
        });
    }

    private static void backpackUpgrade(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.CLASSIC, 3, 4, 4);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.BACKPACK_UPGRADE.get(), 5));
        for (int i = 0; i < 5; i++) {
            clickEntity(p, c);
        }
        int rows = c.backpackRows;
        int left = p.getMainHandItem().getCount();
        removePlayers(h, p);
        h.assertTrue(rows == HerobrineCompanion.MAX_BACKPACK_ROWS, "Рюкзак мав розширитися до максимуму, рядів " + rows);
        h.assertTrue(left == 2, "Мали витратитися лише 3 апгрейди, лишилося " + left);
        h.succeed();
    }

    private static void menuCommands(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.CLASSIC, 3, 4, 4);
        CompanionTask[] expected = {CompanionTask.STAY, CompanionTask.GUARD, CompanionTask.MINE, CompanionTask.HAUL, CompanionTask.FOLLOW};
        for (CompanionTask t : expected) {
            c.runCommand(HerobrineCompanion.CMD_CYCLE_TASK, p);
            h.assertTrue(c.getTask() == t, "Очікувалось завдання " + t + ", є " + c.getTask());
            h.assertTrue(c.isOrderedToSit() == (t == CompanionTask.STAY), "Сидіти лише на «Стояти»");
        }
        boolean attack = c.autoAttack;
        c.runCommand(HerobrineCompanion.CMD_TOGGLE_ATTACK, p);
        h.assertTrue(c.autoAttack != attack, "Перемикач атаки не спрацював");
        c.runCommand(HerobrineCompanion.CMD_SKILL, p); // без ворогів: лише повідомлення, без помилок
        c.runCommand(HerobrineCompanion.CMD_COME, p);
        h.assertTrue(c.getTask() == CompanionTask.FOLLOW, "«До мене» має перевести в «Іти за мною»");
        removePlayers(h, p);
        h.succeed();
    }

    private static void staffLinksHaulChests(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE, 2, 2);
        HerobrineCompanion c = companion(h, p, HerobrineVariant.ENDER, 5, 4, 4);
        set(h, 8, 1, 4, Blocks.CHEST);
        set(h, 8, 1, 8, Blocks.BARREL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HerobrineMod.COMMAND_STAFF.get()));
        cycleStaff(h, p, 4); // → Перенесення
        clickEntity(p, c);
        clickBlock(h, p, 8, 1, 4);           // сундук → склад
        p.setShiftKeyDown(true);
        clickBlock(h, p, 8, 1, 8);           // Shift + бочка → джерело
        p.setShiftKeyDown(false);
        BlockPos storage = c.getStoragePos();
        BlockPos source = c.getSourcePos();
        removePlayers(h, p);
        h.assertTrue(h.absolutePos(new BlockPos(8, 1, 4)).equals(storage), "Склад має бути сундуком, є " + storage);
        h.assertTrue(h.absolutePos(new BlockPos(8, 1, 8)).equals(source), "Джерело має бути бочкою, є " + source);
        h.succeed();
    }
}
