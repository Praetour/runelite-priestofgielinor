package com.vowtaker.ui;

import com.vowtaker.VowTakerConfig;
import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.Rank;
import com.vowtaker.service.VowStorageService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class VowOverlay extends OverlayPanel
{
    private final Client client;
    private final VowStorageService storageService;
    private final VowTakerConfig config;

    @Inject
    public VowOverlay(Client client, VowStorageService storageService, VowTakerConfig config)
    {
        this.client = client;
        this.storageService = storageService;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay() || client.getLocalPlayer() == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(180, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text("VowTaker").color(Color.ORANGE).build());

        Rank rank = storageService.getCurrentRank();
        GodAlignment god = storageService.getSelectedGod();
        Rank next = rank.next();
        int current = storageService.getTotalPoints();

        String title = rank.fullTitle(god);
        String progress = next == rank ? current + " (max)" : current + " / " + next.getRequiredPoints();

        panelComponent.getChildren().add(LineComponent.builder().left(title).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Points").right(progress).build());

        panelComponent.setBackgroundColor(new Color(0, 0, 0, config.overlayOpacity()));
        return super.render(graphics);
    }
}
