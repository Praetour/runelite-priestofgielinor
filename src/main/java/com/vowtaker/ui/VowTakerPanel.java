package com.vowtaker.ui;

import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.Rank;
import com.vowtaker.model.TaskDefinition;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowType;
import com.vowtaker.service.TaskRegistry;
import com.vowtaker.service.VowRegistry;
import com.vowtaker.service.VowSelectionService;
import com.vowtaker.service.VowStorageService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Sidebar panel for VowTaker. Shows a Leagues-style god picker on first run,
 * then a tabbed Overview/Vows/Rituals/Points view once a god is chosen.
 */
@Singleton
public class VowTakerPanel extends PluginPanel
{
    private final VowStorageService storage;
    private final VowSelectionService selection;
    private final com.vowtaker.service.TaskService taskService;

    private Consumer<GodAlignment> godChosenCallback;
    private Consumer<String> vowSelectedCallback;

    private final JPanel body = new JPanel(new BorderLayout());
    private TabbedMainView currentTabbedView;

    @Inject
    public VowTakerPanel(VowStorageService storage, VowSelectionService selection, com.vowtaker.service.TaskService taskService)
    {
        super(false);
        this.storage = storage;
        this.selection = selection;
        this.taskService = taskService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("VowTaker");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(title, BorderLayout.NORTH);

        body.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(body, BorderLayout.CENTER);
    }

    public void setGodChosenCallback(Consumer<GodAlignment> callback)
    {
        this.godChosenCallback = callback;
    }

    public void setVowSelectedCallback(Consumer<String> callback)
    {
        this.vowSelectedCallback = callback;
    }

    public void refresh()
    {
        SwingUtilities.invokeLater(() -> {
            // If the tabbed view is already up, just refresh its current tab so we don't reset to Overview.
            if (currentTabbedView != null && storage.getSelectedGod() != GodAlignment.NONE)
            {
                currentTabbedView.refreshCurrentTab();
                body.revalidate();
                body.repaint();
                return;
            }
            rebuild();
        });
    }

    private void rebuild()
    {
        body.removeAll();
        if (storage.getSelectedGod() == GodAlignment.NONE)
        {
            currentTabbedView = null;
            body.add(new GodPickerView(this::onGodChosen), BorderLayout.CENTER);
        }
        else
        {
            currentTabbedView = new TabbedMainView(storage, selection, taskService, this::onVowChosen, this::rebuild);
            body.add(currentTabbedView, BorderLayout.CENTER);
        }
        body.revalidate();
        body.repaint();
    }

    private void onGodChosen(GodAlignment god)
    {
        if (godChosenCallback != null)
        {
            godChosenCallback.accept(god);
        }
        rebuild();
    }

    private void onVowChosen(String vowId)
    {
        if (vowSelectedCallback != null)
        {
            vowSelectedCallback.accept(vowId);
        }
        rebuild();
    }

    /**
     * Grid of god cards — one must be picked before the rest of the UI opens.
     */
    private static final class GodPickerView extends JPanel
    {
        GodPickerView(Consumer<GodAlignment> onChosen)
        {
            setLayout(new BorderLayout());
            setBackground(ColorScheme.DARK_GRAY_COLOR);

            JLabel prompt = new JLabel("<html><body style='width:180px;text-align:center'>Choose a god to begin your path. Your choice unlocks god-bound vows.</body></html>");
            prompt.setForeground(Color.LIGHT_GRAY);
            prompt.setHorizontalAlignment(SwingConstants.CENTER);
            prompt.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
            add(prompt, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
            grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

            for (GodAlignment god : GodAlignment.values())
            {
                if (god == GodAlignment.NONE)
                {
                    continue;
                }
                grid.add(buildCard(god, onChosen));
            }

            JScrollPane scroll = new JScrollPane(grid,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
            scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            add(scroll, BorderLayout.CENTER);
        }

        private JPanel buildCard(GodAlignment god, Consumer<GodAlignment> onChosen)
        {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(godColor(god), 2),
                BorderFactory.createEmptyBorder(8, 4, 8, 4)));
            card.setPreferredSize(new Dimension(90, 110));

            ImageIcon icon = loadGodIcon(god);
            if (icon != null)
            {
                JLabel iconLabel = new JLabel(icon);
                iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                card.add(iconLabel);
                card.add(Box.createVerticalStrut(4));
            }

            JLabel name = new JLabel(prettyName(god));
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            name.setForeground(godColor(god));
            name.setFont(FontManager.getRunescapeBoldFont());

            JLabel tag = new JLabel("<html><body style='text-align:center;width:80px'>"
                + godTagline(god) + "</body></html>");
            tag.setAlignmentX(Component.CENTER_ALIGNMENT);
            tag.setForeground(Color.LIGHT_GRAY);
            tag.setFont(FontManager.getRunescapeSmallFont());
            tag.setHorizontalAlignment(SwingConstants.CENTER);

            card.add(name);
            card.add(Box.createVerticalStrut(4));
            card.add(tag);

            card.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    onChosen.accept(god);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    card.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                }
            });

            return card;
        }

        /** Loads the god's symbol from resources; returns null if the icon is missing. */
        private ImageIcon loadGodIcon(GodAlignment god)
        {
            String fileName = god.name().toLowerCase() + ".png";
            try
            {
                BufferedImage img = ImageUtil.loadImageResource(VowTakerPanel.class, "icons/" + fileName);
                if (img == null) return null;
                Image scaled = img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            catch (Exception ex)
            {
                return null;
            }
        }
    }

    /**
     * Tabs: Overview / Vows / Rituals / Points.
     */
    private static final class TabbedMainView extends JPanel
    {
        private final JPanel content = new JPanel(new BorderLayout());
        private final JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        private final VowStorageService storage;
        private final VowSelectionService selection;
        private final com.vowtaker.service.TaskService taskService;
        private final Consumer<String> onVowChosen;
        private final Runnable rebuildRoot;
        private java.util.function.Supplier<JComponent> currentSupplier;

        TabbedMainView(VowStorageService storage, VowSelectionService selection,
                       com.vowtaker.service.TaskService taskService,
                       Consumer<String> onVowChosen, Runnable rebuildRoot)
        {
            this.storage = storage;
            this.selection = selection;
            this.taskService = taskService;
            this.onVowChosen = onVowChosen;
            this.rebuildRoot = rebuildRoot;

            setLayout(new BorderLayout());
            setBackground(ColorScheme.DARK_GRAY_COLOR);

            tabBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
            content.setBackground(ColorScheme.DARK_GRAY_COLOR);

            addTab("Overview", this::buildOverview, true);
            addTab("Tasks", this::buildTasks, false);
            addTab("Vows", this::buildVows, false);
            addTab("Rituals", this::buildRituals, false);

            add(tabBar, BorderLayout.NORTH);
            add(content, BorderLayout.CENTER);

            showTab(this::buildOverview);
        }

        private void addTab(String label, java.util.function.Supplier<JComponent> supplier, boolean active)
        {
            JButton btn = new JButton(label);
            btn.setFocusable(false);
            btn.setBackground(active ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
            btn.setForeground(active ? Color.BLACK : Color.LIGHT_GRAY);
            btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            btn.addActionListener(e -> {
                for (Component c : tabBar.getComponents())
                {
                    if (c instanceof JButton)
                    {
                        c.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                        c.setForeground(Color.LIGHT_GRAY);
                    }
                }
                btn.setBackground(ColorScheme.BRAND_ORANGE);
                btn.setForeground(Color.BLACK);
                showTab(supplier);
            });
            tabBar.add(btn);
        }

        private void showTab(java.util.function.Supplier<JComponent> supplier)
        {
            // Preserve vertical scroll position across rebuilds so completing a task
            // doesn't snap the tasks list back to the top.
            int savedScroll = 0;
            if (currentSupplier == supplier && content.getComponentCount() > 0)
            {
                Component existing = content.getComponent(0);
                if (existing instanceof JScrollPane)
                {
                    savedScroll = ((JScrollPane) existing).getVerticalScrollBar().getValue();
                }
            }
            currentSupplier = supplier;
            content.removeAll();
            JComponent built = supplier.get();
            content.add(built, BorderLayout.CENTER);
            content.revalidate();
            content.repaint();
            if (savedScroll > 0 && built instanceof JScrollPane)
            {
                final JScrollPane sp = (JScrollPane) built;
                final int target = savedScroll;
                SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(target));
            }
        }

        /** Rebuild only the current tab's contents so the active tab is preserved after data mutations. */
        private void refreshCurrentTab()
        {
            if (currentSupplier != null) showTab(currentSupplier);
        }

        private JComponent buildOverview()
        {
            JPanel root = new JPanel(new BorderLayout(0, 6));
            root.setBackground(ColorScheme.DARK_GRAY_COLOR);
            root.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

            root.add(buildOverviewStats(), BorderLayout.NORTH);
            root.add(wrapScroll(buildOverviewInfo()), BorderLayout.CENTER);

            JButton reset = new JButton("Reset plugin");
            reset.setBackground(new Color(140, 40, 40));
            reset.setForeground(Color.WHITE);
            reset.setToolTipText("Wipe god choice, active vows, tasks, and progress. Cannot be undone.");
            reset.addActionListener(e -> onVowChosen.accept("__reset__"));
            root.add(reset, BorderLayout.SOUTH);

            return root;
        }

        private JComponent buildOverviewStats()
        {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(ColorScheme.DARK_GRAY_COLOR);

            Rank rank = storage.getCurrentRank();
            GodAlignment god = storage.getSelectedGod();
            p.add(compactRow("Rank", rank.fullTitle(god), godColor(god)));

            Rank nextRank = rank.next();
            int current = storage.getTotalPoints();
            int needed = nextRank == rank ? 0 : nextRank.getRequiredPoints();
            String progress = nextRank == rank ? current + " (max)" : current + " / " + needed;
            p.add(compactRow("Points", progress, ColorScheme.BRAND_ORANGE));

            p.add(compactRow("Gear ceiling", gearCeilingLabel(rank), new Color(180, 200, 220)));
            p.add(compactRow("Re-rolls", String.valueOf(storage.getRerollTokens()), new Color(220, 200, 140)));

            int tierTaskCount = 0;
            for (String id : storage.getCompletedTaskIds())
            {
                TaskDefinition td = TaskRegistry.getById(id);
                if (td != null && !td.isMilestone() && td.getTier() == rank)
                {
                    tierTaskCount++;
                }
            }
            p.add(compactRow("Tier tasks", String.valueOf(tierTaskCount), Color.LIGHT_GRAY));

            VowDefinition godVow = storage.getActiveMajorVow();
            p.add(compactRow("Latest major", godVow == null ? "none" : godVow.getName(), Color.LIGHT_GRAY));
            p.add(compactRow("Vows sworn", String.valueOf(storage.getSwornVows().size()), new Color(220, 200, 140)));

            TaskDefinition milestone = TaskRegistry.milestoneFor(rank);
            if (milestone != null)
            {
                boolean done = storage.isTaskCompleted(milestone.getId());
                p.add(compactRow("Milestone", (done ? "\u2713 " : "") + milestone.getName(),
                    done ? new Color(90, 200, 90) : new Color(230, 180, 60)));
            }

            int unlockedCount = storage.getUnlockedGearPatterns().size();
            if (unlockedCount > 0)
            {
                p.add(compactRow("Gear unlocks", unlockedCount + " item set" + (unlockedCount == 1 ? "" : "s"),
                    new Color(180, 220, 180)));
            }

            if (selection.hasPendingSelection())
            {
                JLabel notice = new JLabel(
                    "<html><body style='width:180px'>A vow choice is waiting in-game. Right-click a card to accept.</body></html>");
                notice.setForeground(ColorScheme.BRAND_ORANGE);
                notice.setFont(FontManager.getRunescapeSmallFont());
                notice.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
                p.add(notice);
            }
            return p;
        }

        private JComponent buildOverviewInfo()
        {
            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setBackground(ColorScheme.DARK_GRAY_COLOR);
            info.setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));

            JLabel heading = new JLabel("About VowTaker");
            heading.setForeground(ColorScheme.BRAND_ORANGE);
            heading.setFont(FontManager.getRunescapeBoldFont());
            heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            info.add(heading);

            info.add(faqEntry("What is VowTaker?",
                "A devotion-flavour challenge plugin. Pledge yourself to a god, take binding vows,"
                + " complete themed tasks, and climb seven ranks from Follower to Chosen."));
            info.add(faqEntry("How do I earn points?",
                "Complete tasks in the Tasks tab. Most trigger automatically from kills or chat messages;"
                + " a few must be ticked manually. Each rank has a point threshold to promote."));
            info.add(faqEntry("What is a milestone?",
                "A signature test (Brutus, Scurrius, Barrows, Fire Cape, Zulrah, DT2) that promotes you"
                + " to the next rank and opens a major vow selection."));
            info.add(faqEntry("What is a god vow?",
                "An active discipline tied to your god (e.g. Shadow-Marked, Hand of Purity). Actions"
                + " that violate it are blocked at the menu level until you fulfil or lose it."));
            info.add(faqEntry("What is a ritual?",
                "A short mandatory objective. All combat is blocked until the ritual is fulfilled in-game."));
            info.add(faqEntry("Why is my Wear / Wield greyed out?",
                "Rank gear ceiling. Each promotion raises the tier you may equip. Completing certain"
                + " Cardinal-tier tasks (ToA 300+, CoX CM, ToB HM, DT2 bosses) unlocks that raid's"
                + " uniques even before the ceiling reaches them."));
            info.add(faqEntry("What happens at Chosen rank?",
                "No new vows are drawn. Only the five 100-point Chosen tasks remain \u2014 Inferno,"
                + " Colosseum, CoX, ToB, ToA. Finish all five to fulfil your vows."));
            info.add(faqEntry("Can I skip a vow?",
                "Yes. Right-click the card in-game or use `!vow major` / `!vow minor` in chat to reopen"
                + " the picker. Declined vows are remembered so you won't be offered the same one twice."));
            info.add(faqEntry("Where is my data saved?",
                "Per RSN, under RuneLite's plugin data folder. Switching accounts loads a separate save."
                + " Use Reset plugin below to wipe the current save."));
            info.add(faqEntry("Chat commands",
                "`!vow major` reopens the major vow picker at your current rank. `!vow minor` reopens"
                + " the minor vow picker."));

            return info;
        }

        private JPanel compactRow(String left, String right, Color rightColor)
        {
            JPanel r = new JPanel(new BorderLayout());
            r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            r.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            JLabel l = new JLabel(left);
            l.setForeground(Color.LIGHT_GRAY);
            l.setFont(FontManager.getRunescapeSmallFont());
            JLabel v = new JLabel(right);
            v.setForeground(rightColor);
            v.setFont(FontManager.getRunescapeSmallFont());
            v.setHorizontalAlignment(SwingConstants.RIGHT);
            r.add(l, BorderLayout.WEST);
            r.add(v, BorderLayout.EAST);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
            wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
            wrap.add(r, BorderLayout.CENTER);
            wrap.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 20));
            return wrap;
        }

        private JPanel faqEntry(String question, String answer)
        {
            JPanel wrap = new JPanel();
            wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
            wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
            wrap.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));

            JLabel q = new JLabel("<html><body style='width:180px'>" + question + "</body></html>");
            q.setForeground(ColorScheme.BRAND_ORANGE);
            q.setFont(FontManager.getRunescapeBoldFont());
            q.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrap.add(q);

            JLabel a = new JLabel("<html><body style='width:180px'>" + answer + "</body></html>");
            a.setForeground(Color.LIGHT_GRAY);
            a.setFont(FontManager.getRunescapeSmallFont());
            a.setAlignmentX(Component.LEFT_ALIGNMENT);
            a.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            wrap.add(a);

            return wrap;
        }

        private JComponent buildTasks()
        {
            JPanel list = new ScrollingListPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);
            list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            Rank playerRank = storage.getCurrentRank();
            GodAlignment god = storage.getSelectedGod();

            for (Rank tier : new Rank[] { Rank.FOLLOWER, Rank.DEACON, Rank.PRIEST, Rank.BISHOP,
                Rank.ARCHBISHOP, Rank.CARDINAL, Rank.CHOSEN })
            {
                java.util.List<TaskDefinition> tierTasks = TaskRegistry.forTier(tier, god);
                if (tierTasks.isEmpty()) continue;

                JLabel header = new JLabel(tier.getLabel() + (tier.getTier() > playerRank.getTier() ? " (locked)" : ""));
                header.setForeground(tier.getTier() > playerRank.getTier() ? Color.GRAY : ColorScheme.BRAND_ORANGE);
                header.setFont(FontManager.getRunescapeBoldFont());
                header.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
                list.add(header);

                for (TaskDefinition t : tierTasks)
                {
                    boolean tierLocked = tier.getTier() > playerRank.getTier();
                    boolean milestoneLocked = !tierLocked && t.isMilestone() && !taskService.isMilestoneUnlocked(t);
                    list.add(buildTaskRow(t, tierLocked, milestoneLocked));
                    list.add(Box.createVerticalStrut(3));
                }
            }

            return wrapScroll(list);
        }

        private JPanel buildTaskRow(TaskDefinition t, boolean tierLocked, boolean milestoneLocked)
        {
            boolean done = storage.isTaskCompleted(t.getId());
            boolean locked = tierLocked || milestoneLocked;

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            Color border = done ? new Color(90, 200, 90) : (locked ? new Color(60, 60, 60) : (t.isMilestone() ? new Color(230, 180, 60) : ColorScheme.MEDIUM_GRAY_COLOR));
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Header: task name (left) + points (right).
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JLabel name = new JLabel((t.isMilestone() ? "\u2605 " : "") + t.getName());
            name.setForeground(done ? new Color(120, 200, 120) : (locked ? Color.GRAY : Color.WHITE));
            name.setFont(FontManager.getRunescapeSmallFont());
            String ptsText = t.isMilestone() ? "Promote" : ("+" + t.getPoints());
            JLabel pts = new JLabel(ptsText);
            pts.setForeground(t.isMilestone() ? new Color(230, 180, 60) : new Color(180, 180, 180));
            pts.setFont(FontManager.getRunescapeSmallFont());
            header.add(name, BorderLayout.CENTER);
            header.add(pts, BorderLayout.EAST);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));

            JLabel desc = new JLabel("<html><body style='width:170px'>" + t.getDescription() + "</body></html>");
            desc.setForeground(Color.LIGHT_GRAY);
            desc.setFont(FontManager.getRunescapeSmallFont());
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            desc.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

            // Action bar: status badge (left) + toggle button (right), one horizontal row.
            JPanel action = new JPanel(new BorderLayout(6, 0));
            action.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            String status;
            if (done) status = "Complete";
            else if (tierLocked) status = "Locked";
            else if (milestoneLocked)
            {
                Rank cur = storage.getCurrentRank();
                Rank next = cur.next();
                status = "Locked (" + storage.getTotalPoints() + "/" + next.getRequiredPoints() + ")";
            }
            else status = (t.getTrigger() != null && t.getTrigger().getKind().name().equals("MANUAL") ? "Manual" : "Open");
            JLabel badge = new JLabel(status);
            badge.setForeground(done ? new Color(120, 200, 120) : (locked ? Color.GRAY : new Color(180, 180, 180)));
            badge.setFont(FontManager.getRunescapeSmallFont());

            JButton toggle = new JButton(done ? "Not done" : "Done");
            toggle.setFont(FontManager.getRunescapeSmallFont());
            toggle.setForeground(Color.WHITE);
            toggle.setBackground(done ? new Color(140, 60, 60) : new Color(60, 100, 140));
            toggle.setFocusPainted(false);
            toggle.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            toggle.setMargin(new java.awt.Insets(2, 6, 2, 6));
            toggle.setEnabled(!locked || done);
            toggle.addActionListener(e -> {
                if (done) taskService.uncompleteManual(t.getId());
                else taskService.completeManual(t.getId());
                refreshCurrentTab();
            });
            action.add(badge, BorderLayout.WEST);
            action.add(toggle, BorderLayout.EAST);
            action.setAlignmentX(Component.LEFT_ALIGNMENT);
            // Cap height so BoxLayout doesn't stretch the button vertically.
            action.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, toggle.getPreferredSize().height + 4));

            row.add(header);
            row.add(desc);
            row.add(javax.swing.Box.createVerticalStrut(2));
            row.add(action);

            return row;
        }

        private JComponent buildVows()
        {
            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);
            list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            List<VowDefinition> sworn = storage.getSwornVows();
            List<VowDefinition> majors = new java.util.ArrayList<>();
            List<VowDefinition> minors = new java.util.ArrayList<>();
            for (VowDefinition v : sworn)
            {
                if (VowRegistry.isMajor(v)) majors.add(v);
                else minors.add(v);
            }

            addVowSection(list, "God & Major Vows", majors,
                "No god or major vows sworn yet.");
            addVowSection(list, "Minor Vows", minors,
                "No minor vows sworn yet.");

            if (majors.isEmpty() && minors.isEmpty())
            {
                JLabel empty = new JLabel("<html><body style='width:170px'>No vows sworn yet. When a card picker "
                    + "opens in-game, whatever you choose is added here permanently.</body></html>");
                empty.setForeground(Color.GRAY);
                empty.setFont(FontManager.getRunescapeSmallFont());
                list.add(empty);
            }

            return wrapScroll(list);
        }

        private void addVowSection(JPanel list, String title, List<VowDefinition> vows, String emptyText)
        {
            if (vows.isEmpty()) return;

            JLabel header = new JLabel(title + "  (" + vows.size() + ")");
            header.setForeground(ColorScheme.BRAND_ORANGE);
            header.setFont(FontManager.getRunescapeBoldFont());
            header.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(header);

            for (VowDefinition v : vows)
            {
                list.add(buildVowRow(v));
                list.add(Box.createVerticalStrut(4));
            }
        }

        private JComponent buildRituals()
        {
            return wrapScroll(vowList(v -> v.getType() == VowType.RITUAL && (isActive(v) || storage.isCompleted(v))));
        }

        private JPanel vowList(java.util.function.Predicate<VowDefinition> filter)
        {
            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);
            list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            List<VowDefinition> all = VowRegistry.all();
            int shown = 0;
            for (VowDefinition v : all)
            {
                if (!filter.test(v))
                {
                    continue;
                }
                list.add(buildVowRow(v));
                list.add(Box.createVerticalStrut(4));
                shown++;
            }

            if (shown == 0)
            {
                JLabel empty = new JLabel("<html>No vows taken yet. When a card picker opens in-game,<br>the vow you choose will appear here.</html>");
                empty.setForeground(Color.GRAY);
                list.add(empty);
            }

            return list;
        }

        private JPanel buildVowRow(VowDefinition v)
        {
            boolean completed = storage.isCompleted(v);
            boolean active = isActive(v);
            boolean ritual = v.getType() == VowType.RITUAL;
            // Everything except a ritual binds permanently once taken.
            boolean sworn = completed && !ritual;

            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(sworn ? new Color(200, 140, 50)
                    : (active ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JLabel name = new JLabel(v.getName());
            name.setForeground(sworn ? new Color(240, 200, 90) : (active ? ColorScheme.BRAND_ORANGE : Color.WHITE));
            name.setFont(FontManager.getRunescapeSmallFont());
            JLabel desc = new JLabel("<html><body style='width:135px'>" + v.getDescription() + "</body></html>");
            desc.setForeground(Color.LIGHT_GRAY);
            desc.setFont(FontManager.getRunescapeSmallFont());
            left.add(name);
            left.add(desc);
            row.add(left, BorderLayout.CENTER);

            String status;
            Color statusColor;
            if (sworn)
            {
                status = "Sworn";
                statusColor = new Color(240, 200, 90);
            }
            else if (ritual && completed)
            {
                status = "Done";
                statusColor = new Color(120, 200, 120);
            }
            else if (active)
            {
                status = ritual ? "In progress" : "Active";
                statusColor = ColorScheme.BRAND_ORANGE;
            }
            else
            {
                status = "Locked";
                statusColor = new Color(120, 120, 120);
            }
            JLabel badge = new JLabel(status);
            badge.setForeground(statusColor);
            badge.setFont(FontManager.getRunescapeSmallFont());
            badge.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
            row.add(badge, BorderLayout.EAST);

            return row;
        }

        private boolean isActive(VowDefinition v)
        {
            VowDefinition p = storage.getActivePermanentVow();
            VowDefinition g = storage.getActiveGodVow();
            VowDefinition r = storage.getActiveRitualVow();
            return (p != null && p.getId().equals(v.getId()))
                || (g != null && g.getId().equals(v.getId()))
                || (r != null && r.getId().equals(v.getId()));
        }

        private JPanel row(String left, String right, Color rightColor)
        {
            JPanel r = new JPanel(new BorderLayout());
            r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            r.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            JLabel l = new JLabel(left);
            l.setForeground(Color.LIGHT_GRAY);
            l.setFont(FontManager.getRunescapeSmallFont());
            JLabel v = new JLabel(right);
            v.setForeground(rightColor);
            v.setFont(FontManager.getRunescapeSmallFont());
            v.setHorizontalAlignment(SwingConstants.RIGHT);
            r.add(l, BorderLayout.WEST);
            r.add(v, BorderLayout.EAST);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
            wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
            wrap.add(r, BorderLayout.CENTER);
            return wrap;
        }

        private JComponent wrapScroll(JComponent inner)
        {
            JScrollPane scroll = new JScrollPane(inner,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
            scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            return scroll;
        }
    }

    /** JPanel that reports its scrollable-width as the viewport's width so children never overflow horizontally. */
    private static final class ScrollingListPanel extends JPanel implements javax.swing.Scrollable
    {
        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static String prettyName(GodAlignment god)
    {
        if (god == null || god == GodAlignment.NONE)
        {
            return "None";
        }
        String s = god.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String godTagline(GodAlignment god)
    {
        switch (god)
        {
            case SARADOMIN: return "Order & light";
            case ZAMORAK:   return "Chaos & blood";
            case GUTHIX:    return "Balance";
            case ARMADYL:   return "Justice & sky";
            case ZAROS:     return "Fate & shadow";
            case BANDOS:    return "War & strength";
            default:        return "";
        }
    }

    private static String gearCeilingLabel(Rank rank)
    {
        if (rank == null) return "\u2014";
        switch (rank)
        {
            case NONE:
            case FOLLOWER:
                return "up to Steel / Black";
            case DEACON:
                return "up to Mithril";
            case PRIEST:
                return "up to Adamant";
            case BISHOP:
                return "up to Rune / d'hide";
            case ARCHBISHOP:
                return "up to Dragon / mystic";
            case CARDINAL:
                return "up to Barrows / Bandos";
            case CHOSEN:
                return "unrestricted";
            default:
                return "\u2014";
        }
    }

    private static Color godColor(GodAlignment god)
    {
        switch (god)
        {
            case SARADOMIN: return new Color(64, 156, 255);
            case ZAMORAK:   return new Color(214, 60, 60);
            case GUTHIX:    return new Color(76, 175, 80);
            case ARMADYL:   return new Color(240, 214, 100);
            case ZAROS:     return new Color(158, 90, 214);
            case BANDOS:    return new Color(140, 100, 60);
            default:        return ColorScheme.BRAND_ORANGE;
        }
    }
}
