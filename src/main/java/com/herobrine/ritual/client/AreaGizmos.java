package com.herobrine.ritual.client;

import com.herobrine.ritual.CompanionTask;
import com.herobrine.ritual.HerobrineCompanion;
import com.herobrine.ritual.HerobrineMod;
import com.herobrine.ritual.item.CommandStaffItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Сітка робочих областей. Поки гравець тримає Посох наказів, видно:
 * <ul>
 *   <li>області своїх Херобрінів (видобуток — помаранчева, охорона — червона, перенесення — синя) з сіткою зверху;</li>
 *   <li>коло охорони навколо поста, зону збору для перенесення «в точці»;</li>
 *   <li>сундук-склад (зелений) і сундук-джерело (жовтий);</li>
 *   <li>рамку навколо вибраного Херобріна;</li>
 *   <li>живий попередній перегляд області, поки виділяєш другий кут.</li>
 * </ul>
 * Малюється вбудованими «гізмо» Minecraft (лінії поверх блоків), тільки на клієнті.
 */
@EventBusSubscriber(modid = HerobrineMod.MODID, value = Dist.CLIENT)
public class AreaGizmos {
    private static final int MINE = 0xFFFFA020;
    private static final int GUARD = 0xFFFF4040;
    private static final int HAUL = 0xFF40A0FF;
    private static final int PREVIEW = 0xFFFFFFFF;
    private static final int STORAGE = 0xFF50FF50;
    private static final int SOURCE = 0xFFFFFF50;
    private static final int SELECTED = 0xFFFFE060;
    private static final double STAFF_RANGE = 32.0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        ItemStack staff = heldStaff(player);
        if (staff == null) {
            return;
        }
        UUID selectedId = CommandStaffItem.getSelectedId(staff);

        for (HerobrineCompanion c : mc.level.getEntitiesOfClass(HerobrineCompanion.class,
                player.getBoundingBox().inflate(80.0), c -> c.isAlive() && c.isOwnedBy(player))) {
            boolean selected = c.getUUID().equals(selectedId);
            drawCompanionJob(c, selected);
            if (selected) {
                Gizmos.cuboid(c.getBoundingBox().inflate(0.15), GizmoStyle.stroke(SELECTED, 3.0F)).setAlwaysOnTop();
            }
        }

        BlockPos corner = CommandStaffItem.getCorner(staff);
        if (corner != null) {
            drawPreview(player, staff, corner);
        }
    }

    private static @Nullable ItemStack heldStaff(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(HerobrineMod.COMMAND_STAFF.get())) {
                return stack;
            }
        }
        return null;
    }

    private static int colorFor(CompanionTask task) {
        return switch (task) {
            case MINE -> MINE;
            case GUARD -> GUARD;
            case HAUL -> HAUL;
            default -> PREVIEW;
        };
    }

    private static void drawCompanionJob(HerobrineCompanion c, boolean selected) {
        CompanionTask task = c.clientTask();
        if (task != CompanionTask.GUARD && task != CompanionTask.MINE && task != CompanionTask.HAUL) {
            return;
        }
        int color = colorFor(task);
        Optional<BlockPos> min = c.clientAreaMin();
        Optional<BlockPos> max = c.clientAreaMax();
        if (min.isPresent() && max.isPresent()) {
            drawArea(min.get(), max.get(), color, selected, I18n.get(task.translationKey()) + " · " + c.getName().getString());
        } else {
            c.clientWorkPos().ifPresent(work -> {
                Vec3 center = Vec3.atBottomCenterOf(work);
                switch (task) {
                    case GUARD -> Gizmos.circle(center.add(0, 0.05, 0), HerobrineCompanion.GUARD_RADIUS,
                            GizmoStyle.stroke(color, 2.0F)).setAlwaysOnTop();
                    case HAUL -> Gizmos.cuboid(new AABB(work).inflate(12.0, 0.0, 12.0),
                            GizmoStyle.stroke(color, 2.0F)).setAlwaysOnTop();
                    default -> Gizmos.cuboid(work, 0.02F, GizmoStyle.stroke(color, 2.5F)).setAlwaysOnTop();
                }
            });
        }
        if (task == CompanionTask.MINE || task == CompanionTask.HAUL) {
            c.clientStoragePos().ifPresent(p -> Gizmos.cuboid(p, 0.05F, GizmoStyle.stroke(STORAGE, 2.5F)).setAlwaysOnTop());
        }
        if (task == CompanionTask.HAUL) {
            c.clientSourcePos().ifPresent(p -> Gizmos.cuboid(p, 0.05F, GizmoStyle.stroke(SOURCE, 2.5F)).setAlwaysOnTop());
        }
    }

    /** Рамка області + сітка по верхній грані + підпис з розміром. */
    private static void drawArea(BlockPos a, BlockPos b, int color, boolean bright, String label) {
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        int fill = (color & 0x00FFFFFF) | (bright ? 0x30000000 : 0x18000000);
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, bright ? 3.0F : 2.0F, fill)).setAlwaysOnTop();

        // Сітка на верхній грані (кожен блок)
        double top = box.maxY + 0.01;
        int gridColor = (color & 0x00FFFFFF) | 0x90000000;
        for (int x = min.getX() + 1; x <= max.getX(); x++) {
            Gizmos.line(new Vec3(x, top, box.minZ), new Vec3(x, top, box.maxZ), gridColor, 1.0F).setAlwaysOnTop();
        }
        for (int z = min.getZ() + 1; z <= max.getZ(); z++) {
            Gizmos.line(new Vec3(box.minX, top, z), new Vec3(box.maxX, top, z), gridColor, 1.0F).setAlwaysOnTop();
        }

        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        BlockPos labelPos = new BlockPos((min.getX() + max.getX()) / 2, max.getY() + 1, (min.getZ() + max.getZ()) / 2);
        Gizmos.billboardTextOverBlock(label + "  " + sx + "×" + sy + "×" + sz, labelPos, 0, color, 0.9F);
    }

    /** Поки вибрано перший кут — показуємо область до блока, на який дивиться гравець. */
    private static void drawPreview(LocalPlayer player, ItemStack staff, BlockPos corner) {
        Gizmos.cuboid(corner, 0.03F, GizmoStyle.stroke(PREVIEW, 3.0F)).setAlwaysOnTop();
        HitResult hit = player.pick(STAFF_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos other = blockHit.getBlockPos();
        boolean mine = CommandStaffItem.getMode(staff) == CommandStaffItem.Mode.MINE;
        if (mine && other.getY() == corner.getY() && !other.equals(corner)) {
            other = other.below(7); // як на сервері: обидва кути на одній висоті — кар'єр 8 блоків углиб
        }
        String label = I18n.get("gui.herobrine_ritual.area_preview");
        drawArea(corner, other, PREVIEW, true, label);
    }
}
