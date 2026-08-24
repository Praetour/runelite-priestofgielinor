package com.vowtaker.launch;

import com.vowtaker.VowTakerPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Entry point used by the local RuneLite.exe shim, which launches this instead of RuneLite's
 * own main so the plugin loads as a builtin. Requires assertions enabled ({@code -ea}).
 *
 * <p>Local development harness only - delete before any Plugin Hub submission.
 */
public final class WrapperMain
{
    private WrapperMain()
    {
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(VowTakerPlugin.class);
        RuneLite.main(args);
    }
}
