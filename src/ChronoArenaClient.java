import javax.swing.*;
import java.awt.*;

public class ChronoArenaClient extends JFrame {
    public ChronoArenaClient() {
        setTitle("ChronoArena");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        add(new GamePanel(), BorderLayout.CENTER);

        JPanel actionBar = new JPanel();
        actionBar.setBackground(Color.DARK_GRAY);
        actionBar.add(new JButton("DASH"));
        actionBar.add(new JButton("TAG"));
        add(actionBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChronoArenaClient());
    }
}