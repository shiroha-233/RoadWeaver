package net.shiroha233.roadweaver.client.map.render;

public enum MapDockAction {
    SEARCH("gui.roadweaver.map.dock.search"),
    FILTER("gui.roadweaver.map.dock.filter"),
    REFRESH("gui.roadweaver.map.dock.refresh"),
    SAMPLE("gui.roadweaver.map.dock.sample"),
    MANUAL_CONNECT("gui.roadweaver.map.dock.manual_connect"),
    CONFIG("gui.roadweaver.map.dock.config"),
    CLOSE("gui.roadweaver.map.dock.close");

    private final String tooltipKey;

    MapDockAction(String tooltipKey) {
        this.tooltipKey = tooltipKey;
    }

    public String tooltipKey() {
        return tooltipKey;
    }
}
