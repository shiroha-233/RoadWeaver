package net.shiroha233.roadweaver.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.*;

/**
 * The static class storing the utility methods for client.
 */
public class Util {

	/**
	 * Get a keybind with the specified translation key.
	 *
	 * @param name The translation key of the name of the desired keybind.
	 * @return The {@link KeyMapping} object represents the desired keybind.
	 * @throws NoSuchElementException If no keybind with the name given is found.
	 * @throws NullPointerException If {@code null} is selected as the keybind.
	 */
	public static KeyMapping getKey(String name) {
		return Arrays.stream(Minecraft.getInstance().options.keyMappings)
			       .filter(key -> key.getName().equals(name))
			       .findFirst()
			       .orElseThrow();
	}
}