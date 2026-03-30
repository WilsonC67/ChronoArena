import javax.swing.*;
import java.awt.*;

public class HUDPanel extends JPanel {

    private final JLabel timerLabel;
    private final JLabel[] scoreBoxLabels = new JLabel[4];

    public HUDPanel() {
        setLayout(null);
        setPreferredSize(new Dimension(900, 48));
        setBackground(Style.BG_DARK);

        JLabel title = Style.makeLabel("CHRONOARENA", Style.FONT_TITLE, Style.TEXT_GOLD);
        title.setBounds(12, 10, 230, 28);
        add(title);

        timerLabel = Style.makeLabel("TIME LEFT: --:--", Style.FONT_LARGE, Style.TEXT_WHITE);
        timerLabel.setBounds(330, 10, 220, 28);
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
    }

    // called by chronoArenaClient when server sends updated state
    public void update(int secondsLeft, int[] scores) {
        int m = secondsLeft / 60, s = secondsLeft % 60;
        timerLabel.setText(String.format("TIME LEFT: %02d:%02d", m, s));
        timerLabel.setForeground(secondsLeft < 30 ? new Color(220, 60, 60) : Style.TEXT_WHITE);
        for (int i = 0; i < 4 && i < scores.length; i++) {
            scoreBoxLabels[i].setText(String.valueOf(scores[i]));
        }
    }
}