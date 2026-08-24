package com.vowtaker.service;

import com.vowtaker.model.Rank;
import com.vowtaker.model.TaskDefinition;
import com.vowtaker.model.TaskTrigger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;

@Singleton
public class TaskService
{
    @Inject
    private VowStorageService storage;

    @Inject
    private Client client;

    @Inject
    private VowSelectionService selection;

    @Inject
    private VowEnforcementService enforcement;

    private final Map<String, Pattern> compiledPatterns = new HashMap<>();
    private final Set<Integer> npcsDamagedByMe = new HashSet<>();
    private Runnable panelRefresh;

    public void setPanelRefreshCallback(Runnable r)
    {
        this.panelRefresh = r;
    }

    public void initialize()
    {
        compiledPatterns.clear();
        npcsDamagedByMe.clear();
        for (TaskDefinition t : TaskRegistry.all())
        {
            TaskTrigger trigger = t.getTrigger();
            if (trigger != null && trigger.getKind() == TaskTrigger.Kind.CHAT_PATTERN)
            {
                try
                {
                    compiledPatterns.put(t.getId(), Pattern.compile(trigger.getPattern()));
                }
                catch (PatternSyntaxException ignored)
                {
                    // skip malformed regex — future JSON override should validate
                }
            }
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null || storage.getCurrentRank() == Rank.NONE)
        {
            return;
        }

        String body = event.getMessage();
        Rank playerRank = storage.getCurrentRank();

        for (TaskDefinition task : TaskRegistry.all())
        {
            if (!task.availableTo(storage.getSelectedGod())) continue;
            if (task.getTier().getTier() > playerRank.getTier()) continue;
            if (storage.isTaskCompleted(task.getId())) continue;
            if (task.isMilestone() && !isMilestoneUnlocked(task)) continue;

            Pattern p = compiledPatterns.get(task.getId());
            if (p != null && p.matcher(body).find())
            {
                completeTask(task);
            }
        }
    }

    /** Track NPCs the local player has damaged so we can credit their kill on despawn. */
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (event == null || event.getActor() == null || event.getHitsplat() == null) return;
        if (!event.getHitsplat().isMine()) return;
        Actor target = event.getActor();
        if (target instanceof NPC)
        {
            NPC npc = (NPC) target;
            npcsDamagedByMe.add(System.identityHashCode(npc));
        }
    }

    public void onNpcDespawned(NpcDespawned event)
    {
        if (event == null || event.getNpc() == null || storage.getCurrentRank() == Rank.NONE) return;
        NPC npc = event.getNpc();
        int key = System.identityHashCode(npc);
        boolean ours = npcsDamagedByMe.remove(key);
        if (!ours) return;
        // Despawn without damage-by-us was probably a walk-away; despawn after our damage AND at 0 hp is a kill.
        if (!npc.isDead()) return;

        // Refresh apostle_zamorak's "kill credit" combat window on every kill.
        enforcement.notifyKillCredit(npc.getName());

        String name = npc.getName();
        if (name == null) return;
        String lower = name.toLowerCase();
        Rank playerRank = storage.getCurrentRank();

        for (TaskDefinition task : TaskRegistry.all())
        {
            TaskTrigger trigger = task.getTrigger();
            if (trigger == null || trigger.getKind() != TaskTrigger.Kind.NPC_KILL) continue;
            if (!task.availableTo(storage.getSelectedGod())) continue;
            if (task.getTier().getTier() > playerRank.getTier()) continue;
            if (storage.isTaskCompleted(task.getId())) continue;
            if (task.isMilestone() && !isMilestoneUnlocked(task)) continue;
            if (lower.contains(trigger.getPattern().toLowerCase()))
            {
                completeTask(task);
            }
        }
    }

    /** Manually mark a task done — used for tasks whose detection isn't wired yet. */
    public boolean completeManual(String taskId)
    {
        TaskDefinition task = TaskRegistry.getById(taskId);
        if (task == null) return false;
        if (!task.availableTo(storage.getSelectedGod())) return false;
        if (task.getTier().getTier() > storage.getCurrentRank().getTier()) return false;
        if (task.isMilestone() && !isMilestoneUnlocked(task))
        {
            announce("VowTaker: milestone locked \u2014 earn " + storage.getCurrentRank().next().getRequiredPoints()
                + " points first.");
            return false;
        }
        return completeTask(task);
    }

    /** Manually unmark a task (e.g. player realises they did something they shouldn't have). Refunds the points. */
    public boolean uncompleteManual(String taskId)
    {
        TaskDefinition task = TaskRegistry.getById(taskId);
        if (task == null) return false;
        if (!storage.uncompleteTask(taskId)) return false;
        storage.subtractPoints(task.getPoints());
        announce("VowTaker: task uncompleted \u2014 " + task.getName() + " (-" + task.getPoints() + " pts)");
        if (panelRefresh != null) panelRefresh.run();
        return true;
    }

    private boolean completeTask(TaskDefinition task)
    {
        if (!storage.completeTask(task.getId()))
        {
            return false;
        }
        int pts = task.getPoints();
        if (pts > 0)
        {
            storage.addPoints(pts);
            announce("VowTaker: task complete \u2014 " + task.getName() + " (+" + pts + " pts)");
        }
        else
        {
            announce("VowTaker: task complete \u2014 " + task.getName());
        }
        applyTaskUnlocks(task);
        if (task.isMilestone())
        {
            promote();
        }
        checkVowsFulfilled();
        if (panelRefresh != null) panelRefresh.run();
        return true;
    }

    /** Rank up, raise the gear ceiling, grant a re-roll token, and queue the Major vow draw. */
    private void promote()
    {
        Rank current = storage.getCurrentRank();
        Rank next = current.next();
        storage.setPendingPromotion(false);
        if (next == current)
        {
            return;
        }

        storage.setCurrentRank(next);
        announce("VowTaker: you have ascended to " + next.fullTitle(storage.getSelectedGod()) + "!");
        announce("VowTaker: gear ceiling raised \u2014 " + gearCeilingLabel(next) + " is now permitted.");
        String flavor = godRankFlavor(storage.getSelectedGod(), next, resolvePlayerName());
        if (flavor != null)
        {
            announce(flavor);
        }
        storage.grantRerollToken();
        announce("VowTaker: +1 re-roll token (" + storage.getRerollTokens() + " held).");
        selection.queueMajorOpen();
        announce("VowTaker: a new major vow awaits \u2014 the picker opens once you are clear of combat.");
    }

    /** Grants any gear-name patterns tied to this task so the rank ceiling stops blocking them. */
    private void applyTaskUnlocks(TaskDefinition task)
    {
        java.util.List<String> patterns = TaskRegistry.unlocksFor(task.getId());
        if (patterns.isEmpty()) return;
        if (storage.addUnlockedGearPatterns(patterns))
        {
            String label = TaskRegistry.unlockLabelFor(task.getId());
            if (label != null)
            {
                announce("VowTaker: " + label + " unlocked \u2014 rank gear ceiling lifted for those items.");
            }
        }
    }

    private static final String[] CHOSEN_COMPLETION_TASKS = new String[]{
        "chosen_zuk", "chosen_sol", "chosen_tob", "chosen_toa", "chosen_cox"
    };

    /** Announces the plugin's endgame completion once all five Chosen-tier tasks are done. */
    private void checkVowsFulfilled()
    {
        if (storage.getCurrentRank() != Rank.CHOSEN) return;
        if (storage.isVowsFulfilledAnnounced()) return;
        for (String id : CHOSEN_COMPLETION_TASKS)
        {
            if (!storage.isTaskCompleted(id)) return;
        }
        storage.setVowsFulfilledAnnounced(true);
        String godName = storage.getSelectedGod() == null ? "your god"
            : Character.toUpperCase(storage.getSelectedGod().name().charAt(0))
            + storage.getSelectedGod().name().substring(1).toLowerCase();
        announce("VowTaker: your vows to " + godName + " are fulfilled. The path of devotion is complete.");
    }

    /** True if this milestone task's rank-up point threshold has been earned. */
    public boolean isMilestoneUnlocked(TaskDefinition task)
    {
        if (task == null || !task.isMilestone()) return true;
        Rank current = storage.getCurrentRank();
        Rank next = current.next();
        if (next == current) return true;
        return storage.getTotalPoints() >= next.getRequiredPoints();
    }

    /** Recovery path for saves stuck with a pending-promotion flag from the old two-step flow. */
    public void checkPromotion()
    {
        if (storage.isPendingPromotion())
        {
            promote();
        }
    }

    private String resolvePlayerName()
    {
        if (client == null || client.getLocalPlayer() == null) return "faithful";
        String n = client.getLocalPlayer().getName();
        return n == null || n.isEmpty() ? "faithful" : n;
    }

    /** God-flavoured chat line delivered on rank-up. Returns null if no matching entry. */
    private static String godRankFlavor(com.vowtaker.model.GodAlignment god, Rank rank, String name)
    {
        if (god == null) return null;
        switch (god)
        {
            case ZAMORAK:    return zamorakFlavor(rank);
            case SARADOMIN:  return saradominFlavor(rank);
            case GUTHIX:     return guthixFlavor(rank, name);
            case ARMADYL:    return armadylFlavor(rank);
            case BANDOS:     return bandosFlavor(rank);
            case ZAROS:      return zarosFlavor(rank);
            default:         return null;
        }
    }

    private static String zamorakFlavor(Rank rank)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Zamorak: A new blade offers itself. So be it.";
            case DEACON:     return "Zamorak: Your first blood is drawn. Keep it flowing.";
            case PRIEST:     return "Zamorak: The world burns brighter with each of your kills.";
            case BISHOP:     return "Zamorak: Ha! You do not disappoint. Bring me more.";
            case ARCHBISHOP: return "Zamorak: You are becoming a fine blade in my hand.";
            case CARDINAL:   return "Zamorak: My general \u2014 the mortals whisper your name in fear.";
            case CHOSEN:     return "Zamorak: You are Chosen. Walk with me among the ruins you make.";
            default:         return null;
        }
    }

    private static String saradominFlavor(Rank rank)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Saradomin: Welcome, faithful. The light guides you.";
            case DEACON:     return "Saradomin: Your devotion glimmers like a candle in the dark.";
            case PRIEST:     return "Saradomin: Your works spread my light across the realm.";
            case BISHOP:     return "Saradomin: A shepherd of the flock \u2014 well done, my child.";
            case ARCHBISHOP: return "Saradomin: Blessed are you, keeper of virtue.";
            case CARDINAL:   return "Saradomin: My most loyal servant. The dawn follows in your wake.";
            case CHOSEN:     return "Saradomin: Rise, Chosen. My light lives through you.";
            default:         return null;
        }
    }

    private static String guthixFlavor(Rank rank, String name)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Guthix: *snores lightly*";
            case DEACON:     return "Guthix: *stirs in his sleep*";
            case PRIEST:     return "Guthix: *murmurs something about balance*";
            case BISHOP:     return "Guthix: *shifts, one eye half-open*";
            case ARCHBISHOP: return "Guthix: *sits up slowly, rubbing his eyes*";
            case CARDINAL:   return "Guthix: *yawns* ...how long have I been asleep?";
            case CHOSEN:     return "Guthix: Good morning, " + name + ".";
            default:         return null;
        }
    }

    private static String armadylFlavor(Rank rank)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Armadyl: Winds of change bear you upward.";
            case DEACON:     return "Armadyl: Your wings unfurl. Fly higher still.";
            case PRIEST:     return "Armadyl: The skies watch you kindly, faithful one.";
            case BISHOP:     return "Armadyl: Your justice rides on the wind.";
            case ARCHBISHOP: return "Armadyl: Guardian of the free-spoken and the free-hearted.";
            case CARDINAL:   return "Armadyl: The skies are wide, and you have made them yours.";
            case CHOSEN:     return "Armadyl: You soar with me, Chosen.";
            default:         return null;
        }
    }

    private static String bandosFlavor(Rank rank)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Bandos: Ha! Fresh meat. Break something.";
            case DEACON:     return "Bandos: Good. More breaking. Less thinking.";
            case PRIEST:     return "Bandos: You crush. I like.";
            case BISHOP:     return "Bandos: You break skulls. You break worlds. Yes.";
            case ARCHBISHOP: return "Bandos: My warlord. Bring me the strong ones.";
            case CARDINAL:   return "Bandos: You are my hammer. Strike.";
            case CHOSEN:     return "Bandos: BIG HIGH WAR GOD RECOGNISES YOU. CRUSH ALL.";
            default:         return null;
        }
    }

    private static String zarosFlavor(Rank rank)
    {
        switch (rank)
        {
            case FOLLOWER:   return "Zaros: You have taken the first step. I have measured it.";
            case DEACON:     return "Zaros: You persist. Interesting.";
            case PRIEST:     return "Zaros: A servant grows in knowledge and shadow.";
            case BISHOP:     return "Zaros: You approach worthy. Do not falter.";
            case ARCHBISHOP: return "Zaros: You reach an understanding few possess.";
            case CARDINAL:   return "Zaros: Fewer still stand where you now stand.";
            case CHOSEN:     return "Zaros: You are Chosen. The empire remembers you.";
            default:         return null;
        }
    }

    private static String gearCeilingLabel(Rank rank)
    {
        switch (rank)
        {
            case NONE:
            case FOLLOWER:
                return "up to Steel / Black";
            case DEACON:
                return "up to Mithril";
            case PRIEST:
                return "up to Adamant";
            case BISHOP:
                return "up to Rune / d'hide";
            case ARCHBISHOP:
                return "up to Dragon / mystic";
            case CARDINAL:
                return "up to Barrows / Bandos";
            case CHOSEN:
                return "all gear";
            default:
                return "\u2014";
        }
    }

    /** Public helper so the plugin can force a promotion check after a manual completion. */
    public void reevaluateRank()
    {
        checkPromotion();
    }

    private void announce(String message)
    {
        if (client != null)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
        }
    }
}
