package com.piratechallenge;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Меню «Плеера Серёги» (сундук 9x4).
 * Ряд 1–2: все треки (клик = включить). Ряд 3: пауза/пуск, стоп, пропуск, авто-цикл,
 * сбросить счётчик, проклятие вкл/выкл, перезапуск интро-диалога.
 */
public final class PlayerGui {
    private static final int TRACK_SLOTS = 27;
    private static final int CTRL_PAUSE = 28, CTRL_STOP = 29, CTRL_SKIP = 30, CTRL_AUTO = 31,
            CTRL_RESET = 32, CTRL_CURSE = 33, CTRL_INTRO = 34;

    public static boolean isAdmin(ServerPlayerEntity p) {
        return p.hasPermissionLevel(2) || p.getServer().isSingleplayer();
    }

    public static void open(ServerPlayerEntity sp) {
        SimpleInventory inv = new SimpleInventory(36);
        fill(inv, sp);
        sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) -> new Handler(syncId, playerInv, inv, sp),
                Text.literal("Плеер Серёги")));
    }

    private static ItemStack named(net.minecraft.item.Item item, String name, boolean glint) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(st -> st.withItalic(false)));
        if (glint) s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    static void fill(SimpleInventory inv, ServerPlayerEntity sp) {
        PlayerData d = ChallengeManager.data(sp);
        for (int i = 0; i < 36; i++) inv.setStack(i, ItemStack.EMPTY);

        Track[] all = Track.values();
        for (int i = 0; i < all.length && i < TRACK_SLOTS; i++) {
            Track t = all[i];
            boolean now = d.current == t;
            int s = t.seconds;
            inv.setStack(i, named(Items.NOTE_BLOCK,
                    (now ? "§a▶ " : "§e") + t.title + " §7(" + (s / 60) + ":" + String.format("%02d", s % 60) + ")", now));
        }
        inv.setStack(CTRL_PAUSE, named(Items.CLOCK, d.paused ? "§aПродолжить" : "§6Пауза", d.paused));
        inv.setStack(CTRL_STOP, named(Items.BARRIER, "§cСтоп (остановить текущий трек)", false));
        inv.setStack(CTRL_SKIP, named(Items.ARROW, "§bПропустить (следующий случайный)", false));
        inv.setStack(CTRL_AUTO, named(Items.LEVER, "§fАвто-цикл: " + (d.auto ? "§aВКЛ" : "§cВЫКЛ"), d.auto));
        inv.setStack(CTRL_RESET, named(Items.PAPER, "§fСбросить счётчик треков (сейчас: " + d.listened + ")", false));
        inv.setStack(CTRL_CURSE, named(Items.WITHER_SKELETON_SKULL, "§4Проклятие: " + (d.cursed ? "§aЕСТЬ §7(клик — снять)" : "§cНЕТ §7(клик — наложить)"), d.cursed));
        inv.setStack(CTRL_INTRO, named(Items.BOOK, "§dПерезапустить интро-диалог с Серёгой", false));
    }

    static void click(ServerPlayerEntity sp, int slot) {
        PlayerData d = ChallengeManager.data(sp);
        Track[] all = Track.values();
        if (slot < all.length && slot < TRACK_SLOTS) {
            ChallengeManager.adminPlay(sp, all[slot]);
            return;
        }
        switch (slot) {
            case CTRL_PAUSE -> ChallengeManager.adminTogglePause(sp);
            case CTRL_STOP -> ChallengeManager.adminStop(sp);
            case CTRL_SKIP -> ChallengeManager.adminSkip(sp);
            case CTRL_AUTO -> d.auto = !d.auto;
            case CTRL_RESET -> {
                d.listened = 0;
                d.played.clear();
                java.util.Arrays.fill(d.plays, 0);
                ChallengeSave.get(sp.getServer()).markDirty();
            }
            case CTRL_CURSE -> {
                if (d.cursed) DialogueManager.lift(sp);
                else {
                    d.cursed = true;
                    d.dialogDone = true;
                    sp.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            ModEffects.CURSE, net.minecraft.entity.effect.StatusEffectInstance.INFINITE, 0, false, true, true));
                    ChallengeSave.get(sp.getServer()).markDirty();
                }
            }
            case CTRL_INTRO -> DialogueManager.restart(sp, d);
            default -> {}
        }
    }

    private static class Handler extends GenericContainerScreenHandler {
        private final SimpleInventory inv;
        private final ServerPlayerEntity owner;

        Handler(int syncId, PlayerInventory playerInv, SimpleInventory inv, ServerPlayerEntity owner) {
            super(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4);
            this.inv = inv;
            this.owner = owner;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < 36) {
                if (player instanceof ServerPlayerEntity sp) click(sp, slotIndex);
                fill(inv, owner);
                sendContentUpdates();
                return;
            }
            if (actionType == SlotActionType.QUICK_MOVE) return;
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }
    }

    private PlayerGui() {}
}
