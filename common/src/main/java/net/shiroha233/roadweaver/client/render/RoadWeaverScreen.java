package net.shiroha233.roadweaver.client.render;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared screen base for RoadWeaver UI screens. */
public abstract class RoadWeaverScreen extends Screen {
    protected RoadWeaverScreen(Component title) {
        super(title);
    }

}
