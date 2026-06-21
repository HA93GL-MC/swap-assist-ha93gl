package com.ha93gl.swapassist.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SwapAssistClient implements ClientModInitializer {
    private static final float READY_ATTACK_STRENGTH = 1.0F;
    private static final Identifier ATTRIBUTE_SELECTION =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "attribute_selection");

    private static final Identifier ATTACK_SELECTION =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "attack_selection");

    private static final Identifier HOTBAR_SELECTION_ATTRIBUTE =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "hotbar_selection_attribute");

    private static final Identifier HOTBAR_SELECTION_ATTACK =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "hotbar_selection_attack");

    private static final Identifier MODE_1T =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "1t");

    private static final Identifier MODE_NR =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "nr");

    private static final Identifier MODE_FA =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "fa");

    private static final Identifier MODE_NA =
            Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "na");

    private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath("swap-assist-ha93gl", "status");
    private static final KeyMapping.Category SWAP_ASSIST_CATEGORY =
            KeyMapping.Category.register(Identifier.parse("swap-assist-ha93gl"));


    private static KeyMapping swapAssistKey;
    private static KeyMapping swapModeKey;
    private static KeyMapping preselectSlotsKey;
    private static SwapMode swapMode = SwapMode.DELAY_2_TICKS;
    private static boolean swapAssistEnabled;
    private static SlotSelectionStep slotSelectionStep = SlotSelectionStep.NONE;
    private static boolean enableAfterSlotSelection;
    private static int selectionStartSlot = Inventory.NOT_FOUND_INDEX;
    private static int fixedAttributeSlot = Inventory.NOT_FOUND_INDEX;
    private static int attackSlot = Inventory.NOT_FOUND_INDEX;
    private static int pendingRestoreSlot = Inventory.NOT_FOUND_INDEX;
    private static int restoreDelayTicks;

    @Override
    public void onInitializeClient() {
        swapAssistKey = new KeyMapping(
                "key.swap-assist-ha93gl.swap_assist",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_R,
                SWAP_ASSIST_CATEGORY
        );

        swapModeKey = new KeyMapping(
                "key.swap-assist-ha93gl.swap_mode",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_G,
                SWAP_ASSIST_CATEGORY
        );

        preselectSlotsKey = new KeyMapping(
                "key.swap-assist-ha93gl.preselect_slots",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_V,
                SWAP_ASSIST_CATEGORY
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (swapAssistKey.consumeClick()) {
                toggleSwapAssist(client);
            }

            while (swapModeKey.consumeClick()) {
                cycleSwapMode(client);
            }

            while (preselectSlotsKey.consumeClick()) {
                startSlotSelection(client, false);
            }

            restoreAttributeSlot(client);

            if (slotSelectionStep != SlotSelectionStep.NONE) {
                updateSlotSelection(client);
            }
        });

        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount != 0) {
                prepareAttackSlot(client);
            }

            return false;
        });

        HudElementRegistry.addLast(
                HUD_ELEMENT_ID,
                (graphics, tickCounter) -> {
                    Minecraft client = Minecraft.getInstance();
                    renderSlotOverlays(graphics, client);
                    renderModeIndicator(graphics, client);
                }
        );
    }

    private static void renderModeIndicator(GuiGraphicsExtractor graphics, Minecraft client) {
        if (client.player == null || client.options.hideGui) {
            return;
        }

        Identifier texture;

        if (!swapAssistEnabled) {
            texture = MODE_NA;
        } else {
            texture = switch (swapMode) {
                case DELAY_2_TICKS -> MODE_1T;
                case NO_RESTORE -> MODE_NR;
                case FIXED_ATTRIBUTE -> MODE_FA;
            };
        }

        int centerX = graphics.guiWidth() / 2;

        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                texture,
                centerX - 50,
                5,
                100,
                20
        );
    }

    private static void renderSlotOverlays(GuiGraphicsExtractor graphics, Minecraft client) {
        if (client.player == null || client.options.hideGui) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int x = centerX - 91;
        int y = graphics.guiHeight() - 23;

        int selectedSlot = client.player.getInventory().getSelectedSlot();

        // Attribute slot overlay (only in fixed attribute mode)
        if (swapMode.usesFixedAttributeSlot
        && fixedAttributeSlot != Inventory.NOT_FOUND_INDEX) {
            boolean attributeSelected = selectedSlot == fixedAttributeSlot;

            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    attributeSelected ? HOTBAR_SELECTION_ATTRIBUTE : ATTRIBUTE_SELECTION,
                    x + fixedAttributeSlot * 20 + (attributeSelected ? -1 : 1),
                    y + (attributeSelected ? -1 : 2),
                    attributeSelected ? 24 : 20,
                    attributeSelected ? 24 : 20
            );
        }

        // Attack slot overlay
        if (attackSlot != Inventory.NOT_FOUND_INDEX) {
            boolean attackSelected = selectedSlot == attackSlot;

            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    attackSelected ? HOTBAR_SELECTION_ATTACK : ATTACK_SELECTION,
                    x + attackSlot * 20 + (attackSelected ? -1 : 1),
                    y + (attackSelected ? -1 : 2),
                    attackSelected ? 24 : 20,
                    attackSelected ? 24 : 20
            );
        }
    }
    private static void toggleSwapAssist(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (swapAssistEnabled || slotSelectionStep != SlotSelectionStep.NONE) {
            swapAssistEnabled = false;
            slotSelectionStep = SlotSelectionStep.NONE;
            enableAfterSlotSelection = false;
            selectionStartSlot = Inventory.NOT_FOUND_INDEX;
            showHudMessage(client, "Swap Assist off");
            return;
        }

        if (hasRequiredSlots()) {
            swapAssistEnabled = true;
            showHudMessage(client, "Swap Assist on");
            return;
        }

        startSlotSelection(client, true);
    }

    private static void startSlotSelection(Minecraft client, boolean enableAfterSelection) {
        if (client.player == null) {
            return;
        }

        enableAfterSlotSelection = enableAfterSelection;
        slotSelectionStep = swapMode.usesFixedAttributeSlot ? SlotSelectionStep.ATTRIBUTE : SlotSelectionStep.ATTACK;
        selectionStartSlot = client.player.getInventory().getSelectedSlot();
        showSlotSelectionMessage(client);
    }

    private static void cycleSwapMode(Minecraft client) {
        swapMode = swapMode.next();
        swapAssistEnabled = false;
        slotSelectionStep = SlotSelectionStep.NONE;
        enableAfterSlotSelection = false;
        selectionStartSlot = Inventory.NOT_FOUND_INDEX;
        pendingRestoreSlot = Inventory.NOT_FOUND_INDEX;
        restoreDelayTicks = 0;
        showHudMessage(client, "Swap Assist mode: " + swapMode.label);
    }

    private static void updateSlotSelection(Minecraft client) {
        if (client.player == null) {
            slotSelectionStep = SlotSelectionStep.NONE;
            return;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        int hotbarKeySlot = getPressedHotbarSlot(client);

        if (hotbarKeySlot != Inventory.NOT_FOUND_INDEX) {
            setSelectedConfigSlot(client, hotbarKeySlot);
            return;
        }

        if (selectedSlot != selectionStartSlot) {
            setSelectedConfigSlot(client, selectedSlot);
        }
    }

    private static int getPressedHotbarSlot(Minecraft client) {
        for (int slot = 0; slot < client.options.keyHotbarSlots.length; slot++) {
            if (client.options.keyHotbarSlots[slot].isDown()) {
                return slot;
            }
        }

        return Inventory.NOT_FOUND_INDEX;
    }

    private static void setSelectedConfigSlot(Minecraft client, int slot) {
        if (slotSelectionStep == SlotSelectionStep.ATTRIBUTE) {
            fixedAttributeSlot = slot;
            slotSelectionStep = SlotSelectionStep.ATTACK;
            selectionStartSlot = client.player.getInventory().getSelectedSlot();
            showSlotSelectionMessage(client);
            return;
        }

        attackSlot = slot;
        swapAssistEnabled = enableAfterSlotSelection;
        slotSelectionStep = SlotSelectionStep.NONE;
        enableAfterSlotSelection = false;
        selectionStartSlot = Inventory.NOT_FOUND_INDEX;

        if (swapMode.usesFixedAttributeSlot) {
            showConfiguredSlotsMessage(client);
        } else {
            showConfiguredSlotsMessage(client);
        }
    }

    private static void prepareAttackSlot(Minecraft client) {
        if (!swapAssistEnabled || slotSelectionStep != SlotSelectionStep.NONE || attackSlot == Inventory.NOT_FOUND_INDEX
                || client.player == null || client.screen != null) {
            return;
        }

        if (!isAttackReady(client)) {
            return;
        }

        int attributeSlot = swapMode.usesFixedAttributeSlot
                ? fixedAttributeSlot
                : client.player.getInventory().getSelectedSlot();
        if (attributeSlot == Inventory.NOT_FOUND_INDEX) {
            return;
        }

        if (swapMode.restoreAfterAttack) {
            pendingRestoreSlot = attributeSlot;
            restoreDelayTicks = swapMode.restoreDelayTicks;
        } else {
            pendingRestoreSlot = Inventory.NOT_FOUND_INDEX;
            restoreDelayTicks = 0;
        }

        setSelectedSlot(client, attackSlot);
    }

    private static boolean isAttackReady(Minecraft client) {
        return client.player.getAttackStrengthScale(0.5F) >= READY_ATTACK_STRENGTH;
    }

    private static boolean hasRequiredSlots() {
        return attackSlot != Inventory.NOT_FOUND_INDEX
                && (!swapMode.usesFixedAttributeSlot || fixedAttributeSlot != Inventory.NOT_FOUND_INDEX);
    }

    private static void restoreAttributeSlot(Minecraft client) {
        if (pendingRestoreSlot == Inventory.NOT_FOUND_INDEX) {
            return;
        }

        if (restoreDelayTicks > 0) {
            restoreDelayTicks--;
            return;
        }

        if (client.player != null) {
            setSelectedSlot(client, pendingRestoreSlot);
        }

        pendingRestoreSlot = Inventory.NOT_FOUND_INDEX;
        restoreDelayTicks = 0;
    }

    private static void setSelectedSlot(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);

        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private static void showHudMessage(Minecraft client, String message) {
        client.gui.setOverlayMessage(Component.literal(message), false);
    }

    private static void showSlotSelectionMessage(Minecraft client) {
        if (slotSelectionStep == SlotSelectionStep.ATTRIBUTE) {
            showHudMessage(client, "Select the Swap Assist attribute slot");
            return;
        }

        showHudMessage(client, "Select the Swap Assist attack slot");
    }

    private static void showConfiguredSlotsMessage(Minecraft client) {
        String prefix = swapAssistEnabled ? "Swap Assist on: " : "Swap Assist slots saved: ";

        if (swapMode.usesFixedAttributeSlot) {
            showHudMessage(client, prefix + "attribute " + (fixedAttributeSlot + 1) + ", attack " + (attackSlot + 1));
            return;
        }

        showHudMessage(client, prefix + "attack slot " + (attackSlot + 1));
    }

    private static void renderStatus(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Minecraft client) {
        if (client.player == null || client.options.hideGui) {
            return;
        }

        String text = (swapAssistEnabled ? "[x] " : "[ ] ") + "SwapAssist " + swapMode.shortLabel;
        int textWidth = client.font.width(text);
        int x = (graphics.guiWidth() - textWidth) / 2;
        int y = 6;
        int backgroundColor = swapAssistEnabled ? 0xAA1D5C35 : 0xAA3A3A3A;
        int textColor = swapAssistEnabled ? 0x55FF55 : 0xE0E0E0;

        graphics.fill(x - 4, y - 3, x + textWidth + 4, y + client.font.lineHeight + 3, backgroundColor);
        graphics.text(client.font, text, x, y, textColor);
    }

    private enum SwapMode {
        DELAY_2_TICKS("1 Tick Restore", "1T", 1, true, false),
        NO_RESTORE("No Restore", "NR", 0, false, false),
        FIXED_ATTRIBUTE("Fixed Attribute", "FA", 1, true, true);

        private final String label;
        private final String shortLabel;
        private final int restoreDelayTicks;
        private final boolean restoreAfterAttack;
        private final boolean usesFixedAttributeSlot;

        SwapMode(String label, String shortLabel, int restoreDelayTicks, boolean restoreAfterAttack, boolean usesFixedAttributeSlot) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.restoreDelayTicks = restoreDelayTicks;
            this.restoreAfterAttack = restoreAfterAttack;
            this.usesFixedAttributeSlot = usesFixedAttributeSlot;
        }

        private SwapMode next() {
            SwapMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    private enum SlotSelectionStep {
        NONE,
        ATTRIBUTE,
        ATTACK
    }
}
