package net.shiroha233.roadweaver.client.loading.fabric;

/**
 * 平台侧“打开地图”按键文案实现。
 */
public final class OpenMapKeyTextBridgeImpl {
    private OpenMapKeyTextBridgeImpl() {
    }

    public static String getDisplayText() {
        return net.shiroha233.roadweaver.client.fabric.ClientInit.OPEN_MAP == null
                ? "H"
                : net.shiroha233.roadweaver.client.fabric.ClientInit.OPEN_MAP.getTranslatedKeyMessage().getString();
    }
}