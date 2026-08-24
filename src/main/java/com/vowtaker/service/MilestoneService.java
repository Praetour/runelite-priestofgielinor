package com.vowtaker.service;

import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;

@Singleton
public class MilestoneService
{
    @Inject
    private Client client;

    @Inject
    private VowStorageService storageService;

    @Inject
    private VowSelectionService selectionService;

    private final Set<String> completedMilestones = ConcurrentHashMap.newKeySet();
    private final Set<String> seenQuestChat = ConcurrentHashMap.newKeySet();
    private int lastProgressCheckTick;

    public void initialize()
    {
        completedMilestones.clear();
        seenQuestChat.clear();
        lastProgressCheckTick = 0;
    }

    public void updateMilestones()
    {
        if (client == null || client.getLocalPlayer() == null)
        {
            return;
        }

        if (client.getGameCycle() - lastProgressCheckTick < 25)
        {
            return;
        }
        lastProgressCheckTick = client.getGameCycle();

        checkEarlyGame();
        checkMidGame();
        checkLateGame();
        checkMetaProgression();
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        if (event == null)
        {
            return;
        }

        int varbitId = event.getVarbitId();
        if (varbitId <= 0)
        {
            return;
        }

        if (varbitId == 6787 || varbitId == 7744 || varbitId == 2007 || varbitId == 2010)
        {
            markMilestone("milestone:varbit:quest_progress");
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        String message = event.getMessage();
        if (message == null)
        {
            return;
        }

        String lower = message.toLowerCase();
        if (lower.contains("waterfall quest") && seenQuestChat.add("quest:waterfall"))
        {
            markMilestone("milestone:early:waterfall_quest");
        }
        else if (lower.contains("obor") && seenQuestChat.add("boss:obor"))
        {
            markMilestone("milestone:early:obor");
        }
        else if (lower.contains("zulrah") && seenQuestChat.add("boss:zulrah"))
        {
            markMilestone("milestone:late:zulrah");
        }
    }

    public void markMilestone(String milestoneId)
    {
        // Records progress only. Vow pickers are driven solely by god selection, promotions and
        // point checkpoints - queueing from skill-level milestones fired before a god was chosen.
        completedMilestones.add(milestoneId);
    }

    public boolean hasCompleted(String milestoneId)
    {
        return completedMilestones.contains(milestoneId);
    }

    public boolean isGodAlignedUnlocked(GodAlignment god)
    {
        return storageService.getSelectedGod() == god || god == GodAlignment.NONE;
    }

    public List<VowDefinition> getEligibleVowsForCurrentGod()
    {
        GodAlignment selectedGod = storageService.getSelectedGod();
        List<VowDefinition> eligible = new ArrayList<>();

        for (VowDefinition vow : VowRegistry.all())
        {
            if (vow.getType() == VowType.PERMANENT && !storageService.isCompleted(vow))
            {
                eligible.add(vow);
            }
            else if (vow.getType() == VowType.GOD && vow.getGodAlignment() == selectedGod && !storageService.isCompleted(vow))
            {
                eligible.add(vow);
            }
            else if (vow.getType() == VowType.RITUAL && !storageService.isCompleted(vow) && storageService.getActiveRitualVow() == null)
            {
                eligible.add(vow);
            }
        }
        return eligible;
    }

    private void checkEarlyGame()
    {
        if (client.getRealSkillLevel(Skill.ATTACK) >= 50 || client.getRealSkillLevel(Skill.STRENGTH) >= 50 || client.getRealSkillLevel(Skill.RANGED) >= 50 || client.getRealSkillLevel(Skill.MAGIC) >= 50)
        {
            markMilestone("milestone:early:50_combat");
        }
        if (client.getRealSkillLevel(Skill.PRAYER) >= 70)
        {
            markMilestone("milestone:mid:70_prayer");
        }
    }

    private void checkMidGame()
    {
        if (client.getRealSkillLevel(Skill.ATTACK) >= 70 || client.getRealSkillLevel(Skill.RANGED) >= 70 || client.getRealSkillLevel(Skill.MAGIC) >= 70 || client.getRealSkillLevel(Skill.STRENGTH) >= 70)
        {
            markMilestone("milestone:mid:midgame_progress");
        }
    }

    private void checkLateGame()
    {
        if (client.getRealSkillLevel(Skill.ATTACK) >= 99 || client.getRealSkillLevel(Skill.STRENGTH) >= 99 || client.getRealSkillLevel(Skill.RANGED) >= 99 || client.getRealSkillLevel(Skill.MAGIC) >= 99)
        {
            markMilestone("milestone:late:99_skill");
        }
    }

    private void checkMetaProgression()
    {
        if (client.getRealSkillLevel(Skill.SLAYER) >= 99)
        {
            markMilestone("milestone:meta:slayer_mastery");
        }
    }
}
