package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapFilterState;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapWorkspacePanel {
    public enum Tab {
        SEARCH,
        FILTER,
        DETAILS
    }

    public enum HitKind {
        NONE,
        CLOSE,
        SEARCH_TAB,
        FILTER_TAB,
        DETAILS_TAB,
        SEARCH_FIELD,
        SEARCH_RESULT,
        STATUS_FILTER,
        SOURCE_FILTER,
        TYPE_FILTER,
        RESET_FILTER,
        DETAIL_TELEPORT,
        DETAIL_ALIAS,
        DETAIL_NOTE
    }

    public record Hit(HitKind kind, Object value) {
        public static Hit none() {
            return new Hit(HitKind.NONE, null);
        }
    }

    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_MIN_WIDTH = 244;
    private static final int PANEL_MAX_WIDTH = 320;
    private static final int PANEL_MIN_HEIGHT = 180;
    private static final float PANEL_WIDTH_RATIO = 0.24f;
    private static final float PANEL_HEIGHT_RATIO = 0.84f;
    private static final int PANEL_PADDING = 12;
    private static final int SEARCH_ROW_HEIGHT = 30;
    private static final int SEARCH_ROW_GAP = 4;
    private static final int FILTER_ROW_HEIGHT = 20;
    private static final int FILTER_ROW_GAP = 2;
    private static final int FILTER_GROUP_GAP = 10;
    private static final int DETAILS_BUTTON_HEIGHT = 20;

    private boolean open;
    private Tab tab = Tab.SEARCH;
    private BlockPos selectedStructure;
    private Rect bounds;
    private Rect searchField;
    private Rect searchViewport;
    private Rect filterViewport;
    private Rect resetFilterButton;
    private Rect closeButton;
    private final EnumMap<Tab, Rect> tabButtons = new EnumMap<>(Tab.class);
    private final List<SearchRow> searchRows = new ArrayList<>();
    private final List<FilterRow> filterRows = new ArrayList<>();
    private final List<Rect> detailButtons = new ArrayList<>();
    private int scrollOffset;
    private int maxScrollOffset;

    public void openSearch() {
        open = true;
        tab = Tab.SEARCH;
        scrollOffset = 0;
    }

    public void openFilter() {
        open = true;
        tab = Tab.FILTER;
        scrollOffset = 0;
    }

    public void openDetails(BlockPos pos) {
        selectedStructure = pos;
        open = true;
        tab = Tab.DETAILS;
        scrollOffset = 0;
    }

    public void close() {
        open = false;
        scrollOffset = 0;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isTab(Tab wanted) {
        return open && tab == wanted;
    }

    public BlockPos selectedStructure() {
        return selectedStructure;
    }

    public Rect searchField() {
        return searchField;
    }

    public int leftEdge(int screenWidth, int screenHeight, int unobstructedBottom) {
        return open ? panelBounds(screenWidth, screenHeight, unobstructedBottom).x() : screenWidth;
    }

    public boolean contains(double mouseX, double mouseY,
                            int screenWidth, int screenHeight, int unobstructedBottom) {
        return open && panelBounds(screenWidth, screenHeight, unobstructedBottom).contains(mouseX, mouseY);
    }

    public void scroll(double delta) {
        if (!open || tab == Tab.DETAILS) return;
        int rowStride = tab == Tab.SEARCH
                ? SEARCH_ROW_HEIGHT + SEARCH_ROW_GAP
                : FILTER_ROW_HEIGHT + FILTER_ROW_GAP;
        int amount = delta > 0 ? -rowStride : rowStride;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset + amount));
    }

    public void render(GuiGraphics graphics,
                       Font font,
                       int screenWidth,
                       int screenHeight,
                       int unobstructedBottom,
                       MapSnapshot snapshot,
                       MapFilterState filterState,
                       List<MapSearchResult> searchResults,
                       boolean searchLoading,
                       boolean searchFailed) {
        if (!open) return;
        layout(screenWidth, screenHeight, unobstructedBottom, snapshot, filterState, searchResults);
        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();
        int h = bounds.height();

        MapDockRenderer.fillRounded(graphics, x + 3, y + 4, w, h, 10, MapTheme.PANEL_SHADOW);
        MapDockRenderer.fillRounded(graphics, x, y, w, h, 10, MapTheme.PANEL_BG);
        MapDockRenderer.fillRounded(graphics, x + 1, y + 1, w - 2, h - 2, 9, MapTheme.PANEL_HIGHLIGHT);
        MapDockRenderer.fillRounded(graphics, x + 2, y + 2, w - 4, h - 4, 8, MapTheme.PANEL_BG);

        Component title = Component.translatable(tabTitleKey(tab));
        graphics.drawString(font, title, x + PANEL_PADDING, y + 10, MapTheme.PANEL_TEXT, false);
        MapDockRenderer.fillRounded(graphics, closeButton.x(), closeButton.y(), closeButton.width(), closeButton.height(), 6,
                closeButton.contains(lastMouseX, lastMouseY) ? MapTheme.PANEL_CLOSE_HOVER : MapTheme.PANEL_CONTROL_BG);
        net.shiroha233.roadweaver.client.map.render.MapIconRenderer.render(
                graphics, MapDockAction.CLOSE,
                closeButton.x() + closeButton.width() / 2,
                closeButton.y() + closeButton.height() / 2,
                MapTheme.PANEL_TEXT,
                12);

        renderTabs(graphics, font, x, y);
        graphics.enableScissor(bounds.x(), bounds.y() + 49, bounds.right(), bounds.bottom() - 8);
        switch (tab) {
            case SEARCH -> renderSearch(graphics, font, searchResults, searchLoading, searchFailed);
            case FILTER -> renderFilter(graphics, font, snapshot, filterState);
            case DETAILS -> renderDetails(graphics, font, snapshot);
        }
        graphics.disableScissor();

        lastMouseX = -1;
        lastMouseY = -1;
    }

    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public void setMousePosition(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public Hit hit(double mouseX,
                   double mouseY,
                   int screenWidth,
                   int screenHeight,
                   int unobstructedBottom,
                   MapSnapshot snapshot,
                   MapFilterState filterState,
                   List<MapSearchResult> searchResults) {
        if (!open) return Hit.none();
        layout(screenWidth, screenHeight, unobstructedBottom, snapshot, filterState, searchResults);
        if (closeButton.contains(mouseX, mouseY)) return new Hit(HitKind.CLOSE, null);
        for (Map.Entry<Tab, Rect> entry : tabButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return new Hit(switch (entry.getKey()) {
                    case SEARCH -> HitKind.SEARCH_TAB;
                    case FILTER -> HitKind.FILTER_TAB;
                    case DETAILS -> HitKind.DETAILS_TAB;
                }, null);
            }
        }
        if (tab == Tab.SEARCH) {
            if (searchField.contains(mouseX, mouseY)) return new Hit(HitKind.SEARCH_FIELD, null);
            if (searchViewport.contains(mouseX, mouseY)) {
                for (SearchRow row : searchRows) {
                    if (row.bounds().contains(mouseX, mouseY) && row.resultIndex() < searchResults.size()) {
                        return new Hit(HitKind.SEARCH_RESULT, searchResults.get(row.resultIndex()));
                    }
                }
            }
        } else if (tab == Tab.FILTER) {
            if (resetFilterButton != null && resetFilterButton.contains(mouseX, mouseY)) {
                return new Hit(HitKind.RESET_FILTER, null);
            }
            if (filterViewport.contains(mouseX, mouseY)) {
                for (FilterRow row : filterRows) {
                    if (row.bounds().contains(mouseX, mouseY)) return new Hit(row.kind(), row.value());
                }
            }
        } else if (tab == Tab.DETAILS) {
            if (detailButtons.size() > 0 && detailButtons.get(0).contains(mouseX, mouseY)) {
                return new Hit(HitKind.DETAIL_TELEPORT, selectedStructure);
            }
            if (detailButtons.size() > 1 && detailButtons.get(1).contains(mouseX, mouseY)) {
                return new Hit(HitKind.DETAIL_ALIAS, selectedStructure);
            }
            if (detailButtons.size() > 2 && detailButtons.get(2).contains(mouseX, mouseY)) {
                return new Hit(HitKind.DETAIL_NOTE, selectedStructure);
            }
        }
        return bounds.contains(mouseX, mouseY) ? new Hit(HitKind.NONE, null) : Hit.none();
    }

    private void layout(int screenWidth,
                        int screenHeight,
                        int unobstructedBottom,
                        MapSnapshot snapshot,
                        MapFilterState filterState,
                        List<MapSearchResult> searchResults) {
        bounds = panelBounds(screenWidth, screenHeight, unobstructedBottom);
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        closeButton = new Rect(x + width - 36, y + 6, 24, 24);
        tabButtons.clear();
        int tabY = y + 32;
        int tabWidth = (width - PANEL_PADDING * 2) / 3;
        int tabX = x + PANEL_PADDING;
        for (Tab value : Tab.values()) {
            tabButtons.put(value, new Rect(tabX, tabY, tabWidth, 20));
            tabX += tabWidth;
        }

        searchField = new Rect(x + PANEL_PADDING, y + 58, width - PANEL_PADDING * 2, 20);
        searchViewport = new Rect(x + PANEL_PADDING, searchField.bottom() + 6,
                width - PANEL_PADDING * 2, Math.max(1, bounds.bottom() - searchField.bottom() - 16));
        resetFilterButton = new Rect(x + PANEL_PADDING, bounds.bottom() - 32,
                width - PANEL_PADDING * 2, 20);
        int filterY = y + 58;
        filterViewport = new Rect(x + PANEL_PADDING, filterY,
                width - PANEL_PADDING * 2, Math.max(1, resetFilterButton.y() - filterY - 8));
        searchRows.clear();
        filterRows.clear();
        detailButtons.clear();
        if (tab == Tab.SEARCH) {
            int resultCount = searchResults == null ? 0 : searchResults.size();
            int contentHeight = resultCount == 0
                    ? 0
                    : resultCount * (SEARCH_ROW_HEIGHT + SEARCH_ROW_GAP) - SEARCH_ROW_GAP;
            maxScrollOffset = Math.max(0, contentHeight - searchViewport.height());
            scrollOffset = Math.min(scrollOffset, maxScrollOffset);
            int rowY = searchViewport.y() - scrollOffset;
            if (searchResults != null) {
                for (int i = 0; i < searchResults.size(); i++) {
                    Rect row = new Rect(x + PANEL_PADDING, rowY,
                            width - PANEL_PADDING * 2, SEARCH_ROW_HEIGHT);
                    if (intersects(row, searchViewport)) searchRows.add(new SearchRow(row, i));
                    rowY += SEARCH_ROW_HEIGHT + SEARCH_ROW_GAP;
                }
            }
        } else if (tab == Tab.FILTER) {
            Set<String> types = filterState == null ? Set.of() : filterState.availableTypes(snapshot);
            int rowStride = FILTER_ROW_HEIGHT + FILTER_ROW_GAP;
            int contentHeight = (ConnectionStatus.values().length
                    + MapStructureSource.values().length
                    + types.size()) * rowStride + FILTER_GROUP_GAP * 2;
            maxScrollOffset = Math.max(0, contentHeight - filterViewport.height());
            scrollOffset = Math.min(scrollOffset, maxScrollOffset);
            int rowY = filterViewport.y() - scrollOffset;
            for (ConnectionStatus status : ConnectionStatus.values()) {
                Rect row = new Rect(x + PANEL_PADDING, rowY,
                        width - PANEL_PADDING * 2, FILTER_ROW_HEIGHT);
                if (intersects(row, filterViewport)) filterRows.add(new FilterRow(row, HitKind.STATUS_FILTER, status));
                rowY += rowStride;
            }
            rowY += FILTER_GROUP_GAP;
            for (MapStructureSource source : MapStructureSource.values()) {
                Rect row = new Rect(x + PANEL_PADDING, rowY,
                        width - PANEL_PADDING * 2, FILTER_ROW_HEIGHT);
                if (intersects(row, filterViewport)) filterRows.add(new FilterRow(row, HitKind.SOURCE_FILTER, source));
                rowY += rowStride;
            }
            rowY += FILTER_GROUP_GAP;
            for (String type : types.stream().sorted().toList()) {
                Rect row = new Rect(x + PANEL_PADDING, rowY,
                        width - PANEL_PADDING * 2, FILTER_ROW_HEIGHT);
                if (intersects(row, filterViewport)) filterRows.add(new FilterRow(row, HitKind.TYPE_FILTER, type));
                rowY += rowStride;
            }
        } else {
            maxScrollOffset = 0;
            int buttonY = Math.min(y + 152,
                    bounds.bottom() - PANEL_PADDING - DETAILS_BUTTON_HEIGHT);
            int buttonWidth = (width - PANEL_PADDING * 2 - 8) / 3;
            for (int i = 0; i < 3; i++) {
                detailButtons.add(new Rect(x + PANEL_PADDING + i * (buttonWidth + 4),
                        buttonY, buttonWidth, DETAILS_BUTTON_HEIGHT));
            }
        }
    }

    static Rect panelBounds(int screenWidth, int screenHeight, int unobstructedBottom) {
        int availableWidth = Math.max(1, screenWidth - PANEL_MARGIN * 2);
        int preferredWidth = Math.round(screenWidth * PANEL_WIDTH_RATIO);
        int width = Math.min(availableWidth,
                Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, preferredWidth)));
        int x = Math.max(0, screenWidth - width - PANEL_MARGIN);

        int bottom = Math.max(PANEL_MARGIN + 1,
                Math.min(screenHeight - PANEL_MARGIN, unobstructedBottom));
        int availableHeight = Math.max(1, bottom - PANEL_MARGIN);
        int preferredHeight = Math.max(PANEL_MIN_HEIGHT, Math.round(screenHeight * PANEL_HEIGHT_RATIO));
        int height = Math.min(availableHeight, preferredHeight);
        return new Rect(x, PANEL_MARGIN, width, height);
    }

    private void renderTabs(GuiGraphics graphics, Font font, int x, int y) {
        for (Map.Entry<Tab, Rect> entry : tabButtons.entrySet()) {
            Rect rect = entry.getValue();
            boolean selected = entry.getKey() == tab;
            MapDockRenderer.fillRounded(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 5,
                    selected ? MapTheme.PANEL_TAB_ACTIVE : MapTheme.PANEL_CONTROL_BG);
            Component label = fitLabel(font, Component.translatable(tabTitleKey(entry.getKey())), rect.width() - 8);
            drawCentered(graphics, font, label, rect,
                    selected ? MapTheme.PANEL_TEXT : MapTheme.PANEL_MUTED);
        }
    }

    private void renderSearch(GuiGraphics graphics,
                              Font font,
                              List<MapSearchResult> results,
                              boolean loading,
                              boolean failed) {
        if (loading) {
            graphics.drawString(font, Component.translatable("gui.roadweaver.map.panel.search.loading"),
                    searchViewport.x(), searchViewport.y() + 2, MapTheme.PANEL_MUTED, false);
            return;
        }
        if (failed) {
            graphics.drawString(font, Component.translatable("gui.roadweaver.map.panel.search.failed"),
                    searchViewport.x(), searchViewport.y() + 2, MapTheme.COLOR_FAILED, false);
            return;
        }
        if (results == null || results.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.roadweaver.map.panel.search.empty"),
                    searchViewport.x(), searchViewport.y() + 2, MapTheme.PANEL_MUTED, false);
            return;
        }
        graphics.enableScissor(searchViewport.x(), searchViewport.y(), searchViewport.right(), searchViewport.bottom());
        for (SearchRow searchRow : searchRows) {
            if (searchRow.resultIndex() >= results.size()) continue;
            MapSearchResult result = results.get(searchRow.resultIndex());
            Rect row = searchRow.bounds();
            boolean hovered = row.contains(lastMouseX, lastMouseY);
            if (hovered) MapDockRenderer.fillRounded(graphics, row.x(), row.y(), row.width(), row.height(), 5, MapTheme.PANEL_CONTROL_BG);
            String alias = ClientMapNotes.getAlias(result.pos());
            String primary = alias != null && !alias.isBlank() ? alias : result.structureId();
            String secondary = result.structureId() + "  " + result.pos().getX() + ", " + result.pos().getZ();
            graphics.drawString(font, font.plainSubstrByWidth(primary, row.width() - 12), row.x() + 6, row.y() + 4,
                    MapTheme.PANEL_TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(secondary, row.width() - 12), row.x() + 6, row.y() + 16,
                    MapTheme.PANEL_MUTED, false);
        }
        graphics.disableScissor();
    }

    private void renderFilter(GuiGraphics graphics, Font font, MapSnapshot snapshot, MapFilterState filterState) {
        graphics.enableScissor(filterViewport.x(), filterViewport.y(), filterViewport.right(), filterViewport.bottom());
        for (FilterRow row : filterRows) {
            boolean checked = switch (row.kind()) {
                case STATUS_FILTER -> filterState != null && filterState.isStatusSelected((ConnectionStatus) row.value());
                case SOURCE_FILTER -> filterState != null && filterState.isSourceSelected((MapStructureSource) row.value());
                case TYPE_FILTER -> filterState != null && filterState.isTypeSelected((String) row.value(), filterState.availableTypes(snapshot));
                default -> false;
            };
            drawCheckbox(graphics, row.bounds().x() + 2, row.bounds().y() + 4, checked);
            Component label = switch (row.kind()) {
                case STATUS_FILTER -> Component.translatable("gui.roadweaver.map.filter.status." + ((ConnectionStatus) row.value()).name().toLowerCase());
                case SOURCE_FILTER -> Component.translatable("gui.roadweaver.map.filter.source." + ((MapStructureSource) row.value()).name().toLowerCase());
                case TYPE_FILTER -> Component.literal((String) row.value());
                default -> Component.empty();
            };
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), row.bounds().width() - 28),
                    row.bounds().x() + 18, row.bounds().y() + 5, MapTheme.PANEL_TEXT, false);
        }
        graphics.disableScissor();
        MapDockRenderer.fillRounded(graphics, resetFilterButton.x(), resetFilterButton.y(),
                resetFilterButton.width(), resetFilterButton.height(), 5, MapTheme.PANEL_CONTROL_BG);
        drawCentered(graphics, font, Component.translatable("gui.roadweaver.map.panel.filter.reset"),
                resetFilterButton, MapTheme.PANEL_TEXT);
    }

    private void renderDetails(GuiGraphics graphics, Font font, MapSnapshot snapshot) {
        int x = bounds.x() + PANEL_PADDING;
        int y = bounds.y() + 62;
        if (selectedStructure == null) {
            graphics.drawString(font, Component.translatable("gui.roadweaver.map.panel.details.empty"), x, y, MapTheme.PANEL_MUTED, false);
            return;
        }
        String alias = ClientMapNotes.getAlias(selectedStructure);
        String name = snapshot == null ? null : snapshot.structureName(selectedStructure);
        String primary = alias != null && !alias.isBlank() ? alias : name != null ? name : "unknown";
        graphics.drawString(font, font.plainSubstrByWidth(primary, bounds.width() - PANEL_PADDING * 2), x, y, MapTheme.PANEL_TEXT, false);
        graphics.drawString(font, Component.translatable("gui.roadweaver.map.coord", selectedStructure.getX(), selectedStructure.getZ()),
                x, y + 17, MapTheme.PANEL_MUTED, false);
        if (name != null && alias != null && !alias.equals(name)) {
            graphics.drawString(font, font.plainSubstrByWidth(name, bounds.width() - PANEL_PADDING * 2), x, y + 33, MapTheme.PANEL_MUTED, false);
        }
        for (int i = 0; i < detailButtons.size(); i++) {
            Rect button = detailButtons.get(i);
            MapDockRenderer.fillRounded(graphics, button.x(), button.y(), button.width(), button.height(), 5, MapTheme.PANEL_CONTROL_BG);
            Component label = Component.translatable(switch (i) {
                case 0 -> "gui.roadweaver.map.panel.details.teleport";
                case 1 -> "gui.roadweaver.map.panel.details.alias";
                default -> "gui.roadweaver.map.panel.details.note";
            });
            drawCentered(graphics, font, label, button, MapTheme.PANEL_TEXT);
        }
    }

    private static void drawCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 12, y + 12, checked ? MapTheme.PANEL_CHECKED : MapTheme.PANEL_CHECKBOX);
        if (checked) {
            graphics.fill(x + 3, y + 5, x + 5, y + 9, MapTheme.PANEL_TEXT);
            graphics.fill(x + 5, y + 7, x + 10, y + 9, MapTheme.PANEL_TEXT);
        }
    }

    private static Component fitLabel(Font font, Component label, int maxWidth) {
        if (font.width(label) <= maxWidth) return label;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (suffixWidth >= maxWidth) {
            return Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(0, maxWidth)));
        }
        String prefix = font.plainSubstrByWidth(label.getString(), maxWidth - suffixWidth);
        return Component.literal(prefix + suffix);
    }

    private static void drawCentered(GuiGraphics graphics, Font font, Component text, Rect rect, int color) {
        graphics.drawString(font, text, rect.x() + (rect.width() - font.width(text)) / 2,
                rect.y() + (rect.height() - font.lineHeight) / 2, color, false);
    }

    private static String tabTitleKey(Tab tab) {
        return switch (tab) {
            case SEARCH -> "gui.roadweaver.map.panel.search.title";
            case FILTER -> "gui.roadweaver.map.panel.filter.title";
            case DETAILS -> "gui.roadweaver.map.panel.details.title";
        };
    }

    private static boolean intersects(Rect first, Rect second) {
        return first.right() >= second.x()
                && first.x() <= second.right()
                && first.bottom() >= second.y()
                && first.y() <= second.bottom();
    }

    private record SearchRow(Rect bounds, int resultIndex) {}
    private record FilterRow(Rect bounds, HitKind kind, Object value) {}
}
