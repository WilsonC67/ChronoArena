import javax.swing.*;
import java.awt.*;

// overlays the map before the game starts
public class LobbyPanel extends JPanel {

    private final JLabel[] playerSlots = new JLabel[4];
    private final JLabel statusLabel;
    private final JLabel countdownLabel;
    private final boolean[] playerConnected = new boolean[4];
    private final boolean[] playerReady = new boolean[4];

    private Timer countdownTimer;
    private int secondsLeft = 5;
    private Runnable onCountdownEnd;

    public LobbyPanel() {
        setLayout(null);
        setOpaque(true);
        setBackground(new Color(10, 10, 20, 230));
        setVisible(true);

        // title
        JLabel title = new JLabel("CHRONOARENA", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 48));
        title.setForeground(new Color(255, 200, 40));
        title.setBounds(0, 60, 900, 60);
        add(title);

        // sub-header
        JLabel sub = new JLabel("WAITING FOR PLAYERS", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.BOLD, 16));
        sub.setForeground(new Color(150, 155, 170));
        sub.setBounds(0, 120, 900, 24);
        add(sub);

        // player slots
        Color[] accents = Style.PLAYER_ACCENTS;
        for (int i = 0; i < 4; i++) {
            playerSlots[i] = new JLabel("PLAYER " + (i + 1) + " — WAITING...", SwingConstants.CENTER);
            playerSlots[i].setFont(new Font("SansSerif", Font.BOLD, 18));
            playerSlots[i].setForeground(new Color(70, 75, 95));
            playerSlots[i].setBounds(0, 180 + i * 52, 900, 36);
            add(playerSlots[i]);
        }

        // status line
        statusLabel = new JLabel("0 / 4 players connected", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(100, 110, 130));
        statusLabel.setBounds(0, 400, 900, 24);
        add(statusLabel);

        // countdown label (hidden until all players connect and are ready)
        countdownLabel = new JLabel("", SwingConstants.CENTER);
        countdownLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        countdownLabel.setForeground(new Color(255, 200, 40));
        countdownLabel.setBounds(0, 440, 900, 50);
        add(countdownLabel);
    }

    // called by ChronoArenaClient when a player connects or disconnects.
    public void updatePlayers(boolean[] connected) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            playerConnected[i] = connected[i];
            if (connected[i]) {
                playerSlots[i].setText("PLAYER " + (i + 1) + " — CONNECTED ✓");
                playerSlots[i].setForeground(Style.PLAYER_ACCENTS[i]);
                count++;
            } else {
                playerSlots[i].setText("PLAYER " + (i + 1) + " — WAITING...");
                playerSlots[i].setForeground(new Color(70, 75, 95));
                playerReady[i] = false; // Reset ready status if disconnected
            }
        }

        statusLabel.setText(count + " / 4 players connected");

        checkIfShouldStartCountdown();
    }

    // called to mark a player as ready
    public void setPlayerReady(int playerIndex, boolean ready) {
        if (playerIndex >= 0 && playerIndex < 4) {
            playerReady[playerIndex] = ready;
            checkIfShouldStartCountdown();
        }
    }

    private void checkIfShouldStartCountdown() {
        boolean allConnected = true;

        for (int i = 0; i < 4; i++) {
            if (!playerConnected[i]) {
                allConnected = false;
            }
        }

        if (allConnected) {
            startCountdown();
        } else {
            stopCountdown();
        }
    }

    // set the callback that fires when the countdown hits zero.
    public void setOnCountdownEnd(Runnable callback) {
        this.onCountdownEnd = callback;
    }

    private void startCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) return;
        secondsLeft = 5;
        countdownLabel.setText("Game starts in " + secondsLeft + "...");
        statusLabel.setText("All players ready — Starting game!");
        statusLabel.setForeground(new Color(100, 220, 100));

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
        statusLabel.setForeground(new Color(100, 110, 130));
    }
}