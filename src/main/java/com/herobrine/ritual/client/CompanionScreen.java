package com.herobrine.ritual.client;

import com.herobrine.ritual.CompanionMenu;
import com.herobrine.ritual.HerobrineCompanion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Екран компаньйона. Малюється без текстур (прості прямокутники).
 * Ліворуч броня, посередині кнопки (завдання, до мене, автоатака, годування, автоздібність, здібність),
 * праворуч рівень / здоров'я / досвід і слоти рук, нижче рюкзак та інвентар гравця.
 * Кнопки працюють через стандартний механізм меню (clickMenuButton), окремі пакети не потрібні.
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int HP_TEXT = 0xFFA01818;
    private static final int XP_TEXT = 0xFF2E7D1E;

    private Button taskButton;
    private Button attackButton;
    private Button feedButton;
    private Button autoSkillButton;
    private Button skillButton;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, menu.imageHeight());
        // inventoryLabelY за замовчуванням = imageHeight - 94, що збігається з нашою розкладкою
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + 28;
        int y = this.topPos + 17;
        int w = 84;
        int h = 12;
        int step = 13;

        taskButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendCommand(HerobrineCompanion.CMD_CYCLE_TASK)).bounds(x, y, w, h).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_ritual.cmd.come"),
                b -> sendCommand(HerobrineCompanion.CMD_COME)).bounds(x, y + step, w, h).build());
        attackButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendCommand(HerobrineCompanion.CMD_TOGGLE_ATTACK)).bounds(x, y + step * 2, w, h).build());
        feedButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendCommand(HerobrineCompanion.CMD_TOGGLE_FEED)).bounds(x, y + step * 3, w, h).build());
        autoSkillButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendCommand(HerobrineCompanion.CMD_TOGGLE_AUTOSKILL)).bounds(x, y + step * 4, w, h).build());
        skillButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendCommand(HerobrineCompanion.CMD_SKILL)).bounds(x, y + step * 5, w, h).build());
        refreshLabels();
    }

    private void sendCommand(int command) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, command);
        }
    }

    private void refreshLabels() {
        if (taskButton == null) {
            return;
        }
        taskButton.setMessage(Component.literal("▶ ")
                .append(Component.translatable(menu.task().translationKey())));
        attackButton.setMessage(Component.translatable(menu.isAutoAttack()
                ? "gui.herobrine_ritual.cmd.attack_on" : "gui.herobrine_ritual.cmd.attack_off"));
        feedButton.setMessage(Component.translatable(menu.isAutoFeed()
                ? "gui.herobrine_ritual.cmd.feed_on" : "gui.herobrine_ritual.cmd.feed_off"));
        autoSkillButton.setMessage(Component.translatable(menu.isAutoSkill()
                ? "gui.herobrine_ritual.cmd.autoskill_on" : "gui.herobrine_ritual.cmd.autoskill_off"));

        Component skillName = Component.translatable("gui.herobrine_ritual.skill." + menu.variant().id());
        int cd = menu.skillCooldownSeconds();
        skillButton.setMessage(cd > 0
                ? Component.empty().append(skillName).append(Component.literal(" (" + cd + ")"))
                : skillName);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gg, mouseX, mouseY, partialTick);
        refreshLabels();

        int x = this.leftPos;
        int y = this.topPos;

        // Панель з "об'ємною" рамкою
        gg.fill(x, y, x + this.imageWidth, y + this.imageHeight, DARK);
        gg.fill(x, y, x + this.imageWidth - 2, y + this.imageHeight - 2, LIGHT);
        gg.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, PANEL);

        // Рамки слотів
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            gg.fill(sx, sy, sx + 18, sy + 18, SLOT_BORDER);
            gg.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_FILL);
        }

        // Смужка досвіду над слотами рук
        int barX = x + 116;
        int barY = y + 48;
        int barW = 52;
        gg.fill(barX, barY, barX + barW, barY + 3, 0xFF2A2A2A);
        int filled = (int) (barW * Math.min(1.0F, menu.xp() / (float) menu.xpToNext()));
        gg.fill(barX, barY, barX + filled, barY + 3, 0xFF7CFC3C);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        super.extractLabels(gg, mouseX, mouseY);
        // Статистика праворуч (координати вже відносно вікна)
        gg.text(this.font, Component.translatable("gui.herobrine_ritual.level", menu.level()), 116, 18, TEXT, false);
        gg.text(this.font, "❤ " + menu.health() + "/" + menu.maxHealth(), 116, 28, HP_TEXT, false);
        int percent = (int) (100.0F * menu.xp() / menu.xpToNext());
        gg.text(this.font, Component.translatable("gui.herobrine_ritual.xp", percent), 116, 38, XP_TEXT, false);
    }
}
