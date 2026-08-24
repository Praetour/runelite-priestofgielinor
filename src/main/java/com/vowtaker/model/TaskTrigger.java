package com.vowtaker.model;

/**
 * How a task is detected at runtime. Kept simple for now — only chat-pattern
 * detection is wired; other kinds are placeholders for later expansion.
 */
public final class TaskTrigger
{
    public enum Kind
    {
        CHAT_PATTERN,   // pattern is a case-insensitive regex; matched against ChatMessage bodies
        NPC_KILL,       // pattern is an NPC name; amount = kills needed
        ITEM_OBTAIN,    // pattern is an item name
        WIDGET,         // pattern is groupId:childId, for widget-driven completions
        MANUAL          // player marks completion themselves (for now)
    }

    private final Kind kind;
    private final String pattern;
    private final int amount;

    public TaskTrigger(Kind kind, String pattern, int amount)
    {
        this.kind = kind;
        this.pattern = pattern;
        this.amount = Math.max(1, amount);
    }

    public static TaskTrigger chat(String regex)
    {
        return new TaskTrigger(Kind.CHAT_PATTERN, regex, 1);
    }

    public static TaskTrigger chat(String regex, int amount)
    {
        return new TaskTrigger(Kind.CHAT_PATTERN, regex, amount);
    }

    public static TaskTrigger manual()
    {
        return new TaskTrigger(Kind.MANUAL, "", 1);
    }

    public static TaskTrigger npcKill(String npcName)
    {
        return new TaskTrigger(Kind.NPC_KILL, npcName, 1);
    }

    public static TaskTrigger npcKill(String npcName, int amount)
    {
        return new TaskTrigger(Kind.NPC_KILL, npcName, amount);
    }

    public Kind getKind()
    {
        return kind;
    }

    public String getPattern()
    {
        return pattern;
    }

    public int getAmount()
    {
        return amount;
    }
}
