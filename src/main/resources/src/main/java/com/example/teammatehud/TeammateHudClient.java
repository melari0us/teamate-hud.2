package com.example.teammatehud;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeammateHudClient implements ClientModInitializer {

    private static final Set<String> TRACKED_FRIENDS = new HashSet<>();

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"H", "C", "L", "B"};

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(this::renderTeammateHud);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("teamfind")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            TRACKED_FRIENDS.add(name.toLowerCase());
                            context.getSource().sendFeedback(Text.literal("§aAdded " + name + " to your tracked teammate HUD."));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            TRACKED_FRIENDS.remove(name.toLowerCase());
                            context.getSource().sendFeedback(Text.literal("§cRemoved " + name + " from your tracked teammate HUD."));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("list")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§6Tracked Teammates: §f" + String.join(", ", TRACKED_FRIENDS)));
                        return 1;
                    })
                )
            );
        });
    }

    private void renderTeammateHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || TRACKED_FRIENDS.isEmpty()) {
            return;
        }

        List<AbstractClientPlayerEntity> targets = new ArrayList<>();

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            String playerName = player.getName().getString().toLowerCase();
            if (TRACKED_FRIENDS.contains(playerName)) {
                targets.add(player);
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int x = 8;
        int y = 8;
        int boxWidth = 175;
        int entryHeight = 26;

        for (AbstractClientPlayerEntity teammate : targets) {
            context.fill(x - 3, y - 2, x + boxWidth, y + entryHeight - 3, 0x88000000);

            double distance = client.player.distanceTo(teammate);
            String name = teammate.getName().getString();
            String distStr = String.format("%.0fm", distance);

            context.drawTextWithShadow(textRenderer, Text.literal(name), x, y, 0xFFFFFF);
            int distWidth = textRenderer.getWidth(distStr);
            context.drawTextWithShadow(textRenderer, Text.literal(distStr), x + boxWidth - distWidth - 4, y, 0xAAAAAA);

            float health = teammate.getHealth();
            float maxHealth = teammate.getMaxHealth();
            float absorption = teammate.getAbsorptionAmount();
            int healthColor = getHealthColor(health, maxHealth);

            String healthStr = absorption > 0
                ? String.format("♥ %.1f (+%.0f)", health, absorption)
                : String.format("♥ %.1f/%.0f", health, maxHealth);

            context.drawTextWithShadow(textRenderer, Text.literal(healthStr), x, y + 11, healthColor);

            int armorX = x + 68;
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ItemStack stack = teammate.getEquippedStack(ARMOR_SLOTS[i]);
                String slotText;
                int slotColor;

                if (stack.isEmpty() || !stack.isDamageable()) {
                    slotText = SLOT_LABELS[i] + ":-";
                    slotColor = 0x666666;
                } else {
                    int maxDamage = stack.getMaxDamage();
                    int remaining = maxDamage - stack.getDamage();
                    int pct = Math.max(0, Math.round((remaining * 100.0f) / maxDamage));
                    slotText = SLOT_LABELS[i] + ":" + pct;
                    slotColor = getDurabilityColor(pct);
                }

                context.drawTextWithShadow(textRenderer, Text.literal(slotText), armorX, y + 11, slotColor);
                armorX += textRenderer.getWidth(slotText) + 4;
            }

            y += entryHeight + 2;
        }
    }

    private int getHealthColor(float health, float maxHealth) {
        float ratio = health / Math.max(1.0f, maxHealth);
        if (ratio > 0.6f) return 0x55FF55;
        if (ratio > 0.3f) return 0xFFFF55;
        return 0xFF5555;
    }

    private int getDurabilityColor(int pct) {
        if (pct > 60) return 0x55FFFF;
        if (pct > 25) return 0xFFAA00;
        return 0xFF5555;
    }
}
