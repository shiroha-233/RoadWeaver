package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用右键菜单组件
 */
public final class ContextMenu {
    private static final int PADDING_X = MapTheme.MENU_PADDING_X;
    private static final int PADDING_Y = MapTheme.MENU_PADDING_Y;
    private static final int ITEM_HEIGHT = MapTheme.MENU_ITEM_HEIGHT;
    private static final int SEPARATOR_HEIGHT = 6;

    public record Item(Component label, Runnable action, boolean enabled, boolean isSeparator) {
        public static Item of(Component label, Runnable action) {
            return new Item(label, action, true, false);
        }
        public static Item disabled(Component label) {
            return new Item(label, null, false, false);
        }
        public static Item createSeparator() {
            return new Item(Component.empty(), null, false, true);
        }
    }

    private int anchorX;
    private int anchorY;
    private Rect bounds;
    private final List<Item> items = new ArrayList<>();
    private boolean open = false;
    private int hoverIndex = -1;

    public void open(int x, int y) {
        this.anchorX = x;
        this.anchorY = y;
        this.open = true;
        this.hoverIndex = -1;
    }

    public void close() { 
        this.open = false;
        this.hoverIndex = -1;
    }
    
    public boolean isOpen() { return open; }

    public void clearItems() { items.clear(); }
    
    public void addItem(Component label, Runnable action) { 
        items.add(Item.of(label, action)); 
    }
    
    public void addItem(Item item) {
        items.add(item);
    }
    
    public void addSeparator() {
        items.add(Item.createSeparator());
    }

    public void layout(Font font, int screenW, int screenH) {
        int w = 0;
        int h = PADDING_Y * 2;
        for (Item it : items) {
            if (it.isSeparator()) {
                h += SEPARATOR_HEIGHT;
            } else {
                w = Math.max(w, font.width(it.label()));
                h += ITEM_HEIGHT;
            }
        }
        w += PADDING_X * 2 + 8;
        Rect raw = new Rect(anchorX + 4, anchorY, w, h);
        this.bounds = raw.clampToScreen(screenW, screenH, 4);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
        if (!open || items.isEmpty()) return;
        layout(font, screenW, screenH);
        
        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();
        int h = bounds.height();
        
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, MapTheme.MENU_BORDER);
        g.fill(x, y, x + w, y + h, MapTheme.MENU_BG);
        
        updateHoverIndex(mouseX, mouseY);
        
        int ty = y + PADDING_Y;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.isSeparator()) {
                int lineY = ty + SEPARATOR_HEIGHT / 2;
                g.fill(x + 4, lineY, x + w - 4, lineY + 1, 0x40FFFFFF);
                ty += SEPARATOR_HEIGHT;
            } else {
                if (i == hoverIndex && it.enabled()) {
                    g.fill(x + 2, ty, x + w - 2, ty + ITEM_HEIGHT, MapTheme.MENU_HOVER);
                }
                int textColor = it.enabled() ? MapTheme.MENU_TEXT : 0xFF808080;
                g.drawString(font, it.label(), x + PADDING_X,
                        ty + (ITEM_HEIGHT - font.lineHeight) / 2, textColor, false);
                ty += ITEM_HEIGHT;
            }
        }
    }

    private void updateHoverIndex(int mouseX, int mouseY) {
        hoverIndex = -1;
        if (bounds == null || !bounds.contains(mouseX, mouseY)) return;
        
        int y = bounds.y() + PADDING_Y;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int itemH = it.isSeparator() ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
            if (mouseY >= y && mouseY < y + itemH) {
                if (!it.isSeparator()) {
                    hoverIndex = i;
                }
                return;
            }
            y += itemH;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) {
            close();
            return true;
        }
        
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        
        if (hoverIndex >= 0 && hoverIndex < items.size()) {
            Item it = items.get(hoverIndex);
            if (it.enabled() && it.action() != null) {
                close();
                it.action().run();
                return true;
            }
        }
        
        close();
        return true;
    }
    
    public void updateMousePos(int mouseX, int mouseY) {
        if (open && bounds != null) {
            updateHoverIndex(mouseX, mouseY);
        }
    }
}
