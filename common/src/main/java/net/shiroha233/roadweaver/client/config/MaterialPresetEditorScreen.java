package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.config.PresetService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 材料预设编辑器界面
 */
public class MaterialPresetEditorScreen extends Screen {
    private static final int MARGIN = 6;
    private static final float ZH_SCALE = 1.35f;
    private static final float EN_SCALE = 0.85f;
    
    private final Screen parent;
    
    private PresetListWidget presetList;
    private EditBox nameBox;
    private Button typeButton;
    private MaterialGridWidget baseMaterialGrid;
    private MaterialGridWidget slabMaterialGrid;
    private BlockCandidateWidget blockCandidateWidget;
    private Button saveButton;
    private Button cancelButton;
    private Button newButton;
    private Button deleteButton;
    private PresetService.RoadType filterType = PresetService.RoadType.ARTIFICIAL;
    
    private int editorLeft;
    private int editorWidth;
    private int editorHeaderY;
    
    private final List<UiPreset> presets = new ArrayList<>();
    private final Set<String> originalIds = new HashSet<>();
    private int activePresetIndex = -1;
    
    private static class UiPreset {
        String id;
        String name;
        PresetService.RoadType type;
        List<String> materials = new ArrayList<>();
        List<String> slabMaterials = new ArrayList<>();
    }

    public MaterialPresetEditorScreen(Screen parent) {
        super(Component.translatable("gui.roadweaver.preset_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int topBarY = 24;
        int bottomY = this.height - 24;

        int leftPanelX = MARGIN;
        int leftPanelW = Math.max(170, Math.min(240, this.width / 4));

        int rightMinW = 160 + 200 + 6;
        int maxLeftW = Math.max(120, this.width - (MARGIN * 3) - rightMinW);
        leftPanelW = Math.min(leftPanelW, maxLeftW);

        int rightPanelX = leftPanelX + leftPanelW + MARGIN;
        int rightPanelW = this.width - rightPanelX - MARGIN;

        this.nameBox = new EditBox(font, leftPanelX, topBarY, leftPanelW, 18, Component.translatable("gui.roadweaver.preset_editor.name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setResponder(this::onNameChanged);
        this.addRenderableWidget(nameBox);

        int listTop = this.nameBox.getY() + this.nameBox.getHeight() + 4;
        int listHeight = bottomY - listTop - 26;
        this.presetList = new PresetListWidget(minecraft, leftPanelW, listHeight, listTop, this::selectPresetEntry);
        this.presetList.setLeftPos(leftPanelX);
        this.addRenderableWidget(presetList);
        
        this.newButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.new"), b -> createNewPreset())
                .bounds(leftPanelX, bottomY - 24, leftPanelW / 2 - 2, 20).build();
        this.deleteButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.delete"), b -> deleteActivePreset())
                .bounds(leftPanelX + leftPanelW / 2 + 2, bottomY - 24, leftPanelW / 2 - 2, 20).build();
        this.addRenderableWidget(newButton);
        this.addRenderableWidget(deleteButton);

        int gap = 6;
        int candidateMinW = 160;
        int editorMinW = 200;

        int candidateW = Math.min(Math.max(candidateMinW, rightPanelW / 2), 360);
        int maxCandidateW = Math.max(candidateMinW, rightPanelW - editorMinW - gap);
        candidateW = Math.min(candidateW, maxCandidateW);

        int editorW = rightPanelW - candidateW - gap;
        if (editorW < editorMinW) {
            editorW = Math.max(editorMinW, (rightPanelW - gap) / 2);
            candidateW = Math.max(candidateMinW, rightPanelW - gap - editorW);
        }

        int editorX = rightPanelX;
        int candidateX = editorX + editorW + gap;

        int contentH = bottomY - topBarY - 4;
        this.blockCandidateWidget = new BlockCandidateWidget(candidateX, topBarY, candidateW, contentH, this::onBlockSelectedFromCandidate);
        this.addRenderableWidget(blockCandidateWidget);

        int topY = topBarY;
        this.typeButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.road_type"), b -> toggleTypeFilter())
                .bounds(editorX, topY, 120, 20).build();
        this.addRenderableWidget(typeButton);
        updateTypeButton(filterType);

        topY += 24;
        topY += 6;
        this.editorHeaderY = topY;
        topY += 34;

        int gridCols = Math.max(1, editorW / 18);
        if (gridCols > 24) gridCols = 24;
        
        this.baseMaterialGrid = new MaterialGridWidget(editorX, topY, gridCols, 2,
                Component.translatable("gui.roadweaver.preset_editor.base_materials").getString(),
                this::removeBaseMaterial, () -> setTargetGrid(true));
        this.addRenderableWidget(baseMaterialGrid);
        
        topY += 2 * 18 + 12 + 8;
        
        this.slabMaterialGrid = new MaterialGridWidget(editorX, topY, gridCols, 2,
                Component.translatable("gui.roadweaver.preset_editor.slab_materials").getString(),
                this::removeSlabMaterial, () -> setTargetGrid(false));
        this.addRenderableWidget(slabMaterialGrid);

        this.editorLeft = editorX;
        this.editorWidth = editorW;

        this.saveButton = Button.builder(Component.translatable("gui.roadweaver.common.save"), b -> onSave())
                .bounds(this.width / 2 - 82, this.height - 22, 80, 20).build();
        this.cancelButton = Button.builder(Component.translatable("gui.roadweaver.common.cancel"), b -> onClose())
                .bounds(this.width / 2 + 2, this.height - 22, 80, 20).build();
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);

        if (presets.isEmpty()) {
            loadPresets();
        }
        
        refreshPresetListUI();
        ensureSelectionMatchesFilter();
    }
    
    private void loadPresets() {
        presets.clear();
        originalIds.clear();
        PresetService.reload();
        List<PresetService.PresetDef> defs = PresetService.getAllPresets();
        for (PresetService.PresetDef def : defs) {
            UiPreset p = new UiPreset();
            p.id = def.id();
            p.name = def.name();
            p.type = def.type();
            p.materials = new ArrayList<>(def.materials());
            p.slabMaterials = new ArrayList<>(def.slabMaterials());
            presets.add(p);
            originalIds.add(p.id);
        }
    }

    private void selectPresetEntry(PresetListWidget.PresetEntry entry) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(entry.getId())) {
                selectPreset(i);
                return;
            }
        }
    }

    private void selectPreset(int index) {
        if (index < 0 || index >= presets.size()) return;
        this.activePresetIndex = index;
        UiPreset p = presets.get(index);
        
        this.nameBox.setValue(p.name);
        this.baseMaterialGrid.setMaterials(p.materials);
        this.slabMaterialGrid.setMaterials(p.slabMaterials);
        
        setTargetGrid(true);
        setEditorActive(true);
        refreshPresetListUI();
    }
    
    private void setEditorActive(boolean active) {
        this.nameBox.setEditable(active);
        this.baseMaterialGrid.active = active;
        this.slabMaterialGrid.active = active;
        this.deleteButton.active = active;
        
        if (!active) {
            this.nameBox.setValue("");
            this.baseMaterialGrid.setMaterials(null);
            this.slabMaterialGrid.setMaterials(null);
        }
    }

    private void createNewPreset() {
        UiPreset p = new UiPreset();
        p.id = "custom_" + System.currentTimeMillis();
        p.name = "New Preset";
        p.type = filterType;
        if (filterType == PresetService.RoadType.NATURAL) {
            p.materials.add("minecraft:dirt_path");
        } else {
            p.materials.add("minecraft:stone_bricks");
            p.slabMaterials.add("minecraft:stone_brick_slab");
        }
        presets.add(p);
        
        selectPreset(presets.size() - 1);
        refreshPresetListUI();
        
        if (presetList != null) {
            presetList.setScrollAmount(presetList.getMaxScroll());
        }
    }

    private void deleteActivePreset() {
        if (activePresetIndex >= 0 && activePresetIndex < presets.size()) {
            presets.remove(activePresetIndex);
            activePresetIndex = -1;
            refreshPresetListUI();
            if (!presets.isEmpty()) {
                selectPreset(Math.max(0, presets.size() - 1));
            } else {
                setEditorActive(false);
            }
        }
    }

    private void refreshPresetListUI() {
        if (presetList == null) return;
        presetList.clearPresets();
        for (int i = 0; i < presets.size(); i++) {
            UiPreset p = presets.get(i);
            if (!matchesFilter(p)) continue;
            if (p.type == PresetService.RoadType.NATURAL) {
                ResourceLocation biomeId = tryGetBiomeIdFromPreset(p);
                if (biomeId != null) {
                    presetList.addPreset(p.id, getBiomeZhName(biomeId), getBiomeEnName(biomeId), i == activePresetIndex);
                    continue;
                }
            }
            presetList.addPreset(p.id, p.name, i == activePresetIndex);
        }
    }

    private ResourceLocation tryGetBiomeIdFromPreset(UiPreset p) {
        if (p == null || p.type != PresetService.RoadType.NATURAL) return null;
        if (p.id == null) return null;
        if (!p.id.startsWith("natural_")) return null;
        String rest = p.id.substring("natural_".length());
        if (rest.isBlank()) return null;

        ResourceLocation vanilla = new ResourceLocation("minecraft", rest);

        int firstUnderscore = rest.indexOf('_');
        if (firstUnderscore > 0 && firstUnderscore < rest.length() - 1) {
            String ns = rest.substring(0, firstUnderscore);
            String path = rest.substring(firstUnderscore + 1);
            try {
                ResourceLocation candidate = new ResourceLocation(ns, path);
                String key = "biome." + candidate.getNamespace() + "." + candidate.getPath();
                Component translated = Component.translatable(key);
                if (!Objects.equals(translated.getString(), key)) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        return vanilla;
    }

    private String getBiomeZhName(ResourceLocation biomeId) {
        String key = "biome." + biomeId.getNamespace() + "." + biomeId.getPath();
        Component translated = Component.translatable(key);
        String s = translated.getString();
        return Objects.equals(s, key) ? biomeId.toString() : s;
    }

    private String getBiomeEnName(ResourceLocation biomeId) {
        String path = biomeId.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        String out = sb.toString();
        return out.isBlank() ? biomeId.toString() : out;
    }

    private boolean matchesFilter(UiPreset p) {
        if (p == null) return false;
        if (filterType != null && p.type != filterType) return false;
        return true;
    }

    private void ensureSelectionMatchesFilter() {
        if (activePresetIndex >= 0 && activePresetIndex < presets.size()) {
            if (matchesFilter(presets.get(activePresetIndex))) {
                setEditorActive(true);
                return;
            }
        }
        for (int i = 0; i < presets.size(); i++) {
            if (matchesFilter(presets.get(i))) {
                selectPreset(i);
                return;
            }
        }
        activePresetIndex = -1;
        setEditorActive(false);
        refreshPresetListUI();
    }

    private void onNameChanged(String name) {
        if (activePresetIndex >= 0) {
            presets.get(activePresetIndex).name = name;
            refreshPresetListUI();
        }
    }

    private void toggleTypeFilter() {
        filterType = filterType == PresetService.RoadType.ARTIFICIAL ? PresetService.RoadType.NATURAL : PresetService.RoadType.ARTIFICIAL;
        updateTypeButton(filterType);
        refreshPresetListUI();
        ensureSelectionMatchesFilter();
    }
    
    private void updateTypeButton(PresetService.RoadType type) {
        if (typeButton == null) return;
        String key = "gui.roadweaver.preset_editor.road_type." + type.name().toLowerCase(Locale.ROOT);
        typeButton.setMessage(Component.translatable(key));
        typeButton.setTooltip(Tooltip.create(Component.translatable("gui.roadweaver.preset_editor.road_type.tooltip")));
    }

    private void setTargetGrid(boolean isBase) {
        this.baseMaterialGrid.setIsTarget(isBase);
        this.slabMaterialGrid.setIsTarget(!isBase);
    }
    
    private void removeBaseMaterial(int index) {
        if (activePresetIndex >= 0) {
            UiPreset p = presets.get(activePresetIndex);
            if (index >= 0 && index < p.materials.size()) {
                p.materials.remove(index);
                baseMaterialGrid.setMaterials(p.materials);
            }
        }
    }
    
    private void removeSlabMaterial(int index) {
        if (activePresetIndex >= 0) {
            UiPreset p = presets.get(activePresetIndex);
            if (index >= 0 && index < p.slabMaterials.size()) {
                p.slabMaterials.remove(index);
                slabMaterialGrid.setMaterials(p.slabMaterials);
            }
        }
    }

    private void onBlockSelectedFromCandidate(Block block) {
        if (activePresetIndex < 0) return;
        UiPreset p = presets.get(activePresetIndex);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return;
        String idStr = id.toString();

        if (baseMaterialGrid.isTarget()) {
            if (p.materials.size() < 16) {
                p.materials.add(idStr);
                baseMaterialGrid.setMaterials(p.materials);
            }
        } else {
            if (p.slabMaterials.size() < 16) {
                p.slabMaterials.add(idStr);
                slabMaterialGrid.setMaterials(p.slabMaterials);
            }
        }
    }

    private void onSave() {
        Set<String> currentIds = presets.stream().map(p -> p.id).collect(Collectors.toSet());
        for (String oldId : originalIds) {
            if (!currentIds.contains(oldId)) {
                PresetService.deletePresetFile(oldId);
            }
        }
        
        for (UiPreset p : presets) {
            PresetService.saveOrUpdatePresetFile(
                p.id, 
                p.name, 
                p.type, 
                p.materials, 
                p.slabMaterials
            );
        }
        
        PresetService.reload();
        onClose();
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        UiPreset p = (activePresetIndex >= 0 && activePresetIndex < presets.size()) ? presets.get(activePresetIndex) : null;
        if (p != null && p.type == PresetService.RoadType.NATURAL) {
            ResourceLocation biomeId = tryGetBiomeIdFromPreset(p);
            if (biomeId != null) {
                String zh = getBiomeZhName(biomeId);
                String en = getBiomeEnName(biomeId);
                int x = editorLeft;
                int y = editorHeaderY;

                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(ZH_SCALE, ZH_SCALE, 1.0F);
                g.drawString(font, font.plainSubstrByWidth(zh, (int) (editorWidth / ZH_SCALE)), 0, 0, 0xFFFFFF, false);
                g.pose().popPose();

                g.pose().pushPose();
                g.pose().translate(x, y + 18, 0);
                g.pose().scale(EN_SCALE, EN_SCALE, 1.0F);
                g.drawString(font, font.plainSubstrByWidth(en, (int) (editorWidth / EN_SCALE)), 0, 0, 0xBBBBBB, false);
                g.pose().popPose();
            }
        }
        
        g.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFF);
    }
    
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
