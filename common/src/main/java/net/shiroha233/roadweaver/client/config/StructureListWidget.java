package net.shiroha233.roadweaver.client.config;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.structure.StructureEntry;
import net.shiroha233.roadweaver.config.structure.StructureTagEntry;

import java.util.*;
import java.util.function.Consumer;

/**
 * 结构列表组件，支持树形展示和模组图标。
 */
public class StructureListWidget extends RoadWeaverSelectionList<StructureListWidget.Entry> {
    private static final Map<String, Optional<ResourceLocation>> MOD_ICON_CACHE = new HashMap<>();
    private static final int ROW_HEIGHT = 24;
    private static final int CHECKBOX_SIZE = 10;

    public StructureListWidget(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, ROW_HEIGHT, 40);
        setBackgroundColor(0xAA101216);
    }

    public void clearEntries() {
        super.clearEntries();
    }

    public int doAddEntry(Entry entry) {
        return super.addEntry(entry);
    }

    static String getLocalizedStructureName(StructureEntry structure) {
        if (structure == null) return "";

        if (structure.isVanilla()) {
            var id = structure.id();
            String key = "structure." + id.getNamespace() + "." + id.getPath();
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) return translated;
        }
        return structure.displayName();
    }

    static ResourceLocation getModIconTexture(String modId) {
        if (modId == null || modId.isEmpty()) return null;
        Optional<ResourceLocation> cached = MOD_ICON_CACHE.get(modId);
        if (cached != null) return cached.orElse(null);

        ResourceLocation resolved = null;
        try {
            var optMod = Platform.getOptionalMod(modId);
            if (optMod.isPresent()) {
                var mod = optMod.get();
                var logoOpt = mod.getLogoFile(32);
                if (logoOpt.isPresent()) {
                    String logoPath = logoOpt.get();
                    List<ResourceLocation> candidates = new ArrayList<>();

                    if (logoPath.startsWith("assets/")) {
                        String rel = logoPath.substring("assets/".length());
                        int slash = rel.indexOf('/');
                        if (slash >= 0) {
                            String ns = rel.substring(0, slash);
                            String path = rel.substring(slash + 1);
                            candidates.add(new ResourceLocation(ns, path));
                        }
                    } else if (logoPath.indexOf(':') >= 0) {
                        ResourceLocation rl = ResourceLocation.tryParse(logoPath);
                        if (rl != null) candidates.add(rl);
                    } else {
                        candidates.add(new ResourceLocation(modId, logoPath));
                        if (!logoPath.startsWith("textures/")) {
                            candidates.add(new ResourceLocation(modId, "textures/" + logoPath));
                            candidates.add(new ResourceLocation(modId, "textures/gui/" + logoPath));
                        }
                    }

                    var rm = Minecraft.getInstance().getResourceManager();
                    for (ResourceLocation rl : candidates) {
                        if (rl == null) continue;
                        try {
                            if (rm.getResource(rl).isPresent()) {
                                resolved = rl;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        MOD_ICON_CACHE.put(modId, Optional.ofNullable(resolved));
        return resolved;
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<StructureListWidget.Entry> {
        protected final StructureListWidget list;

        protected Entry(StructureListWidget list) {
            this.list = list;
        }
    }

    public static class ModHeaderEntry extends Entry {
        private final String modId;
        private final Component title;
        private final boolean expanded;
        private final boolean allEnabled;
        private final boolean partialEnabled;
        private final Consumer<String> onExpandToggle;
        private final Consumer<String> onSelectAll;

        public ModHeaderEntry(StructureListWidget list, String modId, Component title,
                              boolean expanded, Consumer<String> onToggle) {
            this(list, modId, title, expanded, false, false, onToggle, m -> {});
        }

        public ModHeaderEntry(StructureListWidget list, String modId, Component title,
                              boolean expanded, boolean allEnabled, boolean partialEnabled,
                              Consumer<String> onExpandToggle, Consumer<String> onSelectAll) {
            super(list);
            this.modId = modId;
            this.title = title;
            this.expanded = expanded;
            this.allEnabled = allEnabled;
            this.partialEnabled = partialEnabled;
            this.onExpandToggle = onExpandToggle;
            this.onSelectAll = onSelectAll;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            String expandIcon = expanded ? "v" : ">";
            graphics.drawString(mc.font, expandIcon, left + 2, top + 7, 0xFFFFFF, false);

            int boxX = left + 14;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (allEnabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            } else if (partialEnabled) {
                graphics.fill(boxX + 3, boxY + 3, boxX + CHECKBOX_SIZE - 3, boxY + CHECKBOX_SIZE - 3, 0xFFFFFF55);
            }

            int textX = left + 30;
            ResourceLocation icon = getModIconTexture(modId);
            if (icon != null) {
                RenderSystem.enableBlend();
                graphics.blit(icon, textX, top + 4, 0, 0, 16, 16, 16, 16);
                RenderSystem.disableBlend();
                textX += 18;
            }

            graphics.drawString(mc.font, title, textX, top + 7, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = (this.list.width - this.list.getRowWidth()) / 2;
                if (mouseX >= rowLeft && mouseX <= rowLeft + 14) {
                    onExpandToggle.accept(modId);
                    return true;
                }
                if (mouseX > rowLeft + 14 && mouseX <= rowLeft + 26) {
                    onSelectAll.accept(modId);
                    return true;
                }
                if (mouseX > rowLeft + 26 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onExpandToggle.accept(modId);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class MessageEntry extends Entry {
        private final Component message;

        public MessageEntry(StructureListWidget list, Component message) {
            super(list);
            this.message = message;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawCenteredString(Minecraft.getInstance().font, message,
                    left + width / 2, top + 5, 0xAAAAAA);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class HeaderEntry extends Entry {
        private final Component title;

        public HeaderEntry(StructureListWidget list, Component title) {
            super(list);
            this.title = title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, title, left + 5, top + 5, 0xFFFF00);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class TagEntry extends Entry {
        private final StructureTagEntry tag;
        private final boolean enabled;
        private final boolean expanded;
        private final Consumer<StructureTagEntry> onToggle;
        private final Consumer<StructureTagEntry> onExpandToggle;

        public TagEntry(StructureListWidget list, StructureTagEntry tag, boolean enabled, boolean expanded,
                       Consumer<StructureTagEntry> onToggle, Consumer<StructureTagEntry> onExpandToggle) {
            super(list);
            this.tag = tag;
            this.enabled = enabled;
            this.expanded = expanded;
            this.onToggle = onToggle;
            this.onExpandToggle = onExpandToggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            int indent = 10;
            String expandIcon = expanded ? "v" : ">";
            graphics.drawString(mc.font, expandIcon, left + indent, top + 7, 0xAAAAAA, false);

            int boxX = left + indent + 12;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            graphics.drawString(mc.font, tag.displayName(), boxX + 14, top + 7, 0xDDDDDD, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = (this.list.width - this.list.getRowWidth()) / 2;
                int indent = 10;
                if (mouseX >= rowLeft + indent && mouseX <= rowLeft + indent + 10) {
                    onExpandToggle.accept(tag);
                    return true;
                }
                if (mouseX > rowLeft + indent + 10 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onToggle.accept(tag);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class StructureChildEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private static final int INDENT = 30;

        public StructureChildEntry(StructureListWidget list,
                                   StructureEntry structure,
                                   boolean enabled,
                                   Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.onToggle = onToggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            int checkboxX = left + INDENT;
            int checkboxY = top + 6;
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(checkboxX + 2, checkboxY + 2, checkboxX + CHECKBOX_SIZE - 2, checkboxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            int textColor = structure.isVanilla() ? 0xAAAAAA : 0xFFAAAA;
            String name = getLocalizedStructureName(structure);
            graphics.drawString(mc.font, "  " + name, left + INDENT + 20, top + 7, textColor);
            graphics.drawString(mc.font, structure.id().toString(), left + INDENT + 20, top + 15, 0x666666);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                if (mouseX >= left && mouseX <= left + this.list.getRowWidth()) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class PathFolderEntry extends Entry {
        private final StructurePathNode pathNode;
        private final boolean expanded;
        private final boolean allEnabled;
        private final boolean partialEnabled;
        private final Consumer<StructurePathNode> onExpandToggle;
        private final Consumer<StructurePathNode> onSelectAllToggle;
        private final int baseIndent;
        private static final int INDENT_PER_LEVEL = 15;

        public PathFolderEntry(StructureListWidget list,
                               StructurePathNode pathNode,
                               boolean expanded,
                               boolean allEnabled,
                               boolean partialEnabled,
                               int baseIndent,
                               Consumer<StructurePathNode> onExpandToggle,
                               Consumer<StructurePathNode> onSelectAllToggle) {
            super(list);
            this.pathNode = pathNode;
            this.expanded = expanded;
            this.allEnabled = allEnabled;
            this.partialEnabled = partialEnabled;
            this.baseIndent = baseIndent;
            this.onExpandToggle = onExpandToggle;
            this.onSelectAllToggle = onSelectAllToggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;

            String expandIcon = expanded ? "v" : ">";
            int expandX = left + indent + 5;
            graphics.drawString(mc.font, expandIcon, expandX, top + 7, 0xAAAAAA, false);

            int boxX = left + indent + 17;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (allEnabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            } else if (partialEnabled) {
                graphics.fill(boxX + 3, boxY + 3, boxX + CHECKBOX_SIZE - 3, boxY + CHECKBOX_SIZE - 3, 0xFFFFFF55);
            }

            String displayText = pathNode.name() + " (" + pathNode.getTotalStructureCount() + ")";
            graphics.drawString(mc.font, displayText, boxX + 14, top + 7, 0xDDDDDD, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = (this.list.width - this.list.getRowWidth()) / 2;
                int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;
                if (mouseX >= rowLeft + indent && mouseX <= rowLeft + indent + 10) {
                    onExpandToggle.accept(pathNode);
                    return true;
                }
                if (mouseX > rowLeft + indent + 10 && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onSelectAllToggle.accept(pathNode);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class PathStructureEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private final int baseIndent;
        private final int depth;
        private static final int INDENT_PER_LEVEL = 15;

        public PathStructureEntry(StructureListWidget list,
                                  StructureEntry structure,
                                  boolean enabled,
                                  int baseIndent,
                                  int depth,
                                  Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.baseIndent = baseIndent;
            this.depth = depth;
            this.onToggle = onToggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            int indent = baseIndent + depth * INDENT_PER_LEVEL;

            int boxX = left + indent + 12;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            String leafName = StructurePathNode.getLeafName(structure);
            int textColor = structure.isVanilla() ? 0xFFFFFF : 0xFFAAAA;
            graphics.drawString(mc.font, leafName, boxX + 14, top + 7, textColor, false);

            if (hovering && mouseX >= boxX + 14) {
                graphics.renderTooltip(mc.font, Component.literal(structure.id().toString()), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = (this.list.width - this.list.getRowWidth()) / 2;
                if (mouseX >= rowLeft && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public static class SingleStructureEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;

        public SingleStructureEntry(StructureListWidget list,
                                    StructureEntry structure,
                                    boolean enabled,
                                    Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.onToggle = onToggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            int boxX = left + 12;
            int boxY = top + 6;
            graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF000000);
            graphics.fill(boxX + 1, boxY + 1, boxX + CHECKBOX_SIZE - 1, boxY + CHECKBOX_SIZE - 1, 0xFF888888);
            if (enabled) {
                graphics.fill(boxX + 2, boxY + 2, boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            int textColor = structure.isVanilla() ? 0xFFFFFF : 0xFFAAAA;
            String name = getLocalizedStructureName(structure);
            graphics.drawString(mc.font, name, boxX + 14, top + 7, textColor, false);

            if (hovering && mouseX >= boxX + 14) {
                graphics.renderTooltip(mc.font, Component.literal(structure.id().toString()), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = (this.list.width - this.list.getRowWidth()) / 2;
                if (mouseX >= rowLeft && mouseX <= rowLeft + this.list.getRowWidth()) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
