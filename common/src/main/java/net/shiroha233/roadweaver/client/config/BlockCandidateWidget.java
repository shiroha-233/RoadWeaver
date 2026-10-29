package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.client.render.RoadWeaverUi;
import net.shiroha233.roadweaver.client.render.SafeGuiItemRenderer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 方块候选选择器，支持搜索和滚动条交互。
 */
public class BlockCandidateWidget extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private static final int SLOT_SIZE = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 10;
    
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int visibleRows;
    private final int visibleCols;
    
    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filteredBlocks = new ArrayList<>();
    private final EditBox searchBox;
    private final Consumer<Block> onBlockSelected;
    
    private int scrollOffset = 0;
    private boolean draggingScrollbar = false;

    public BlockCandidateWidget(int x, int y, int width, int height, Consumer<Block> onBlockSelected) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onBlockSelected = onBlockSelected;

        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, x, y, width, 16, Component.translatable("gui.roadweaver.preset_editor.search"));
        this.searchBox.setResponder(this::onSearchChanged);

        this.visibleCols = Math.max(1, width / SLOT_SIZE);
        this.visibleRows = Math.max(1, (height - 20) / SLOT_SIZE);
        
        buildCandidateBlocks();
        rebuildFilteredList();
    }

    private void buildCandidateBlocks() {
        allBlocks.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.level() == null) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR) continue;
                allBlocks.add(b);
            }
            return;
        }

        FeatureFlagSet features = mc.player.connection.enabledFeatures();
        boolean hasPermissions = mc.player.canUseGameMasterBlocks();
        HolderLookup.Provider registries = mc.player.level().registryAccess();
        CreativeModeTabs.tryRebuildTabContents(features, hasPermissions, registries);

        Set<Block> unique = new LinkedHashSet<>();
        addBlocksFromTab(unique, "building_blocks");
        addBlocksFromTab(unique, "natural_blocks");
        addBlocksFromTab(unique, "functional_blocks");

        if (unique.isEmpty()) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR) continue;
                unique.add(b);
            }
        }
        allBlocks.addAll(unique);
    }

    private void addBlocksFromTab(Set<Block> out, String tabId) {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.parse(tabId));
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
        if (tab == null) return;
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block != Blocks.AIR) out.add(block);
            }
        }
    }

    private void onSearchChanged(String text) {
        scrollOffset = 0;
        rebuildFilteredList();
    }

    private void rebuildFilteredList() {
        filteredBlocks.clear();
        String q = searchBox.getValue().trim().toLowerCase();
        for (Block b : allBlocks) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (id == null) continue;
            if (q.isEmpty() || id.toString().toLowerCase().contains(q)) {
                filteredBlocks.add(b);
            }
        }
    }

    private int getMaxOffset() {
        int totalRows = (filteredBlocks.size() + visibleCols - 1) / visibleCols;
        return Math.max(0, totalRows - visibleRows);
    }

    private int getScrollbarStartY() {
        return y + 20;
    }

    private int getScrollbarHeight() {
        return height - 20;
    }

    private int getThumbHeight(int maxOffset) {
        int barHeight = getScrollbarHeight();
        int totalRows = (filteredBlocks.size() + visibleCols - 1) / visibleCols;
        if (totalRows <= 0) return barHeight;
        return Math.max(MIN_THUMB_HEIGHT, barHeight * visibleRows / totalRows);
    }

    private int getThumbY(int maxOffset, int thumbHeight) {
        int startY = getScrollbarStartY();
        int barHeight = getScrollbarHeight();
        if (maxOffset <= 0) return startY;
        return startY + (barHeight - thumbHeight) * scrollOffset / maxOffset;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RoadWeaverUi.drawPanel(g, x - 4, y - 4, width + 8, height + 8);
        this.searchBox.render(g, mouseX, mouseY, partialTick);

        int startY = y + 20;
        int maxOffset = getMaxOffset();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        int startIndex = scrollOffset * visibleCols;
        int endIndex = Math.min(startIndex + visibleRows * visibleCols, filteredBlocks.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int r = relIndex / visibleCols;
            int c = relIndex % visibleCols;
            
            int bx = x + c * SLOT_SIZE;
            int by = startY + r * SLOT_SIZE;
            
            g.fill(bx, by, bx + SLOT_SIZE, by + SLOT_SIZE, 0x920F1318);
            
            Block b = filteredBlocks.get(i);
            ItemStack stack = new ItemStack(b);
            SafeGuiItemRenderer.renderFakeItemSafe(g, stack, bx + 1, by + 1);
            
            if (mouseX >= bx && mouseX < bx + SLOT_SIZE && mouseY >= by && mouseY < by + SLOT_SIZE) {
                g.fill(bx, by, bx + SLOT_SIZE, by + SLOT_SIZE, 0x80FFFFFF);
                g.renderOutline(bx, by, SLOT_SIZE, SLOT_SIZE, 0xFFC8D2DE);
                SafeGuiItemRenderer.renderTooltipSafe(g, Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }
        
        if (maxOffset > 0) {
            int barHeight = getScrollbarHeight();
            int thumbHeight = getThumbHeight(maxOffset);
            int thumbY = getThumbY(maxOffset, thumbHeight);
            int barX = x + width - SCROLLBAR_WIDTH;
            g.fill(barX, startY, barX + SCROLLBAR_WIDTH, startY + barHeight, 0x600A0C10);
            g.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFD6DEE8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            int maxOffset = getMaxOffset();
            if (maxOffset > 0) {
                int barX = x + width - SCROLLBAR_WIDTH;
                int startY = getScrollbarStartY();
                int barH = getScrollbarHeight();
                int thumbH = getThumbHeight(maxOffset);
                int thumbY = getThumbY(maxOffset, thumbH);

                boolean inBarX = mouseX >= barX && mouseX < barX + SCROLLBAR_WIDTH;
                boolean inBarY = mouseY >= startY && mouseY < startY + barH;
                if (inBarX && inBarY) {
                    if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                        draggingScrollbar = true;
                    } else {
                        int track = Math.max(1, barH - thumbH);
                        double rel = (mouseY - startY - thumbH / 2.0) / track;
                        int target = (int) Math.round(rel * maxOffset);
                        scrollOffset = Math.max(0, Math.min(target, maxOffset));
                    }
                    return true;
                }
            }
        }
        
        int startY = y + 20;
        if (mouseY >= startY && mouseY < startY + visibleRows * SLOT_SIZE && mouseX >= x && mouseX < x + visibleCols * SLOT_SIZE) {
            int c = (int)(mouseX - x) / SLOT_SIZE;
            int r = (int)(mouseY - startY) / SLOT_SIZE;
            int index = (scrollOffset + r) * visibleCols + c;
            if (index >= 0 && index < filteredBlocks.size()) {
                if (button == 0) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    onBlockSelected.accept(filteredBlocks.get(index));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            int maxOffset = getMaxOffset();
            if (maxOffset <= 0) return true;

            int startY = getScrollbarStartY();
            int barH = getScrollbarHeight();
            int thumbH = getThumbHeight(maxOffset);
            int track = Math.max(1, barH - thumbH);

            double rel = (mouseY - startY - thumbH / 2.0) / track;
            int target = (int) Math.round(rel * maxOffset);
            scrollOffset = Math.max(0, Math.min(target, maxOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            if (scrollY > 0) scrollOffset--;
            else if (scrollY < 0) scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(searchBox);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        this.searchBox.updateNarration(narrationElementOutput);
    }
}
