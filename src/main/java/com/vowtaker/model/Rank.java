package com.vowtaker.model;

/** Devotion rank tiers. Ranks are gated by cumulative points + the previous tier's milestone. */
public enum Rank
{
    NONE(0, 0, "Unaligned"),
    FOLLOWER(0, 0, "Follower"),
    DEACON(1, 50, "Deacon"),
    PRIEST(2, 150, "Priest"),
    BISHOP(3, 350, "Bishop"),
    ARCHBISHOP(4, 700, "Archbishop"),
    CARDINAL(5, 1200, "Cardinal"),
    CHOSEN(6, 2000, "Chosen");

    private final int tier;
    private final int requiredPoints;
    private final String label;

    Rank(int tier, int requiredPoints, String label)
    {
        this.tier = tier;
        this.requiredPoints = requiredPoints;
        this.label = label;
    }

    public int getTier()
    {
        return tier;
    }

    /** Cumulative point total needed to earn this rank (0 for NONE/FOLLOWER). */
    public int getRequiredPoints()
    {
        return requiredPoints;
    }

    public String getLabel()
    {
        return label;
    }

    public Rank next()
    {
        switch (this)
        {
            case NONE: return FOLLOWER;
            case FOLLOWER: return DEACON;
            case DEACON: return PRIEST;
            case PRIEST: return BISHOP;
            case BISHOP: return ARCHBISHOP;
            case ARCHBISHOP: return CARDINAL;
            case CARDINAL: return CHOSEN;
            default: return CHOSEN;
        }
    }

    public String fullTitle(GodAlignment god)
    {
        if (this == NONE || god == null || god == GodAlignment.NONE)
        {
            return label;
        }
        String g = god.name().charAt(0) + god.name().substring(1).toLowerCase();
        return label + " of " + g;
    }
}
