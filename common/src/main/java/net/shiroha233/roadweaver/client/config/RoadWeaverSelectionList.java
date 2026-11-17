package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

public abstract class RoadWeaverSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private static final int DEFAULT_BACKGROUND = 0xAA101216;

    private final int rowWidthPadding;
    private boolean renderBackground = true;
    private boolean renderTopAndBottom = true;
    private int backgroundColor = DEFAULT_BACKGROUND;

    protected RoadWeaverSelectionList(Minecraft minecraft, int width, int height, int top, int rowHeight, int rowWidthPadding) {
        super(minecraft, width, height, top, rowHeight);
        this.rowWidthPadding = rowWidthPadding;
    }

    public void setLeftPos(int left) {
        setX(left);
    }

    public void setRenderBackground(boolean renderBackground) {
        this.renderBackground = renderBackground;
    }

    public void setRenderTopAndBottom(boolean renderTopAndBottom) {
        this.renderTopAndBottom = renderTopAndBottom;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    @Override
    public int getRowWidth() {
        return width - rowWidthPadding;
    }

    @Override
    protected int scrollBarX() {
        return getRowLeft() + getRowWidth() + 6;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        if (!renderBackground) {
            return;
        }
        graphics.fill(getX(), getY(), getRight(), getBottom(), backgroundColor);
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        if (renderTopAndBottom) {
            super.extractListSeparators(graphics);
        }
    }
}
