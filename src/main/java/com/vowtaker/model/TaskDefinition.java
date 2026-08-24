package com.vowtaker.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TaskDefinition
{
    private final String id;
    private final String name;
    private final String description;
    private final Rank tier;
    private final TaskCategory category;
    private final GodAlignment godFlavor; // NONE = shared across all gods
    private final int points;
    private final boolean milestone;
    private final TaskTrigger trigger;
    private final List<String> gatedTargets;

    public TaskDefinition(String id, String name, String description, Rank tier,
                          TaskCategory category, GodAlignment godFlavor, int points,
                          boolean milestone, TaskTrigger trigger)
    {
        this(id, name, description, tier, category, godFlavor, points, milestone, trigger, Collections.emptyList());
    }

    public TaskDefinition(String id, String name, String description, Rank tier,
                          TaskCategory category, GodAlignment godFlavor, int points,
                          boolean milestone, TaskTrigger trigger, List<String> gatedTargets)
    {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tier = tier;
        this.category = category;
        this.godFlavor = godFlavor == null ? GodAlignment.NONE : godFlavor;
        this.points = points;
        this.milestone = milestone;
        this.trigger = trigger;
        this.gatedTargets = gatedTargets == null ? Collections.emptyList() : Collections.unmodifiableList(gatedTargets);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Rank getTier() { return tier; }
    public TaskCategory getCategory() { return category; }
    public GodAlignment getGodFlavor() { return godFlavor; }
    public int getPoints() { return points; }
    public boolean isMilestone() { return milestone; }
    public TaskTrigger getTrigger() { return trigger; }
    public List<String> getGatedTargets() { return gatedTargets; }

    public boolean availableTo(GodAlignment playerGod)
    {
        return godFlavor == GodAlignment.NONE || godFlavor == playerGod;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof TaskDefinition)) return false;
        return Objects.equals(id, ((TaskDefinition) o).id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }
}
