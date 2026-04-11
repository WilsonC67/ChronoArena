import java.awt.*;
import javax.swing.*;

// overlays the map before the game starts
public class LobbyPanel extends JPanel {

    private static final int MIN_PLAYERS = 2; // minimum to allow game start

    private final JLabel[]   playerSlots     = new JLabel[4];
    private final JLabel[]   readyBadges     = new JLabel[4];
    private final JButton[]  readyButtons    = new JButton[4];
    private final JLabel     statusLabel;
    private final JLabel     countdownLabel;
    private final JButton    startButton;  // hidden — kept for layout compat, not shown
    private final JButton    voteButton;   // each player's personal vote-to-start
    private final JButton    timerButton;  // cycles round duration 2→3→4→5→2 min
    private final boolean[]  playerConnected = new boolean[4];
    private final boolean[]  playerReady     = new boolean[4];

    private int      localPlayerId    = -1;
    private Runnable readyUDPCallback;
    private Runnable voteUDPCallback;
    private Runnable timerChangeCallback;
    private int      voteCount        = 0;
    private int      totalCount       = 0;
    private boolean  localVoted       = false;

    private Timer    countdownTimer;
    private int      secondsLeft = 5;
    private Runnable onCountdownEnd;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color COL_MUTED  = new Color(70,  75,  95);
    private static final Color COL_STATUS = new Color(100, 110, 130);
    private static final Color COL_GREEN  = new Color(100, 220, 100);
    private static final Color COL_GOLD   = new Color(255, 200,  40);
    private static final Color COL_BTN_BG = new Color(30,   60, 100);
    private static final Color COL_BTN_FG = new Color(100, 180, 255);

    // ── Row sizing constants (used in doLayout) ───────────────────────────────
    private static final int NAME_W  = 420;
    private static final int BADGE_W = 110;
    private static final int BTN_W   = 110;
    private static final int BTN_H   = 30;
    private static final int GAP     = 12;
    private static final int ROW_W   = NAME_W + GAP + BADGE_W + GAP + BTN_W; // 664
    private static final int ROW_H   = 52;
    private static final int ROW_Y0  = 180;

    public LobbyPanel() {
        setLayout(null);
        setOpaque(true);
        setBackground(new Color(10, 10, 20));
        setVisible(true);

        // title
        JLabel title = new JLabel("CHRONOARENA", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 48));
        title.setForeground(COL_GOLD);
        add(title); // index 0

        // sub-header
        JLabel sub = new JLabel("WAITING FOR PLAYERS", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.BOLD, 16));
        sub.setForeground(new Color(150, 155, 170));
        add(sub); // index 1

        // player rows (3 components each: slot label, badge label, ready button)
        for (int i = 0; i < 4; i++) {
            final int slot = i;

            playerSlots[i] = new JLabel("PLAYER " + (i + 1) + " — WAITING...", SwingConstants.CENTER);
            playerSlots[i].setFont(new Font("SansSerif", Font.BOLD, 18));
            playerSlots[i].setForeground(COL_MUTED);
            add(playerSlots[i]);

            readyBadges[i] = new JLabel("", SwingConstants.CENTER);
            readyBadges[i].setFont(new Font("SansSerif", Font.BOLD, 13));
            readyBadges[i].setForeground(COL_MUTED);
            add(readyBadges[i]);

            readyButtons[i] = new JButton("READY UP");
            readyButtons[i].setFont(new Font("SansSerif", Font.BOLD, 13));
            readyButtons[i].setForeground(COL_BTN_FG);
            readyButtons[i].setBackground(COL_BTN_BG);
            readyButtons[i].setFocusPainted(false);
            readyButtons[i].setBorder(BorderFactory.createLineBorder(new Color(60, 120, 200), 1));
            readyButtons[i].setVisible(false);
            readyButtons[i].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            readyButtons[i].addActionListener(e -> onReadyClicked(slot));
            add(readyButtons[i]);
        }

        // status line
        statusLabel = new JLabel("0 / 4 players connected", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(COL_STATUS);
        add(statusLabel);

        // START GAME button
        startButton = new JButton("START GAME");
        startButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        startButton.setForeground(new Color(80, 80, 90));
        startButton.setBackground(new Color(50, 50, 60));
        startButton.setFocusPainted(false);
        startButton.setEnabled(false);
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        startButton.addActionListener(e -> startCountdown());
        add(startButton);

        // vote button (replaces START GAME for each client)
        voteButton = new JButton("VOTE TO START");
        voteButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        voteButton.setForeground(COL_BTN_FG);
        voteButton.setBackground(COL_BTN_BG);
        voteButton.setFocusPainted(false);
        voteButton.setBorder(BorderFactory.createLineBorder(new Color(60, 120, 200), 1));
        voteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voteButton.addActionListener(e -> onVoteClicked());
        add(voteButton);

        // timer button — any player can click to cycle round duration
        timerButton = new JButton("\u23f1  2:00");
        timerButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        timerButton.setForeground(COL_GOLD);
        timerButton.setBackground(new Color(40, 35, 10));
        timerButton.setFocusPainted(false);
        timerButton.setBorder(BorderFactory.createLineBorder(new Color(160, 120, 20), 1));
        timerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        timerButton.setToolTipText("Click to cycle round duration: 2 \u2192 3 \u2192 4 \u2192 5 \u2192 2 min");
        timerButton.addActionListener(e -> onTimerClicked());
        add(timerButton);

        // countdown label
        countdownLabel = new JLabel("", SwingConstants.CENTER);
        countdownLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        countdownLabel.setForeground(COL_GOLD);
        add(countdownLabel);
    }

    // ── doLayout — runs every resize, so all bounds use the real panel width ──
    @Override
    public void doLayout() {
        super.doLayout();
        int W = getWidth();
        if (W == 0) return;

        int left   = (W - ROW_W) / 2;
        int badgeX = left + NAME_W + GAP;
        int btnX   = badgeX + BADGE_W + GAP;

        // title (index 0), sub (index 1)
        getComponent(0).setBounds(0, 60,  W, 60);
        getComponent(1).setBounds(0, 120, W, 24);

        // player rows: components added in triples starting at index 2
        for (int i = 0; i < 4; i++) {
            int rowY = ROW_Y0 + i * ROW_H;
            playerSlots[i] .setBounds(left,   rowY,                      NAME_W,  ROW_H);
            readyBadges[i] .setBounds(badgeX, rowY,                      BADGE_W, ROW_H);
            readyButtons[i].setBounds(btnX,   rowY + (ROW_H - BTN_H)/2,  BTN_W,   BTN_H);
        }

        final int START_W = 250, START_H = 44;
        final int VOTE_W  = 220, VOTE_H  = 44;
        final int TIMER_W = 120, TIMER_H = 30;
        statusLabel   .setBounds(0,              400, W,       24);
        startButton   .setBounds(-9999, -9999, 0, 0); // hidden — server controls start
        voteButton    .setBounds((W-VOTE_W)/2,   432, VOTE_W,  VOTE_H);
        timerButton   .setBounds((W-TIMER_W)/2,  486, TIMER_W, TIMER_H);
        countdownLabel.setBounds(0,              526, W,       50);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call after server assigns a player ID — reveals READY UP on correct slot. */
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
        SwingUtilities.invokeLater(() -> { refreshReadyButtons(); refreshReadyBadges(); });
    }

    /** Wire to sendUDP so clicking READY UP fires the network message. */
    public void setReadyUDPCallback(Runnable callback) {
        this.readyUDPCallback = callback;
    }

    /** Wire to sendUDP so clicking VOTE TO START fires the network message. */
    public void setVoteUDPCallback(Runnable callback) {
        this.voteUDPCallback = callback;
    }

    /** Wire to sendUDP so clicking the timer button fires the network message. */
    public void setTimerChangeCallback(Runnable callback) {
        this.timerChangeCallback = callback;
    }

    /** Called when the server broadcasts a new round duration — updates the button label. */
    public void updateTimerDisplay(int seconds) {
        SwingUtilities.invokeLater(() ->
                timerButton.setText("\u23f1  " + (seconds / 60) + ":00"));
    }

    /** Called by the server (via COUNTDOWN_START) — starts countdown on ALL clients. */
    public void triggerCountdown() {
        SwingUtilities.invokeLater(this::startCountdown);
    }

    /** Called by ChronoArenaClient when a player connects or disconnects (existing hook). */
    public void updatePlayers(boolean[] connected) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            playerConnected[i] = connected[i];
            if (connected[i]) {
                playerSlots[i].setText("PLAYER " + (i + 1) + " — CONNECTED \u2713");
                playerSlots[i].setForeground(Style.PLAYER_ACCENTS[i]);
                count++;
            } else {
                playerSlots[i].setText("PLAYER " + (i + 1) + " — WAITING...");
                playerSlots[i].setForeground(COL_MUTED);
                playerReady[i] = false;
            }
        }
        statusLabel.setText(count + " / 4 players connected");
        refreshReadyButtons();
        refreshReadyBadges();
        refreshStartButton();
    }

    /** Called when the server broadcasts ready states for all players. */
    public void updateReady(boolean[] readyFlags) {
        for (int i = 0; i < 4; i++) playerReady[i] = readyFlags[i];
        SwingUtilities.invokeLater(() -> {
            refreshReadyBadges();
            refreshReadyButtons();
            refreshStartButton();
        });
    }

    /** Called to mark a single player as ready (existing hook). */
    public void setPlayerReady(int playerIndex, boolean ready) {
        if (playerIndex >= 0 && playerIndex < 4) {
            playerReady[playerIndex] = ready;
            refreshReadyBadges();
            refreshStartButton();
        }
    }

    /** Set the callback that fires when the countdown hits zero (existing hook). */
    public void setOnCountdownEnd(Runnable callback) {
        this.onCountdownEnd = callback;
    }

    /**
     * Resets all lobby state and makes the panel visible again.
     * Call this when returning from game-over, whether restarting or returning to lobby.
     */
    public void reset() {
        SwingUtilities.invokeLater(() -> {
            // Clear ready/vote state
            for (int i = 0; i < 4; i++) playerReady[i] = false;
            localVoted = false;
            voteCount  = 0;
            totalCount = 0;

            // Reset vote button
            voteButton.setText("VOTE TO START");
            voteButton.setEnabled(true);
            voteButton.setVisible(false);

            // Reset timer button
            timerButton.setText("\u23f1  2:00");

            // Reset countdown
            stopCountdown();
            countdownLabel.setText("");

            // Reset status
            statusLabel.setText("0 / 4 players connected");
            statusLabel.setForeground(COL_STATUS);

            // Reset ready badges and buttons
            refreshReadyBadges();
            refreshReadyButtons();

            setVisible(true);
            revalidate();
            repaint();
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void onVoteClicked() {
        if (localVoted) return;
        localVoted = true;
        voteButton.setEnabled(false);
        voteButton.setText("Voted! Waiting...");
        if (voteUDPCallback != null) voteUDPCallback.run();
    }

    private void onTimerClicked() {
        if (timerChangeCallback != null) timerChangeCallback.run();
    }

    private void onReadyClicked(int slot) {
        if (!playerConnected[slot]) return;
        playerReady[slot] = true;
        if (readyUDPCallback != null) readyUDPCallback.run();
        refreshReadyBadges();
        refreshReadyButtons();
        refreshStartButton();
    }

    private void refreshReadyButtons() {
        for (int i = 0; i < 4; i++) {
            boolean isLocalSlot = (localPlayerId - 1) == i;
            readyButtons[i].setVisible(isLocalSlot && playerConnected[i] && !playerReady[i]);
        }
    }

    private void refreshReadyBadges() {
        for (int i = 0; i < 4; i++) {
            boolean isLocalSlot = (localPlayerId - 1) == i;
            if (!playerConnected[i]) {
                readyBadges[i].setText("");
            } else if (playerReady[i]) {
                // always show READY for anyone who is ready, including local player
                readyBadges[i].setText("READY \u2713");
                readyBadges[i].setForeground(COL_GREEN);
            } else if (isLocalSlot) {
                readyBadges[i].setText(""); // local unready slot shows the button instead
            } else {
                readyBadges[i].setText("WAITING...");
                readyBadges[i].setForeground(COL_MUTED);
            }
        }
    }

    private void refreshStartButton() {
        int connectedCount = 0, readyCount = 0;
        for (int i = 0; i < 4; i++) {
            if (playerConnected[i]) { connectedCount++; if (playerReady[i]) readyCount++; }
        }
        totalCount = connectedCount;

        // vote button: only show after local player is ready, and only if not yet voted
        boolean localReady = localPlayerId >= 1 && playerReady[localPlayerId - 1];
        voteButton.setVisible(localReady && !localVoted);
        if (localVoted) {
            voteButton.setText(voteCount + " / " + totalCount + " voted to start");
        }

        // Only update the status label here if we're NOT in the voting phase yet.
        // Once voting starts, updateVotes() owns the status label.
        if (voteCount == 0) {
            if (readyCount >= MIN_PLAYERS) {
                statusLabel.setText(readyCount + " / " + connectedCount + " players ready");
                statusLabel.setForeground(COL_GREEN);
            } else {
                int needed = MIN_PLAYERS - readyCount;
                statusLabel.setText(readyCount + " / " + connectedCount + " players ready  —  need " + needed + " more ready");
                statusLabel.setForeground(COL_STATUS);
            }
        }
    }

    /** Called when server broadcasts updated vote tally (voteCount / totalCount). */
    public void updateVotes(int votes, int total) {
        this.voteCount = votes;
        this.totalCount = total;
        SwingUtilities.invokeLater(() -> {
            // Always update the status label so ALL clients see the live tally
            statusLabel.setText(votes + " / " + total + " voting to start");
            statusLabel.setForeground(COL_STATUS);
            // Update the vote button label for whoever already voted
            if (localVoted) {
                voteButton.setText(votes + " / " + total + " voted to start");
                voteButton.setVisible(true);
            }
        });
    }

    private void startCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) return;
        startButton.setEnabled(false);
        secondsLeft = 5;
        countdownLabel.setText("Game starts in " + secondsLeft + "...");
        statusLabel.setText("All players ready \u2014 Starting game!");
        statusLabel.setForeground(COL_GREEN);

        countdownTimer = new Timer(1000, e -> {
            secondsLeft--;
            if (secondsLeft > 0) {
                countdownLabel.setText("Game starts in " + secondsLeft + "...");
            } else {
                countdownTimer.stop();
                countdownLabel.setText("GO!");
                Timer hideTimer = new Timer(600, ev -> {
                    setVisible(false);
                    if (onCountdownEnd != null) onCountdownEnd.run();
                });
                hideTimer.setRepeats(false);
                hideTimer.start();
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) countdownTimer.stop();
        countdownLabel.setText("");
        statusLabel.setForeground(COL_STATUS);
    }
}