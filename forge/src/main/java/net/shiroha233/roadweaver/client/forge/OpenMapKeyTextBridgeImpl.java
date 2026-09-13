package net.shiroha233.roadweaver.client.loading.forge;

/**
 * Forge 侧“打开地图”按键文案实现。
 */
public final class OpenMapKeyTextBridgeImpl {
    private OpenMapKeyTextBridgeImpl() {
    }

    public static String getDisplayText() {
        return net.shiroha233.roadweaver.client.forge.ClientKeyMappings.OPEN_MAP == null
                ? "H"
                : net.shiroha233.roadweaver.client.forge.ClientKeyMappings.OPEN_MAP.getTranslatedKeyMessage().getString();
    }
}
