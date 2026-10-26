package net.shiroha233.roadweaver.client.ui;

/**
 * 简单矩形记录类
 */
public record Rect(int x, int y, int width, int height) {
    
    public int right() { return x + width; }
    public int bottom() { return y + height; }
    
    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }
    
    public Rect clampToScreen(int screenW, int screenH, int margin) {
        int nx = x;
        int ny = y;
        if (nx + width > screenW - margin) nx = screenW - margin - width;
        if (ny + height > screenH - margin) ny = screenH - margin - height;
        if (nx < margin) nx = margin;
        if (ny < margin) ny = margin;
        return new Rect(nx, ny, width, height);
    }
}
