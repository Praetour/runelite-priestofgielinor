package com.vowtaker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development launcher. Sideloads the plugin into a local client.
 * Must run with assertions enabled ({@code -ea}) or {@code loadBuiltin} throws.
 */
public final class VowTakerPluginLauncher
{
    private VowTakerPluginLauncher()
    {
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(VowTakerPlugin.class);
        RuneLite.main(args);
    }
}
