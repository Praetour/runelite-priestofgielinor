package com.vowtaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowType;
import com.vowtaker.service.VowRegistry;
import com.vowtaker.service.VowSelectionService;
import com.vowtaker.service.VowStorageService;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VowSmokeTest
{
    private VowSelectionService createSelectionService(VowStorageService storage) throws Exception
    {
        VowSelectionService selectionService = new VowSelectionService();
        Field field = VowSelectionService.class.getDeclaredField("storageService");
        field.setAccessible(true);
        field.set(selectionService, storage);
        selectionService.initialize();
        return selectionService;
    }

    private VowStorageService createStorage(Path tempDir) throws Exception
    {
        VowStorageService storage = new VowStorageService();
        java.lang.reflect.Method setter = VowStorageService.class.getDeclaredMethod("setStorageDirectoryOverride", Path.class);
        setter.setAccessible(true);
        setter.invoke(storage, tempDir.resolve("vowtaker"));
        storage.initializeSession("junit-" + UUID.randomUUID());
        return storage;
    }

    @Test
    void registryContainsDefaultApprovedVows(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        List<VowDefinition> approved = VowRegistry.approvedVows(storage);
        assertFalse(approved.isEmpty());
        assertTrue(approved.stream().anyMatch(v -> "apostle_saradomin".equals(v.getId())));
    }

    @Test
    void draftQueueIncludesPendingEntries(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        List<VowDefinition> draft = VowRegistry.draftReviewQueue(storage);
        // Every default-approved vow should not be in the draft queue.
        for (VowDefinition vow : draft)
        {
            assertFalse(storage.isApproved(vow.getId()));
        }
    }

    @Test
    void selectionServiceExposesQueues(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selectionService = createSelectionService(storage);

        List<VowDefinition> pending = selectionService.getPendingApprovalVows();
        List<VowDefinition> approved = selectionService.getApprovedVows();

        assertNotNull(pending);
        assertNotNull(approved);
    }

    @Test
    void vowDefinitionMetadataLoads()
    {
        VowDefinition vow = new VowDefinition(
            "test_vow",
            "Test Vow",
            "Example vow",
            VowType.PERMANENT,
            GodAlignment.NONE,
            "equipment",
            "milestone",
            "medium",
            true
        );

        assertTrue(vow.isDefaultApproved());
        assertEquals("equipment", vow.getCategory());
        assertEquals("milestone", vow.getTrigger());
    }

    @Test
    void directApprovalPersistsThroughStorage(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selectionService = createSelectionService(storage);

        VowDefinition vow = VowRegistry.getById("no_ranged_training");
        assertNotNull(vow);

        selectionService.approveVow(vow.getId());

        assertTrue(storage.isApproved(vow.getId()));
        assertTrue(selectionService.getApprovedVows().stream().anyMatch(v -> v.getId().equals(vow.getId())));
        assertTrue(selectionService.getPendingApprovalVows().stream().noneMatch(v -> v.getId().equals(vow.getId())));
    }

    @Test
    void everyGodHasAFullVowPool()
    {
        for (GodAlignment god : GodAlignment.values())
        {
            if (god == GodAlignment.NONE) continue;
            List<VowDefinition> pool = VowRegistry.godVows(god);
            assertTrue(pool.size() >= 10, god + " has only " + pool.size() + " god vows");
        }
    }

    @Test
    void majorVowPoolIsDeepEnoughToDrawFrom(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        long approvedPermanents = VowRegistry.approvedVows(storage).stream()
            .filter(v -> v.getType() == VowType.PERMANENT)
            .count();
        assertTrue(approvedPermanents >= 15, "only " + approvedPermanents + " approved permanent vows");
    }

    @Test
    void blockedTagsResolveAgainstTheItemBlocklist() throws Exception
    {
        com.vowtaker.service.ItemTagRegistry tags = new com.vowtaker.service.ItemTagRegistry();
        java.lang.reflect.Method setter = com.vowtaker.service.ItemTagRegistry.class
            .getDeclaredMethod("setDirectoryOverride", Path.class);
        setter.setAccessible(true);
        setter.invoke(tags, java.nio.file.Files.createTempDirectory("vowtags"));
        tags.initialize();

        assertTrue(tags.getTagCount() > 0);
        assertTrue(tags.blocks("boots", "wear", "Rune boots"));
        assertFalse(tags.blocks("boots", "drop", "Rune boots"));
        assertTrue(tags.blocks("food_t60", "eat", "Shark"));
        assertFalse(tags.blocks("food_t60", "eat", "Shrimps"));
        // Exclusions must beat substring matches.
        assertFalse(tags.blocks("armour_metal", "wear", "Rune pouch"));

        // Every tag a vow references must actually exist in the blocklist.
        for (VowDefinition vow : VowRegistry.all())
        {
            for (String tag : vow.getBlockedTags())
            {
                assertTrue(tags.getTagNames().contains(tag),
                    vow.getId() + " references unknown tag \"" + tag + "\"");
            }
        }
    }

    @Test
    void tagsDoNotCatchUnrelatedItems() throws Exception
    {
        com.vowtaker.service.ItemTagRegistry tags = new com.vowtaker.service.ItemTagRegistry();
        java.lang.reflect.Method setter = com.vowtaker.service.ItemTagRegistry.class
            .getDeclaredMethod("setDirectoryOverride", Path.class);
        setter.setAccessible(true);
        setter.invoke(tags, java.nio.file.Files.createTempDirectory("vowtags2"));
        tags.initialize();

        // Ammunition must never be caught by armour tier bands.
        assertFalse(tags.hasTag("armour_t10", -1, "Bronze arrow"));
        assertFalse(tags.hasTag("armour_t20", -1, "Steel dart"));
        assertFalse(tags.hasTag("armour_t30", -1, "Mithril bolts"));
        assertFalse(tags.hasTag("armour_t40", -1, "Adamant arrow"));
        assertFalse(tags.hasTag("armour_t50", -1, "Rune pouch"));
        assertFalse(tags.hasTag("armour_t60", -1, "Dragon bones"));
        // Black mask and black d'hide are not tier 30 melee armour.
        assertFalse(tags.hasTag("armour_t30", -1, "Black mask"));
        assertFalse(tags.hasTag("armour_t30", -1, "Black d'hide body"));
        // Enchanted bolts must not catch the salve amulet variants.
        assertFalse(tags.hasTag("ammo_enchanted_bolts", -1, "Salve amulet (e)"));
        assertTrue(tags.hasTag("ammo_enchanted_bolts", -1, "Ruby bolts (e)"));

        // Positive spot checks across the new bands.
        assertTrue(tags.hasTag("armour_t50", -1, "Rune platebody"));
        assertTrue(tags.hasTag("armour_t70", -1, "Dharok's platebody"));
        assertTrue(tags.hasTag("armour_magic", -1, "Mystic robe top"));
        assertTrue(tags.hasTag("armour_ranged", -1, "Black d'hide body"));
        assertTrue(tags.hasTag("potion_antipoison", -1, "Anti-venom+(4)"));
        assertTrue(tags.hasTag("tool_high_tier", -1, "Dragon pickaxe"));
    }

    private com.vowtaker.service.TaskService createTaskService(VowStorageService storage,
                                                               VowSelectionService selection) throws Exception
    {
        com.vowtaker.service.TaskService taskService = new com.vowtaker.service.TaskService();
        for (String field : new String[]{"storage", "selection"})
        {
            Field f = com.vowtaker.service.TaskService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(taskService, "storage".equals(field) ? storage : selection);
        }
        return taskService;
    }

    @Test
    void promotionRecoversASaveThatIsAlreadyEligible(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        com.vowtaker.service.TaskService tasks = createTaskService(storage, selection);

        // Reproduces a save left behind by an earlier build: milestone done, threshold met,
        // rank never advanced. There is no completion event left to fire.
        storage.setSelectedGod(GodAlignment.ZAMORAK);
        storage.setCurrentRank(com.vowtaker.model.Rank.FOLLOWER);
        storage.addPoints(50);
        storage.completeTask("m1_brutus");
        assertEquals(com.vowtaker.model.Rank.FOLLOWER, storage.getCurrentRank());

        tasks.checkPromotion();

        assertEquals(com.vowtaker.model.Rank.DEACON, storage.getCurrentRank());
    }

    @Test
    void promotionWaitsForBothPointsAndMilestone(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        com.vowtaker.service.TaskService tasks = createTaskService(storage, selection);

        storage.setSelectedGod(GodAlignment.ZAMORAK);
        storage.setCurrentRank(com.vowtaker.model.Rank.FOLLOWER);

        // Milestone done but short on points.
        storage.completeTask("m1_brutus");
        storage.addPoints(49);
        tasks.checkPromotion();
        assertEquals(com.vowtaker.model.Rank.FOLLOWER, storage.getCurrentRank());

        // Threshold reached: the next check promotes, no completion event required.
        storage.addPoints(1);
        tasks.checkPromotion();
        assertEquals(com.vowtaker.model.Rank.DEACON, storage.getCurrentRank());
    }

    @Test
    void everyVowStaysSwornAndEnforced(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.ZAMORAK);

        assertTrue(selection.applySelection("mark_of_blood"));
        assertTrue(selection.applySelection("chaos_tethered"));
        assertTrue(selection.applySelection("no_teleport_spells"));

        // Taking a second major must not displace the first: nothing is ever retired.
        java.util.List<String> sworn = new java.util.ArrayList<>();
        for (VowDefinition v : storage.getSwornVows()) sworn.add(v.getId());

        assertTrue(sworn.contains("mark_of_blood"));
        assertTrue(sworn.contains("chaos_tethered"));
        assertTrue(sworn.contains("no_teleport_spells"));
        assertEquals(3, sworn.size());
    }

    @Test
    void swornListPutsMajorsBeforeMinors(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.ZAMORAK);

        assertTrue(selection.applySelection("no_teleport_spells"));
        assertTrue(selection.applySelection("mark_of_blood"));

        java.util.List<VowDefinition> sworn = storage.getSwornVows();
        assertTrue(VowRegistry.isMajor(sworn.get(0)));
        assertFalse(VowRegistry.isMajor(sworn.get(sworn.size() - 1)));
    }

    @Test
    void everyDrawOffersThreeCards(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.SARADOMIN);

        selection.forceOpenSelection(VowSelectionService.DrawMode.MAJOR_REVEALED);
        assertEquals(3, selection.getHiddenCards().size());
        selection.applySelection(selection.getHiddenCards().get(0).getVow().getId());

        selection.forceOpenSelection(VowSelectionService.DrawMode.MINOR);
        assertEquals(3, selection.getHiddenCards().size());
    }

    @Test
    void resetClearsEverything(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.ZAMORAK);
        selection.applySelection("mark_of_blood");
        storage.completeTask("kill_chicken");
        storage.addPoints(40);

        storage.resetAll();

        assertEquals(0, storage.getTotalPoints());
        assertEquals(GodAlignment.NONE, storage.getSelectedGod());
        assertEquals(com.vowtaker.model.Rank.NONE, storage.getCurrentRank());
        assertTrue(storage.getSwornVows().isEmpty());
        assertTrue(storage.getCompletedTaskIds().isEmpty());
        assertEquals(0, storage.getHighestQuartileFired());
    }

    @Test
    void versionCompareNeverTriggersADowngrade()
    {
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("1.0.1", "1.0.0") > 0);
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("1.1.0", "1.0.9") > 0);
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("2.0.0", "1.9.9") > 0);
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("1.0.10", "1.0.9") > 0);
        assertEquals(0, com.vowtaker.update.VowTakerUpdater.compareVersions("1.0.0", "1.0.0"));
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("1.0.0", "1.0.1") < 0);
        // Shorter strings pad with zeroes rather than mis-sorting.
        assertEquals(0, com.vowtaker.update.VowTakerUpdater.compareVersions("1.0", "1.0.0"));
        assertTrue(com.vowtaker.update.VowTakerUpdater.compareVersions("1.0.1", "1.0") > 0);
    }

    @Test
    void everyVowIsActuallyEnforced() throws Exception
    {
        com.vowtaker.service.ItemTagRegistry tags = new com.vowtaker.service.ItemTagRegistry();
        java.lang.reflect.Method setter = com.vowtaker.service.ItemTagRegistry.class
            .getDeclaredMethod("setDirectoryOverride", Path.class);
        setter.setAccessible(true);
        setter.invoke(tags, java.nio.file.Files.createTempDirectory("vowtags3"));
        tags.initialize();

        java.util.List<String> inert = new java.util.ArrayList<>();
        for (VowDefinition vow : VowRegistry.all())
        {
            // Rituals are location objectives, not menu restrictions.
            if (vow.getType() == VowType.RITUAL) continue;

            boolean hasTag = !vow.getBlockedTags().isEmpty();
            boolean hasRule = com.vowtaker.service.VowEnforcementService.RULE_ENFORCED_IDS.contains(vow.getId());
            if (!hasTag && !hasRule) inert.add(vow.getId());
        }

        assertTrue(inert.isEmpty(), "vows with no enforcement at all: " + inert);
    }

    @Test
    void spellcastVowsMatchTheCastOptionNotTheSpellName() throws Exception
    {
        com.vowtaker.service.ItemTagRegistry tags = new com.vowtaker.service.ItemTagRegistry();
        java.lang.reflect.Method setter = com.vowtaker.service.ItemTagRegistry.class
            .getDeclaredMethod("setDirectoryOverride", Path.class);
        setter.setAccessible(true);
        setter.invoke(tags, java.nio.file.Files.createTempDirectory("vowtags4"));
        tags.initialize();

        // A spellbook cast is option="Cast", target="Teleport to House".
        assertTrue(tags.blocks("travel_poh", "cast", "Teleport to House"));
        assertTrue(tags.blocks("travel_poh", "break", "House teleport"));
        assertTrue(tags.blocks("travel_poh", "teleport", "Mounted glory"));
        assertFalse(tags.blocks("travel_poh", "cast", "Varrock Teleport"));

        // Prayer targets are the bare prayer name.
        assertTrue(tags.blocks("prayer_any", "activate", "Protect from Melee"));
        assertTrue(tags.blocks("prayer_any", "activate", "Piety"));
        assertFalse(tags.blocks("prayer_any", "wear", "Piety"));
    }

    @Test
    void noVowIsOfferedBeforeAGodIsChosen(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        assertEquals(GodAlignment.NONE, storage.getSelectedGod());

        // Legacy skill-milestone trigger must never queue a draw.
        selection.tryQueueSelection();
        selection.tryQueueSelection();
        selection.tryQueueSelection();
        selection.tryQueueSelection();
        assertFalse(selection.hasPendingForcedOpen());

        // Nor may any explicit non-god draw open one.
        selection.forceOpenSelection(VowSelectionService.DrawMode.MINOR);
        assertFalse(selection.hasPendingSelection());
        selection.forceOpenSelection(VowSelectionService.DrawMode.MAJOR_REVEALED);
        assertFalse(selection.hasPendingSelection());

        // The god's own first draw is the one thing allowed.
        storage.setSelectedGod(GodAlignment.ZAMORAK);
        selection.forceOpenSelection(VowSelectionService.DrawMode.GOD_ONLY);
        assertTrue(selection.hasPendingSelection());
    }

    @Test
    void rerollSpendsATokenAndChangesTheOffer(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.SARADOMIN);

        selection.forceOpenSelection(VowSelectionService.DrawMode.MAJOR_REVEALED);
        java.util.Set<String> before = new java.util.HashSet<>();
        for (com.vowtaker.model.VowSelection s : selection.getHiddenCards()) before.add(s.getVow().getId());

        int tokens = storage.getRerollTokens();
        assertTrue(tokens > 0);
        assertTrue(selection.rerollSelection());
        assertEquals(tokens - 1, storage.getRerollTokens());
        assertEquals(3, selection.getHiddenCards().size());

        java.util.Set<String> after = new java.util.HashSet<>();
        for (com.vowtaker.model.VowSelection s : selection.getHiddenCards()) after.add(s.getVow().getId());
        assertFalse(before.equals(after));
    }
}
