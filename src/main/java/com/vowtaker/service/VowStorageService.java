package com.vowtaker.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.Rank;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowType;
import com.vowtaker.util.VowJson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;

@Singleton
public class VowStorageService
{
    private static final String STORAGE_DIR = "vowtaker";
    private static final String STORAGE_FILE = "vow_state.json";

    private final Gson gson;
    private final Set<String> completedPermanentVows = new HashSet<>();
    private final Set<String> completedGodVows = new HashSet<>();
    private final Set<String> completedRitualVows = new HashSet<>();
    private final Set<String> approvedVowIds = new HashSet<>();
    private final Set<String> declinedVowIds = new HashSet<>();
    private final Set<String> completedTaskIds = new HashSet<>();
    private final Set<String> unlockedGearPatterns = new HashSet<>();
    // Majors that were replaced by a later pick. Kept so the panel can show your history.
    private final Set<String> retiredMajorVows = new HashSet<>();
    private int totalPoints;
    private Rank currentRank = Rank.NONE;
    private int highestQuartileFired;
    private boolean approvalsInitialized;
    private boolean vowsFulfilledAnnounced;
    // Set when a milestone task is completed; consumed by the next resolved card pick to promote.
    private boolean pendingPromotion;
    private int rerollTokens = 1;
    private String sessionKey = "local";
    private String adoptedLegacySave;
    private GodAlignment selectedGod = GodAlignment.NONE;
    private VowDefinition activePermanentVow;
    private VowDefinition activeGodVow;
    private VowDefinition activeRitualVow;
    private boolean selectionOpen;    private Path storageDirectoryOverride;
    /** Fired every time totalPoints changes; the plugin wires this to trigger a promotion re-check. */
    private Runnable pointsListener;

    public VowStorageService()
    {
        this.gson = VowJson.createGson();
    }

    public void setPointsListener(Runnable r)
    {
        this.pointsListener = r;
    }

    public void initializeSession(String accountName)
    {
        String key = accountName == null ? "" : accountName.trim();
        // A random key here would mint a brand-new empty save on every launch, because the client
        // isn't logged in yet when the plugin starts. Fall back to a stable shared key instead.
        sessionKey = key.isEmpty() ? "default" : sanitiseKey(key);
        adoptLegacySaveIfNeeded();
        load();
    }

    /** True when a save file already exists for the current session key. */
    private boolean saveExists()
    {
        try
        {
            return resolveDirectory().resolve(sessionKey + "_" + STORAGE_FILE).toFile().exists();
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * One-time rescue for progress stranded under the old random per-launch keys: if this account
     * has no save yet, adopt the most recently written {@code local-*} file.
     */
    private void adoptLegacySaveIfNeeded()
    {
        if (saveExists()) return;
        try
        {
            Path dir = resolveDirectory();
            File newest = null;
            File[] files = dir.toFile().listFiles((d, name) ->
                name.startsWith("local-") && name.endsWith("_" + STORAGE_FILE));
            if (files == null) return;
            for (File f : files)
            {
                if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
            }
            if (newest != null)
            {
                Files.copy(newest.toPath(), dir.resolve(sessionKey + "_" + STORAGE_FILE));
                adoptedLegacySave = newest.getName();
            }
        }
        catch (IOException ignored)
        {
            // Migration is best-effort; a fresh profile is an acceptable outcome.
        }
    }

    /** Name of the legacy file adopted on this run, or null. Consumed once by the plugin. */
    public String consumeAdoptedLegacySave()
    {
        String s = adoptedLegacySave;
        adoptedLegacySave = null;
        return s;
    }

    private static String sanitiseKey(String raw)
    {
        return raw.replace('\u00A0', ' ').trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public String getSessionKey()
    {
        return sessionKey;
    }

    private Path resolveDirectory() throws IOException
    {
        Path directory;
        if (storageDirectoryOverride != null)
        {
            directory = storageDirectoryOverride;
        }
        else
        {
            File base = RuneLite.RUNELITE_DIR != null ? RuneLite.RUNELITE_DIR : new File(System.getProperty("user.home"), ".runelite");
            directory = base.toPath().resolve(STORAGE_DIR);
        }
        Files.createDirectories(directory);
        return directory;
    }

    /**
     * Package-private test override.
     */
    void setStorageDirectoryOverride(Path directory)
    {
        this.storageDirectoryOverride = directory;
    }

    public void load()
    {
        try
        {
            Path directory = resolveDirectory();
            File file = directory.resolve(sessionKey + "_" + STORAGE_FILE).toFile();
            if (!file.exists())
            {
                if (!approvalsInitialized)
                {
                    seedDefaultApprovals();
                }
                save();
                return;
            }

            try (FileReader reader = new FileReader(file))
            {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                if (root.has("selectedGod"))
                {
                    selectedGod = GodAlignment.valueOf(root.get("selectedGod").getAsString());
                }
                if (root.has("completedPermanentVows"))
                {
                    completedPermanentVows.clear();
                    for (var element : root.getAsJsonArray("completedPermanentVows"))
                    {
                        completedPermanentVows.add(element.getAsString());
                    }
                }
                if (root.has("completedGodVows"))
                {
                    completedGodVows.clear();
                    for (var element : root.getAsJsonArray("completedGodVows"))
                    {
                        completedGodVows.add(element.getAsString());
                    }
                }
                if (root.has("completedRitualVows"))
                {
                    completedRitualVows.clear();
                    for (var element : root.getAsJsonArray("completedRitualVows"))
                    {
                        completedRitualVows.add(element.getAsString());
                    }
                }
                if (root.has("activePermanentVow"))
                {
                    activePermanentVow = VowRegistry.getById(root.get("activePermanentVow").getAsString());
                }
                if (root.has("activeGodVow"))
                {
                    activeGodVow = VowRegistry.getById(root.get("activeGodVow").getAsString());
                }
                if (root.has("activeRitualVow"))
                {
                    activeRitualVow = VowRegistry.getById(root.get("activeRitualVow").getAsString());
                }
                if (root.has("selectionOpen"))
                {
                    selectionOpen = root.get("selectionOpen").getAsBoolean();
                }
                if (root.has("approvedVowIds"))
                {
                    approvedVowIds.clear();
                    for (var element : root.getAsJsonArray("approvedVowIds"))
                    {
                        approvedVowIds.add(element.getAsString());
                    }
                    approvalsInitialized = true;
                }
                if (root.has("declinedVowIds"))
                {
                    declinedVowIds.clear();
                    for (var element : root.getAsJsonArray("declinedVowIds"))
                    {
                        declinedVowIds.add(element.getAsString());
                    }
                }
                if (root.has("completedTaskIds"))
                {
                    completedTaskIds.clear();
                    for (var element : root.getAsJsonArray("completedTaskIds"))
                    {
                        completedTaskIds.add(element.getAsString());
                    }
                }
                if (root.has("unlockedGearPatterns"))
                {
                    unlockedGearPatterns.clear();
                    for (var element : root.getAsJsonArray("unlockedGearPatterns"))
                    {
                        unlockedGearPatterns.add(element.getAsString());
                    }
                }
                if (root.has("totalPoints"))
                {
                    totalPoints = root.get("totalPoints").getAsInt();
                }
                if (root.has("highestQuartileFired"))
                {
                    highestQuartileFired = root.get("highestQuartileFired").getAsInt();
                }
                if (root.has("currentRank"))
                {
                    try
                    {
                        currentRank = Rank.valueOf(root.get("currentRank").getAsString());
                    }
                    catch (IllegalArgumentException ignored)
                    {
                        currentRank = Rank.NONE;
                    }
                }
                if (root.has("vowsFulfilledAnnounced"))
                {
                    vowsFulfilledAnnounced = root.get("vowsFulfilledAnnounced").getAsBoolean();
                }
                if (root.has("pendingPromotion"))
                {
                    pendingPromotion = root.get("pendingPromotion").getAsBoolean();
                }
                if (root.has("rerollTokens"))
                {
                    rerollTokens = root.get("rerollTokens").getAsInt();
                }
                if (root.has("retiredMajorVows"))
                {
                    retiredMajorVows.clear();
                    for (var element : root.getAsJsonArray("retiredMajorVows"))
                    {
                        retiredMajorVows.add(element.getAsString());
                    }
                }
            }
            if (!approvalsInitialized)
            {
                seedDefaultApprovals();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void save()
    {
        try
        {
            Path directory = resolveDirectory();
            File file = directory.resolve(sessionKey + "_" + STORAGE_FILE).toFile();

            JsonObject root = new JsonObject();
            root.addProperty("selectedGod", selectedGod.name());
            root.add("completedPermanentVows", toJsonArray(completedPermanentVows));
            root.add("completedGodVows", toJsonArray(completedGodVows));
            root.add("completedRitualVows", toJsonArray(completedRitualVows));

            if (activePermanentVow != null)
            {
                root.addProperty("activePermanentVow", activePermanentVow.getId());
            }
            if (activeGodVow != null)
            {
                root.addProperty("activeGodVow", activeGodVow.getId());
            }
            if (activeRitualVow != null)
            {
                root.addProperty("activeRitualVow", activeRitualVow.getId());
            }
            root.addProperty("selectionOpen", selectionOpen);
            root.add("approvedVowIds", toJsonArray(approvedVowIds));
            root.add("declinedVowIds", toJsonArray(declinedVowIds));
            root.add("completedTaskIds", toJsonArray(completedTaskIds));
            root.add("unlockedGearPatterns", toJsonArray(unlockedGearPatterns));
            root.addProperty("totalPoints", totalPoints);
            root.addProperty("highestQuartileFired", highestQuartileFired);
            root.addProperty("currentRank", currentRank.name());
            root.addProperty("vowsFulfilledAnnounced", vowsFulfilledAnnounced);
            root.addProperty("pendingPromotion", pendingPromotion);
            root.addProperty("rerollTokens", rerollTokens);
            root.add("retiredMajorVows", toJsonArray(retiredMajorVows));

            try (FileWriter writer = new FileWriter(file))
            {
                gson.toJson(root, writer);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public boolean isApproved(String vowId)
    {
        return vowId != null && approvedVowIds.contains(vowId);
    }

    public boolean isDeclined(String vowId)
    {
        return vowId != null && declinedVowIds.contains(vowId);
    }

    public void approve(String vowId)
    {
        if (vowId == null)
        {
            return;
        }
        approvedVowIds.add(vowId);
        declinedVowIds.remove(vowId);
        save();
    }

    public void decline(String vowId)
    {
        if (vowId == null)
        {
            return;
        }
        approvedVowIds.remove(vowId);
        declinedVowIds.add(vowId);
        save();
    }

    public Set<String> getApprovedVowIds()
    {
        return Collections.unmodifiableSet(approvedVowIds);
    }

    public Set<String> getDeclinedVowIds()
    {
        return Collections.unmodifiableSet(declinedVowIds);
    }

    private void seedDefaultApprovals()
    {
        for (VowDefinition vow : VowRegistry.all())
        {
            if (vow.isDefaultApproved())
            {
                approvedVowIds.add(vow.getId());
            }
        }
        approvalsInitialized = true;
    }

    public void setSelectedGod(GodAlignment god)
    {
        this.selectedGod = god;
        save();
    }

    /** Wipe every persisted field back to defaults and reseed approvals. */
    public void resetAll()
    {
        selectedGod = GodAlignment.NONE;
        activePermanentVow = null;
        activeGodVow = null;
        activeRitualVow = null;
        selectionOpen = false;
        completedPermanentVows.clear();
        completedGodVows.clear();
        completedRitualVows.clear();
        approvedVowIds.clear();
        declinedVowIds.clear();
        completedTaskIds.clear();
        totalPoints = 0;
        highestQuartileFired = 0;
        currentRank = Rank.NONE;
        approvalsInitialized = false;
        vowsFulfilledAnnounced = false;
        pendingPromotion = false;
        rerollTokens = 1;
        retiredMajorVows.clear();
        unlockedGearPatterns.clear();
        seedDefaultApprovals();
        save();
    }

    public boolean isTaskCompleted(String taskId)
    {
        return taskId != null && completedTaskIds.contains(taskId);
    }

    public boolean completeTask(String taskId)
    {
        if (taskId == null || completedTaskIds.contains(taskId))
        {
            return false;
        }
        completedTaskIds.add(taskId);
        save();
        return true;
    }

    public boolean uncompleteTask(String taskId)
    {
        if (taskId == null || !completedTaskIds.contains(taskId))
        {
            return false;
        }
        completedTaskIds.remove(taskId);
        save();
        return true;
    }

    public void subtractPoints(int points)
    {
        totalPoints = Math.max(0, totalPoints - Math.max(0, points));
        save();
    }

    public Set<String> getCompletedTaskIds()
    {
        return Collections.unmodifiableSet(completedTaskIds);
    }

    public Set<String> getUnlockedGearPatterns()
    {
        return Collections.unmodifiableSet(unlockedGearPatterns);
    }

    /** Adds gear-name patterns that override the rank gear ceiling for matching items. */
    public boolean addUnlockedGearPatterns(java.util.Collection<String> patterns)
    {
        if (patterns == null || patterns.isEmpty()) return false;
        boolean changed = false;
        for (String p : patterns)
        {
            if (p == null || p.isEmpty()) continue;
            if (unlockedGearPatterns.add(p.toLowerCase())) changed = true;
        }
        if (changed) save();
        return changed;
    }

    public int getTotalPoints()
    {
        return totalPoints;
    }

    public void addPoints(int points)
    {
        totalPoints += Math.max(0, points);
        save();
        if (pointsListener != null)
        {
            pointsListener.run();
        }
    }

    public Rank getCurrentRank()
    {
        if (currentRank == Rank.NONE && selectedGod != GodAlignment.NONE)
        {
            currentRank = Rank.FOLLOWER;
            save();
        }
        return currentRank;
    }

    public void setCurrentRank(Rank rank)
    {
        Rank previous = this.currentRank;
        this.currentRank = rank == null ? Rank.NONE : rank;
        if (previous != this.currentRank)
        {
            highestQuartileFired = 0;
        }
        save();
    }

    public int getHighestQuartileFired()
    {
        return highestQuartileFired;
    }

    public void setHighestQuartileFired(int quartile)
    {
        this.highestQuartileFired = Math.max(0, quartile);
        save();
    }

    public boolean isVowsFulfilledAnnounced()
    {
        return vowsFulfilledAnnounced;
    }

    public void setVowsFulfilledAnnounced(boolean announced)
    {
        this.vowsFulfilledAnnounced = announced;
        save();
    }

    public boolean isPendingPromotion()
    {
        return pendingPromotion;
    }

    public void setPendingPromotion(boolean pending)
    {
        this.pendingPromotion = pending;
        save();
    }

    public int getRerollTokens()
    {
        return rerollTokens;
    }

    public void grantRerollToken()
    {
        rerollTokens++;
        save();
    }

    public boolean consumeRerollToken()
    {
        if (rerollTokens <= 0) return false;
        rerollTokens--;
        save();
        return true;
    }

    public GodAlignment getSelectedGod()
    {
        return selectedGod;
    }

    public void completeVow(VowDefinition vow)
    {
        if (vow == null)
        {
            return;
        }

        switch (vow.getType())
        {
            case PERMANENT:
                completedPermanentVows.add(vow.getId());
                break;
            case GOD:
                completedGodVows.add(vow.getId());
                break;
            case RITUAL:
                completedRitualVows.add(vow.getId());
                break;
            default:
                break;
        }
        save();
    }

    public boolean isCompleted(VowDefinition vow)
    {
        if (vow == null)
        {
            return false;
        }
        switch (vow.getType())
        {
            case PERMANENT:
                return completedPermanentVows.contains(vow.getId());
            case GOD:
                return completedGodVows.contains(vow.getId());
            case RITUAL:
                return completedRitualVows.contains(vow.getId());
            default:
                return false;
        }
    }

    public void setActivePermanentVow(VowDefinition vow)
    {
        this.activePermanentVow = vow;
        save();
    }

    public void setActiveGodVow(VowDefinition vow)
    {
        this.activeGodVow = vow;
        save();
    }

    public void setActiveRitualVow(VowDefinition vow)
    {
        this.activeRitualVow = vow;
        save();
    }

    public void clearActiveRitualVow()
    {
        this.activeRitualVow = null;
        save();
    }

    /** True while a ritual card is picked but the ritual objective hasn't been fulfilled in-world yet. */
    public boolean hasPendingRitual()
    {
        if (activeRitualVow == null) return false;
        return !completedRitualVows.contains(activeRitualVow.getId());
    }

    public VowDefinition getActivePermanentVow()
    {
        return activePermanentVow;
    }

    public VowDefinition getActiveGodVow()
    {
        return activeGodVow;
    }

    public VowDefinition getActiveRitualVow()
    {
        return activeRitualVow;
    }

    /**
     * Vows currently binding you: every minor permanent ever taken, plus the single live Major.
     * Retired Majors stop being enforced but stay completed so they never re-enter the draw pool.
     */
    public List<VowDefinition> getSwornVows()
    {
        List<VowDefinition> out = new ArrayList<>();
        for (String id : completedPermanentVows)
        {
            if (VowRegistry.isMajorFillerId(id)) continue;
            VowDefinition v = VowRegistry.getById(id);
            if (v != null) out.add(v);
        }
        VowDefinition major = getActiveMajorVow();
        if (major != null) out.add(major);
        return out;
    }

    /** The one Major (god vow or heavy permanent) currently in force, or null. */
    public VowDefinition getActiveMajorVow()
    {
        if (activeGodVow != null) return activeGodVow;
        if (activePermanentVow != null && VowRegistry.isMajorFillerId(activePermanentVow.getId()))
        {
            return activePermanentVow;
        }
        return null;
    }

    /**
     * Swap in a new Major, retiring whatever was worn before it.
     *
     * @return the retired vow, or null if this is the player's first Major
     */
    public VowDefinition swapActiveMajorVow(VowDefinition incoming)
    {
        VowDefinition previous = getActiveMajorVow();
        if (previous != null && !previous.getId().equals(incoming == null ? null : incoming.getId()))
        {
            retiredMajorVows.add(previous.getId());
        }

        activeGodVow = null;
        activePermanentVow = null;
        if (incoming != null)
        {
            if (incoming.getType() == VowType.GOD)
            {
                activeGodVow = incoming;
            }
            else
            {
                activePermanentVow = incoming;
            }
        }
        save();
        return previous == null || previous.equals(incoming) ? null : previous;
    }

    public Set<String> getRetiredMajorVows()
    {
        return Collections.unmodifiableSet(retiredMajorVows);
    }

    public void setSelectionOpen(boolean selectionOpen)
    {
        this.selectionOpen = selectionOpen;
        save();
    }

    public boolean isSelectionOpen()
    {
        return selectionOpen;
    }

    public List<VowDefinition> getCompletedVows()
    {
        List<VowDefinition> result = new ArrayList<>();
        for (String id : completedPermanentVows)
        {
            VowDefinition vow = VowRegistry.getById(id);
            if (vow != null)
            {
                result.add(vow);
            }
        }
        for (String id : completedGodVows)
        {
            VowDefinition vow = VowRegistry.getById(id);
            if (vow != null)
            {
                result.add(vow);
            }
        }
        for (String id : completedRitualVows)
        {
            VowDefinition vow = VowRegistry.getById(id);
            if (vow != null)
            {
                result.add(vow);
            }
        }
        return result;
    }

    private JsonArray toJsonArray(Set<String> values)
    {
        JsonArray array = new JsonArray();
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        for (String value : sorted)
        {
            array.add(value);
        }
        return array;
    }
}
