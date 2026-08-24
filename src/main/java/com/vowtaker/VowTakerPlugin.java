package com.vowtaker;

import com.google.inject.Provides;
import com.vowtaker.model.GodAlignment;
import com.vowtaker.model.VowDefinition;
import com.vowtaker.model.VowSelection;
import com.vowtaker.service.MilestoneService;
import com.vowtaker.service.TaskEnforcementService;
import com.vowtaker.service.TaskRegistry;
import com.vowtaker.service.TaskService;
import com.vowtaker.service.VowEnforcementService;
import com.vowtaker.service.VowSelectionService;
import com.vowtaker.service.VowStorageService;
import com.vowtaker.ui.VowChoiceOverlay;
import com.vowtaker.ui.VowOverlay;
import com.vowtaker.ui.VowTakerPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "VowTaker",
    description = "Vow-based challenge system with milestone-triggered selection and client-side restriction enforcement.",
    enabledByDefault = false,
    tags = {"challenge", "ironman", "vows", "progression"}
)
public class VowTakerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private VowTakerConfig config;

    @Inject
    private VowStorageService storageService;

    @Inject
    private MilestoneService milestoneService;

    @Inject
    private VowSelectionService selectionService;

    @Inject
    private VowEnforcementService enforcementService;

    @Inject
    private com.vowtaker.service.ItemTagRegistry itemTags;

    @Inject
    private com.vowtaker.update.VowTakerUpdater updater;

    @Inject
    private TaskService taskService;

    @Inject
    private TaskEnforcementService taskEnforcement;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private VowOverlay vowOverlay;

    @Inject
    private VowChoiceOverlay choiceOverlay;

    @Inject
    private VowTakerPanel panel;

    @Inject
    private ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private boolean started;

    @Provides
    VowTakerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(VowTakerConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        started = true;
        String accountKey = client != null ? client.getUsername() : null;
        storageService.initializeSession(accountKey);
        milestoneService.initialize();
        selectionService.initialize();
        enforcementService.initialize();
        itemTags.initialize();

        TaskRegistry.loadDefaults();
        int overrides = TaskRegistry.loadOverrides(taskOverridePath());
        if (overrides > 0)
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: loaded " + overrides + " task override(s) from tasks.json.", null);
        }

        taskService.initialize();

        if (config.showOverlay())
        {
            overlayManager.add(vowOverlay);
        }
        overlayManager.add(choiceOverlay);

        selectionService.setOnSelectionResolved(msg ->
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", msg, null);
            taskService.reevaluateRank();
            panel.refresh();
        });

        Runnable panelRefresh = () -> SwingUtilities.invokeLater(panel::refresh);
        enforcementService.setPanelRefreshCallback(panelRefresh);
        taskService.setPanelRefreshCallback(panelRefresh);

        // Any point award (task, vow, ritual) re-checks promotion so no source silently skips the ladder.
        storageService.setPointsListener(taskService::checkPromotion);

        panel.setGodChosenCallback(g -> {
            // Panel invokes this on the Swing EDT. Persist the god immediately so the panel view flips,
            // then hop to the client thread for anything that reads client state or opens the modal.
            storageService.setSelectedGod(g);
            panel.refresh();
            clientThread.invoke(() -> {
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "VowTaker: sworn to " + g.name() + ". Drawing your first god-bound vow...", null);
                selectionService.queueForcedGodOnlyOpen();
                requestSelectionOpen();
                if (selectionService.hasPendingSelection())
                {
                    // ok
                }
                else if (selectionService.hasPendingForcedOpen())
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: card picker queued \u2014 waiting for you to leave the instance / disengage.", null);
                }
                else
                {
                    int approved = storageService.getApprovedVowIds().size();
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: no eligible vows to draw (approved=" + approved + "). Try !vow reset or check tasks.json.", null);
                }
            });
        });
        panel.setVowSelectedCallback(vowId -> {
            if ("__reset__".equals(vowId))
            {
                storageService.resetAll();
                selectionService.initialize();
                panel.refresh();
                return;
            }
            boolean applied = selectionService.applySelection(vowId);
            if (applied)
            {
                showActivationSummary(vowId);
            }
            panel.refresh();
        });

        SwingUtilities.invokeLater(panel::refresh);

        navButton = NavigationButton.builder()
            .tooltip("VowTaker")
            .icon(buildIcon())
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        if (config.autoUpdate())
        {
            // Off the client thread: a slow release server must never stall plugin startup.
            new Thread(() -> updater.checkForUpdate(msg ->
                clientThread.invoke(() ->
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", msg, null))),
                "vowtaker-update-check").start();
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        started = false;
        overlayManager.remove(vowOverlay);
        overlayManager.remove(choiceOverlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        storageService.save();
        updater.scheduleSwapOnExit();
    }

    private static BufferedImage buildIcon()
    {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(210, 130, 30));
        g.fillRoundRect(1, 1, 22, 22, 6, 6);
        g.setColor(new Color(28, 28, 32));
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int x = (24 - fm.stringWidth("V")) / 2;
        int y = (24 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString("V", x, y);
        g.dispose();
        return img;
    }

    private static java.nio.file.Path taskOverridePath()
    {
        java.io.File base = net.runelite.client.RuneLite.RUNELITE_DIR != null
            ? net.runelite.client.RuneLite.RUNELITE_DIR
            : new java.io.File(System.getProperty("user.home"), ".runelite");
        return base.toPath().resolve("vowtaker").resolve("tasks.json");
    }

    /** Request the card picker; opens immediately if safe, otherwise queues for when the player leaves the instance. */
    private void requestSelectionOpen()
    {
        if (inSensitiveArea())
        {
            selectionService.queueForcedOpen();
            return;
        }
        selectionService.forceOpenSelection();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return;
        }

        milestoneService.updateMilestones();
        selectionService.pollSelection();
        enforcementService.tick();
        // Cheap idempotent re-check: guarantees a promotion can never be permanently missed,
        // whatever order points and milestones arrived in.
        taskService.checkPromotion();

        // Quartile ticker: 25/50/75% of the current rank-up bar each queue a rank-appropriate vow draw.
        if (selectionService.checkPointsProgress())
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: your devotion deepens \u2014 a new vow awaits.", null);
        }

        // Safety gate: if we owe the player a card pick but they're in an instance (Inferno, raid, Fight Caves)
        // or actively interacting with something, defer until they leave / disengage.
        if (selectionService.hasPendingForcedOpen() && !selectionService.hasPendingSelection() && !inSensitiveArea())
        {
            selectionService.forceOpenSelection();
            if (selectionService.hasPendingSelection())
            {
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "VowTaker: your vow selection is now open.", null);
            }
        }
    }

    private boolean inSensitiveArea()
    {
        if (client.isInInstancedRegion())
        {
            return true;
        }
        net.runelite.api.Player local = client.getLocalPlayer();
        return local != null && local.getInteracting() != null;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (!started)
        {
            return;
        }

        enforcementService.onVarbitChanged(event);
        milestoneService.onVarbitChanged(event);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() == client.getLocalPlayer())
        {
            enforcementService.onAnimationChanged(event);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        taskService.onHitsplatApplied(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        taskService.onNpcDespawned(event);
        enforcementService.onNpcDespawned(event);
        panel.refresh();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }

        String message = event.getMessage().trim();
        // RuneScape auto-capitalises the first character of chat lines, so match case-insensitively.
        String lower = message.toLowerCase();
        boolean isCommand = lower.startsWith("!vow ") || lower.startsWith("::vow ")
            || lower.equals("!vow") || lower.equals("::vow");

        if (isCommand && isFromLocalPlayer(event))
        {
            handleVowCommand(lower);
            // Prevent the command from bleeding through to task/enforcement chat listeners.
            return;
        }

        enforcementService.onChatMessage(event);
        milestoneService.onChatMessage(event);
        taskService.onChatMessage(event);
    }

    /** RuneScape display names use NBSP (\u00A0) and may carry color/icon tags — normalise before comparing. */
    private boolean isFromLocalPlayer(ChatMessage event)
    {
        // Public/friend/clan chats include the sender's display name; other channels do not, so accept those too.
        net.runelite.api.ChatMessageType type = event.getType();
        if (type == net.runelite.api.ChatMessageType.GAMEMESSAGE
            || type == net.runelite.api.ChatMessageType.CONSOLE
            || type == net.runelite.api.ChatMessageType.ENGINE)
        {
            return true;
        }
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
        {
            return false;
        }
        String local = normaliseName(client.getLocalPlayer().getName());
        String sender = normaliseName(event.getName());
        return !local.isEmpty() && local.equals(sender);
    }

    private static String normaliseName(String raw)
    {
        if (raw == null) return "";
        String stripped = net.runelite.client.util.Text.removeTags(raw);
        return stripped.replace('\u00A0', ' ').trim().toLowerCase();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // Modal gate: while a forced vow selection is open, translate any click into a card pick
        // if the mouse is over a card, otherwise consume the click so the world action doesn't fire.
        if (selectionService.hasPendingSelection())
        {
            net.runelite.api.Point mp = client.getMouseCanvasPosition();
            java.awt.Point pt = mp != null ? new java.awt.Point(mp.getX(), mp.getY()) : null;
            if (choiceOverlay.isRerollAt(pt))
            {
                if (selectionService.rerollSelection())
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: re-rolled \u2014 " + selectionService.getRerollTokens() + " token(s) left.", null);
                    panel.refresh();
                }
                event.consume();
                return;
            }
            String vowId = pt != null ? choiceOverlay.cardAt(pt) : null;
            if (vowId != null)
            {
                boolean applied = selectionService.applySelection(vowId);
                if (!applied)
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: could not apply that vow.", null);
                }
                panel.refresh();
            }
            event.consume();
            return;
        }

        enforcementService.onMenuOptionClicked(event);
        taskEnforcement.onMenuOptionClicked(event);
    }

    @Subscribe
    public void onGameStateChanged(net.runelite.api.events.GameStateChanged event)
    {
        if (!started || event.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        // The client has no username at startUp(), so bind the save to the account on login instead.
        String name = client.getUsername();
        if (name == null || name.trim().isEmpty())
        {
            return;
        }
        if (storageService.getSessionKey().equalsIgnoreCase(name.trim()))
        {
            return;
        }

        storageService.initializeSession(name);
        String adopted = storageService.consumeAdoptedLegacySave();
        if (adopted != null)
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: imported your previous progress from " + adopted + ".", null);
        }
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
            "VowTaker: profile loaded for " + name.trim() + ".", null);
        // Re-evaluate here so a save that was left eligible-but-unpromoted corrects itself.
        taskService.checkPromotion();
        SwingUtilities.invokeLater(panel::refresh);
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        enforcementService.filterMenuEntries();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        // While a forced selection is open, deprioritize every game menu entry so the overlay's
        // Choose-vow entries dominate right-click. We don't remove them (removal is fiddly and
        // wholesale menu wipes race with the client), we just push them below Cancel.
        if (selectionService.hasPendingSelection()
            && event.getMenuEntry() != null
            && event.getMenuEntry().getType() != net.runelite.api.MenuAction.RUNELITE_OVERLAY
            && event.getMenuEntry().getType() != net.runelite.api.MenuAction.CANCEL)
        {
            event.getMenuEntry().setDeprioritized(true);
        }

        taskEnforcement.onMenuEntryAdded(event);
        enforcementService.onMenuEntryAdded(event);
    }

    private void handleVowCommand(String message)
    {
        String[] parts = message.split("\\s+");
        if (parts.length < 2)
        {
            showHelpBanner();
            return;
        }

        String command = parts[1].toLowerCase();
        switch (command)
        {
            case "help":
            case "h":
                showHelpBanner();
                break;
            case "choose":
            case "select":
                if (parts.length >= 3)
                {
                    boolean selected = selectionService.applySelection(parts[2]);
                    if (selected)
                    {
                        showActivationSummary(parts[2]);
                    }
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: " + (selected ? "applied vow " + parts[2] + " to your account." : "could not apply vow " + parts[2] + "."), null);
                }
                break;
            case "status":
                showStatusBanner();
                break;
            case "tags":
                showTagDiagnostics(parts.length > 2 ? message.substring(message.indexOf(parts[2])) : null);
                break;
            case "trigger":
            case "test":
                requestSelectionOpen();
                if (selectionService.hasPendingSelection())
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: card picker opened.", null);
                }
                else if (selectionService.hasPendingForcedOpen())
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: selection queued \u2014 will open when you leave this instance / disengage.", null);
                }
                else
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: no vows available to draw. Pick a god first.", null);
                }
                panel.refresh();
                break;
            case "major":
                selectionService.queueMajorOpen();
                requestSelectionOpen();
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    selectionService.hasPendingSelection() ? "VowTaker: major picker opened."
                        : (selectionService.hasPendingForcedOpen() ? "VowTaker: major picker queued."
                            : "VowTaker: no major vows available (pick a god first)."), null);
                panel.refresh();
                break;
            case "minor":
                selectionService.queueMinorOpen();
                requestSelectionOpen();
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    selectionService.hasPendingSelection() ? "VowTaker: minor picker opened."
                        : (selectionService.hasPendingForcedOpen() ? "VowTaker: minor picker queued."
                            : "VowTaker: no minor vows available."), null);
                panel.refresh();
                break;
            case "reset":
                storageService.resetAll();
                selectionService.initialize();
                panel.refresh();
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: all state cleared. Choose your god again.", null);
                break;
            case "task":
                if (parts.length >= 3)
                {
                    boolean done = taskService.completeManual(parts[2]);
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        done ? "VowTaker: marked task " + parts[2] + " complete."
                             : "VowTaker: could not complete " + parts[2] + " (locked, unknown, or already done).", null);
                    panel.refresh();
                }
                else
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: usage !vow task <taskId>", null);
                }
                break;
            case "tasks":
                handleTasksCommand(parts);
                break;
            case "god":
                if (parts.length >= 3)
                {
                    try
                    {
                        storageService.setSelectedGod(GodAlignment.valueOf(parts[2].toUpperCase()));
                        showStatusBanner();
                    }
                    catch (IllegalArgumentException ignored)
                    {
                        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: valid gods are SARADOMIN, ZAMORAK, GUTHIX, ARMADYL, ZAROS.", null);
                    }
                }
                break;
            case "approve":
                if (parts.length >= 3)
                {
                    selectionService.approveVow(parts[2]);
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: approved vow " + parts[2] + ".", null);
                }
                else
                {
                    selectionService.approveAllPendingVows();
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: approved all pending draft vows.", null);
                }
                break;
            case "decline":
                if (parts.length >= 3)
                {
                    selectionService.declineVow(parts[2]);
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: declined vow " + parts[2] + ".", null);
                }
                else
                {
                    selectionService.declineAllPendingVows();
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: declined all pending draft vows.", null);
                }
                break;
            case "promote":
            {
                com.vowtaker.model.Rank before = storageService.getCurrentRank();
                taskService.checkPromotion();
                com.vowtaker.model.Rank after = storageService.getCurrentRank();
                if (before != after)
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: forced promotion " + before.getLabel() + " -> " + after.getLabel() + ".", null);
                }
                else
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: no promotion available (see !vow status for details).", null);
                }
                panel.refresh();
                break;
            }
            default:
                showHelpBanner();
                break;
        }
    }

    private void showHelpBanner()
    {
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: !vow help | status | trigger | major | minor | promote | reset | god <GOD> | task <taskId> | tasks reload | tasks template", null);
    }

    private void handleTasksCommand(String[] parts)
    {
        String sub = parts.length >= 3 ? parts[2].toLowerCase() : "";
        switch (sub)
        {
            case "reload":
            {
                TaskRegistry.loadDefaults();
                int applied = TaskRegistry.loadOverrides(taskOverridePath());
                taskService.initialize();
                panel.refresh();
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "VowTaker: reloaded tasks (" + applied + " override(s) applied).", null);
                break;
            }
            case "template":
            {
                java.nio.file.Path path = taskOverridePath();
                try
                {
                    java.nio.file.Files.createDirectories(path.getParent());
                    if (java.nio.file.Files.exists(path))
                    {
                        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                            "VowTaker: tasks.json already exists at " + path + " (not overwriting).", null);
                        return;
                    }
                    try (java.io.InputStream in = getClass().getResourceAsStream("/com/vowtaker/tasks.template.json"))
                    {
                        if (in == null)
                        {
                            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: template resource missing.", null);
                            return;
                        }
                        java.nio.file.Files.copy(in, path);
                    }
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: wrote template to " + path + " — edit then !vow tasks reload.", null);
                }
                catch (java.io.IOException ex)
                {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                        "VowTaker: failed to write template: " + ex.getMessage(), null);
                }
                break;
            }
            default:
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: usage !vow tasks reload | !vow tasks template", null);
                break;
        }
    }

    private void showActivationSummary(String vowId)
    {
        VowDefinition vow = com.vowtaker.service.VowRegistry.getById(vowId);
        if (vow == null)
        {
            return;
        }

        String summary = "VowTaker: activated " + vow.getName() + " — " + vow.getDescription() + " [Type=" + vow.getType() + "]";
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", summary, null);
    }

    private void showStatusBanner()
    {
        String summary;
        VowDefinition permanent = storageService.getActivePermanentVow();
        VowDefinition god = storageService.getActiveGodVow();
        VowDefinition ritual = storageService.getActiveRitualVow();
        summary = "VowTaker: God=" + storageService.getSelectedGod() + ", Permanent=" + (permanent == null ? "none" : permanent.getName()) + ", GodVow=" + (god == null ? "none" : god.getName()) + ", Ritual=" + (ritual == null ? "none" : ritual.getName());
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", summary, null);

        // Progression diagnostic block — critical for debugging why a promotion didn't fire.
        com.vowtaker.model.Rank current = storageService.getCurrentRank();
        com.vowtaker.model.Rank next = current.next();
        int totalPoints = storageService.getTotalPoints();
        int nextThreshold = next == current ? totalPoints : next.getRequiredPoints();
        com.vowtaker.model.TaskDefinition milestone = com.vowtaker.service.TaskRegistry.milestoneFor(current);
        String milestoneLabel;
        if (milestone == null)
        {
            milestoneLabel = "none";
        }
        else
        {
            milestoneLabel = milestone.getName() + " [" + milestone.getId() + "] " + (storageService.isTaskCompleted(milestone.getId()) ? "DONE" : "pending");
        }
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
            "VowTaker: Rank=" + current.getLabel() + " (tier " + current.getTier() + "), Points=" + totalPoints + "/" + nextThreshold
                + ", Next=" + next.getLabel() + ", Milestone=" + milestoneLabel
                + ", PendingPromotion=" + storageService.isPendingPromotion(), null);
    }

    /** "!vow tags" reports the blocklist file location; "!vow tags <item name>" shows its tags. */
    private void showTagDiagnostics(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: " + itemTags.getTagCount() + " item tags loaded. Edit: " + itemTags.getUserFileLocation(), null);
            String err = itemTags.getLastLoadError();
            if (err != null)
            {
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "VowTaker: tag warning \u2014 " + err, null);
            }
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                "VowTaker: tags = " + String.join(", ", itemTags.getTagNames()), null);
            return;
        }

        String name = itemName.trim();
        StringBuilder hits = new StringBuilder();
        for (String tag : itemTags.getTagNames())
        {
            if (itemTags.hasTag(tag, -1, name))
            {
                if (hits.length() > 0) hits.append(", ");
                hits.append(tag);
            }
        }
        client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
            "VowTaker: \"" + name + "\" tags = " + (hits.length() == 0 ? "(none)" : hits), null);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        enforcementService.onWidgetLoaded(event);
        selectionService.onWidgetLoaded(event);
    }

    public boolean isStarted()
    {
        return started;
    }

    public GodAlignment getSelectedGod()
    {
        return storageService.getSelectedGod();
    }

    public List<VowDefinition> getAvailableVows()
    {
        return selectionService.getForecastedVows();
    }

    public List<VowSelection> getHiddenCards()
    {
        return selectionService.getHiddenCards();
    }

    public VowDefinition getActivePermanentVow()
    {
        return storageService.getActivePermanentVow();
    }

    public VowDefinition getActiveGodVow()
    {
        return storageService.getActiveGodVow();
    }

    public VowDefinition getActiveRitualVow()
    {
        return storageService.getActiveRitualVow();
    }

    public void approveVow(String vowId)
    {
        selectionService.approveVow(vowId);
    }

    public void declineVow(String vowId)
    {
        selectionService.declineVow(vowId);
    }

    public void approveAllPendingVows()
    {
        selectionService.approveAllPendingVows();
    }

    public void declineAllPendingVows()
    {
        selectionService.declineAllPendingVows();
    }
}
