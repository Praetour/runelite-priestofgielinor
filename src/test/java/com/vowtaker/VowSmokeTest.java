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
    void swearingAMajorRetiresThePreviousOne(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.ZAMORAK);

        assertTrue(selection.applySelection("mark_of_blood"));
        assertEquals("mark_of_blood", storage.getActiveMajorVow().getId());
        assertTrue(storage.getSwornVows().stream().anyMatch(v -> "mark_of_blood".equals(v.getId())));

        assertTrue(selection.applySelection("chaos_tethered"));
        assertEquals("chaos_tethered", storage.getActiveMajorVow().getId());
        // The old Major stops binding you but stays completed so it can never be drawn again.
        assertTrue(storage.getRetiredMajorVows().contains("mark_of_blood"));
        assertTrue(storage.getSwornVows().stream().noneMatch(v -> "mark_of_blood".equals(v.getId())));
        assertTrue(storage.isCompleted(VowRegistry.getById("mark_of_blood")));
    }

    @Test
    void minorVowsKeepStacking(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.ZAMORAK);

        assertTrue(selection.applySelection("no_teleport_spells"));
        assertTrue(selection.applySelection("no_fairy_rings"));

        assertTrue(storage.getSwornVows().stream().anyMatch(v -> "no_teleport_spells".equals(v.getId())));
        assertTrue(storage.getSwornVows().stream().anyMatch(v -> "no_fairy_rings".equals(v.getId())));
        assertTrue(storage.getRetiredMajorVows().isEmpty());
    }

    @Test
    void majorPickerOffersOneCardAndMinorOffersThree(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.SARADOMIN);

        selection.forceOpenSelection(VowSelectionService.DrawMode.MAJOR_REVEALED);
        assertTrue(selection.hasPendingSelection());
        assertEquals(1, selection.getHiddenCards().size());

        selection.applySelection(selection.getHiddenCards().get(0).getVow().getId());

        selection.forceOpenSelection(VowSelectionService.DrawMode.MINOR);
        assertTrue(selection.hasPendingSelection());
        assertEquals(3, selection.getHiddenCards().size());
    }

    @Test
    void rerollSpendsATokenAndChangesTheOffer(@TempDir Path tempDir) throws Exception
    {
        VowStorageService storage = createStorage(tempDir);
        VowSelectionService selection = createSelectionService(storage);
        storage.setSelectedGod(GodAlignment.SARADOMIN);

        selection.forceOpenSelection(VowSelectionService.DrawMode.MAJOR_REVEALED);
        String before = selection.getHiddenCards().get(0).getVow().getId();
        int tokens = storage.getRerollTokens();
        assertTrue(tokens > 0);

        assertTrue(selection.rerollSelection());
        assertEquals(tokens - 1, storage.getRerollTokens());
        assertEquals(1, selection.getHiddenCards().size());
        assertFalse(before.equals(selection.getHiddenCards().get(0).getVow().getId()));
    }
}
