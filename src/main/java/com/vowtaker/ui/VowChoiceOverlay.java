package com.vowtaker.ui;

import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowSelection;
import com.vowtaker.service.VowSelectionService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Card-picker overlay that appears in-world when a vow selection is pending.
 * Interaction: left-click a card to pick it. The plugin translates the click
 * position through {@link #cardAt(Point)} into a vow id.
 */
@Singleton
public class VowChoiceOverlay extends Overlay
{
    private static final int CARD_WIDTH = 260;
    private static final int CARD_HEIGHT = 360;
    private static final int GAP = 20;
    private static final Color BACKDROP = new Color(0, 0, 0, 180);
    private static final Color CARD_BG = new Color(28, 28, 32);
    private static final Color CARD_BG_HOVER = new Color(48, 40, 22);
    private static final Color CARD_BORDER = new Color(210, 130, 30);
    private static final Color CARD_BORDER_HOVER = new Color(250, 200, 90);

    private final Client client;
    private final VowSelectionService selection;

    /** Filled every frame with the current card rectangles + owning vow id. */
    private final List<CardHit> cardHits = new ArrayList<>();
    /** Non-null only while the re-roll button is drawn and affordable. */
    private Rectangle rerollRect;

    @Inject
    public VowChoiceOverlay(Client client, VowSelectionService selection)
    {
        this.client = client;
        this.selection = selection;
        // DYNAMIC anchors us to the top-left of the game canvas so our centering math against
        // client.getCanvasWidth()/getCanvasHeight() lines up regardless of any user-dragged position.
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(net.runelite.client.ui.overlay.OverlayPriority.HIGHEST);
        setMovable(false);
        setSnappable(false);
        setResettable(false);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        cardHits.clear();

        if (!selection.hasPendingSelection())
        {
            return null;
        }

        List<VowSelection> cards = selection.getHiddenCards();
        if (cards.isEmpty())
        {
            return null;
        }

        Rectangle bounds = client.getCanvas() != null ? client.getCanvas().getBounds() : new Rectangle(0, 0, 800, 600);
        int w = client.getCanvasWidth() > 0 ? client.getCanvasWidth() : bounds.width;
        int h = client.getCanvasHeight() > 0 ? client.getCanvasHeight() : bounds.height;
        int totalWidth = cards.size() * CARD_WIDTH + (cards.size() - 1) * GAP;
        int startX = Math.max(0, (w - totalWidth) / 2);
        int y = Math.max(0, (h - CARD_HEIGHT) / 2 - 20);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, w, h);

        boolean major = selection.getCurrentDrawMode() == VowSelectionService.DrawMode.MAJOR_REVEALED
            || selection.getCurrentDrawMode() == VowSelectionService.DrawMode.GOD_ONLY;

        Font titleFont = g.getFont().deriveFont(Font.BOLD, 20f);
        Font bodyFont = g.getFont().deriveFont(Font.PLAIN, 14f);
        FontMetrics tm = g.getFontMetrics(titleFont);

        g.setFont(titleFont);
        g.setColor(new Color(240, 200, 90));
        String header = major ? "A major vow is upon you" : "Choose a minor vow";
        int hx = (w - tm.stringWidth(header)) / 2;
        g.drawString(header, hx, y - 30);

        String sub = major
            ? "Click the card to swear it. Swearing a major vow lifts and retires your current one."
            : "Left-click a card to swear it. Minor vows bind you for good and stack with the rest.";
        g.setFont(bodyFont);
        FontMetrics bm = g.getFontMetrics(bodyFont);
        int sx = (w - bm.stringWidth(sub)) / 2;
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(sub, sx, y - 8);

        Point mouse = client.getMouseCanvasPosition() != null
            ? new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())
            : null;

        for (int i = 0; i < cards.size(); i++)
        {
            VowDefinition vow = cards.get(i).getVow();
            int x = startX + i * (CARD_WIDTH + GAP);
            Rectangle rect = new Rectangle(x, y, CARD_WIDTH, CARD_HEIGHT);
            boolean hover = mouse != null && rect.contains(mouse);
            drawCard(g, x, y, vow, titleFont, bodyFont, hover);
            cardHits.add(new CardHit(rect, vow.getId()));
        }

        drawRerollButton(g, startX, y + CARD_HEIGHT + 16, totalWidth, bodyFont, mouse);

        if (major)
        {
            VowDefinition outgoing = selection.getActiveMajorVow();
            if (outgoing != null)
            {
                String note = "Replaces: " + outgoing.getName();
                g.setFont(bodyFont);
                FontMetrics nm = g.getFontMetrics(bodyFont);
                g.setColor(new Color(190, 150, 120));
                g.drawString(note, startX + (totalWidth - nm.stringWidth(note)) / 2, y + CARD_HEIGHT + 70);
            }
        }

        return new Dimension(w, h);
    }

    /** Draws the re-roll button under the cards and records its hit rect. */
    private void drawRerollButton(Graphics2D g, int startX, int y, int totalWidth, Font bodyFont, Point mouse)
    {
        rerollRect = null;
        int tokens = selection.getRerollTokens();
        boolean enabled = selection.canReroll();

        String label = "Re-roll  (" + tokens + " token" + (tokens == 1 ? "" : "s") + ")";        Font f = bodyFont.deriveFont(Font.BOLD, 15f);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics(f);
        int bw = fm.stringWidth(label) + 36;
        int bh = 32;
        int bx = startX + (totalWidth - bw) / 2;
        Rectangle rect = new Rectangle(bx, y, bw, bh);
        boolean hover = enabled && mouse != null && rect.contains(mouse);

        g.setColor(enabled ? (hover ? new Color(70, 58, 28) : new Color(38, 34, 24)) : new Color(26, 26, 28));
        g.fillRoundRect(bx, y, bw, bh, 10, 10);
        g.setStroke(new java.awt.BasicStroke(hover ? 3f : 2f));
        g.setColor(enabled ? (hover ? CARD_BORDER_HOVER : CARD_BORDER) : new Color(70, 70, 76));
        g.drawRoundRect(bx, y, bw, bh, 10, 10);
        g.setColor(enabled ? new Color(240, 200, 90) : new Color(110, 110, 118));
        g.drawString(label, bx + 18, y + 21);

        if (enabled)
        {
            rerollRect = rect;
        }
    }

    /** Returns true if the given canvas point is over an enabled re-roll button. */
    public boolean isRerollAt(Point canvasPoint)
    {
        return canvasPoint != null && rerollRect != null && rerollRect.contains(canvasPoint);
    }

    /** Returns the vow id whose card contains the given canvas point, or null. */
    public String cardAt(Point canvasPoint)
    {
        if (canvasPoint == null) return null;
        for (CardHit hit : cardHits)
        {
            if (hit.rect.contains(canvasPoint))
            {
                return hit.vowId;
            }
        }
        return null;
    }

    public List<Rectangle> getCardRects()
    {
        List<Rectangle> out = new ArrayList<>(cardHits.size());
        for (CardHit hit : cardHits)
        {
            out.add(hit.rect);
        }
        return Collections.unmodifiableList(out);
    }

    private void drawCard(Graphics2D g, int x, int y, VowDefinition vow, Font titleFont, Font bodyFont,
        boolean hover)
    {
        int points = com.vowtaker.service.VowSelectionService.pointsFor(vow);

        Color bg = hover ? CARD_BG_HOVER : CARD_BG;
        Color border = hover ? CARD_BORDER_HOVER : CARD_BORDER;

        g.setColor(bg);
        g.fillRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 12, 12);
        g.setColor(border);
        g.setStroke(new java.awt.BasicStroke(hover ? 3f : 2f));
        g.drawRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 12, 12);

        // Points badge (top-right).
        String badge = "+" + points + " pts";
        Font badgeFont = bodyFont.deriveFont(Font.BOLD, 14f);
        g.setFont(badgeFont);
        FontMetrics bfm = g.getFontMetrics(badgeFont);
        int bw = bfm.stringWidth(badge) + 12;
        int bh = 22;
        int bx = x + CARD_WIDTH - bw - 10;
        int by = y + 10;
        g.setColor(new Color(30, 30, 32));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(240, 200, 90));
        g.drawRoundRect(bx, by, bw, bh, 10, 10);
        g.drawString(badge, bx + 6, by + 16);

        g.setFont(titleFont);
        g.setColor(new Color(240, 200, 90));
        drawWrapped(g, vow.getName(), x + 14, y + 32, CARD_WIDTH - 28, 3);

        g.setFont(bodyFont);
        g.setColor(new Color(200, 200, 210));
        drawWrapped(g, vow.getDescription(), x + 14, y + 110, CARD_WIDTH - 28, 12);

        g.setFont(bodyFont.deriveFont(Font.ITALIC));
        g.setColor(Color.LIGHT_GRAY);
        String type = vow.getType().name() + " \u00b7 " + vow.getSeverity();
        g.drawString(type, x + 14, y + CARD_HEIGHT - 16);
    }

    private void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int maxLines)
    {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int drawn = 0;
        for (String word : words)
        {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth)
            {
                g.drawString(line.toString(), x, y + drawn * lineHeight);
                drawn++;
                line.setLength(0);
                line.append(word);
                if (drawn >= maxLines - 1)
                {
                    break;
                }
            }
            else
            {
                line.setLength(0);
                line.append(test);
            }
        }
        if (drawn < maxLines && line.length() > 0)
        {
            g.drawString(line.toString(), x, y + drawn * lineHeight);
        }
    }

    private static final class CardHit
    {
        final Rectangle rect;
        final String vowId;

        CardHit(Rectangle rect, String vowId)
        {
            this.rect = rect;
            this.vowId = vowId;
        }
    }
}
