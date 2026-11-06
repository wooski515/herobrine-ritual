package com.herobrine.ritual;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Інвентар компаньйона:
 *  - слоти 0..5: шолом, нагрудник, штани, черевики, основна рука, друга рука;
 *  - слоти 6..23: рюкзак (звідси він бере їжу, щоб годувати тварин);
 *  - далі інвентар гравця.
 */
public class CompanionMenu extends AbstractContainerMenu {
    public static final int GEAR_COUNT = 6;
    public static final int DATA_COUNT = 11;
    public static final int BACKPACK_START = GEAR_COUNT;

    private final HerobrineCompanion companion;
    private final ContainerData data;
    private final int backpackRows;
    private final int backpackEnd;

    /** Клієнтський конструктор (викликається з MenuType). Порядок читання: id сутності, потім рядки рюкзака. */
    public CompanionMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv,
                (HerobrineCompanion) playerInv.player.level().getEntity(buf.readVarInt()),
                buf.readVarInt(),
                new SimpleContainerData(DATA_COUNT));
    }

    public CompanionMenu(int id, Inventory playerInv, HerobrineCompanion companion,
                         int backpackRows, ContainerData data) {
        super(HerobrineMod.COMPANION_MENU.get(), id);
        this.companion = companion;
        this.data = data;
        this.backpackRows = backpackRows;
        this.backpackEnd = BACKPACK_START + backpackRows * 9;
        this.addDataSlots(data);
        Container gear = new GearContainer(companion);

        // Броня (зліва зверху вниз)
        for (int i = 0; i < 4; i++) {
            this.addSlot(new GearSlot(gear, i, 8, 18 + i * 18, HerobrineCompanion.GEAR_SLOTS[i]));
        }
        // Руки
        this.addSlot(new GearSlot(gear, 4, 116, 54, EquipmentSlot.MAINHAND));
        this.addSlot(new GearSlot(gear, 5, 134, 54, EquipmentSlot.OFFHAND));

        // Рюкзак: 9 колонок, стільки рядків, скільки відкрито апгрейдами
        for (int i = 0; i < backpackRows * 9; i++) {
            this.addSlot(new Slot(companion.backpack, i, 8 + (i % 9) * 18, 98 + (i / 9) * 18));
        }

        // Інвентар гравця
        int invY = playerInvY(backpackRows);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, invY + 58));
        }
    }

    /** Y першого ряду інвентаря гравця для заданої кількості рядків рюкзака. */
    public static int playerInvY(int backpackRows) {
        return 98 + backpackRows * 18 + 14;
    }

    /** Висота вікна меню. */
    public int imageHeight() {
        return playerInvY(backpackRows) + 58 + 18 + 8;
    }

    public CompanionTask task() {
        return CompanionTask.byOrdinal(data.get(0));
    }

    public boolean isAutoAttack() {
        return data.get(1) != 0;
    }

    public boolean isAutoFeed() {
        return data.get(2) != 0;
    }

    /** Секунди до готовності активної здібності (0 = готова). */
    public int skillCooldownSeconds() {
        return data.get(3);
    }

    public HerobrineVariant variant() {
        return HerobrineVariant.byOrdinal(data.get(4));
    }

    public int level() {
        return data.get(5);
    }

    public int xp() {
        return data.get(6);
    }

    public int xpToNext() {
        return Math.max(1, data.get(7));
    }

    public boolean isAutoSkill() {
        return data.get(8) != 0;
    }

    public int health() {
        return data.get(9);
    }

    public int maxHealth() {
        return Math.max(1, data.get(10));
    }

    /** Кнопки команд з екрана (id = HerobrineCompanion.CMD_*). Виконується на сервері. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) {
            return false;
        }
        companion.runCommand(id, player);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return companion != null && companion.isAlive()
                && companion.isOwnedBy(player)
                && player.distanceToSqr(companion) < 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < backpackEnd) {
                // З екіпірування чи рюкзака в інвентар гравця
                if (!this.moveItemStackTo(stack, backpackEnd, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // З інвентаря гравця: броня одягається, решта йде в рюкзак
                if (stack.has(DataComponents.EQUIPPABLE)) {
                    this.moveItemStackTo(stack, 0, 4, false);
                }
                if (!stack.isEmpty() && !this.moveItemStackTo(stack, BACKPACK_START, backpackEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /** Слот екіпірування: броня тільки у свій слот, руки приймають усе. */
    private static class GearSlot extends Slot {
        private final EquipmentSlot equipmentSlot;

        GearSlot(Container container, int index, int x, int y, EquipmentSlot equipmentSlot) {
            super(container, index, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND) {
                return true;
            }
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot() == equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return (equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND)
                    ? super.getMaxStackSize() : 1;
        }
    }

    /** Обгортка над екіпіруванням сутності, щоб показувати його як Container. */
    private static class GearContainer implements Container {
        private final HerobrineCompanion entity;

        GearContainer(HerobrineCompanion entity) {
            this.entity = entity;
        }

        @Override
        public int getContainerSize() {
            return GEAR_COUNT;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot slot : HerobrineCompanion.GEAR_SLOTS) {
                if (!entity.getItemBySlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return entity.getItemBySlot(HerobrineCompanion.GEAR_SLOTS[index]);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.split(count);
            setItem(index, current);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = getItem(index).copy();
            entity.setItemSlot(HerobrineCompanion.GEAR_SLOTS[index], ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            entity.setItemSlot(HerobrineCompanion.GEAR_SLOTS[index], stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return entity.isAlive();
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot slot : HerobrineCompanion.GEAR_SLOTS) {
                entity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
