import java.awt.*;
import javax.swing.*;

public class HUDPanel extends JPanel {

    private final JLabel timerLabel;
    private final JLabel[] scoreBoxLabels = new JLabel[4];
    private final JLabel respawnCountdownLabel;
    private GameEventListener gameEventListener;
    private boolean gameEndFired = false;
    private int currentRespawnCountdown = 0;
    private long lastRespawnUpdateTime = 0;

    public void setGameEventListener(GameEventListener listener) {
        this.gameEventListener = listener;
    }

    public HUDPanel() {
        setLayout(null);
        setPreferredSize(new Dimension(900, 48));
        setBackground(Style.BG_DARK);

        JLabel title = Style.makeLabel("CHRONOARENA", Style.FONT_TITLE, Style.TEXT_GOLD);
        title.setBounds(12, 10, 230, 28);
        add(title);

        timerLabel = Style.makeLabel("TIME LEFT: --:--", Style.FONT_LARGE, Style.TEXT_WHITE);
        timerLabel.setBounds(270, 10, 160, 28);
        add(timerLabel);

        JLabel scoreLbl = Style.makeLabel("SCORE", Style.FONT_XS, Color.LIGHT_GRAY);
        scoreLbl.setBounds(643, 13, 50, 22);
        add(scoreLbl);

        for (int i = 0; i < 4; i++) {
            scoreBoxLabels[i] = new JLabel("0", SwingConstants.CENTER);
            scoreBoxLabels[i].setFont(Style.FONT_NORM);
            scoreBoxLabels[i].setForeground(Style.TEXT_WHITE);
            scoreBoxLabels[i].setOpaque(true);
            scoreBoxLabels[i].setBackground(Style.PLAYER_ACCENTS[i]);
            scoreBoxLabels[i].setBounds(690 + i * 52, 10, 46, 28);
            add(scoreBoxLabels[i]);
        }

        respawnCountdownLabel = new JLabel("", SwingConstants.CENTER);
        respawnCountdownLabel.setFont(Style.FONT_LARGE);
        respawnCountdownLabel.setForeground(new Color(100, 180, 255));
        respawnCountdownLabel.setBounds(440, 0, 180, 48);
        add(respawnCountdownLabel);
    }

    // called by chronoArenaClient when server sends updated state
    public void update(int secondsLeft, int[] scores) {
        int m = secondsLeft / 60, s = secondsLeft % 60;
        timerLabel.setText(String.format("TIME LEFT: %02d:%02d", m, s));
        timerLabel.setForeground(secondsLeft < 30 ? new Color(220, 60, 60) : Style.TEXT_WHITE);
        for (int i = 0; i < 4 && i < scores.length; i++) {
            scoreBoxLabels[i].setText(String.valueOf(scores[i]));
        }
        if (secondsLeft <= 0 && !gameEndFired && gameEventListener != null) {
            gameEndFired = true;
            gameEventListener.onGameEnd();
        }
    }

    public void showRespawnCountdown() {
        currentRespawnCountdown = 1; // non-zero signals active respawn
        lastRespawnUpdateTime = System.currentTimeMillis();
        respawnCountdownLabel.setText("RESPAWNING");
        repaint();
    }

    public void clearRespawnMessage() {
        respawnCountdownLabel.setText("");
        currentRespawnCountdown = 0;
        repaint();
    }

    public void reset() {
        gameEndFired = false;
        clearRespawnMessage();
    }


}