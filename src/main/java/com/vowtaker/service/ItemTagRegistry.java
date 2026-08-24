package com.vowtaker.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.RuneLite;

/**
 * User-editable item blocklist. Tags map a label ("boots", "food_t60") to the item names/ids it
 * covers and the menu options it gates. Vows reference tags, so adding an item to a tag in the
 * on-disk copy immediately affects every vow that blocks that tag.
 */
@Singleton
public class ItemTagRegistry
{
    private static final String STORAGE_DIR = "vowtaker";
    private static final String FILE_NAME = "item-tags.json";
    private static final String RESOURCE = "/com/vowtaker/item-tags.json";

    @Inject
    private Client client;

    private final Map<String, Tag> tags = new LinkedHashMap<>();
    private Path directoryOverride;
    private String lastLoadError;

    /** Load bundled defaults, then merge the user's on-disk copy over them. */
    public void initialize()
    {
        tags.clear();
        lastLoadError = null;

        JsonObject defaults = readResource();
        if (defaults != null)
        {
            merge(defaults);
        }

        Path userFile = userFilePath();
        if (userFile != null)
        {
            if (!Files.exists(userFile))
            {
                writeDefaultTemplate(userFile);
            }
            else
            {
                JsonObject user = readFile(userFile);
                if (user != null)
                {
                    merge(user);
                }
            }
        }
    }

    /** Absolute path of the editable copy, or null if it could not be resolved. */
    public String getUserFileLocation()
    {
        Path p = userFilePath();
        return p == null ? "<unavailable>" : p.toString();
    }

    public String getLastLoadError()
    {
        return lastLoadError;
    }

    public int getTagCount()
    {
        return tags.size();
    }

    public Set<String> getTagNames()
    {
        return Collections.unmodifiableSet(tags.keySet());
    }

    /**
     * True when the given menu action targets an item covered by the tag.
     *
     * @param tagName tag to test
     * @param option  menu option, lowercase (e.g. "wear")
     * @param target  menu target, lowercase; may carry colour tags which are ignored
     */
    public boolean blocks(String tagName, String option, String target)
    {
        Tag tag = tags.get(normalise(tagName));
        if (tag == null) return false;
        if (!tag.gatesOption(option)) return false;
        return tag.matchesName(cleanTarget(target));
    }

    /** True when an item id/name carries the tag, ignoring menu options. Used for inventory sweeps. */
    public boolean hasTag(String tagName, int itemId, String itemName)
    {
        Tag tag = tags.get(normalise(tagName));
        if (tag == null) return false;
        if (itemId > 0 && tag.ids.contains(itemId)) return true;
        return itemName != null && tag.matchesName(itemName.toLowerCase());
    }

    /** Resolves an item id to its name via the client cache, or null when unavailable. */
    public String itemName(int itemId)
    {
        if (client == null || itemId <= 0) return null;
        ItemComposition comp = client.getItemDefinition(itemId);
        return comp == null ? null : comp.getName();
    }

    void setDirectoryOverride(Path dir)
    {
        this.directoryOverride = dir;
    }

    private Path userFilePath()
    {
        try
        {
            Path dir;
            if (directoryOverride != null)
            {
                dir = directoryOverride;
            }
            else
            {
                File base = RuneLite.RUNELITE_DIR != null
                    ? RuneLite.RUNELITE_DIR
                    : new File(System.getProperty("user.home"), ".runelite");
                dir = base.toPath().resolve(STORAGE_DIR);
            }
            Files.createDirectories(dir);
            return dir.resolve(FILE_NAME);
        }
        catch (IOException e)
        {
            lastLoadError = "could not resolve storage directory: " + e.getMessage();
            return null;
        }
    }

    private JsonObject readResource()
    {
        try (InputStream in = ItemTagRegistry.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                lastLoadError = "bundled " + RESOURCE + " is missing";
                return null;
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
            {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        }
        catch (Exception e)
        {
            lastLoadError = "bundled tag file unreadable: " + e.getMessage();
            return null;
        }
    }

    private JsonObject readFile(Path file)
    {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
        catch (Exception e)
        {
            lastLoadError = FILE_NAME + " is invalid JSON, using defaults: " + e.getMessage();
            return null;
        }
    }

    /** Copy the bundled file out verbatim so the user has the comments and the full default set. */
    private void writeDefaultTemplate(Path target)
    {
        try (InputStream in = ItemTagRegistry.class.getResourceAsStream(RESOURCE))
        {
            if (in == null) return;
            Files.copy(in, target);
        }
        catch (IOException e)
        {
            lastLoadError = "could not write " + FILE_NAME + ": " + e.getMessage();
        }
    }

    /** Later files win per-tag: a user tag entry replaces the bundled one outright. */
    private void merge(JsonObject root)
    {
        if (!root.has("tags") || !root.get("tags").isJsonObject()) return;
        JsonObject tagObj = root.getAsJsonObject("tags");
        for (Map.Entry<String, JsonElement> entry : tagObj.entrySet())
        {
            if (!entry.getValue().isJsonObject()) continue;
            tags.put(normalise(entry.getKey()), Tag.parse(entry.getValue().getAsJsonObject()));
        }
    }

    private static String normalise(String s)
    {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /** Menu targets arrive wrapped in colour tags and sometimes carry a level suffix. */
    private static String cleanTarget(String target)
    {
        if (target == null) return "";
        String s = net.runelite.client.util.Text.removeTags(target);
        int paren = s.indexOf(" (level");
        if (paren > 0) s = s.substring(0, paren);
        return s.replace('\u00A0', ' ').trim().toLowerCase();
    }

    private static final class Tag
    {
        final Set<String> options = new LinkedHashSet<>();
        final List<String> names = new ArrayList<>();
        final Set<String> exactNames = new LinkedHashSet<>();
        final Set<Integer> ids = new LinkedHashSet<>();
        final List<String> exclude = new ArrayList<>();

        static Tag parse(JsonObject o)
        {
            Tag t = new Tag();
            for (String s : strings(o, "options")) t.options.add(s);
            t.names.addAll(strings(o, "names"));
            for (String s : strings(o, "exactNames")) t.exactNames.add(s);
            t.exclude.addAll(strings(o, "exclude"));
            if (o.has("ids") && o.get("ids").isJsonArray())
            {
                for (JsonElement e : o.getAsJsonArray("ids"))
                {
                    try
                    {
                        t.ids.add(e.getAsInt());
                    }
                    catch (RuntimeException ignored)
                    {
                        // Skip malformed id entries rather than failing the whole tag.
                    }
                }
            }
            return t;
        }

        private static List<String> strings(JsonObject o, String key)
        {
            List<String> out = new ArrayList<>();
            if (!o.has(key) || !o.get(key).isJsonArray()) return out;
            JsonArray arr = o.getAsJsonArray(key);
            for (JsonElement e : arr)
            {
                try
                {
                    String s = e.getAsString();
                    if (s != null && !s.trim().isEmpty()) out.add(s.trim().toLowerCase());
                }
                catch (RuntimeException ignored)
                {
                    // Skip malformed entries.
                }
            }
            return out;
        }

        /** Empty options list means the tag gates every action on a matching item. */
        boolean gatesOption(String option)
        {
            return options.isEmpty() || (option != null && options.contains(option));
        }

        boolean matchesName(String name)
        {
            if (name == null || name.isEmpty()) return false;
            for (String ex : exclude)
            {
                if (name.contains(ex)) return false;
            }
            if (exactNames.contains(name)) return true;
            for (String n : names)
            {
                if (name.contains(n)) return true;
            }
            return false;
        }
    }
}
