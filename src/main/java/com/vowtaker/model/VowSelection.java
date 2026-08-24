package com.vowtaker.model;

public class VowSelection
{
    private final VowDefinition vow;
    private final boolean selected;

    public VowSelection(VowDefinition vow, boolean selected)
    {
        this.vow = vow;
        this.selected = selected;
    }

    public VowDefinition getVow()
    {
        return vow;
    }

    public boolean isSelected()
    {
        return selected;
    }
}
