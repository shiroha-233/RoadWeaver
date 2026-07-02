package net.shiroha233.roadweaver.client.loading.fabric;

import net.shiroha233.roadweaver.client.fabric.ClientInit;

/**
 * 平台侧“打开地图”按键文案实现。
 */
public final class OpenMapKeyTextBridgeImpl {
    private OpenMapKeyTextBridgeImpl() {
    }

    public static String getDisplayText() {
        return ClientInit.OPEN_MAP == null
                ? "H" : ClientInit.OPEN_MAP.getTranslatedKeyMessage().getString();
    }
}