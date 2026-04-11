import javax.swing.*;
import java.awt.*;

public class GameOverPanel extends JPanel {

    // ── Colors (mirrors LobbyPanel palette) ───────────────────────────────────
    private static final Color COL_GOLD   = new Color(255, 200,  40);
    private static final Color COL_STATUS = new Color(100, 110, 130);
    private static final Color COL_BTN_BG = new Color(30,   60, 100);
    private static final Color COL_BTN_FG = new Color(100, 180, 255);
    private static final Color COL_GREEN  = new Color(100, 220, 100);
    private static final Color COL_RED    = new Color(220, 80,  80);

    private static final int PANEL_W = 900;

    private final JLabel[]  scoreLabels  = new JLabel[4];
    private final JLabel[]  nameLabels   = new JLabel[4];
    private final JLabel    titleLabel;
    private final JLabel    subHeaderLabel;
    private final JLabel    winnerLabel;
    private final JLabel    timerLabel;

    private Runnable onLobbyCallback;       // fires when server says return to lobby

    private Timer   decisionTimer;          // server-side timeout mirrored here
    private int     secondsLeft = 15;

    public GameOverPanel() {
        setLayout(null);
        setOpaque(true);
        setBackground(new Color(10, 10, 20));
        setVisible(false);

        // ── Title ─────────────────────────────────────────────────────────────
        titleLabel = new JLabel("GAME OVER", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        titleLabel.setForeground(COL_GOLD);
        add(titleLabel);

        subHeaderLabel = new JLabel("FINAL SCORES", SwingConstants.CENTER);
        subHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        subHeaderLabel.setForeground(new Color(150, 155, 170));
        add(subHeaderLabel);

        // ── Score rows ────────────────────────────────────────────────────────
        Color[] accents = Style.PLAYER_ACCENTS;
        for (int i = 0; i < 4; i++) {
            nameLabels[i] = new JLabel("PLAYER " + (i + 1) + ":", SwingConstants.RIGHT);
            nameLabels[i].setFont(new Font("SansSerif", Font.BOLD, 20));
            nameLabels[i].setForeground(accents[i]);
            add(nameLabels[i]);

            scoreLabels[i] = new JLabel("0 pts", SwingConstants.LEFT);
            scoreLabels[i].setFont(new Font("SansSerif", Font.BOLD, 20));
            scoreLabels[i].setForeground(Style.TEXT_WHITE);
            add(scoreLabels[i]);
        }

        // ── Winner ────────────────────────────────────────────────────────────

        winnerLabel = new JLabel("", SwingConstants.CENTER);
        winnerLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        winnerLabel.setForeground(COL_GOLD);
        add(winnerLabel);

        // ── Countdown timer ───────────────────────────────────────────────────
        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        timerLabel.setForeground(COL_GOLD);
        timerLabel.setOpaque(true);
        timerLabel.setBackground(new Color(10, 10, 20));
        add(timerLabel);
    }

    // ── doLayout — runs every resize, centers content horizontally ────────────
    @Override
    public void doLayout() {
        super.doLayout();
        int W = getWidth();
        if (W == 0) return;

        int left = (W - PANEL_W) / 2;

        // Title and sub-header span full width, centered
        titleLabel.setBounds(0, 60, W, 60);
        subHeaderLabel.setBounds(0, 130, W, 28);

        // Score rows: names on the left, scores on the right (relative to center left)
        for (int i = 0; i < 4; i++) {
            nameLabels[i].setBounds(left + 250, 175 + i * 46, 180, 32);
            scoreLabels[i].setBounds(left + 450, 175 + i * 46, 200, 32);
        }

        // Winner label spans full width, centered
        winnerLabel.setBounds(0, 376, W, 40);

        // Timer label spans full width, centered
        timerLabel.setBounds(0, 512, W, 22);
    }

    // ── Public API ────────────────────────────────────────────────────────────



    /** Called when the server says not everyone agreed — return to lobby. */
    public void setOnLobbyCallback(Runnable callback) {
        this.onLobbyCallback = callback;
    }

    /** Called by ChronoArenaClient.onGameEnd() with final scores. */
    public void show(int[] scores, String[] playerNames, int connectedCount) {
        for (int i = 0; i < 4 && i < scores.length; i++) {
            scoreLabels[i].setText(scores[i] + " pts");
        }

        int maxScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > maxScore) maxScore = scores[i];
        }

        // Find all players with the max score
        java.util.List<Integer> winners = new java.util.ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] == maxScore) winners.add(i);
        }

        // Display tie or single winner
        if (winners.size() > 1) {
            StringBuilder tieMsg = new StringBuilder("PLAYERS ");
            for (int j = 0; j < winners.size(); j++) {
                tieMsg.append(winners.get(j) + 1);
                if (j < winners.size() - 2) tieMsg.append(", ");
                else if (j == winners.size() - 2) tieMsg.append(" AND ");
            }
            tieMsg.append(" TIED!");
            winnerLabel.setText(tieMsg.toString());
        } else {
            int winnerIdx = winners.get(0);
            winnerLabel.setText("\uD83C\uDFC6  PLAYER " + (winnerIdx + 1) + " WINS!");
        }

        setVisible(true);
        repaint();
        startDecisionTimer();
    }



    /** Automatically return to lobby after the timer expires. */
    public void triggerReturnToLobby() {
        stopDecisionTimer();
        SwingUtilities.invokeLater(() -> {
            timerLabel.setText("");
            Timer delay = new Timer(500, e -> {
                setVisible(false);
                if (onLobbyCallback != null) onLobbyCallback.run();
            });
            delay.setRepeats(false);
            delay.start();
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────



    /** Counts down 15 s locally — server is authoritative but this keeps the UI alive. */
    private void startDecisionTimer() {
        stopDecisionTimer();
        secondsLeft = 15;
        timerLabel.setText("Returning to lobby in " + secondsLeft + "s!");
        decisionTimer = new Timer(1000, e -> {
            secondsLeft--;
            if (secondsLeft > 0) {
                timerLabel.setText("Returning to lobby in " + secondsLeft + "s!");
            } else {
                // server will send RESTART_RESULT either way; just stop the display timer
                stopDecisionTimer();
                timerLabel.setText("");
            }
        });
        decisionTimer.start();
    }

    private void stopDecisionTimer() {
        if (decisionTimer != null) { decisionTimer.stop(); decisionTimer = null; }
    }

    public void reset() {
        SwingUtilities.invokeLater(() -> {
            stopDecisionTimer();
            timerLabel.setText("");
            setVisible(false);
        });
    }
}