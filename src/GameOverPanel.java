import javax.swing.*;
import java.awt.*;

public class GameOverPanel extends JPanel {

    private final JLabel[] scoreLabels = new JLabel[4];
    private final JLabel winnerLabel;

    public GameOverPanel() {
        setLayout(null);
        setOpaque(true);
        setBackground(new Color(10, 10, 20, 220));
        setVisible(false);

        // title
        JLabel title = new JLabel("GAME OVER", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 48));
        title.setForeground(new Color(255, 200, 40));
        title.setBounds(0, 80, 900, 60);
        add(title);

        // sub-header
        JLabel subHeader = new JLabel("FINAL SCORES", SwingConstants.CENTER);
        subHeader.setFont(new Font("SansSerif", Font.BOLD, 18));
        subHeader.setForeground(new Color(150, 155, 170));
        subHeader.setBounds(0, 155, 900, 28);
        add(subHeader);

        Color[] accents = Style.PLAYER_ACCENTS;

        for (int i = 0; i < 4; i++) {
            // Left side: coloured player name
            JLabel nameLbl = new JLabel("PLAYER " + (i + 1) + ":", SwingConstants.RIGHT);
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 20));
            nameLbl.setForeground(accents[i]);
            nameLbl.setBounds(250, 200 + i * 48, 180, 32);
            add(nameLbl);

            // Right side: score value (updated in show())
            scoreLabels[i] = new JLabel("0 pts", SwingConstants.LEFT);
            scoreLabels[i].setFont(new Font("SansSerif", Font.BOLD, 20));
            scoreLabels[i].setForeground(Style.TEXT_WHITE);
            scoreLabels[i].setBounds(450, 200 + i * 48, 200, 32);
            add(scoreLabels[i]);
        }

        // divider above winner
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 65, 80));
        sep.setBounds(250, 408, 400, 2);
        add(sep);

        // winner
        winnerLabel = new JLabel("", SwingConstants.CENTER);
        winnerLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        winnerLabel.setForeground(new Color(255, 200, 40));
        winnerLabel.setBounds(0, 420, 900, 40);
        add(winnerLabel);

        // play again hint
        JLabel hint = new JLabel("Waiting for server...", SwingConstants.CENTER);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hint.setForeground(new Color(100, 110, 130));
        hint.setBounds(0, 480, 900, 24);
        add(hint);
    }

    // called by ChronoArenaClient.onGameEnd() with final scores.
    public void show(int[] scores, String[] playerNames) {
        for (int i = 0; i < 4 && i < scores.length; i++) {
            scoreLabels[i].setText(scores[i] + " pts");
        }

        int winnerIdx = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[winnerIdx]) winnerIdx = i;
        }

        winnerLabel.setText("\uD83C\uDFC6 PLAYER " + (winnerIdx + 1) + " WINS!");

        setVisible(true);
        repaint();
    }
}