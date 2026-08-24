package com.vowtaker.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.Rank;
import com.vowtaker.model.TaskCategory;
import com.vowtaker.model.TaskDefinition;
import com.vowtaker.model.TaskTrigger;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task pool. Loaded from the built-in seed on plugin startup; a JSON file at
 * {@code %USERPROFILE%\.runelite\vowtaker\tasks.json} may add or override
 * tasks by id (merge, not replace).
 */
public final class TaskRegistry
{
    private static final Map<String, TaskDefinition> TASKS = new LinkedHashMap<>();

    static
    {
        loadDefaults();
    }

    private TaskRegistry() { }

    /** Wipes the registry and re-seeds the built-in defaults. */
    public static synchronized void loadDefaults()
    {
        TASKS.clear();
        seedFollower();
        seedDeacon();
        seedPriest();
        seedBishop();
        seedArchbishop();
        seedCardinal();
        seedChosen();
        seedMilestones();
    }

    /**
     * Merges tasks from a JSON file into the registry. Existing ids are
     * overridden; new ids are added.
     */
    public static synchronized int loadOverrides(Path file)
    {
        if (file == null || !file.toFile().exists())
        {
            return 0;
        }

        int applied = 0;
        try (FileReader reader = new FileReader(file.toFile()))
        {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject())
            {
                return 0;
            }
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("tasks") || !obj.get("tasks").isJsonArray())
            {
                return 0;
            }
            JsonArray arr = obj.getAsJsonArray("tasks");
            for (JsonElement el : arr)
            {
                if (!el.isJsonObject()) continue;
                TaskDefinition parsed = parseTask(el.getAsJsonObject());
                if (parsed != null)
                {
                    TASKS.put(parsed.getId(), parsed);
                    applied++;
                }
            }
        }
        catch (IOException | RuntimeException ignored)
        {
            // caller logs
        }
        return applied;
    }

    private static TaskDefinition parseTask(JsonObject j)
    {
        try
        {
            String id = j.get("id").getAsString();
            String name = j.get("name").getAsString();
            String desc = j.has("description") ? j.get("description").getAsString() : "";
            Rank tier = Rank.valueOf(j.get("tier").getAsString().toUpperCase());
            TaskCategory cat = j.has("category")
                ? TaskCategory.valueOf(j.get("category").getAsString().toUpperCase())
                : TaskCategory.SKILLING;
            GodAlignment god = j.has("godFlavor")
                ? GodAlignment.valueOf(j.get("godFlavor").getAsString().toUpperCase())
                : GodAlignment.NONE;
            int points = j.has("points") ? j.get("points").getAsInt() : 1;
            boolean milestone = j.has("milestone") && j.get("milestone").getAsBoolean();

            TaskTrigger trigger = TaskTrigger.manual();
            if (j.has("trigger") && j.get("trigger").isJsonObject())
            {
                JsonObject t = j.getAsJsonObject("trigger");
                TaskTrigger.Kind kind = TaskTrigger.Kind.valueOf(t.get("kind").getAsString().toUpperCase());
                String pattern = t.has("pattern") ? t.get("pattern").getAsString() : "";
                int amount = t.has("amount") ? t.get("amount").getAsInt() : 1;
                trigger = new TaskTrigger(kind, pattern, amount);
            }

            List<String> gated = new ArrayList<>();
            if (j.has("gatedTargets") && j.get("gatedTargets").isJsonArray())
            {
                for (JsonElement e : j.getAsJsonArray("gatedTargets"))
                {
                    gated.add(e.getAsString());
                }
            }

            return new TaskDefinition(id, name, desc, tier, cat, god, points, milestone, trigger, gated);
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    public static List<TaskDefinition> all()
    {
        return Collections.unmodifiableList(new ArrayList<>(TASKS.values()));
    }

    public static TaskDefinition getById(String id)
    {
        return id == null ? null : TASKS.get(id);
    }

    /** Gear-name patterns unlocked when the given task is completed. Empty if none. */
    public static List<String> unlocksFor(String taskId)
    {
        List<String> out = TASK_GEAR_UNLOCKS.get(taskId);
        return out == null ? Collections.emptyList() : out;
    }

    /** Short label describing what a task unlocks (for chat announcement). Null if none. */
    public static String unlockLabelFor(String taskId)
    {
        return TASK_GEAR_UNLOCK_LABELS.get(taskId);
    }

    private static final Map<String, String> TASK_GEAR_UNLOCK_LABELS = new LinkedHashMap<>();
    private static final Map<String, List<String>> TASK_GEAR_UNLOCKS = new LinkedHashMap<>();
    static
    {
        // Cardinal & Chosen raid tasks unlock their raid's uniques past the rank ceiling.
        registerUnlock("toa_normal", "Tombs of Amascut rewards",
            "masori", "tumeken's shadow", "osmumten's fang", "elidinis' ward", "lightbearer", "menaphite");
        registerUnlock("chosen_toa", "Tombs of Amascut rewards",
            "masori", "tumeken's shadow", "osmumten's fang", "elidinis' ward", "lightbearer", "menaphite");
        registerUnlock("cox_normal", "Chambers of Xeric rewards",
            "twisted bow", "tbow", "kodai", "ancestral", "dragon claws", "dragon hunter crossbow",
            "elder maul", "dinh's", "twisted buckler", "dragon warhammer");
        registerUnlock("chosen_cox", "Chambers of Xeric rewards",
            "twisted bow", "tbow", "kodai", "ancestral", "dragon claws", "dragon hunter crossbow",
            "elder maul", "dinh's", "twisted buckler", "dragon warhammer");
        registerUnlock("tob_normal", "Theatre of Blood rewards",
            "scythe of vitur", "ghrazi rapier", "sanguinesti staff", "justiciar", "avernic defender");
        registerUnlock("chosen_tob", "Theatre of Blood rewards",
            "scythe of vitur", "ghrazi rapier", "sanguinesti staff", "justiciar", "avernic defender");
        registerUnlock("chosen_zuk", "Inferno rewards",
            "infernal cape", "tokhaar-kal", "tzhaar-ket-em", "obsidian cape");
        registerUnlock("chosen_sol", "Colosseum rewards",
            "dizana's quiver", "sunfire fanatic", "echo boots", "tonalztics");
        registerUnlock("nex_kill", "Nex rewards",
            "torva", "nihil horn", "zaryte crossbow", "ancient hilt");
        registerUnlock("solo_vorkath_100", "Vorkath rewards",
            "draconic visage", "vorkath's head", "dragonbone necklace");
        registerUnlock("solo_zulrah_100", "Zulrah rewards",
            "blowpipe", "toxic staff", "serpentine helm", "tanzanite fang", "magic fang", "mutagen");
        registerUnlock("kill_leviathan", "Leviathan rewards",
            "bellator ring", "virtus");
        registerUnlock("kill_whisperer", "Whisperer rewards",
            "magus ring", "virtus");
        registerUnlock("kill_vardorvis", "Vardorvis rewards",
            "ultor ring", "virtus", "soulreaper axe");
        registerUnlock("kill_duke", "Duke Sucellus rewards",
            "venator ring", "virtus", "eye of the duke");
    }

    private static void registerUnlock(String taskId, String label, String... patterns)
    {
        TASK_GEAR_UNLOCK_LABELS.put(taskId, label);
        TASK_GEAR_UNLOCKS.put(taskId, Collections.unmodifiableList(Arrays.asList(patterns)));
    }

    public static List<TaskDefinition> forTier(Rank tier, GodAlignment god)
    {
        List<TaskDefinition> out = new ArrayList<>();
        for (TaskDefinition t : TASKS.values())
        {
            if (t.getTier() == tier && t.availableTo(god))
            {
                out.add(t);
            }
        }
        return out;
    }

    public static TaskDefinition milestoneFor(Rank tier)
    {
        for (TaskDefinition t : TASKS.values())
        {
            if (t.isMilestone() && t.getTier() == tier)
            {
                return t;
            }
        }
        return null;
    }

    // ================== FOLLOWER ==================
    private static void seedFollower()
    {
        add(sharedChat("bake_bread", "Bake bread", "Cook a loaf of bread on any fire or range.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2, "(?i)you manage to bake"));
        add(sharedChat("cook_shrimp", "Cook a shrimp", "Cook any shrimp.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2, "(?i)you successfully cook (a )?shrimps?"));
        add(sharedChat("catch_shrimp", "Catch a shrimp", "Net a raw shrimp at any fishing spot.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2, "(?i)you catch (a|some) shrimps?"));
        add(sharedChat("catch_anchovies", "Catch anchovies", "Net raw anchovies.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2, "(?i)you catch (some )?anchovies"));
        add(sharedChat("cut_oak", "Cut an oak log", "Chop an oak tree.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2, "(?i)you get (an|some) oak logs?"));
        add(sharedChat("cut_willow", "Cut a willow log", "Chop a willow tree.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3, "(?i)you get (some )?willow logs?"));
        add(sharedChat("mine_iron", "Mine iron ore", "Mine iron ore from any rock.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3, "(?i)you (manage to )?mine some iron"));
        add(sharedChat("mine_coal", "Mine coal", "Mine coal ore from any rock.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3, "(?i)you (manage to )?mine some coal"));
        add(sharedChat("fletch_arrows", "Fletch arrows", "Fletch a batch of arrows.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3, "(?i)you attach"));
        add(sharedManual("craft_leather", "Craft leather armour", "Create any leather armour piece.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3));
        add(sharedManual("plant_potato", "Plant a potato seed", "Plant potatoes in an allotment patch.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 3));
        add(sharedManual("craft_bronze_bar", "Smelt a bronze bar", "Smelt a bronze bar at any furnace.",
            Rank.FOLLOWER, TaskCategory.SKILLING, 2));

        add(gatedShared("kill_chicken", "Slay a chicken", "Defeat any chicken.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 2, TaskTrigger.npcKill("Chicken")));
        add(gatedShared("kill_goblin", "Slay a goblin", "Defeat any goblin.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 2, TaskTrigger.npcKill("Goblin")));
        add(gatedShared("kill_cow", "Slay a cow", "Defeat any cow.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 2, TaskTrigger.npcKill("Cow")));
        add(gatedShared("kill_imp", "Slay an imp", "Defeat any imp.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 2, TaskTrigger.npcKill("Imp")));
        add(gatedShared("kill_hill_giant", "Slay a hill giant", "Defeat a hill giant.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 4, TaskTrigger.npcKill("Hill Giant")));
        add(gatedShared("kill_moss_giant", "Slay a moss giant", "Defeat a moss giant.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 5, TaskTrigger.npcKill("Moss Giant")));
        add(gatedShared("kill_zombie", "Slay an undead", "Defeat any zombie or skeleton.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 3, TaskTrigger.npcKill("Zombie")));
        add(sharedManual("loot_giant_key", "Claim a giant key", "Loot a giant key from a hill giant — the door to Obor's prison.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 4));
        add(sharedManual("loot_mossy_key", "Claim a mossy key", "Loot a mossy key from a moss giant — the door to Bryophyta's grove.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 5));
        add(gatedShared("kill_bryophyta", "Slay Bryophyta", "Defeat Bryophyta beneath Varrock. Requires a mossy key.",
            Rank.FOLLOWER, TaskCategory.COMBAT, 12, TaskTrigger.npcKill("Bryophyta"), "Bryophyta"));

        add(sharedChat("q_cooks_assistant", "Complete Cook's Assistant", "Finish Cook's Assistant.",
            Rank.FOLLOWER, TaskCategory.QUEST, 8, "(?i)quest complete!.*cook"));
        add(sharedChat("q_sheep_shearer", "Complete Sheep Shearer", "Finish Sheep Shearer.",
            Rank.FOLLOWER, TaskCategory.QUEST, 6, "(?i)quest complete!.*sheep shearer"));
        add(sharedChat("q_romeo_juliet", "Complete Romeo & Juliet", "Finish Romeo & Juliet.",
            Rank.FOLLOWER, TaskCategory.QUEST, 6, "(?i)quest complete!.*romeo"));
        add(sharedManual("explore_lumbridge", "Tour Lumbridge Castle", "Visit every level of Lumbridge Castle.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, 4));
        add(sharedManual("explore_varrock", "Tour Varrock", "Visit the palace, museum, and both banks.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, 4));

        add(godTask("saradomin_first_prayer", "First Prayer", "Restore prayer at a Saradomin altar.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.SARADOMIN, 3));
        add(godTask("saradomin_donate_coins", "Give alms", "Drop 100 coins near a beggar.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.SARADOMIN, 3));

        add(godTask("zamorak_first_blood", "First Blood", "Deal a killing blow with any weapon.",
            Rank.FOLLOWER, TaskCategory.COMBAT, GodAlignment.ZAMORAK, 3));
        add(godTask("zamorak_wilderness_walk", "Walk the Wilderness", "Enter the Wilderness for the first time.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.ZAMORAK, 3));

        add(godTask("guthix_grow_sapling", "Tend the Grove", "Plant a sapling in any patch.",
            Rank.FOLLOWER, TaskCategory.SKILLING, GodAlignment.GUTHIX, 3));
        add(godTask("guthix_druids_circle", "Druid's Circle", "Visit the Taverley druid circle.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.GUTHIX, 3));

        add(godTask("armadyl_first_flight", "First Flight", "Use any spirit tree or gnome glider.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.ARMADYL, 3));
        add(godTask("armadyl_eagles_peak", "Climb Eagles' Peak", "Reach the summit of Eagles' Peak.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.ARMADYL, 3));

        add(godTask("zaros_shadow_step", "Shadow Step", "Enter the Wilderness.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.ZAROS, 3));
        add(godTask("zaros_ancient_altar", "Ancient Altar", "Kneel at an ancient magicks altar.",
            Rank.FOLLOWER, TaskCategory.EXPLORATION, GodAlignment.ZAROS, 3));

        add(godTask("bandos_first_kill", "War Cry", "Kill any Bandos follower or ogre.",
            Rank.FOLLOWER, TaskCategory.COMBAT, GodAlignment.BANDOS, 3));
        add(godTask("bandos_break_bones", "Break Bones", "Bury 50 bones.",
            Rank.FOLLOWER, TaskCategory.SKILLING, GodAlignment.BANDOS, 3));
    }

    // ================== DEACON ==================
    // First promotion tier. Skilling levels 30-40, F2P mini-bosses, easy P2P mobs.
    private static void seedDeacon()
    {
        add(sharedChat("mine_mithril", "Mine mithril ore", "Mine mithril ore.",
            Rank.DEACON, TaskCategory.SKILLING, 4, "(?i)you (manage to )?mine some mithril"));
        add(sharedChat("cut_yew", "Cut a yew log", "Chop a yew tree.",
            Rank.DEACON, TaskCategory.SKILLING, 4, "(?i)you get (some )?yew logs?"));
        add(sharedChat("catch_lobster", "Catch a lobster", "Cage a raw lobster.",
            Rank.DEACON, TaskCategory.SKILLING, 4, "(?i)you catch a lobster"));
        add(sharedChat("cook_lobster", "Cook a lobster", "Cook a raw lobster.",
            Rank.DEACON, TaskCategory.SKILLING, 4, "(?i)you (manage to )?cook a lobster"));
        add(sharedManual("high_alch", "Cast High Alchemy", "Cast High Alchemy on any item.",
            Rank.DEACON, TaskCategory.SKILLING, 4));
        add(sharedManual("craft_air_runes", "Craft air runes", "Craft a batch of air runes at the Air Altar.",
            Rank.DEACON, TaskCategory.SKILLING, 4));
        add(sharedManual("plant_pineapple", "Plant a pineapple", "Grow a pineapple plant to harvest.",
            Rank.DEACON, TaskCategory.SKILLING, 4));

        add(sharedManual("pest_control_win", "Win Pest Control", "Complete a Pest Control game.",
            Rank.DEACON, TaskCategory.MINIGAME, 4));
        add(sharedManual("mta_win", "Mage Training Arena", "Earn points in every MTA room.",
            Rank.DEACON, TaskCategory.MINIGAME, 5));

        add(godTask("saradomin_temple_trekking", "Temple Trek", "Complete a Temple Trekking route.",
            Rank.DEACON, TaskCategory.MINIGAME, GodAlignment.SARADOMIN, 5));
        add(godTask("zamorak_castle_wars", "Castle Wars", "Win a Castle Wars game as Zamorak team.",
            Rank.DEACON, TaskCategory.MINIGAME, GodAlignment.ZAMORAK, 5));
        add(godTask("guthix_druidic_ritual", "Druidic Ritual", "Complete Druidic Ritual quest.",
            Rank.DEACON, TaskCategory.QUEST, GodAlignment.GUTHIX, 5));
        add(godTask("armadyl_first_flight_glider", "The Gnome Sky", "Use every gnome glider destination.",
            Rank.DEACON, TaskCategory.EXPLORATION, GodAlignment.ARMADYL, 5));
        add(godTask("zaros_shadow_step_wild", "Shadow of the North", "Enter level 30+ Wilderness and return alive.",
            Rank.DEACON, TaskCategory.EXPLORATION, GodAlignment.ZAROS, 5));
        add(godTask("bandos_defeat_ogre", "Break the Ogres", "Defeat 20 ogres in Feldip Hills.",
            Rank.DEACON, TaskCategory.COMBAT, GodAlignment.BANDOS, 5));
    }

    // ================== PRIEST ==================
    // Skilling 40-50, mid P2P combat, easy P2P quests.
    private static void seedPriest()
    {
        add(sharedChat("mine_adamantite", "Mine adamantite ore", "Mine adamantite ore.",
            Rank.PRIEST, TaskCategory.SKILLING, 6, "(?i)you (manage to )?mine some adamantite"));
        add(sharedChat("cut_magic", "Cut a magic log", "Chop a magic tree.",
            Rank.PRIEST, TaskCategory.SKILLING, 7, "(?i)you get (some )?magic logs?"));
        add(sharedChat("catch_swordfish", "Catch a swordfish", "Harpoon a swordfish.",
            Rank.PRIEST, TaskCategory.SKILLING, 5, "(?i)you catch a swordfish"));
        add(sharedManual("skill_40_any", "Reach 40 in any skill", "Achieve level 40 in any skill.",
            Rank.PRIEST, TaskCategory.SKILLING, 6));

        add(gatedShared("kill_scurrius_pre", "Face the Rat King", "Enter the Scurrius arena and survive an attempt (win optional).",
            Rank.PRIEST, TaskCategory.COMBAT, 6, TaskTrigger.manual(), "Scurrius"));
        add(gatedShared("kill_blue_dragon", "Slay a blue dragon", "Defeat a blue dragon.",
            Rank.PRIEST, TaskCategory.COMBAT, 8, TaskTrigger.manual(), "Blue dragon"));
        add(gatedShared("kill_greater_demon", "Slay a greater demon", "Defeat a greater demon.",
            Rank.PRIEST, TaskCategory.COMBAT, 6, TaskTrigger.manual(), "Greater demon"));
        add(gatedShared("kill_lesser_demon", "Slay a lesser demon", "Defeat a lesser demon.",
            Rank.PRIEST, TaskCategory.COMBAT, 5, TaskTrigger.manual(), "Lesser demon"));
        add(gatedShared("kill_dagannoth", "Slay a dagannoth", "Defeat a dagannoth.",
            Rank.PRIEST, TaskCategory.COMBAT, 6, TaskTrigger.manual(), "Dagannoth"));
        add(gatedShared("kill_kalphite_soldier", "Slay a Kalphite soldier", "Defeat a Kalphite soldier.",
            Rank.PRIEST, TaskCategory.COMBAT, 5, TaskTrigger.manual(), "Kalphite Soldier"));
        add(gatedShared("kill_ankou", "Slay an ankou", "Defeat an ankou.",
            Rank.PRIEST, TaskCategory.COMBAT, 5, TaskTrigger.manual(), "Ankou"));

        add(sharedChat("tempoross_win", "Weather the Storm", "Complete a Tempoross game.",
            Rank.PRIEST, TaskCategory.MINIGAME, 8, "(?i)subdued in:"));
        add(sharedChat("wintertodt_win", "Kindle the Frozen", "Subdue the Wintertodt.",
            Rank.PRIEST, TaskCategory.MINIGAME, 8, "(?i)your subdued count is:"));
        add(sharedManual("nmz_survive", "Survive Nightmare Zone", "Complete a 60-minute NMZ session.",
            Rank.PRIEST, TaskCategory.MINIGAME, 6));

        add(sharedChat("q_priest_in_peril", "Complete Priest in Peril", "Finish the quest.",
            Rank.PRIEST, TaskCategory.QUEST, 10, "(?i)quest complete!.*priest in peril"));
        add(sharedChat("q_dragon_slayer", "Complete Dragon Slayer I", "Finish Dragon Slayer I.",
            Rank.PRIEST, TaskCategory.QUEST, 12, "(?i)quest complete!.*dragon slayer( i)?$"));
        add(sharedChat("q_underground_pass", "Complete Underground Pass", "Finish Underground Pass.",
            Rank.PRIEST, TaskCategory.QUEST, 10, "(?i)quest complete!.*underground pass"));

        add(godTask("saradomin_falador_shield", "Falador Shield", "Earn any Falador diary reward.",
            Rank.PRIEST, TaskCategory.EXPLORATION, GodAlignment.SARADOMIN, 6));
        add(godTask("zamorak_kill_saradomin_wizard", "Purge Saradomin", "Defeat a Saradomin wizard.",
            Rank.PRIEST, TaskCategory.COMBAT, GodAlignment.ZAMORAK, 5));
        add(godTask("guthix_woodcutting_50", "Reach 50 Woodcutting", "Achieve level 50 Woodcutting.",
            Rank.PRIEST, TaskCategory.SKILLING, GodAlignment.GUTHIX, 6));
        add(godTask("armadyl_kill_aviansie", "Purge the Skies", "Defeat any Aviansie.",
            Rank.PRIEST, TaskCategory.COMBAT, GodAlignment.ARMADYL, 6));
        add(godTask("armadyl_ranged_50", "Reach 50 Ranged", "Achieve level 50 Ranged.",
            Rank.PRIEST, TaskCategory.SKILLING, GodAlignment.ARMADYL, 6));
        add(godTask("zaros_desert_treasure", "Uncover Ancient Magicks", "Complete Desert Treasure I.",
            Rank.PRIEST, TaskCategory.QUEST, GodAlignment.ZAROS, 8));
        add(godTask("zaros_magic_50", "Reach 50 Magic", "Achieve level 50 Magic.",
            Rank.PRIEST, TaskCategory.SKILLING, GodAlignment.ZAROS, 6));
        add(godTask("bandos_strength_50", "Reach 50 Strength", "Achieve level 50 Strength.",
            Rank.PRIEST, TaskCategory.SKILLING, GodAlignment.BANDOS, 6));
    }

    // ================== BISHOP ==================
    // Rune tier, level 60-70 skilling, Barrows prep, mid P2P quests.
    private static void seedBishop()
    {
        add(sharedChat("mine_runite", "Mine runite ore", "Mine runite ore.",
            Rank.BISHOP, TaskCategory.SKILLING, 10, "(?i)you (manage to )?mine some runite"));
        add(sharedManual("catch_shark", "Catch a shark", "Harpoon a raw shark.",
            Rank.BISHOP, TaskCategory.SKILLING, 8));
        add(sharedManual("catch_karambwan", "Catch a karambwan", "Catch a raw karambwan.",
            Rank.BISHOP, TaskCategory.SKILLING, 8));
        add(sharedManual("craft_rune_armour", "Craft rune armour", "Smith a full set of rune armour.",
            Rank.BISHOP, TaskCategory.SKILLING, 12));
        add(sharedManual("plant_palm", "Grow a palm tree", "Grow a palm tree to fruition.",
            Rank.BISHOP, TaskCategory.SKILLING, 8));
        add(sharedManual("prayer_60", "Reach 60 Prayer", "Achieve level 60 Prayer.",
            Rank.BISHOP, TaskCategory.SKILLING, 10));
        add(sharedManual("hunter_60", "Reach 60 Hunter", "Achieve level 60 Hunter.",
            Rank.BISHOP, TaskCategory.SKILLING, 8));
        add(sharedManual("agility_60", "Reach 60 Agility", "Achieve level 60 Agility.",
            Rank.BISHOP, TaskCategory.SKILLING, 8));

        add(gatedShared("kill_kraken", "Slay the Kraken", "Defeat the Cave Kraken boss.",
            Rank.BISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "Kraken", "Cave kraken"));
        add(gatedShared("kill_cerberus", "Slay Cerberus", "Defeat Cerberus.",
            Rank.BISHOP, TaskCategory.COMBAT, 12, TaskTrigger.manual(), "Cerberus"));
        add(gatedShared("kill_kbd", "Slay the King Black Dragon", "Defeat the KBD.",
            Rank.BISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "King Black Dragon"));
        add(gatedShared("kill_kq", "Slay the Kalphite Queen", "Defeat the Kalphite Queen.",
            Rank.BISHOP, TaskCategory.COMBAT, 12, TaskTrigger.manual(), "Kalphite Queen"));

        // Barrows prep: individual brothers as stepping stones to the m3 milestone.
        add(gatedShared("kill_barrows_bro", "Slay a Barrows brother", "Defeat any single Barrows brother.",
            Rank.BISHOP, TaskCategory.COMBAT, 8, TaskTrigger.manual(),
            "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
            "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"));
        add(sharedManual("barrows_full_set", "Assemble a Barrows set", "Collect a complete set of any Barrows brother's equipment.",
            Rank.BISHOP, TaskCategory.COMBAT, 15));

        add(sharedChat("q_monkey_madness", "Complete Monkey Madness I", "Finish MM1.",
            Rank.BISHOP, TaskCategory.QUEST, 15, "(?i)quest complete!.*monkey madness( i)?$"));
        add(sharedChat("q_recipe_for_disaster", "Complete Recipe for Disaster", "Finish RFD.",
            Rank.BISHOP, TaskCategory.QUEST, 15, "(?i)quest complete!.*recipe for disaster"));

        add(godTask("saradomin_wolves_grimtail", "The Kinshra", "Defeat 25 Kinshra black knights.",
            Rank.BISHOP, TaskCategory.COMBAT, GodAlignment.SARADOMIN, 10));
        add(godTask("zamorak_chaos_altar", "Chaos Altar", "Use a Chaos Altar to pray.",
            Rank.BISHOP, TaskCategory.EXPLORATION, GodAlignment.ZAMORAK, 6));
        add(godTask("guthix_guardians_of_the_rift", "Guardians of the Rift", "Complete a GoTR game.",
            Rank.BISHOP, TaskCategory.MINIGAME, GodAlignment.GUTHIX, 8));
        add(godTask("armadyl_ranged_70", "Reach 70 Ranged", "Achieve level 70 Ranged.",
            Rank.BISHOP, TaskCategory.SKILLING, GodAlignment.ARMADYL, 10));
        add(godTask("zaros_magic_70", "Reach 70 Magic", "Achieve level 70 Magic.",
            Rank.BISHOP, TaskCategory.SKILLING, GodAlignment.ZAROS, 10));
        add(godTask("bandos_strength_70", "Reach 70 Strength", "Achieve level 70 Strength.",
            Rank.BISHOP, TaskCategory.SKILLING, GodAlignment.BANDOS, 10));
    }

    // ================== ARCHBISHOP ==================
    // Level 80s, mid-endgame bosses, raid entry.
    private static void seedArchbishop()
    {
        add(sharedManual("skill_80_any", "Reach 80 in any skill", "Achieve level 80 in any single skill.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 15));
        add(sharedManual("skill_85_slayer", "Reach 85 Slayer", "Unlock the majority of the Slayer roster.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 15));

        add(gatedShared("kill_vorkath", "Slay Vorkath", "Defeat Vorkath.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 15, TaskTrigger.manual(), "Vorkath"));
        add(gatedShared("kill_hydra", "Slay the Alchemical Hydra", "Defeat the Alchemical Hydra.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 15, TaskTrigger.manual(), "Alchemical Hydra"));
        add(gatedShared("kill_dagannoth_kings", "Slay a Dagannoth King", "Defeat any of Rex, Prime, or Supreme.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 12, TaskTrigger.manual(),
            "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
        add(gatedShared("kill_thermy", "Slay Thermonuclear Smoke Devil", "Defeat the Thermy boss.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "Thermonuclear smoke devil"));

        // Fire cape prep — practice runs before the m4 Trial of Flame milestone.
        add(sharedChat("fight_caves_kc", "Fight Caves veteran", "Complete a Fight Caves run (no cape required).",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 15, "(?i)^tzhaar-ket-om"));

        add(godTask("saradomin_zilyana", "Slay Commander Zilyana", "Defeat Commander Zilyana.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, GodAlignment.SARADOMIN, 15));
        add(godTask("zamorak_kril", "Slay K'ril Tsutsaroth", "Defeat K'ril Tsutsaroth.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, GodAlignment.ZAMORAK, 15));
        add(godTask("guthix_farming_80", "Reach 80 Farming", "Achieve level 80 Farming.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, GodAlignment.GUTHIX, 12));
        add(godTask("armadyl_kree", "Slay Kree'arra", "Defeat Kree'arra.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, GodAlignment.ARMADYL, 15));
        add(godTask("armadyl_ranged_80", "Reach 80 Ranged", "Achieve level 80 Ranged.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, GodAlignment.ARMADYL, 12));
        add(godTask("zaros_nightmare", "Slay The Nightmare", "Defeat The Nightmare.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, GodAlignment.ZAROS, 15));
        add(godTask("zaros_magic_80", "Reach 80 Magic", "Achieve level 80 Magic.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, GodAlignment.ZAROS, 12));
        add(godTask("bandos_bandos_avatar", "Slay Bandos's Avatar", "Break Bandos's avatar in Dorgesh-Kaan.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, GodAlignment.BANDOS, 10));
        add(godTask("bandos_strength_80", "Reach 80 Strength", "Achieve level 80 Strength.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, GodAlignment.BANDOS, 12));

        // Extra Archbishop content — mid-endgame bosses, level-80 skills, achievement diaries.
        add(sharedManual("skill_90_slayer", "Reach 90 Slayer", "Achieve level 90 Slayer.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 20));
        add(sharedManual("skill_80_prayer", "Reach 80 Prayer", "Achieve level 80 Prayer.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 15));
        add(sharedManual("skill_80_herblore", "Reach 80 Herblore", "Achieve level 80 Herblore.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 12));
        add(sharedManual("skill_80_construction", "Reach 80 Construction", "Achieve level 80 Construction.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 12));
        add(sharedManual("skill_80_farming", "Reach 80 Farming", "Achieve level 80 Farming.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 12));
        add(sharedManual("diary_karamja_elite", "Karamja Elite diary", "Complete every Karamja Elite diary task.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 15));
        add(sharedManual("diary_any_hard", "Any Hard diary", "Complete any Hard achievement diary.",
            Rank.ARCHBISHOP, TaskCategory.SKILLING, 10));
        add(gatedShared("kill_sarachnis", "Slay Sarachnis", "Defeat Sarachnis.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "Sarachnis"));
        add(gatedShared("kill_gg", "Slay the Grotesque Guardians", "Defeat Dusk and Dawn atop the Slayer Tower.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 12, TaskTrigger.manual(),
            "Dusk", "Dawn"));
        add(gatedShared("kill_skotizo", "Slay Skotizo", "Defeat Skotizo in the Catacombs of Kourend.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "Skotizo"));
        add(gatedShared("kill_zalcano", "Best Zalcano", "Contribute to a successful Zalcano fight.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 10, TaskTrigger.manual(), "Zalcano"));
        add(gatedShared("kill_gauntlet", "Complete the Gauntlet", "Finish a standard Gauntlet run.",
            Rank.ARCHBISHOP, TaskCategory.COMBAT, 15, TaskTrigger.manual(), "The Crystalline Hunllef"));
        add(sharedChat("q_mep2", "Complete Mourning's End Part II", "Finish MEP2.",
            Rank.ARCHBISHOP, TaskCategory.QUEST, 12, "(?i)quest complete!.*mourning's end part ii"));
    }

    // ================== CARDINAL ==================
    // Level 90s, endgame bosses, Grandmaster quests, 99s.
    private static void seedCardinal()
    {
        add(sharedManual("skill_99_any", "Reach 99 in any skill", "Cape any skill.",
            Rank.CARDINAL, TaskCategory.SKILLING, 30));
        add(sharedManual("total_2000", "Reach 2000 total level", "Achieve 2000 total level.",
            Rank.CARDINAL, TaskCategory.SKILLING, 25));
        add(sharedManual("collection_log_500", "Collection log: 500 slots", "Fill 500 collection log slots.",
            Rank.CARDINAL, TaskCategory.SKILLING, 25));
        add(sharedManual("clue_master", "Complete a master clue", "Solve a master clue scroll.",
            Rank.CARDINAL, TaskCategory.SKILLING, 15));

        add(gatedShared("solo_graardor", "Solo General Graardor", "Defeat General Graardor without help.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "General Graardor"));
        add(gatedShared("nex_kill", "Slay Nex", "Defeat Nex.",
            Rank.CARDINAL, TaskCategory.COMBAT, 30, TaskTrigger.manual(), "Nex"));
        add(gatedShared("solo_zulrah_100", "Zulrah 100 KC", "Reach 100 Zulrah kills.",
            Rank.CARDINAL, TaskCategory.COMBAT, 20, TaskTrigger.manual()));
        add(gatedShared("solo_vorkath_100", "Vorkath 100 KC", "Reach 100 Vorkath kills.",
            Rank.CARDINAL, TaskCategory.COMBAT, 20, TaskTrigger.manual()));
        add(gatedShared("kill_sire", "Slay the Abyssal Sire", "Defeat the Abyssal Sire.",
            Rank.CARDINAL, TaskCategory.COMBAT, 15, TaskTrigger.manual(), "Abyssal Sire"));
        add(gatedShared("kill_corp", "Slay the Corporeal Beast", "Defeat the Corporeal Beast.",
            Rank.CARDINAL, TaskCategory.COMBAT, 20, TaskTrigger.manual(), "Corporeal Beast"));
        add(gatedShared("kill_muspah", "Slay the Phantom Muspah", "Defeat the Phantom Muspah.",
            Rank.CARDINAL, TaskCategory.COMBAT, 15, TaskTrigger.manual(), "Phantom Muspah"));
        add(gatedShared("kill_duke", "Slay Duke Sucellus", "Defeat Duke Sucellus.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "Duke Sucellus"));
        add(gatedShared("kill_vardorvis", "Slay Vardorvis", "Defeat Vardorvis.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "Vardorvis"));
        add(gatedShared("kill_leviathan", "Slay the Leviathan", "Defeat the Leviathan.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "The Leviathan"));
        add(gatedShared("kill_whisperer", "Slay the Whisperer", "Defeat the Whisperer.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "The Whisperer"));

        add(sharedChat("q_sote", "Complete Song of the Elves", "Finish SotE.",
            Rank.CARDINAL, TaskCategory.QUEST, 20, "(?i)quest complete!.*song of the elves"));
        add(sharedChat("q_ds2", "Complete Dragon Slayer II", "Finish DS2.",
            Rank.CARDINAL, TaskCategory.QUEST, 20, "(?i)quest complete!.*dragon slayer ii"));
        add(sharedChat("q_mm2", "Complete Monkey Madness II", "Finish MM2.",
            Rank.CARDINAL, TaskCategory.QUEST, 20, "(?i)quest complete!.*monkey madness ii"));
        add(sharedChat("q_wgs", "Complete While Guthix Sleeps", "Finish WGS.",
            Rank.CARDINAL, TaskCategory.QUEST, 20, "(?i)quest complete!.*while guthix sleeps"));

        add(godTask("saradomin_zilyana_solo", "Solo Zilyana", "Defeat Commander Zilyana without help.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.SARADOMIN, 20));
        add(godTask("saradomin_saradomin_sword", "Saradomin Sword", "Obtain a Saradomin sword or hilt.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.SARADOMIN, 20));
        add(godTask("zamorak_kril_solo", "Solo K'ril", "Defeat K'ril Tsutsaroth without help.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.ZAMORAK, 20));
        add(godTask("zamorak_zamorakian_spear", "Zamorakian Spear", "Obtain a Zamorakian spear or hilt.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.ZAMORAK, 20));
        add(godTask("guthix_farming_99", "Cape Farming", "Achieve 99 Farming.",
            Rank.CARDINAL, TaskCategory.SKILLING, GodAlignment.GUTHIX, 25));
        add(godTask("armadyl_kree_solo", "Solo Kree'arra", "Defeat Kree'arra without help.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.ARMADYL, 20));
        add(godTask("armadyl_ranged_99", "Cape Ranged", "Achieve 99 Ranged.",
            Rank.CARDINAL, TaskCategory.SKILLING, GodAlignment.ARMADYL, 25));
        add(godTask("zaros_nightmare_solo", "Solo Nightmare", "Defeat The Nightmare solo.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.ZAROS, 25));
        add(godTask("zaros_magic_99", "Cape Magic", "Achieve 99 Magic.",
            Rank.CARDINAL, TaskCategory.SKILLING, GodAlignment.ZAROS, 25));
        add(godTask("bandos_graardor_solo", "Solo Graardor", "Defeat General Graardor without help.",
            Rank.CARDINAL, TaskCategory.COMBAT, GodAlignment.BANDOS, 20));
        add(godTask("bandos_strength_99", "Cape Strength", "Achieve 99 Strength.",
            Rank.CARDINAL, TaskCategory.SKILLING, GodAlignment.BANDOS, 25));

        // Extra Cardinal content — normal-difficulty raids, level-90 combat, elite bossing.
        add(gatedShared("cox_normal", "Chambers of Xeric", "Complete a Chambers of Xeric raid (normal mode).",
            Rank.CARDINAL, TaskCategory.COMBAT, 30, TaskTrigger.manual()));
        add(gatedShared("tob_normal", "Theatre of Blood", "Complete a Theatre of Blood raid (normal mode).",
            Rank.CARDINAL, TaskCategory.COMBAT, 30, TaskTrigger.manual()));
        add(gatedShared("toa_normal", "Tombs of Amascut", "Complete a Tombs of Amascut raid at 150+ invocation.",
            Rank.CARDINAL, TaskCategory.COMBAT, 30, TaskTrigger.manual()));
        add(sharedManual("skill_90_any_combat", "Reach 90 in a combat skill",
            "Achieve level 90 in Attack, Strength, Defence, Ranged, or Magic.",
            Rank.CARDINAL, TaskCategory.SKILLING, 20));
        add(sharedManual("skill_95_slayer", "Reach 95 Slayer", "Achieve level 95 Slayer.",
            Rank.CARDINAL, TaskCategory.SKILLING, 25));
        add(sharedManual("diary_any_elite", "Any Elite diary", "Complete any Elite achievement diary.",
            Rank.CARDINAL, TaskCategory.SKILLING, 20));
        add(sharedManual("quest_cape", "Quest cape", "Complete every quest in the game.",
            Rank.CARDINAL, TaskCategory.QUEST, 30));
        add(gatedShared("kill_araxxor", "Slay Araxxor", "Defeat Araxxor.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "Araxxor"));
        add(gatedShared("kill_yama", "Slay Yama", "Defeat Yama.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "Yama"));
        add(gatedShared("kill_amoxliatl", "Slay Amoxliatl", "Defeat Amoxliatl.",
            Rank.CARDINAL, TaskCategory.COMBAT, 20, TaskTrigger.manual(), "Amoxliatl"));
        add(gatedShared("kill_hueycoatl", "Slay The Hueycoatl", "Defeat The Hueycoatl.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "The Hueycoatl"));
        add(sharedManual("voidwaker_full", "Assemble the Voidwaker",
            "Collect all three Voidwaker pieces and combine them.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25));
        add(gatedShared("kill_gauntlet_cg", "Complete the Corrupted Gauntlet",
            "Finish a Corrupted Gauntlet run.",
            Rank.CARDINAL, TaskCategory.COMBAT, 25, TaskTrigger.manual(), "The Corrupted Hunllef"));
        add(gatedShared("kill_nightmare_solo", "Solo the Nightmare", "Defeat The Nightmare solo.",
            Rank.CARDINAL, TaskCategory.COMBAT, 30, TaskTrigger.manual(), "The Nightmare"));
    }

    // ================== CHOSEN ==================
    // Maximum rank. No new vows are drawn; the five completion tasks below fulfil the plugin.
    private static void seedChosen()
    {
        // Completion tasks (see checkVowsFulfilled in TaskService).
        add(new TaskDefinition("chosen_zuk", "Trial of the Inferno",
            "Defeat TzKal-Zuk and claim the Infernal cape.",
            Rank.CHOSEN, TaskCategory.COMBAT, GodAlignment.NONE, 100, false,
            TaskTrigger.chat("(?i)you found: infernal cape"),
            Arrays.asList("TzKal-Zuk")));

        add(new TaskDefinition("chosen_sol", "Champion of the Arena",
            "Defeat Sol Heredit at the Fortis Colosseum.",
            Rank.CHOSEN, TaskCategory.COMBAT, GodAlignment.NONE, 100, false,
            TaskTrigger.manual(),
            Arrays.asList("Sol Heredit")));

        add(new TaskDefinition("chosen_tob", "The Blood-Soaked Theatre",
            "Complete a Theatre of Blood raid at Hard Mode.",
            Rank.CHOSEN, TaskCategory.COMBAT, GodAlignment.NONE, 100, false,
            TaskTrigger.manual()));

        add(new TaskDefinition("chosen_toa", "The Tomb Sealed",
            "Complete a Tombs of Amascut raid at 300+ invocation.",
            Rank.CHOSEN, TaskCategory.COMBAT, GodAlignment.NONE, 100, false,
            TaskTrigger.manual()));

        add(new TaskDefinition("chosen_cox", "Chambers Conquered",
            "Complete a Chambers of Xeric raid at Challenge Mode.",
            Rank.CHOSEN, TaskCategory.COMBAT, GodAlignment.NONE, 100, false,
            TaskTrigger.manual()));
    }

    // ================== MILESTONES ==================
    // Rank-promotion checkpoints. Six milestones drive the seven-rank progression.
    private static void seedMilestones()
    {
        // Follower -> Deacon: first blood, a named low-level enemy.
        add(new TaskDefinition("m1_brutus", "Et Tu, Brutus?",
            "Slay Brutus. Your first named foe on the path of devotion.",
            Rank.FOLLOWER, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.npcKill("Brutus"),
            Arrays.asList("Brutus")));

        // Deacon -> Priest: entry-level boss with mechanics but forgiving stats.
        add(new TaskDefinition("m2_scurrius", "The Usurper",
            "Defeat Scurrius, the rat king, in the Varrock Sewers.",
            Rank.DEACON, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.chat("(?i)^scurrius has been defeated"),
            Arrays.asList("Scurrius")));

        // Priest -> Bishop: classic mid-game gear treadmill entrance. Base 70s + Prayer 43+ expected.
        add(new TaskDefinition("m3_barrows", "Ascend from the Grave",
            "Defeat all 6 Barrows brothers and loot the chest.",
            Rank.PRIEST, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.chat("(?i)you found: (barrows|dharok|ahrim|karil|torag|verac|guthan)")));

        // Bishop -> Archbishop: the classic fire cape gate.
        add(new TaskDefinition("m4_fire_cape", "The Trial of Flame",
            "Defeat TzTok-Jad and claim a Fire cape.",
            Rank.BISHOP, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.chat("(?i)you found: fire cape"),
            Arrays.asList("TzTok-Jad")));

        // Archbishop -> Cardinal: signature mid-endgame solo boss.
        add(new TaskDefinition("m5_zulrah", "The Coiled Serpent",
            "Defeat Zulrah.",
            Rank.ARCHBISHOP, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.npcKill("Zulrah"),
            Arrays.asList("Zulrah")));

        // Cardinal -> Chosen: DT2 four bosses (quest completion signals all four killed).
        add(new TaskDefinition("m6_dt2", "The Awakened Four",
            "Defeat Duke Sucellus, Vardorvis, the Leviathan, and the Whisperer, and complete Desert Treasure II.",
            Rank.CARDINAL, TaskCategory.MILESTONE, GodAlignment.NONE, 0, true,
            TaskTrigger.chat("(?i)quest complete!.*desert treasure ii")));
    }

    // ================== builders ==================
    private static TaskDefinition sharedChat(String id, String name, String desc, Rank tier,
                                             TaskCategory cat, int points, String regex)
    {
        return new TaskDefinition(id, name, desc, tier, cat, GodAlignment.NONE, points, false,
            TaskTrigger.chat(regex));
    }

    private static TaskDefinition sharedManual(String id, String name, String desc, Rank tier,
                                               TaskCategory cat, int points)
    {
        return new TaskDefinition(id, name, desc, tier, cat, GodAlignment.NONE, points, false,
            TaskTrigger.manual());
    }

    private static TaskDefinition gatedShared(String id, String name, String desc, Rank tier,
                                              TaskCategory cat, int points, TaskTrigger trigger,
                                              String... gated)
    {
        return new TaskDefinition(id, name, desc, tier, cat, GodAlignment.NONE, points, false,
            trigger, Arrays.asList(gated));
    }

    private static TaskDefinition godTask(String id, String name, String desc, Rank tier,
                                          TaskCategory cat, GodAlignment god, int points)
    {
        return new TaskDefinition(id, name, desc, tier, cat, god, points, false, TaskTrigger.manual());
    }

    private static void add(TaskDefinition t)
    {
        TASKS.put(t.getId(), t);
    }
}
