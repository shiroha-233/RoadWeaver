package net.shiroha233.roadweaver.client.map;

/**
 * 地图访问被服务端撤销时需要作出反应的界面。
 */
public interface RevocableMapScreen {

    void onMapAccessRevoked();
}
