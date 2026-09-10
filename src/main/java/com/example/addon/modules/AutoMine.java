package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * AutoMine+ — Tự động đào và dùng /sellgui để bán item mục tiêu khi balo đầy.
 */
public class AutoMine extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> mineBlock = sgGeneral.add(new ItemSetting.Builder()
        .name("mine-block")
        .description("Khối quặng cho Baritone #mine.")
        .defaultValue(Items.DIAMOND_ORE)
        .build());

    private final Setting<Boolean> autoDetectDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-detect-drop")
        .description("Tự nhận diện Silk Touch.")
        .defaultValue(true)
        .build());

    private final Setting<Item> collectItem = sgGeneral.add(new ItemSetting.Builder()
        .name("collect-item")
        .description("Item muốn bán khi TẮT auto-detect.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> !autoDetectDrop.get())
        .build());

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Độ trễ giữa các thao tác click slot.")
        .defaultValue(5).min(1).sliderMax(20)
        .build());

    private enum State {
        IDLE, START_MINE, MINING, STOP_MINE, WAIT_STOP, OPEN_SELLGUI, SELLGUI_GUI
    }

    private State currentState = State.IDLE;
    private int timer = 0;

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "AutoMine+", "Tự động đào và bán vào /sellgui khi đầy balo.");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (timer > 0) { timer--; return; }

        switch (currentState) {
            case IDLE:
                currentState = State.START_MINE;
                break;

            case START_MINE:
                if (mc.screen != null) mc.player.closeContainer();
                ChatUtils.sendPlayerMsg("#mine " + BuiltInRegistries.ITEM.getKey(mineBlock.get()).toString());
                timer = 60;
                currentState = State.MINING;
                break;

            case MINING:
                if (isInventoryFull()) {
                    currentState = State.STOP_MINE;
                }
                break;

            case STOP_MINE:
                ChatUtils.sendPlayerMsg("#stop");
                timer = 40;
                currentState = State.WAIT_STOP;
                break;

            case WAIT_STOP:
                currentState = State.OPEN_SELLGUI;
                break;

            case OPEN_SELLGUI:
                if (mc.screen != null) mc.player.closeContainer();
                ChatUtils.sendPlayerMsg("/sellgui");
                timer = 40;
                currentState = State.SELLGUI_GUI;
                break;

            case SELLGUI_GUI:
                if (!(mc.screen instanceof AbstractContainerScreen)) {
                    currentState = State.START_MINE;
                    break;
                }

                if (!doSellTargetItems((AbstractContainerScreen<?>) mc.screen)) {
                    mc.player.closeContainer();
                    timer = 15;
                    currentState = State.START_MINE;
                }
                break;
        }
    }

    private boolean doSellTargetItems(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu h = screen.getMenu();
        int cSz = h.slots.size() - 36;
        Item target = getEffectiveCollectItem();

        for (int i = cSz; i < h.slots.size(); i++) {
            ItemStack st = h.getSlot(i).getItem();
            if (!st.isEmpty() && st.getItem() == target) {
                if (mc.gameMode != null) {
                    mc.gameMode.handleInventoryMouseClick(h.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                }
                timer = actionDelay.get();
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Item getEffectiveCollectItem() {
        if (!autoDetectDrop.get()) return collectItem.get();
        return hasSilkTouchInHotbar() ? mineBlock.get() : getOreDrop(mineBlock.get());
    }

    private boolean hasSilkTouchInHotbar() {
        if (mc.player == null || mc.level == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;

            if (s.getEnchantments().toString().toLowerCase().contains("silk_touch")) {
                return true;
            }
        }
        return false;
    }

    private static Item getOreDrop(Item oreBlock) {
        if (oreBlock == Items.DIAMOND_ORE      || oreBlock == Items.DEEPSLATE_DIAMOND_ORE)  return Items.DIAMOND;
        if (oreBlock == Items.IRON_ORE         || oreBlock == Items.DEEPSLATE_IRON_ORE)     return Items.RAW_IRON;
        if (oreBlock == Items.GOLD_ORE         || oreBlock == Items.DEEPSLATE_GOLD_ORE
                                               || oreBlock == Items.NETHER_GOLD_ORE)        return Items.RAW_GOLD;
        if (oreBlock == Items.COPPER_ORE       || oreBlock == Items.DEEPSLATE_COPPER_ORE)   return Items.RAW_COPPER;
        if (oreBlock == Items.COAL_ORE         || oreBlock == Items.DEEPSLATE_COAL_ORE)     return Items.COAL;
        if (oreBlock == Items.EMERALD_ORE      || oreBlock == Items.DEEPSLATE_EMERALD_ORE)  return Items.EMERALD;
        if (oreBlock == Items.LAPIS_ORE        || oreBlock == Items.DEEPSLATE_LAPIS_ORE)    return Items.LAPIS_LAZULI;
        if (oreBlock == Items.REDSTONE_ORE     || oreBlock == Items.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (oreBlock == Items.NETHER_QUARTZ_ORE)                                            return Items.QUARTZ;
        if (oreBlock == Items.ANCIENT_DEBRIS)                                               return Items.NETHERITE_SCRAP;
        return oreBlock;
    }
}
