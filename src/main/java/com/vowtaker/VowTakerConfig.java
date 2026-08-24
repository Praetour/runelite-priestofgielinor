package com.vowtaker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("vowtaker")
public interface VowTakerConfig extends Config
{
    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Displays the vow status overlay and hidden selection cards.",
        position = 0
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showHiddenCards",
        name = "Reveal vow cards",
        description = "Shows the hidden vow cards in the overlay for debug/local testing.",
        position = 1
    )
    default boolean showHiddenCards()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showDraftReview",
        name = "Show draft review queue",
        description = "Displays the approval queue for draft vows before they become active.",
        position = 2
    )
    default boolean showDraftReview()
    {
        return true;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "overlayOpacity",
        name = "Overlay opacity",
        description = "Opacity of the VowTaker overlay panel.",
        position = 3
    )
    default int overlayOpacity()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "selectedGod",
        name = "Chosen God",
        description = "God allegiance selected for god-bound vows.",
        position = 4
    )
    default String selectedGod()
    {
        return "NONE";
    }

    @ConfigItem(
        keyName = "enforceRankGearCeiling",
        name = "Rank-locked gear tiers",
        description = "Blocks equipping gear above your devotion rank. "
            + "Follower: Mithril, Priest: Adamant, Cardinal: Rune / green d'hide, Bishop: Barrows / Bandos, Archbishop: unlocked.",
        position = 5
    )
    default boolean enforceRankGearCeiling()
    {
        return true;
    }

    @ConfigItem(
        keyName = "autoUpdate",
        name = "Automatic updates",
        description = "Checks for a newer sideloaded build on startup and applies it when you close the client.",
        position = 6
    )
    default boolean autoUpdate()
    {
        return true;
    }
}
