package com.vowtaker.service;

import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.Rank;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowSelection;
import com.vowtaker.model.VowType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.WidgetLoaded;

@Singleton
public class VowSelectionService
{
    /** Kind of vow pool to draw the next set of cards from. */
    public enum DrawMode
    {
        /** God-only vows for the current god. Used on god pick and after milestones. */
        GOD_ONLY,
        /** Major = god's own vows + filler permanent restrictions (No X slot / no prayer / etc). */
        MAJOR_REVEALED,
        /** Minor = smaller permanents + rituals. */
        MINOR,
        /** Any non-completed vow filtered by the current rank's allowed severities. */
        BY_RANK_SEVERITY
    }

    /** IDs of PERMANENT vows that count as "Major" filler and only draw once a god is chosen. */
    private static final int REQUIRED_MILESTONES = 3;
    @Inject
    private VowStorageService storageService;

    private final List<VowSelection> hiddenCards = new ArrayList<>();
    private boolean selectionPending;
    private int milestoneCount;
    private boolean pendingForcedOpen;
    private DrawMode pendingDrawMode = DrawMode.BY_RANK_SEVERITY;
    private DrawMode currentDrawMode = DrawMode.BY_RANK_SEVERITY;
    private Consumer<String> onSelectionResolved;

    public void initialize()
    {
        hiddenCards.clear();
        selectionPending = false;
        milestoneCount = 0;
        pendingForcedOpen = false;
        currentDrawMode = DrawMode.BY_RANK_SEVERITY;
    }

    /** Called by the plugin after a card is chosen, with a chat-friendly summary of point outcomes. */
    public void setOnSelectionResolved(Consumer<String> callback)
    {
        this.onSelectionResolved = callback;
    }

    /** Convert vow severity to point reward. Hidden from cards; revealed only after a pick. */
    public static int severityPoints(String severity)
    {
        if (severity == null) return 0;
        switch (severity.toLowerCase())
        {
            case "extreme": return 25;
            case "hard": return 10;
            case "medium": return 5;
            case "light": return 3;
            default: return 3;
        }
    }

    /** Per-id overrides for vows whose impact does not match their severity tier. */
    private static final java.util.Map<String, Integer> POINT_OVERRIDES;
    static
    {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        // Prayer-book / prayer-slot vows are effectively "no boss content" — priced accordingly.
        m.put("no_prayer_book", 50);
        m.put("no_damage_boosting_prayers", 25);
        // Naked / one-hand / equipment-slot restrictions gut a lot of endgame options.
        m.put("naked_ironman", 60);
        m.put("one_hand_only", 25);
        m.put("no_armor_above_80", 30);
        m.put("no_metal_armour", 30);
        m.put("no_degradeable_gear", 30);
        m.put("no_barrows_armour", 15);
        m.put("no_shield_slot", 20);
        m.put("no_helmet_slot", 15);
        m.put("no_ring_slot", 10);
        m.put("no_amulet_slot", 15);
        m.put("no_jewellery_above_80", 20);
        // Armour tier caps: the lower the cap, the more content it shuts out.
        m.put("no_armour_above_30", 55);
        m.put("no_armour_above_40", 45);
        m.put("no_armour_above_50", 35);
        m.put("no_armour_above_60", 25);
        m.put("no_armour_above_70", 15);
        m.put("no_melee_armour", 30);
        m.put("no_ranged_armour", 25);
        m.put("no_magic_armour", 25);
        // Consumable lockouts scale with how much they cost you at bosses.
        m.put("no_brews", 25);
        m.put("no_prayer_restores", 30);
        m.put("no_combat_potions", 25);
        m.put("no_teleport_items", 20);
        m.put("no_poison_weapons", 10);
        // Minor stacking restrictions.
        m.put("no_overloads", 15);
        m.put("no_combo_eating", 15);
        m.put("no_slayer_helm", 12);
        m.put("no_god_capes", 10);
        m.put("no_spec_weapons", 15);
        m.put("no_defensive_shields", 12);
        m.put("no_crystal_gear", 12);
        m.put("no_high_ammo", 12);
        m.put("no_storage_bags", 10);
        m.put("no_high_tier_tools", 8);
        m.put("no_antifire", 10);
        // Combat-style locks remove an entire style tree.
        m.put("no_ranged_training", 40);
        m.put("no_magic_training", 40);
        m.put("no_melee_training", 40);
        m.put("no_special_attacks", 20);
        // God vows with strict conditions.
        m.put("apostle_zamorak", 25);
        m.put("apostle_guthix", 20);
        m.put("apostle_zaros", 30);
        m.put("shadow_marked", 25);
        m.put("fatebound", 25);
        m.put("keeper_of_balance", 20);
        m.put("chaos_tethered", 20);
        m.put("skybound", 15);
        m.put("windwalker", 20);
        m.put("apostle_bandos", 20);
        m.put("berserker_of_bandos", 15);
        m.put("brute_of_bandos", 15);
        // Tag-driven god vows: priced off the tag they lock, matching the equivalent major vow.
        m.put("plainsong", 40);
        m.put("scorn_of_wizardry", 40);
        m.put("blood_at_arms_length", 40);
        m.put("scorn_of_cowardice", 40);
        m.put("the_last_tide", 40);
        m.put("ironshun", 30);
        m.put("shadowsilk", 30);
        m.put("skywarden", 30);
        m.put("nothing_lasts", 30);
        m.put("modest_means", 30);
        m.put("unburdened_flight", 30);
        m.put("no_crutch", 30);
        m.put("blood_oath_zealot", 30);
        m.put("temperance", 25);
        m.put("seeker_of_balance", 25);
        m.put("unadulterated", 25);
        m.put("open_hand", 25);
        m.put("balanced_grip", 25);
        m.put("scorn_of_comfort", 25);
        m.put("thin_air", 25);
        m.put("ascetic_of_the_empty_lord", 25);
        m.put("reckless_abandon", 20);
        m.put("all_offence", 20);
        m.put("both_hands_to_the_bow", 20);
        m.put("no_retreat", 20);
        m.put("march_never_blink", 20);
        m.put("alms_of_the_faithful", 20);
        m.put("trinketless", 20);
        m.put("lightbringer", 20);
        m.put("bare_faced", 15);
        m.put("open_sky", 15);
        m.put("unbound_throat", 15);
        m.put("untainted_steel", 15);
        m.put("sky_over_grave", 15);
        m.put("older_than_graves", 15);
        m.put("feast_on_ash", 15);
        m.put("hand_of_purity", 10);
        m.put("feather_tread", 10);
        m.put("bare_knuckles", 10);
        m.put("unadorned", 10);
        m.put("no_bindings", 10);
        m.put("unmarked", 10);
        m.put("own_two_feet", 10);
        POINT_OVERRIDES = java.util.Collections.unmodifiableMap(m);
    }

    /** Points awarded for a specific vow — applies id override if defined, otherwise severity. */
    public static int pointsFor(com.vowtaker.model.VowDefinition vow)
    {
        if (vow == null) return 0;
        Integer override = POINT_OVERRIDES.get(vow.getId());
        if (override != null) return override;
        return severityPoints(vow.getSeverity());
    }

    public boolean hasPendingForcedOpen()
    {
        return pendingForcedOpen;
    }

    public void clearPendingForcedOpen()
    {
        pendingForcedOpen = false;
    }

    /** Called when a milestone or the plugin requests selection but the client is in a sensitive area. */
    public void queueForcedOpen()
    {
        pendingForcedOpen = true;
        // Point-threshold triggers draw from the minor pool.
        pendingDrawMode = DrawMode.MINOR;
    }

    /** Queue the next forced-open to draw exclusively from the player's god pool (first vow / post-milestone). */
    public void queueForcedGodOnlyOpen()
    {
        pendingForcedOpen = true;
        pendingDrawMode = DrawMode.GOD_ONLY;
    }

    /** Queue a Major pool draw (god vows + major filler perms). Revealed by default. */
    public void queueMajorOpen()
    {
        pendingForcedOpen = true;
        pendingDrawMode = DrawMode.MAJOR_REVEALED;
    }

    /** Queue a Minor pool draw (small perms + rituals). */
    public void queueMinorOpen()
    {
        pendingForcedOpen = true;
        pendingDrawMode = DrawMode.MINOR;
    }

    public DrawMode getPendingDrawMode()
    {
        return pendingDrawMode;
    }

    /** The draw mode used for the currently-open picker. Used by the overlay to decide layout. */
    public DrawMode getCurrentDrawMode()
    {
        return currentDrawMode;
    }

    /** Spend a token to redraw the offered card. Refused if no other vow could come up. */
    public boolean rerollSelection()
    {
        if (!selectionPending) return false;
        if (buildPool(currentDrawMode).size() <= hiddenCards.size()) return false;
        if (!storageService.consumeRerollToken()) return false;
        buildHiddenCards(currentDrawMode, shownVowIds());
        return true;
    }

    public int getRerollTokens()
    {
        return storageService.getRerollTokens();
    }

    /** The Major currently worn — shown on the picker so the player sees what a pick replaces. */
    public VowDefinition getActiveMajorVow()
    {
        return storageService.getActiveMajorVow();
    }

    /** True when spending a token would actually produce a different option. */
    public boolean canReroll()
    {
        return selectionPending
            && storageService.getRerollTokens() > 0
            && buildPool(currentDrawMode).size() > hiddenCards.size();
    }

    private Set<String> shownVowIds()
    {
        Set<String> ids = new java.util.HashSet<>();
        for (VowSelection s : hiddenCards)
        {
            ids.add(s.getVow().getId());
        }
        return ids;
    }

    public void onWidgetLoaded(WidgetLoaded event)
    {
    }

    public void approveVow(String vowId)
    {
        storageService.approve(vowId);
    }

    public void declineVow(String vowId)
    {
        storageService.decline(vowId);
    }

    public void approveAllPendingVows()
    {
        for (VowDefinition vow : VowRegistry.draftReviewQueue(storageService))
        {
            storageService.approve(vow.getId());
        }
    }

    public void declineAllPendingVows()
    {
        for (VowDefinition vow : VowRegistry.draftReviewQueue(storageService))
        {
            storageService.decline(vow.getId());
        }
    }

    public List<VowDefinition> getPendingApprovalVows()
    {
        return VowRegistry.draftReviewQueue(storageService);
    }

    public List<VowDefinition> getApprovedVows()
    {
        return VowRegistry.approvedVows(storageService);
    }

    public void tryQueueSelection()
    {
        milestoneCount++;
        if (milestoneCount >= REQUIRED_MILESTONES && !getApprovedVows().isEmpty())
        {
            // Milestone reward = Major draw (god vows + filler perms), revealed.
            pendingForcedOpen = true;
            pendingDrawMode = DrawMode.MAJOR_REVEALED;
        }
    }

    /** Bypass milestone gate; open the card picker immediately. Clears any queued forced-open. */
    public void forceOpenSelection()
    {
        buildHiddenCards(pendingDrawMode);
        // Always clear the queue flag: an exhausted pool must not leave a forced-open pending forever.
        pendingForcedOpen = false;
        if (!hiddenCards.isEmpty())
        {
            selectionPending = true;
            currentDrawMode = pendingDrawMode;
            storageService.setSelectionOpen(true);
        }
        pendingDrawMode = DrawMode.BY_RANK_SEVERITY;
    }

    /** Force open with an explicit draw mode; use for the god-pick first vow or post-milestone. */
    public void forceOpenSelection(DrawMode mode)
    {
        pendingDrawMode = mode == null ? DrawMode.BY_RANK_SEVERITY : mode;
        forceOpenSelection();
    }

    /**
     * Fire card picks between promotions. Follower uses fixed 25-point steps (so a single big reward
     * can't chain through multiple quartiles); higher ranks use 25%/50%/75% of the rank-up bar.
     * Returns true if a new checkpoint fired so the plugin can announce it.
     */
    public boolean checkPointsProgress()
    {
        Rank current = storageService.getCurrentRank();
        Rank next = current.next();
        if (current == Rank.NONE || next == current) return false;
        // Never override a picker that's already queued (e.g. a promotion Major draw). The quartile
        // will fire on a later tick once the queued pick is resolved.
        if (pendingForcedOpen || selectionPending) return false;

        int floor = current.getRequiredPoints();
        int ceiling = next.getRequiredPoints();
        int span = ceiling - floor;
        if (span <= 0) return false;

        int gained = Math.max(0, storageService.getTotalPoints() - floor);
        int quartile;
        if (current == Rank.FOLLOWER)
        {
            // Follower rank-up span is only 50 pts; a single reward can otherwise cross multiple
            // quartiles at once. One fixed mid-rank checkpoint at 25 pts keeps early vows spaced out.
            quartile = gained >= 25 ? 1 : 0;
        }
        else
        {
            // Quartiles 1, 2, 3 fire between ranks; quartile 4 is the milestone/rank-up itself.
            quartile = Math.min(3, (gained * 4) / span);
        }
        int already = storageService.getHighestQuartileFired();
        if (quartile > already && quartile >= 1 && quartile <= 3)
        {
            storageService.setHighestQuartileFired(quartile);
            queueForcedOpen();
            return true;
        }
        return false;
    }

    public void pollSelection()
    {
    }

    public void selectCard(VowDefinition vow)
    {
        if (vow == null || !storageService.isApproved(vow.getId()))
        {
            return;
        }

        boolean ritualPending = false;
        int gained = 0;
        List<VowSelection> snapshot = new ArrayList<>(hiddenCards);

        if (vow.getType() == VowType.GOD)
        {
            if (storageService.getSelectedGod() == GodAlignment.NONE)
            {
                storageService.setSelectedGod(vow.getGodAlignment());
            }
            if (storageService.getSelectedGod() != vow.getGodAlignment())
            {
                return;
            }
            storageService.setActiveMajorVow(vow);
            storageService.completeVow(vow);
            gained = pointsFor(vow);
            storageService.addPoints(gained);
        }
        else if (vow.getType() == VowType.PERMANENT)
        {
            if (VowRegistry.isMajorFillerId(vow.getId()))
            {
                storageService.setActiveMajorVow(vow);
            }
            storageService.completeVow(vow);
            gained = pointsFor(vow);
            storageService.addPoints(gained);
        }
        else if (vow.getType() == VowType.RITUAL)
        {
            // Rituals go "in progress" — points award happens on completion, not on pick.
            storageService.setActiveRitualVow(vow);
            ritualPending = true;
        }

        selectionPending = false;
        storageService.setSelectionOpen(false);
        hiddenCards.clear();

        if (onSelectionResolved != null)
        {
            String reveal;
            if (ritualPending)
            {
                reveal = "VowTaker: ritual accepted \u2014 \"" + vow.getName() + "\" is now IN PROGRESS ("
                    + pointsFor(vow) + " pts on completion). All combat is blocked until it is fulfilled.";
            }
            else
            {
                reveal = "VowTaker: sworn \"" + vow.getName() + "\" (+" + gained + " pts). It binds you permanently.";
            }
            StringBuilder passed = new StringBuilder();
            for (VowSelection s : snapshot)
            {
                if (s.getVow().getId().equals(vow.getId())) continue;
                if (passed.length() > 0) passed.append(" and ");
                passed.append(s.getVow().getName()).append(" (").append(pointsFor(s.getVow())).append(" pts)");
            }
            if (passed.length() > 0)
            {
                reveal += " You passed on: " + passed + ".";
            }
            onSelectionResolved.accept(reveal);
        }
    }

    public List<VowSelection> getHiddenCards()
    {
        return Collections.unmodifiableList(hiddenCards);
    }

    public boolean selectCardById(String vowId)
    {
        return applySelection(vowId);
    }

    public boolean hasPendingSelection()
    {
        return selectionPending;
    }

    public List<VowDefinition> getForecastedVows()
    {
        List<VowDefinition> all = new ArrayList<>();
        for (VowDefinition vow : VowRegistry.approvedVows(storageService))
        {
            if (!storageService.isCompleted(vow))
            {
                all.add(vow);
            }
        }
        return all;
    }

    public List<VowDefinition> getDraftQueue()
    {
        return VowRegistry.draftReviewQueue(storageService);
    }

    public List<VowDefinition> getSelectionChoices()
    {
        List<VowDefinition> choices = new ArrayList<>();
        for (VowDefinition vow : VowRegistry.approvedVows(storageService))
        {
            if (storageService.isCompleted(vow))
            {
                continue;
            }

            if (vow.getType() == VowType.PERMANENT || vow.getType() == VowType.RITUAL)
            {
                choices.add(vow);
            }
            else if (vow.getType() == VowType.GOD)
            {
                if (storageService.getSelectedGod() == GodAlignment.NONE)
                {
                    choices.add(vow);
                }
                else if (vow.getGodAlignment() == storageService.getSelectedGod())
                {
                    choices.add(vow);
                }
            }
        }
        return Collections.unmodifiableList(choices);
    }

    public boolean applySelection(String vowId)
    {
        if (vowId == null || vowId.trim().isEmpty())
        {
            return false;
        }

        VowDefinition vow = VowRegistry.getById(vowId);
        if (vow == null)
        {
            return false;
        }

        if (storageService.isCompleted(vow))
        {
            return false;
        }

        if (vow.getType() == VowType.GOD
            && storageService.getSelectedGod() != GodAlignment.NONE
            && storageService.getSelectedGod() != vow.getGodAlignment())
        {
            return false;
        }

        if (!storageService.isApproved(vowId))
        {
            storageService.approve(vowId);
        }

        selectCard(vow);
        return true;
    }

    public void setSelectedGod(GodAlignment god)
    {
        storageService.setSelectedGod(god);
    }

    /** Every draw offers three, so a brutal roll always leaves two alternatives before re-rolling. */
    private static int cardCountFor(DrawMode mode)
    {
        return 3;
    }

    private void buildHiddenCards(DrawMode mode)
    {
        buildHiddenCards(mode, Collections.emptySet());
    }

    /** {@code avoid} lets a re-roll guarantee the offer actually changes. */
    private void buildHiddenCards(DrawMode mode, Set<String> avoid)
    {
        List<VowDefinition> pool = buildPool(mode);
        int want = cardCountFor(mode);

        List<VowDefinition> fresh = new ArrayList<>();
        for (VowDefinition v : pool)
        {
            if (!avoid.contains(v.getId())) fresh.add(v);
        }
        // Only fall back to repeats if avoiding them can't fill the offer.
        if (fresh.size() < want) fresh = pool;

        hiddenCards.clear();
        Collections.shuffle(fresh);
        for (int i = 0; i < Math.min(want, fresh.size()); i++)
        {
            hiddenCards.add(new VowSelection(fresh.get(i), false));
        }
    }

    /** All uncompleted, approved vows eligible for the given draw mode. */
    private List<VowDefinition> buildPool(DrawMode mode)
    {
        Set<String> allowed = allowedSeveritiesFor(storageService.getCurrentRank());
        GodAlignment god = storageService.getSelectedGod();

        List<VowDefinition> godPool = new ArrayList<>();
        List<VowDefinition> majorFillerPool = new ArrayList<>();
        List<VowDefinition> minorPool = new ArrayList<>();

        for (VowDefinition vow : VowRegistry.approvedVows(storageService))
        {
            if (storageService.isCompleted(vow)) continue;

            if (vow.getType() == VowType.GOD)
            {
                if (god != GodAlignment.NONE && vow.getGodAlignment() == god)
                {
                    godPool.add(vow);
                }
            }
            else if (vow.getType() == VowType.PERMANENT)
            {
                if (VowRegistry.isMajorFillerId(vow.getId()))
                {
                    majorFillerPool.add(vow);
                }
                else
                {
                    minorPool.add(vow);
                }
            }
            else if (vow.getType() == VowType.RITUAL)
            {
                minorPool.add(vow);
            }
        }

        List<VowDefinition> pool;
        switch (mode)
        {
            case GOD_ONLY:
                pool = godPool.isEmpty() ? minorPool : godPool;
                break;
            case MAJOR_REVEALED:
                pool = new ArrayList<>();
                pool.addAll(godPool);
                pool.addAll(majorFillerPool);
                if (pool.isEmpty()) pool = minorPool;
                break;
            case MINOR:
            case BY_RANK_SEVERITY:
            default:
                pool = new ArrayList<>();
                for (VowDefinition v : minorPool)
                {
                    if (allowed.contains(v.getSeverity() == null ? "medium" : v.getSeverity().toLowerCase()))
                    {
                        pool.add(v);
                    }
                }
                if (pool.isEmpty()) pool = minorPool;
                if (pool.isEmpty() && !godPool.isEmpty()) pool = godPool;
                break;
        }
        return new ArrayList<>(pool);
    }

    /** Vow severities the given rank is allowed to draw from a general pool. */
    private static Set<String> allowedSeveritiesFor(Rank rank)
    {
        switch (rank)
        {
            case NONE:
            case FOLLOWER:
                return Set.of("light");
            case DEACON:
                return Set.of("light", "medium");
            case PRIEST:
                return Set.of("light", "medium");
            case BISHOP:
                return Set.of("medium", "hard");
            case ARCHBISHOP:
                return Set.of("medium", "hard");
            case CARDINAL:
                return Set.of("hard");
            case CHOSEN:
            default:
                return Set.of("hard");
        }
    }
}
