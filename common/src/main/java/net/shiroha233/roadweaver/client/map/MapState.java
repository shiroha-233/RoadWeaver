package net.shiroha233.roadweaver.client.map;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地图界面状态管理 - 集中管理所有可变UI状态
 */
public final class MapState {

    private boolean dragging;
    private int dragButton;
    private double lastMouseX;
    private double lastMouseY;

    private boolean zoomDebouncePending;
    private long zoomDebounceDeadlineMs;

    private boolean contextMenuOpen;
    @Nullable
    private BlockPos menuTarget;

    private boolean manualMode;
    @Nullable
    private BlockPos selectedA;

    private final AtomicInteger requestSeq = new AtomicInteger(0);

    public void startDrag(int button, double mouseX, double mouseY) {
        this.dragging = true;
        this.dragButton = button;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.zoomDebouncePending = false;
        closeContextMenu();
    }

    public void updateDrag(double mouseX, double mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    public void endDrag() {
        this.dragging = false;
    }

    public boolean isDragging() { return dragging; }
    public int getDragButton() { return dragButton; }
    public double getLastMouseX() { return lastMouseX; }
    public double getLastMouseY() { return lastMouseY; }

    public void scheduleZoomDebounce(long delayMs) {
        this.zoomDebouncePending = true;
        this.zoomDebounceDeadlineMs = System.currentTimeMillis() + delayMs;
    }

    public void cancelZoomDebounce() {
        this.zoomDebouncePending = false;
    }

    public boolean isZoomDebounceReady() {
        return zoomDebouncePending && System.currentTimeMillis() >= zoomDebounceDeadlineMs;
    }

    public void clearZoomDebounce() {
        this.zoomDebouncePending = false;
    }

    public void openContextMenu(int x, int y, BlockPos target) {
        this.contextMenuOpen = true;
        this.menuTarget = target;
    }

    public void closeContextMenu() {
        this.contextMenuOpen = false;
        this.menuTarget = null;
    }

    public boolean isContextMenuOpen() { return contextMenuOpen; }

    public void toggleManualMode() {
        this.manualMode = !this.manualMode;
        if (!this.manualMode) {
            this.selectedA = null;
        }
        closeContextMenu();
    }

    public void setManualMode(boolean enabled) {
        this.manualMode = enabled;
        if (!enabled) {
            this.selectedA = null;
        }
    }

    public boolean isManualMode() { return manualMode; }

    public void selectFirstPoint(BlockPos pos) {
        this.selectedA = pos;
    }

    public void clearSelection() {
        this.selectedA = null;
    }

    @Nullable
    public BlockPos getSelectedA() { return selectedA; }

    public boolean hasSelection() { return selectedA != null; }

    public int incrementAndGetRequestSeq() {
        return requestSeq.incrementAndGet();
    }

    public int getCurrentRequestSeq() {
        return requestSeq.get();
    }

    public void reset() {
        this.dragging = false;
        this.zoomDebouncePending = false;
        this.contextMenuOpen = false;
        this.menuTarget = null;
        this.manualMode = false;
        this.selectedA = null;
    }
}
