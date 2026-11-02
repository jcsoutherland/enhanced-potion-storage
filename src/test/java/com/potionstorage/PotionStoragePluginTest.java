package com.potionstorage;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite with this plugin loaded, for trying it out: run {@code main}, or the jar from {@code shadowJar}. */
public class PotionStoragePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PotionStoragePlugin.class);
		RuneLite.main(args);
	}
}
