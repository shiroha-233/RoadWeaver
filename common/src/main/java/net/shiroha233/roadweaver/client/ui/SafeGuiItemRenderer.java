package net.shiroha233.roadweaver.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 物品渲染安全包装
 */
public final class SafeGuiItemRenderer {

    private SafeGuiItemRenderer() {
    }

    private static final ItemStack PLACEHOLDER = new ItemStack(Items.BARRIER);

    private static final Set<Identifier> BROKEN_WITHOUT_WORLD =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Set<Identifier> BROKEN_ALWAYS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static boolean renderFakeItemSafe(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (g == null || stack == null || stack.isEmpty()) return false;

        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            try {
                g.fakeItem(stack, x, y);
                return true;
            } catch (Throwable ignored) {
                g.fakeItem(PLACEHOLDER, x, y);
                return false;
            }
        }

        if (BROKEN_ALWAYS.contains(key)) {
            g.fakeItem(PLACEHOLDER, x, y);
            return false;
        }

        boolean hasWorldContext = hasWorldContext();
        if (!hasWorldContext && BROKEN_WITHOUT_WORLD.contains(key)) {
            g.fakeItem(PLACEHOLDER, x, y);
            return false;
        }

        try {
            g.fakeItem(stack, x, y);
            return true;
        } catch (Throwable t) {
            if (hasWorldContext) {
                BROKEN_ALWAYS.add(key);
            } else {
                BROKEN_WITHOUT_WORLD.add(key);
            }
            g.fakeItem(PLACEHOLDER, x, y);
            return false;
        }
    }

    public static void renderTooltipSafe(GuiGraphicsExtractor g, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (g == null || font == null || stack == null || stack.isEmpty()) return;
        try {
            g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        } catch (Throwable ignored) {
            g.setTooltipForNextFrame(font, Component.literal("<render error>"), mouseX, mouseY);
        }
    }

    private static boolean hasWorldContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return mc.player != null && mc.level != null;
    }
}
