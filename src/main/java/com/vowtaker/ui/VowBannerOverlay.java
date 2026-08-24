package com.vowtaker.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Souls-style centre-screen announcement. Fades in, holds, fades out, then disappears.
 * Used for the moments worth pausing on: promotions, milestone unlocks, ritual completions.
 */
@Singleton
public class VowBannerOverlay extends Overlay
{
    private static final long FADE_IN_MS = 450;
    private static final long HOLD_MS = 3000;
    private static final long FADE_OUT_MS = 1100;
    /** Full lifetime of a banner. Callers delay follow-up UI by this so it isn't drawn over. */
    public static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    private final Client client;

    private volatile String title;
    private volatile String subtitle;
    private volatile long shownAt;

    @Inject
    public VowBannerOverlay(Client client)
    {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(net.runelite.client.ui.overlay.OverlayPriority.HIGH);
        setMovable(false);
        setSnappable(false);
        setResettable(false);
    }

    /** Queues a banner. Safe to call from any thread. */
    public void show(String title, String subtitle)
    {
        this.title = title;
        this.subtitle = subtitle;
        this.shownAt = System.currentTimeMillis();
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        String heading = title;
        if (heading == null || shownAt == 0) return null;

        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed > TOTAL_MS)
        {
            title = null;
            return null;
        }

        float alpha;
        if (elapsed < FADE_IN_MS)
        {
            alpha = elapsed / (float) FADE_IN_MS;
        }
        else if (elapsed < FADE_IN_MS + HOLD_MS)
        {
            alpha = 1f;
        }
        else
        {
            alpha = 1f - ((elapsed - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS);
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        Rectangle bounds = client.getCanvas() != null
            ? client.getCanvas().getBounds()
            : new Rectangle(0, 0, 800, 600);
        int w = client.getCanvasWidth() > 0 ? client.getCanvasWidth() : bounds.width;
        int h = client.getCanvasHeight() > 0 ? client.getCanvasHeight() : bounds.height;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font titleFont = g.getFont().deriveFont(Font.BOLD, 34f);
        Font subFont = g.getFont().deriveFont(Font.PLAIN, 16f);
        FontMetrics tm = g.getFontMetrics(titleFont);
        FontMetrics sm = g.getFontMetrics(subFont);

        boolean hasSub = subtitle != null && !subtitle.isEmpty();
        int bandHeight = hasSub ? 96 : 70;
        int bandY = Math.max(0, h / 2 - bandHeight - 40);

        // Dark band, faded at both ends so it reads as a vignette rather than a rectangle.
        int bandAlpha = (int) (170 * alpha);
        java.awt.Paint old = g.getPaint();
        g.setPaint(new java.awt.GradientPaint(
            0, 0, new Color(0, 0, 0, 0),
            w / 2f, 0, new Color(0, 0, 0, bandAlpha)));
        g.fillRect(0, bandY, w / 2, bandHeight);
        g.setPaint(new java.awt.GradientPaint(
            w / 2f, 0, new Color(0, 0, 0, bandAlpha),
            w, 0, new Color(0, 0, 0, 0)));
        g.fillRect(w / 2, bandY, w / 2, bandHeight);
        g.setPaint(old);

        int titleAlpha = (int) (255 * alpha);
        int y = bandY + (hasSub ? 46 : 44);

        g.setFont(titleFont);
        g.setColor(new Color(0, 0, 0, titleAlpha));
        g.drawString(heading, (w - tm.stringWidth(heading)) / 2 + 2, y + 2);
        g.setColor(new Color(226, 202, 130, titleAlpha));
        g.drawString(heading, (w - tm.stringWidth(heading)) / 2, y);

        // Hairlines either side of the title, Souls-style.
        int lineY = y + 12;
        int half = tm.stringWidth(heading) / 2;
        g.setColor(new Color(190, 165, 100, (int) (150 * alpha)));
        g.drawLine(w / 2 - half - 60, lineY, w / 2 - half - 14, lineY);
        g.drawLine(w / 2 + half + 14, lineY, w / 2 + half + 60, lineY);

        if (hasSub)
        {
            String sub = subtitle;
            g.setFont(subFont);
            g.setColor(new Color(0, 0, 0, titleAlpha));
            g.drawString(sub, (w - sm.stringWidth(sub)) / 2 + 1, y + 33);
            g.setColor(new Color(215, 215, 220, titleAlpha));
            g.drawString(sub, (w - sm.stringWidth(sub)) / 2, y + 32);
        }

        return new Dimension(w, h);
    }
}
