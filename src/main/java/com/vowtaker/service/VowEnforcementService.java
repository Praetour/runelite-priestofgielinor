package com.vowtaker.service;

import com.vowtaker.VowTakerConfig;
import com.vowtaker.model.Rank;
import com.vowtaker.model.VowDefinition;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;

@Singleton
public class VowEnforcementService
{
    /** OSRS varp id for auto-retaliate. 0 = auto-retaliate ON, 1 = OFF. */
    private static final int AUTO_RETALIATE_VARP = 172;

    /** How far Fatebound lets you stray from your target — enough to dodge, not enough to flee. */
    private static final int FATEBOUND_LEASH_TILES = 10;
    /** Ticks after an Attack click during which the resulting engagement counts as player-initiated. */
    private static final int FATEBOUND_LATCH_TICKS = 5;
    /** Consecutive disengaged ticks before the leash releases. */
    private static final int FATEBOUND_RELEASE_TICKS = 10;

    private static final Set<Prayer> DAMAGE_BOOSTING_PRAYERS = EnumSet.of(
        Prayer.BURST_OF_STRENGTH, Prayer.CLARITY_OF_THOUGHT, Prayer.SHARP_EYE, Prayer.MYSTIC_WILL,
        Prayer.SUPERHUMAN_STRENGTH, Prayer.IMPROVED_REFLEXES, Prayer.HAWK_EYE, Prayer.MYSTIC_LORE,
        Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES, Prayer.EAGLE_EYE, Prayer.MYSTIC_MIGHT,
        Prayer.CHIVALRY, Prayer.PIETY, Prayer.RIGOUR, Prayer.AUGURY
    );

    private static final Set<Prayer> COMBAT_PRAYERS = EnumSet.of(
        Prayer.THICK_SKIN, Prayer.BURST_OF_STRENGTH, Prayer.CLARITY_OF_THOUGHT, Prayer.SHARP_EYE, Prayer.MYSTIC_WILL,
        Prayer.ROCK_SKIN, Prayer.SUPERHUMAN_STRENGTH, Prayer.IMPROVED_REFLEXES, Prayer.HAWK_EYE, Prayer.MYSTIC_LORE,
        Prayer.STEEL_SKIN, Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES,
        Prayer.PROTECT_FROM_MAGIC, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MELEE,
        Prayer.EAGLE_EYE, Prayer.MYSTIC_MIGHT, Prayer.RETRIBUTION, Prayer.REDEMPTION, Prayer.SMITE,
        Prayer.CHIVALRY, Prayer.PIETY, Prayer.RIGOUR, Prayer.AUGURY
    );

    @Inject
    private Client client;

    @Inject
    private VowStorageService storageService;

    @Inject
    private ItemTagRegistry itemTags;

    @Inject
    private VowTakerConfig config;

    private long lastBlockMessageTick;
    private int lastCombatStyle;
    private int lastCombatTick;
    private int teleportAttemptTick;
    private int specialAttemptTick;
    private int killCreditExpiryTick;
    private int lastKillWeaponId = -1;
    private int lastKillAttackStyle = -1;
    private int shadowMarkedExpiryTick;
    private boolean citizenCreditReady;
    private int styleAtCombatExit = -1;
    private boolean wasInCombatLastTick;
    private boolean notifiedAutoRetaliateOn;
    private Runnable panelRefresh;

    /** Fatebound: the NPC *we* chose to fight. Null when we were the ones aggroed. */
    private NPC fateboundTarget;
    private int attackClickTick = -1;
    private int fateboundIdleTicks;

    private java.util.List<VowDefinition> cachedSworn;
    private int cachedSwornTick = -1;

    public void initialize()
    {
        lastBlockMessageTick = 0L;
        lastCombatStyle = -1;
        lastCombatTick = -1;
        teleportAttemptTick = -1;
        specialAttemptTick = -1;
        killCreditExpiryTick = -1;
        lastKillWeaponId = -1;
        lastKillAttackStyle = -1;
        shadowMarkedExpiryTick = -1;
        citizenCreditReady = false;
        styleAtCombatExit = -1;
        wasInCombatLastTick = false;
        notifiedAutoRetaliateOn = false;
        fateboundTarget = null;
        attackClickTick = -1;
        fateboundIdleTicks = 0;
        cachedSworn = null;
        cachedSwornTick = -1;
    }

    public void tick()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }
        // Combat/equip/consumable enforcement is now proactive via onMenuEntryAdded.
        // Only ritual location-completion checks still need a tick sweep.
        enforceRitualVows();
        updateFateboundTarget(player);

        // Apostle of Guthix: on the tick we transition out of combat, remember the style we exited with.
        boolean inCombatNow = isInCombat();
        if (wasInCombatLastTick && !inCombatNow)
        {
            styleAtCombatExit = client.getVarpValue(43);
        }
        wasInCombatLastTick = inCombatNow;

        // One-shot chat nudge: auto-retaliate is ON while a god vow is active.
        if (storageService.getActiveGodVow() != null
            && client.getVarpValue(AUTO_RETALIATE_VARP) == 0
            && !notifiedAutoRetaliateOn)
        {
            notifiedAutoRetaliateOn = true;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: auto-retaliate is on. Disable it in the combat tab \u2014 your vow forbids letting the game swing for you.", null);
        }
        else if (client.getVarpValue(AUTO_RETALIATE_VARP) == 1)
        {
            // Reset the one-shot flag so we'll warn again if the player re-enables it later.
            notifiedAutoRetaliateOn = false;
        }
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        // Real varbit hooks are available for future quest/achievement and resource-state checks.
    }

    public void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() == null || event.getActor() != client.getLocalPlayer())
        {
            return;
        }

        if (client.getLocalPlayer().getAnimation() != -1)
        {
            lastCombatStyle = client.getLocalPlayer().getAnimation();
            lastCombatTick = client.getGameCycle();
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }
    }

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event == null || event.getMenuOption() == null)
        {
            return;
        }

        String option = event.getMenuOption();
        String target = event.getMenuTarget() == null ? "" : event.getMenuTarget().toLowerCase();
        String lowerOption = option.toLowerCase();

        // Ritual: "Talk-to Reldo" completes The Wayfarer regardless of exact coords.
        VowDefinition activeRitual = storageService.getActiveRitualVow();
        if (activeRitual != null && !storageService.isCompleted(activeRitual)
            && "the_wayfarer".equals(activeRitual.getId())
            && lowerOption.startsWith("talk") && target.contains("reldo"))
        {
            completePendingRitual(activeRitual);
        }

        // Consume the click for any option we would have hidden via the menu sweep so the
        // action doesn't fire on servers that still surface the menu entry (e.g. one-click widget spells).
        String blockingVow = findActiveVowBlocking(lowerOption, target);
        if (blockingVow == null)
        {
            blockingVow = findFateboundBlocking(lowerOption, event.getMenuEntry());
        }
        if (blockingVow != null)
        {
            event.consume();
            block("Your vow \"" + blockingVow + "\" forbids that.");
            return;
        }

        // Fatebound only leashes fights we started, so remember when the Attack came from us.
        if (isAttackAction(lowerOption))
        {
            attackClickTick = client.getTickCount();
        }

        if (lowerOption.contains("cast") && (target.contains("teleport") || target.contains("home teleport")))
        {
            teleportAttemptTick = client.getTickCount();
        }
        else if (lowerOption.equals("teleport") || lowerOption.equals("break") || lowerOption.equals("rub"))
        {
            teleportAttemptTick = client.getTickCount();
        }

        if (lowerOption.equals("use") && target.contains("special attack"))
        {
            specialAttemptTick = client.getTickCount();
        }
    }

    /**
     * Rebuilds the menu each frame with forbidden entries stripped out, so blocked options are
     * never rendered at all. Filtering the whole array is far more reliable than popping the
     * just-added entry, which depends on the client not reordering entries afterwards.
     */
    public void filterMenuEntries()
    {
        if (client.isMenuOpen()) return;

        net.runelite.api.MenuEntry[] entries = client.getMenuEntries();
        if (entries.length == 0) return;

        java.util.List<net.runelite.api.MenuEntry> kept = new java.util.ArrayList<>(entries.length);
        for (net.runelite.api.MenuEntry entry : entries)
        {
            if (!isEntryBlocked(entry)) kept.add(entry);
        }

        if (kept.size() != entries.length)
        {
            client.setMenuEntries(kept.toArray(new net.runelite.api.MenuEntry[0]));
        }
    }

    private boolean isEntryBlocked(net.runelite.api.MenuEntry entry)
    {
        if (entry == null || entry.getOption() == null) return false;
        // Never strip Cancel or our own overlay entries or the player can get stuck.
        if (entry.getType() == net.runelite.api.MenuAction.CANCEL
            || entry.getType() == net.runelite.api.MenuAction.RUNELITE_OVERLAY)
        {
            return false;
        }

        String option = net.runelite.client.util.Text.removeTags(entry.getOption()).toLowerCase().trim();
        String target = entry.getTarget() == null
            ? ""
            : net.runelite.client.util.Text.removeTags(entry.getTarget()).toLowerCase().trim();

        return findActiveVowBlocking(option, target) != null
            || findFateboundBlocking(option, entry) != null;
    }

    /** Kept for the plugin's event wiring; the per-frame sweep does the actual removal. */
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
    }

    /** Returns the name of a sworn vow that forbids this menu action, or null. */
    private String findActiveVowBlocking(String option, String target)
    {
        String tagged = findTagBlocking(option, target);
        if (tagged != null) return tagged;

        // Every vow ever sworn stays in force, so each one gets a say.
        for (VowDefinition vow : swornVows())
        {
            if (blocksByRule(vow, option, target)) return vow.getName();
        }

        // Rank-locked gear ceiling: cannot equip gear above the tier your devotion rank has unlocked.
        // Independent of any specific vow, but only applied once a god has been chosen.
        if (isEquipAction(option) && config.enforceRankGearCeiling()
            && storageService.getSelectedGod() != null
            && storageService.getSelectedGod() != com.vowtaker.model.GodAlignment.NONE)
        {
            int itemTier = getGearTierFromName(target);
            int ceiling = getRankGearCeiling(storageService.getCurrentRank());
            if (itemTier > ceiling && !isGearUnlocked(target))
            {
                return "Rank Lock (needs " + gearCeilingRankLabel(itemTier) + ")";
            }
        }

        // Active ritual = ALL combat blocked until the ritual objective is fulfilled in-world.
        if (isAttackAction(option) && storageService.hasPendingRitual())
        {
            VowDefinition ritual = storageService.getActiveRitualVow();
            return ritual != null ? "Ritual in progress: " + ritual.getName() : "Ritual in progress";
        }

        // Auto-retaliate lock: god vows gate combat, and auto-retaliate is the obvious way to
        // bypass those gates via aggressive NPCs. Only hide the toggle while it is already OFF.
        if (storageService.getActiveGodVow() != null && option.equals("auto retaliate")
            && client.getVarpValue(AUTO_RETALIATE_VARP) == 1)
        {
            return "Auto-Retaliate Lock";
        }
        return null;
    }

    /**
     * Vow ids enforced by a behaviour rule rather than an item tag. Kept in sync with
     * {@link #blocksByRule}; a test asserts every vow is covered by one or the other, so a new
     * vow can never end up silently unenforced.
     */
    public static final Set<String> RULE_ENFORCED_IDS = java.util.Collections.unmodifiableSet(
        new java.util.HashSet<>(java.util.Arrays.asList(
            "no_teleport_spells", "no_damage_boosting_prayers",
            "mark_of_blood", "keeper_of_balance", "apostle_zamorak", "apostle_saradomin",
            "shieldbearer_of_light", "chaos_tethered", "skybound", "apostle_armadyl",
            "apostle_guthix", "apostle_bandos", "shadow_marked", "equilibrium_seeker",
            "apostle_zaros", "berserker_of_bandos", "windwalker", "brute_of_bandos",
            "fatebound")));

    /** Per-vow behaviour rules that can't be expressed as an item tag. */
    private boolean blocksByRule(VowDefinition vow, String option, String target)
    {
        switch (vow.getId())
        {
            // Travel and prayer restrictions.
            case "no_teleport_spells":
                return isTeleportSpellCast(option, target);
            case "no_damage_boosting_prayers":
                return option.equals("activate") && isDamagePrayerName(target);

            // Combat preconditions, checked when trying to attack.
            case "mark_of_blood":
                return isAttackAction(option) && !hasRawMeat();
            case "keeper_of_balance":
                return isAttackAction(option) && !isWeightInRange(10, 20);
            case "apostle_zamorak":
                return isAttackAction(option) && !citizenCreditReady && !isCitizen(target);
            case "apostle_saradomin":
                return isAttackAction(option) && client.getBoostedSkillLevel(Skill.PRAYER) < 20;
            case "shieldbearer_of_light":
                return isAttackAction(option) && !isShieldEquipped();
            case "chaos_tethered":
                return isAttackAction(option) && client.getEnergy() >= 5000;
            case "skybound":
                return isAttackAction(option) && client.getEnergy() < 5000;
            case "apostle_armadyl":
                return isAttackAction(option) && !isRangedWeaponEquipped() && !isInCombat();
            case "apostle_guthix":
                return isAttackAction(option) && !hasChangedStyleSinceLastExit();
            case "apostle_bandos":
                return isAttackAction(option) && !isMeleeWeaponEquipped();
            case "shadow_marked":
                return isAttackAction(option) && !hasRecentArceuusKill();

            // Style and gear rules.
            case "equilibrium_seeker":
                return option.equals("activate") && isBoostPrayerName(target);
            case "apostle_zaros":
                return option.equals("cast") && !isOnAncientSpellbook();
            case "berserker_of_bandos":
                return option.equals("eat") && isInCombat();
            case "windwalker":
                return isEquipAction(option) && isMetalChestOrLegs(target);
            case "brute_of_bandos":
                return isEquipAction(option) && matchesSlot(target, "chest") && !isLowTierChest(target);

            // Fatebound's leash needs the destination tile, so it lives in findFateboundBlocking.
            default:
                return false;
        }
    }

    /**
     * Fatebound leash. Only binds fights the player started, and only forbids walking further than
     * {@link #FATEBOUND_LEASH_TILES} from the target — you can still dodge mechanics, just not flee.
     */
    private String findFateboundBlocking(String option, net.runelite.api.MenuEntry entry)
    {
        if (fateboundTarget == null || entry == null) return null;
        if (!option.equals("walk here") || entry.getType() != net.runelite.api.MenuAction.WALK) return null;

        VowDefinition god = null;
        for (VowDefinition vow : swornVows())
        {
            if ("fatebound".equals(vow.getId()))
            {
                god = vow;
                break;
            }
        }
        if (god == null) return null;

        WorldPoint enemy = fateboundTarget.getWorldLocation();
        if (enemy == null) return null;

        WorldPoint destination = WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
        if (destination == null) return null;

        return destination.distanceTo(enemy) > FATEBOUND_LEASH_TILES ? god.getName() : null;
    }

    /**
     * Latches onto the NPC the player deliberately attacked. Fights we didn't start never leash,
     * so walking through aggressive mobs can't trap you.
     */
    private void updateFateboundTarget(Player player)
    {
        if (fateboundTarget != null)
        {
            boolean gone = fateboundTarget.isDead()
                || fateboundTarget.getName() == null
                || fateboundTarget.getWorldLocation() == null;
            boolean disengaged = player.getInteracting() != fateboundTarget
                && fateboundTarget.getInteracting() != player;

            if (gone)
            {
                clearFateboundTarget();
            }
            else if (disengaged)
            {
                // Attack animations leave brief gaps in interaction, so only drop after a real lull.
                if (++fateboundIdleTicks >= FATEBOUND_RELEASE_TICKS) clearFateboundTarget();
            }
            else
            {
                fateboundIdleTicks = 0;
            }
            return;
        }

        // Latch only if the engagement followed our own Attack click.
        if (attackClickTick < 0 || client.getTickCount() - attackClickTick > FATEBOUND_LATCH_TICKS) return;
        if (player.getInteracting() instanceof NPC)
        {
            fateboundTarget = (NPC) player.getInteracting();
            fateboundIdleTicks = 0;
        }
    }

    private void clearFateboundTarget()
    {
        fateboundTarget = null;
        fateboundIdleTicks = 0;
        attackClickTick = -1;
    }

    public void onNpcDespawned(net.runelite.api.events.NpcDespawned event)
    {
        if (event != null && event.getNpc() == fateboundTarget)
        {
            clearFateboundTarget();
        }
    }

    /** Checks the item blocklist for every sworn vow. Returns the blocking vow's name, or null. */
    private String findTagBlocking(String option, String target)
    {
        if (target == null || target.isEmpty()) return null;
        for (VowDefinition vow : swornVows())
        {
            for (String tag : vow.getBlockedTags())
            {
                if (itemTags.blocks(tag, option, target))
                {
                    return vow.getName();
                }
            }
        }
        return null;
    }

    /** The menu sweep runs every frame, so rebuild the sworn list at most once per game tick. */
    private java.util.List<VowDefinition> swornVows()
    {
        int tick = client.getTickCount();
        if (cachedSworn == null || cachedSwornTick != tick)
        {
            cachedSworn = storageService.getSwornVows();
            cachedSwornTick = tick;
        }
        return cachedSworn;
    }

    /** True when the local player is actively engaged in reciprocal combat with an NPC. */
    private boolean isInCombat()
    {
        Player p = client.getLocalPlayer();
        if (p == null || p.getInteracting() == null) return false;
        // Either we're targeting them or they're targeting us within the last combat window.
        return true;
    }

    private static boolean isAttackAction(String option)
    {
        return option.equals("attack") || option.equals("fight");
    }

    private static boolean isEquipAction(String option)
    {
        return option.equals("wear") || option.equals("wield") || option.equals("equip");
    }

    /** Loose match on item name for slot-restriction vows. */
    private static boolean matchesSlot(String target, String slot)
    {
        switch (slot)
        {
            case "boots":
                return target.contains("boots") || target.contains("shoes") || target.contains("sandals");
            case "gloves":
                return target.contains("gloves") || target.contains("gauntlets") || target.contains("vambraces")
                    || target.contains("bracers") || target.contains("mitts");
            case "cape":
                return target.contains("cape") || target.contains("cloak");
            case "chest":
                return target.contains("platebody") || target.contains("chestplate") || target.contains("chainbody")
                    || target.contains("chainmail") || target.contains("body") || target.contains("robe top")
                    || target.contains("hauberk") || target.contains("torso");
            case "legs":
                return target.contains("platelegs") || target.contains("plateskirt") || target.contains("chaps")
                    || target.contains("robe bottom") || target.contains("tassets") || target.contains("skirt")
                    || target.contains(" legs");
            default:
                return false;
        }
    }

    private boolean isWeightInRange(int minKg, int maxKg)
    {
        int weight = client.getWeight();
        return weight >= minKg && weight <= maxKg;
    }

    /** Called by TaskService/plugin whenever we credit an NPC kill (name is the NPC we killed). */
    public void notifyKillCredit(String npcName)
    {
        killCreditExpiryTick = client.getTickCount() + 100;
        lastKillWeaponId = equippedWeaponId();
        lastKillAttackStyle = client.getVarpValue(43);
        // Apostle of Zamorak: killing a citizen enables one monster kill; killing a monster consumes it.
        if (npcName != null)
        {
            if (isCitizenName(npcName))
            {
                citizenCreditReady = true;
            }
            else
            {
                citizenCreditReady = false;
            }
        }
        // Extend shadow-marked window if the kill happened in Arceuus.
        if (isInArceuus())
        {
            notifyArceuusKill();
        }
    }

    /** Back-compat alias — plugin used to call with no arg. */
    public void notifyKillCredit()
    {
        notifyKillCredit(null);
    }

    private static boolean isCitizenName(String npcName)
    {
        if (npcName == null) return false;
        String n = npcName.toLowerCase();
        return n.equals("man") || n.equals("woman") || n.equals("citizen")
            || n.startsWith("man ") || n.startsWith("woman ") || n.startsWith("citizen ");
    }

    /** Menu target check for whether the highlighted NPC is a Man/Woman/Citizen. */
    private static boolean isCitizen(String target)
    {
        if (target == null) return false;
        return isCitizenName(target);
    }

    private static boolean isTeleportSpellCast(String option, String target)
    {
        // Magic spellbook cast: option contains "cast", target has "teleport" (Home, Varrock, Lumbridge, etc.).
        if (option.contains("cast") && target.contains("teleport")) return true;
        // Jewellery/tab shortcut: option is "teleport"/"rub"/"break" on a specific teleport item.
        if (option.equals("teleport")) return true;
        if ((option.equals("rub") || option.equals("break")) && target.contains("teleport")) return true;
        return false;
    }

    public void onWidgetLoaded(WidgetLoaded event)
    {
    }

    private void enforceRitualVows()
    {
        VowDefinition ritual = storageService.getActiveRitualVow();
        if (ritual == null || storageService.isCompleted(ritual))
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        boolean done = false;
        switch (ritual.getId())
        {
            case "the_pilgrim":
                done = isNearRegion(player, 2870, 5350, 16);
                break;
            case "the_purifier":
                done = isNearRegion(player, 2960, 3455, 18);
                break;
            case "the_tidewalker":
                done = isNearRegion(player, 1630, 3890, 20);
                break;
            case "the_shadowbound":
                done = isNearRegion(player, 3090, 3310, 18);
                break;
            case "the_ashwalker":
                done = isNearRegion(player, 3420, 3250, 18);
                break;
            case "the_wayfarer":
                // Reldo lives in the Varrock palace library.
                done = isNearRegion(player, 3211, 3494, 5);
                break;
            case "the_blood_oath":
                done = isInWilderness(player) && player.getHealthRatio() < 0.5;
                break;
            case "the_stonebearer":
                done = isNearRegion(player, 3290, 3180, 18);
                break;
            default:
                break;
        }

        if (done)
        {
            completePendingRitual(ritual);
        }
    }

    /** Called from enforceRitualVows or the plugin's menu hook to finalise a ritual. */
    public void completePendingRitual(VowDefinition ritual)
    {
        if (ritual == null || storageService.isCompleted(ritual)) return;
        storageService.completeVow(ritual);
        int gained = com.vowtaker.service.VowSelectionService.pointsFor(ritual);
        storageService.addPoints(gained);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "VowTaker: ritual complete \u2014 " + ritual.getName() + " (+" + gained + " pts). Combat is unlocked.", null);
        storageService.clearActiveRitualVow();
        if (panelRefresh != null)
        {
            panelRefresh.run();
        }
    }

    /** Set by the plugin so ritual completion + citizen-credit changes reflect in the side panel immediately. */
    public void setPanelRefreshCallback(Runnable r)
    {
        this.panelRefresh = r;
    }

    private boolean isNearRegion(Player player, int centerX, int centerY, int radius)
    {
        if (player == null || player.getWorldLocation() == null)
        {
            return false;
        }

        int x = player.getWorldLocation().getX();
        int y = player.getWorldLocation().getY();
        return Math.abs(x - centerX) <= radius && Math.abs(y - centerY) <= radius;
    }

    private boolean isInWilderness(Player player)
    {
        if (player == null || player.getWorldLocation() == null)
        {
            return false;
        }

        int x = player.getWorldLocation().getX();
        int y = player.getWorldLocation().getY();
        return x >= 2944 && x <= 3672 && y >= 3520 && y <= 3968;
    }

    private boolean hasTeleportSpellBookUsage()
    {
        return teleportAttemptTick > 0 && client.getTickCount() - teleportAttemptTick <= 2;
    }

    private boolean hasStaminaPotion()
    {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null)
        {
            return false;
        }
        return containsItem(container, 12631, 12629, 12627, 12625, 12623);
    }

    private boolean isUsingSpecialAttack()
    {
        return specialAttemptTick > 0 && client.getTickCount() - specialAttemptTick <= 2;
    }

    private boolean hasDamageBoostingPrayerActive()
    {
        for (Prayer prayer : DAMAGE_BOOSTING_PRAYERS)
        {
            if (client.isPrayerActive(prayer))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasFoodAboveTier60()
    {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null)
        {
            return false;
        }
        for (Item item : container.getItems())
        {
            if (item == null)
            {
                continue;
            }
            int id = item.getId();
            if ((id == 379 || id == 385 || id == 392 || id == 397 || id == 1944 || id == 361 || id == 3144 || id == 2289 || id == 2697 || id == 3612))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasPotionAboveTier70()
    {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null)
        {
            return false;
        }
        return containsItem(container, 2436, 2440, 2442, 2444, 2446, 3016, 3022, 3024, 3026, 3028);
    }

    private boolean isShieldEquipped()
    {
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null) return false;
        Item[] items = container.getItems();
        int slot = net.runelite.api.EquipmentInventorySlot.SHIELD.getSlotIdx();
        return slot < items.length && items[slot] != null && items[slot].getId() > 0;
    }

    /** Ranged weapon: bow / crossbow / thrown / chin — by name of the equipped weapon item. */
    private boolean isRangedWeaponEquipped()
    {
        String name = equippedWeaponName();
        if (name == null) return false;
        return name.contains("bow") || name.contains("crossbow") || name.contains("c'bow")
            || name.contains("chinchompa") || name.contains("dart") || name.contains("knife")
            || name.contains("javelin") || name.contains("throwing") || name.contains("blowpipe");
    }

    /** Melee weapon: sword / mace / axe / spear / etc, or nothing at all (fists). */
    private boolean isMeleeWeaponEquipped()
    {
        String name = equippedWeaponName();
        if (name == null || name.isEmpty()) return true;
        if (isRangedWeaponEquipped()) return false;
        if (name.contains("staff") || name.contains("wand") || name.contains("orb")) return false;
        return true;
    }

    private String equippedWeaponName()
    {
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null) return null;
        Item[] items = container.getItems();
        int slot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
        if (slot >= items.length || items[slot] == null || items[slot].getId() <= 0) return "";
        net.runelite.api.ItemComposition comp = client.getItemDefinition(items[slot].getId());
        return comp == null || comp.getName() == null ? "" : comp.getName().toLowerCase();
    }

    /** Apostle of Guthix: after leaving combat, current attack style must differ from the style at exit. */
    private boolean hasChangedStyleSinceLastExit()
    {
        // In combat: chain kills freely on the current style.
        if (isInCombat()) return true;
        // No recorded exit yet — allow the first engagement.
        if (styleAtCombatExit == -1) return true;
        return client.getVarpValue(43) != styleAtCombatExit;
    }

    /** Zaros ancient-spellbook gate: varbit 4070 = 1 when Ancient is active. */
    private boolean isOnAncientSpellbook()
    {
        return client.getVarbitValue(4070) == 1;
    }

    private int equippedWeaponId()
    {
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null) return -1;
        Item[] items = container.getItems();
        int slot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
        return slot < items.length && items[slot] != null ? items[slot].getId() : -1;
    }

    /** Shadow-marked: player has killed something in Arceuus within the 30-min window. */
    private boolean hasRecentArceuusKill()
    {
        // Grace: on first vow tick, grant a 30-min window so the player can travel to Arceuus.
        if (shadowMarkedExpiryTick <= 0)
        {
            shadowMarkedExpiryTick = client.getTickCount() + 3000;
            return true;
        }
        return client.getTickCount() < shadowMarkedExpiryTick;
    }

    public void notifyArceuusKill()
    {
        shadowMarkedExpiryTick = client.getTickCount() + 3000;
    }

    /** True if the player is currently inside the Arceuus region (rough coord box). */
    public boolean isInArceuus()
    {
        Player p = client.getLocalPlayer();
        if (p == null || p.getWorldLocation() == null) return false;
        int x = p.getWorldLocation().getX();
        int y = p.getWorldLocation().getY();
        return x >= 1550 && x <= 1800 && y >= 3650 && y <= 3850;
    }

    /** Combat prayer names — targets in menu entries are the plain prayer name. */
    private static boolean isCombatPrayerName(String target)
    {
        String t = target;
        return t.contains("thick skin") || t.contains("burst of strength") || t.contains("clarity of thought")
            || t.contains("sharp eye") || t.contains("mystic will") || t.contains("rock skin")
            || t.contains("superhuman strength") || t.contains("improved reflexes") || t.contains("hawk eye")
            || t.contains("mystic lore") || t.contains("steel skin") || t.contains("ultimate strength")
            || t.contains("incredible reflexes") || t.contains("protect from magic")
            || t.contains("protect from missiles") || t.contains("protect from melee") || t.contains("eagle eye")
            || t.contains("mystic might") || t.contains("retribution") || t.contains("redemption")
            || t.contains("smite") || t.contains("chivalry") || t.contains("piety") || t.contains("rigour")
            || t.contains("augury") || t.contains("preserve");
    }

    /** Offensive/defensive prayers, EXCLUDING the three overhead damage-protection prayers. */
    private static boolean isBoostPrayerName(String target)
    {
        String t = target;
        if (t.contains("protect from magic") || t.contains("protect from missiles") || t.contains("protect from melee"))
        {
            return false;
        }
        return isCombatPrayerName(t);
    }

    /** Damage-boosting prayers only — subset of combat prayers. */
    private static boolean isDamagePrayerName(String target)
    {
        String t = target;
        return t.contains("burst of strength") || t.contains("clarity of thought") || t.contains("sharp eye")
            || t.contains("mystic will") || t.contains("superhuman strength") || t.contains("improved reflexes")
            || t.contains("hawk eye") || t.contains("mystic lore") || t.contains("ultimate strength")
            || t.contains("incredible reflexes") || t.contains("eagle eye") || t.contains("mystic might")
            || t.contains("chivalry") || t.contains("piety") || t.contains("rigour") || t.contains("augury");
    }

    /** Metal chest/leg armour: name indicates a metal alloy AND the item is a body/leg piece. */
    private static boolean isMetalChestOrLegs(String target)
    {
        boolean metal = target.contains("bronze") || target.contains("iron") || target.contains("steel")
            || target.contains("black") || target.contains("white") || target.contains("mithril")
            || target.contains("adamant") || target.contains("rune ") || target.contains("dragon")
            || target.contains("torag") || target.contains("dharok") || target.contains("verac")
            || target.contains("guthan") || target.contains("bandos ") || target.contains("statius");
        return metal && (matchesSlot(target, "chest") || matchesSlot(target, "legs"));
    }

    /** Chest armour at or below tier 30 (bronze / iron / leather / hardleather). */
    private static boolean isLowTierChest(String target)
    {
        return target.contains("bronze") || target.contains("iron ") || target.contains("leather")
            || target.contains("wooden") || target.contains("training");
    }

    /**
     * Coarse gear-tier estimate by name keywords. Return values map to the ceiling table below.
     * Non-armour / unranked items return 0 (never blocked).
     */
    private static int getGearTierFromName(String target)
    {
        // Chosen-tier gear. Order matters: check specific names before generic keywords.
        if (target.contains("torva") || target.contains("scythe of vitur") || target.contains("twisted bow")
            || target.contains("tbow") || target.contains("masori") || target.contains("ancestral")
            || target.contains("justiciar") || target.contains("avernic") || target.contains("blade of saeldor")
            || target.contains("ghrazi rapier") || target.contains("sanguinesti") || target.contains("virtus")
            || target.contains("dinh's") || target.contains("elysian") || target.contains("arcane spirit")
            || target.contains("primordial") || target.contains("pegasian") || target.contains("eternal boots")
            || target.contains("tumeken's shadow") || target.contains("osmumten's fang")
            || target.contains("elidinis' ward") || target.contains("lightbearer")
            || target.contains("kodai") || target.contains("elder maul") || target.contains("soulreaper axe")
            || target.contains("dizana's quiver") || target.contains("tonalztics")
            || target.contains("sunfire fanatic") || target.contains("echo boots"))
        {
            return 90;
        }
        // Cardinal-tier gear: Barrows, Bandos, Perilous Moons, crystal armour, obsidian, Nex, Zaryte.
        if (target.contains("ahrim") || target.contains("dharok") || target.contains("guthan")
            || target.contains("karil") || target.contains("torag") || target.contains("verac")
            || target.contains("bandos chestplate") || target.contains("bandos tassets")
            || target.contains("blood moon") || target.contains("blue moon") || target.contains("eclipse moon")
            || target.contains("crystal helm") || target.contains("crystal body") || target.contains("crystal legs")
            || target.contains("obsidian platebody") || target.contains("obsidian platelegs")
            || target.contains("armadyl chestplate") || target.contains("armadyl chainskirt")
            || target.contains("zaryte crossbow") || target.contains("nihil horn")
            || target.contains("infernal cape") || target.contains("fire cape"))
        {
            return 70;
        }
        // Archbishop-tier gear: dragon armour + dragon weapons.
        if (target.contains("dragon chainbody") || target.contains("dragon platebody")
            || target.contains("dragon platelegs") || target.contains("dragon plateskirt")
            || target.contains("dragon full helm") || target.contains("dragon med helm")
            || target.contains("dragon sq shield") || target.contains("dragon kiteshield")
            || target.contains("dragon boots") || target.contains("dragon defender")
            || target.contains("dragon scimitar") || target.contains("dragon dagger")
            || target.contains("dragon longsword") || target.contains("dragon mace")
            || target.contains("dragon 2h") || target.contains("dragon battleaxe")
            || target.contains("dragon warhammer") || target.contains("dragon claws")
            || target.contains("dragon crossbow") || target.contains("dragon hunter crossbow")
            || target.contains("dragon halberd") || target.contains("dragon spear")
            || target.contains("dragon hasta") || target.contains("dragon pickaxe")
            || target.contains("dragon axe") || target.contains("dragon harpoon"))
        {
            return 60;
        }
        // Bishop-tier gear: rune, all d'hide, mystic (mid-mage).
        if ((target.contains("rune ") && !target.contains("rune essence") && !target.contains("rune arrow")
                && !target.contains("rune bolt") && !target.contains("rune dart") && !target.contains("rune knife")
                && !target.contains("rune thrownaxe") && !target.contains("rune javelin") && !target.contains("rune pouch"))
            || target.contains("green d'hide") || target.contains("green dragonhide")
            || target.contains("blue d'hide") || target.contains("red d'hide") || target.contains("black d'hide")
            || target.contains("mystic "))
        {
            return 40;
        }
        // Priest-tier gear: adamant.
        if (target.contains("adamant") || target.contains("adamantite")
            || target.contains("studded body") || target.contains("studded chaps"))
        {
            return 30;
        }
        // Deacon-tier gear: mithril.
        if (target.contains("mithril"))
        {
            return 20;
        }
        // Follower-tier gear: black, steel.
        if (target.contains("black platebody") || target.contains("black platelegs")
            || target.contains("black plateskirt") || target.contains("black chainbody")
            || target.contains("black med helm") || target.contains("black full helm")
            || target.contains("black kiteshield") || target.contains("black sq shield")
            || target.contains("steel platebody") || target.contains("steel platelegs")
            || target.contains("steel plateskirt") || target.contains("steel chainbody")
            || target.contains("steel full helm") || target.contains("steel med helm")
            || target.contains("steel kiteshield") || target.contains("steel sq shield"))
        {
            return 10;
        }
        return 0;
    }

    /** Highest gear tier a rank can equip. Chosen is uncapped. */
    private static int getRankGearCeiling(Rank rank)
    {
        if (rank == null) return 0;
        switch (rank)
        {
            case NONE:
            case FOLLOWER:
                return 10;
            case DEACON:
                return 20;
            case PRIEST:
                return 30;
            case BISHOP:
                return 40;
            case ARCHBISHOP:
                return 60;
            case CARDINAL:
                return 70;
            case CHOSEN:
            default:
                return Integer.MAX_VALUE;
        }
    }

    /** Label the rank a piece of gear at this tier requires. */
    private static String gearCeilingRankLabel(int gearTier)
    {
        if (gearTier <= 10) return "Follower";
        if (gearTier <= 20) return "Deacon";
        if (gearTier <= 30) return "Priest";
        if (gearTier <= 40) return "Bishop";
        if (gearTier <= 60) return "Archbishop";
        if (gearTier <= 70) return "Cardinal";
        return "Chosen";
    }

    /** True if the item name matches any pattern the player has unlocked via a task. */
    private boolean isGearUnlocked(String target)
    {
        if (target == null || target.isEmpty()) return false;
        Set<String> unlocked = storageService.getUnlockedGearPatterns();
        if (unlocked.isEmpty()) return false;
        for (String pattern : unlocked)
        {
            if (!pattern.isEmpty() && target.contains(pattern)) return true;
        }
        return false;
    }

    private boolean hasRawMeat()
    {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null)
        {
            return false;
        }
        return containsItem(container, 2132, 2142, 2143, 2144);
    }

    private boolean hasPrayerActive()
    {
        for (Prayer prayer : COMBAT_PRAYERS)
        {
            if (client.isPrayerActive(prayer))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isRetreatingFromCombat()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getInteracting() == null)
        {
            return false;
        }
        return player.getAnimation() == -1 && lastCombatTick > -1 && client.getGameCycle() - lastCombatTick < 25;
    }

    private boolean containsItem(ItemContainer container, int... ids)
    {
        if (container == null)
        {
            return false;
        }

        for (Item item : container.getItems())
        {
            if (item == null)
            {
                continue;
            }
            for (int id : ids)
            {
                if (item.getId() == id)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void block(String reason)
    {
        long now = client.getGameCycle();
        if (now - lastBlockMessageTick > 50)
        {
            lastBlockMessageTick = now;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "VowTaker: " + reason, null);
        }
    }
}
