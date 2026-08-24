package com.vowtaker.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VowDefinition
{
    private final String id;
    private final String name;
    private final String description;
    private final VowType type;
    private final GodAlignment godAlignment;
    private final String category;
    private final String trigger;
    private final String severity;
    private final boolean permanent;
    private final boolean ritual;
    private final boolean defaultApproved;
    private final List<String> blockedTags;

    public VowDefinition(String id, String name, String description, VowType type, GodAlignment godAlignment)
    {
        this(id, name, description, type, godAlignment, "general", "milestone", "light", false);
    }

    public VowDefinition(String id, String name, String description, VowType type, GodAlignment godAlignment,
                        String category, String trigger, String severity, boolean defaultApproved)
    {
        this(id, name, description, type, godAlignment, category, trigger, severity, defaultApproved,
            Collections.emptyList());
    }

    public VowDefinition(String id, String name, String description, VowType type, GodAlignment godAlignment,
                        String category, String trigger, String severity, boolean defaultApproved,
                        List<String> blockedTags)
    {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.godAlignment = godAlignment;
        this.category = category;
        this.trigger = trigger;
        this.severity = severity;
        this.permanent = type == VowType.PERMANENT;
        this.ritual = type == VowType.RITUAL;
        this.defaultApproved = defaultApproved;
        this.blockedTags = blockedTags == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(Arrays.asList(blockedTags.toArray(new String[0])));
    }

    /** Item-blocklist tags this vow forbids. Enforced generically against the menu target. */
    public List<String> getBlockedTags()
    {
        return blockedTags;
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public VowType getType()
    {
        return type;
    }

    public GodAlignment getGodAlignment()
    {
        return godAlignment;
    }

    public String getCategory()
    {
        return category;
    }

    public String getTrigger()
    {
        return trigger;
    }

    public String getSeverity()
    {
        return severity;
    }

    public boolean isDefaultApproved()
    {
        return defaultApproved;
    }

    public boolean isPermanent()
    {
        return permanent;
    }

    public boolean isRitual()
    {
        return ritual;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }
        VowDefinition that = (VowDefinition) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }
}
