package com.vowtaker.service;

import com.vowtaker.model.Rank;
import com.vowtaker.model.TaskDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

/**
 * Full-menu suppression for tasks whose tier the player has not yet reached.
 * On {@link MenuEntryAdded} the entry is marked red/locked; on
 * {@link MenuOptionClicked} the click is consumed with a chat warning.
 */
@Singleton
public class TaskEnforcementService
{
    private static final Set<String> BLOCKABLE_OPTIONS = new HashSet<>(Arrays.asList(
        "attack", "fight", "chop", "chop-down", "mine", "fish", "net", "cage",
        "harpoon", "bait", "big net", "loot", "open", "pick", "steal from",
        "cast", "wield", "throw-at", "shoot-at", "use"
    ));

    private static final long WARN_COOLDOWN_MS = 3_000L;

    @Inject
    private VowStorageService storage;

    @Inject
    private Client client;

    private long lastWarnMs;
    private String lastWarnTarget = "";

    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        MenuEntry entry = event.getMenuEntry();
        if (entry == null) return;

        if (!isBlockable(event.getOption())) return;

        TaskDefinition locked = findLockingTask(event.getTarget());
        if (locked == null) return;

        String stripped = Text.removeTags(event.getTarget());
        entry.setOption("<col=ff4040>" + event.getOption() + "</col>");
        entry.setTarget("<col=ff4040>" + stripped + " (locked: " + locked.getName() + ")</col>");
        entry.setDeprioritized(true);
    }

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption() == null ? "" : event.getMenuOption());
        if (!isBlockable(option)) return;

        String targetText = event.getMenuTarget() == null ? "" : event.getMenuTarget();
        TaskDefinition locked = findLockingTask(targetText);
        if (locked == null) return;

        event.consume();
        maybeWarn(Text.removeTags(targetText), locked);
    }

    private void maybeWarn(String target, TaskDefinition locked)
    {
        long now = System.currentTimeMillis();
        if (now - lastWarnMs < WARN_COOLDOWN_MS && target.equalsIgnoreCase(lastWarnTarget))
        {
            return;
        }
        lastWarnMs = now;
        lastWarnTarget = target;

        String required = locked.getTier().fullTitle(storage.getSelectedGod());
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "VowTaker: your vows forbid this. Reach " + required + " to unlock \"" + locked.getName() + "\".",
            null);
    }

    private boolean isBlockable(String option)
    {
        if (option == null) return false;
        return BLOCKABLE_OPTIONS.contains(Text.removeTags(option).toLowerCase(Locale.ROOT));
    }

    /** Returns the highest-tier task that gates this target if the player is under its tier. */
    private TaskDefinition findLockingTask(String rawTarget)
    {
        if (rawTarget == null) return null;
        String target = Text.removeTags(rawTarget).toLowerCase(Locale.ROOT);
        if (target.isEmpty()) return null;

        Rank playerRank = storage.getCurrentRank();
        List<TaskDefinition> matches = new ArrayList<>();

        for (TaskDefinition t : TaskRegistry.all())
        {
            if (t.getGatedTargets().isEmpty()) continue;
            if (t.getTier().getTier() <= playerRank.getTier()) continue;
            for (String gate : t.getGatedTargets())
            {
                if (target.contains(gate.toLowerCase(Locale.ROOT)))
                {
                    matches.add(t);
                    break;
                }
            }
        }

        if (matches.isEmpty()) return null;
        TaskDefinition strongest = matches.get(0);
        for (TaskDefinition m : matches)
        {
            if (m.getTier().getTier() < strongest.getTier().getTier())
            {
                strongest = m;
            }
        }
        return strongest;
    }
}
