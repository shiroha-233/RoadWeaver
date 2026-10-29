package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.client.render.RoadWeaverUi;
import net.shiroha233.roadweaver.client.render.SafeGuiItemRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 材料网格组件
 */
public class MaterialGridWidget extends AbstractWidget {
    private static final int SLOT_SIZE = 18;
    private static final int LABEL_HEIGHT = 12;
    
    private final List<String> materialIds = new ArrayList<>();
    private final int cols;
    private final int rows;
    private final Consumer<Integer> onRemove;
    private final Runnable onSelect;
    private final String label;
    private boolean isTarget = false;

    public MaterialGridWidget(int x, int y, int cols, int rows, String label, Consumer<Integer> onRemove, Runnable onSelect) {
        super(x, y, cols * SLOT_SIZE, rows * SLOT_SIZE + LABEL_HEIGHT, Component.literal(label));
        this.cols = cols;
        this.rows = rows;
        this.label = label;
        this.onRemove = onRemove;
        this.onSelect = onSelect;
    }

    public void setMaterials(List<String> materials) {
        this.materialIds.clear();
        if (materials != null) {
            this.materialIds.addAll(materials);
        }
    }

    public void setIsTarget(boolean target) {
        this.isTarget = target;
    }

    public boolean isTarget() {
        return isTarget;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RoadWeaverUi.drawPanel(g, this.getX() - 4, this.getY() - 4, this.width + 8, this.height + 8);
        g.drawString(Minecraft.getInstance().font, this.label, this.getX(), this.getY(), isTarget ? 0xFFF4E58A : 0xFFFFFF, false);

        int gridY = this.getY() + LABEL_HEIGHT;
        int index = 0;
        int bgColor = isTarget ? 0xC014171C : 0x920F1318;
        int borderColor = isTarget ? 0xFFF4E58A : 0xFFA4AFBC;

        g.fill(this.getX() - 1, gridY - 1, this.getX() + width + 1, gridY + rows * SLOT_SIZE + 1, borderColor);
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = this.getX() + col * SLOT_SIZE;
                int y = gridY + row * SLOT_SIZE;
                
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bgColor);
                
                if (index < materialIds.size()) {
                    Block b = blockFromId(materialIds.get(index));
                    if (b != null && b != Blocks.AIR) {
                        ItemStack stack = new ItemStack(b);
                         SafeGuiItemRenderer.renderFakeItemSafe(g, stack, x + 1, y + 1);
                         
                         if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                              g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x80FFFFFF);
                              g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFC8D2DE);
                              SafeGuiItemRenderer.renderTooltipSafe(g, Minecraft.getInstance().font, stack, mouseX, mouseY);
                         }
                    }
                }
                index++;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) return false;
        
        int gridY = this.getY() + LABEL_HEIGHT;
        if (mouseX >= this.getX() && mouseX < this.getX() + width &&
            mouseY >= gridY && mouseY < gridY + rows * SLOT_SIZE) {
            
            this.onSelect.run();
            
            int index = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int x = this.getX() + col * SLOT_SIZE;
                    int y = gridY + row * SLOT_SIZE;
                    if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                        if (button == 0 && index < materialIds.size()) {
                            playDownSound(Minecraft.getInstance().getSoundManager());
                            onRemove.accept(index);
                            return true;
                        }
                    }
                    index++;
                }
            }
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    private Block blockFromId(String id) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            return BuiltInRegistries.BLOCK.get(rl);
        } catch (Exception e) {
            return Blocks.AIR;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
