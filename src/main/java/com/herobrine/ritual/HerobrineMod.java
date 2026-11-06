package com.herobrine.ritual;

import com.herobrine.ritual.item.CommandStaffItem;
import com.herobrine.ritual.item.HerobrineOrbItem;
import com.herobrine.ritual.item.HerodexItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

@Mod(HerobrineMod.MODID)
public class HerobrineMod {
    public static final String MODID = "herobrine_ritual";

    // ---- Сутності ----

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<HerobrineBoss>> HEROBRINE_BOSS =
            ENTITY_TYPES.register("herobrine_boss", id -> EntityType.Builder
                    .of(HerobrineBoss::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<HerobrineCompanion>> HEROBRINE_COMPANION =
            ENTITY_TYPES.register("herobrine_companion", id -> EntityType.Builder
                    .of(HerobrineCompanion::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    // ---- Меню ----

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CompanionMenu>> COMPANION_MENU =
            MENU_TYPES.register("companion_menu", () -> IMenuTypeExtension.create(CompanionMenu::new));

    // ---- Предмети ----

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MODID);

    /** Яйця для творчого режиму: по одному на кожен вид, окремо для боса й для компаньйона. */
    public static final Map<HerobrineVariant, DeferredHolder<Item, Item>> BOSS_EGGS =
            new EnumMap<>(HerobrineVariant.class);
    public static final Map<HerobrineVariant, DeferredHolder<Item, Item>> COMPANION_EGGS =
            new EnumMap<>(HerobrineVariant.class);

    static {
        for (HerobrineVariant variant : HerobrineVariant.values()) {
            BOSS_EGGS.put(variant, ITEMS.register("herobrine_boss_egg_" + variant.id(),
                    id -> new VariantEggItem(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id))
                            .spawnEgg(HEROBRINE_BOSS.get()), variant, true)));
            COMPANION_EGGS.put(variant, ITEMS.register("herobrine_companion_egg_" + variant.id(),
                    id -> new VariantEggItem(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id))
                            .spawnEgg(HEROBRINE_COMPANION.get()), variant, false)));
        }
    }

    /** Апгрейд рюкзака: ПКМ по компаньйону додає рядок слотів (максимум 3 апгрейди). */
    public static final DeferredHolder<Item, Item> BACKPACK_UPGRADE =
            ITEMS.register("backpack_upgrade", id -> new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(16)));

    /** Сфера Херобріна: зберігає компаньйона в предметі (і дає змогу передати його іншому гравцю). */
    public static final DeferredHolder<Item, Item> HEROBRINE_ORB =
            ITEMS.register("herobrine_orb", id -> new HerobrineOrbItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(16)));

    /** Посох наказів: вибір компаньйона, атака, дуелі, охорона, видобуток, перенесення. */
    public static final DeferredHolder<Item, Item> COMMAND_STAFF =
            ITEMS.register("command_staff", id -> new CommandStaffItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(1)));

    /** Херодекс: книга з усіма видами, де вони з'являються, їхні ритуали та здібності. */
    public static final DeferredHolder<Item, Item> HERODEX =
            ITEMS.register("herodex", id -> new HerodexItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(1)));

    // ---- Вкладка творчого режиму ----

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.herobrine_ritual"))
                    .icon(() -> new ItemStack(HEROBRINE_ORB.get()))
                    .displayItems((params, output) -> {
                        output.accept(HERODEX.get());
                        output.accept(COMMAND_STAFF.get());
                        output.accept(HEROBRINE_ORB.get());
                        output.accept(BACKPACK_UPGRADE.get());
                        for (HerobrineVariant variant : HerobrineVariant.values()) {
                            output.accept(BOSS_EGGS.get(variant).get());
                        }
                        for (HerobrineVariant variant : HerobrineVariant.values()) {
                            output.accept(COMPANION_EGGS.get(variant).get());
                        }
                    })
                    .build());

    public HerobrineMod(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        modBus.addListener(HerobrineMod::registerAttributes);
        container.registerConfig(ModConfig.Type.LOCAL, HerobrineConfig.SPEC);
        com.herobrine.ritual.test.HerobrineGameTests.init(modBus); // лише в середовищі розробки
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HEROBRINE_BOSS.get(), HerobrineBoss.createAttributes().build());
        event.put(HEROBRINE_COMPANION.get(), HerobrineCompanion.createAttributes().build());
    }
}
