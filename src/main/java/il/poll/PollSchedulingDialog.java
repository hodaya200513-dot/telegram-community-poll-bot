package il.poll;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * דיאלוג תזמון שליחת סקר.
 * מאפשר שליחה מיידית או השהיה, ומציג ספירה לאחור (Countdown) חיה.
 */
public class PollSchedulingDialog extends JDialog {

    private final Poll poll;
    private final Consumer<Poll> onSend;

    private final CardLayout cards = new CardLayout();
    private final JPanel mainPanel = new JPanel(cards);

    // רכיבי שלב ההגדרה
    private final JRadioButton rbImmediate = new JRadioButton("שליחה מיידית", true);
    private final JRadioButton rbDelayed = new JRadioButton("שליחה בעיכוב של (דקות):");
    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 120, 1));
    private final JButton btnStart = new JButton("התחל סקר");

    // רכיבי שלב ה-Countdown
    private final JLabel statusLabel = new JLabel("הסקר ישלח בעוד:", SwingConstants.CENTER);
    private final JLabel countdownLabel = new JLabel("00:00", SwingConstants.CENTER);
    private final JButton btnClose = new JButton("סגור");

    private Timer timer;
    private int secondsRemaining;

    public PollSchedulingDialog(Frame owner, Poll poll, Consumer<Poll> onSend) {
        super(owner, "תזמון ושליחת סקר", true);
        this.poll = poll;
        this.onSend = onSend;

        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        setSize(400, 250);
        setLocationRelativeTo(owner);
        setResizable(false);

        mainPanel.add(buildConfigPanel(), "CONFIG");
        mainPanel.add(buildCountdownPanel(), "COUNTDOWN");
        add(mainPanel);

        setupListeners();
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JLabel title = new JLabel("מתי תרצה לשלוח את הסקר?");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        ButtonGroup group = new ButtonGroup();
        group.add(rbImmediate);
        group.add(rbDelayed);

        delaySpinner.setEnabled(false); // כברירת מחדל "מיידי" נבחר

        JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        delayPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        delayPanel.add(rbDelayed);
        delayPanel.add(delaySpinner);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        rbImmediate.setAlignmentX(Component.RIGHT_ALIGNMENT);
        delayPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        optionsPanel.add(rbImmediate);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(delayPanel);

        btnStart.setFont(btnStart.getFont().deriveFont(Font.BOLD, 14f));
        btnStart.setBackground(new Color(0x1B7F3B)); // ירוק בולט
        btnStart.setForeground(Color.WHITE);

        panel.add(title, BorderLayout.NORTH);
        panel.add(optionsPanel, BorderLayout.CENTER);
        panel.add(btnStart, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildCountdownPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));
        panel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 16f));

        countdownLabel.setFont(new Font("Monospaced", Font.BOLD, 48));
        countdownLabel.setForeground(new Color(0x333333));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        centerPanel.add(countdownLabel, BorderLayout.CENTER);

        btnClose.setVisible(false); // יופיע רק כשהסקר יישלח
        btnClose.addActionListener(e -> dispose());

        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(btnClose, BorderLayout.SOUTH);

        return panel;
    }

    private void setupListeners() {
        // הפעלה/כיבוי של שדה הדקות בהתאם לבחירה
        rbImmediate.addActionListener(e -> delaySpinner.setEnabled(false));
        rbDelayed.addActionListener(e -> delaySpinner.setEnabled(true));

        btnStart.addActionListener(e -> startProcess());
    }

    private void startProcess() {
        if (rbImmediate.isSelected()) {
            executeSend();
        } else {
            int minutes = (Integer) delaySpinner.getValue();
            startCountdown(minutes);
        }
    }

    private void startCountdown(int minutes) {
        secondsRemaining = minutes * 60;
        updateCountdownLabel();

        cards.show(mainPanel, "COUNTDOWN"); // מעבר לתצוגת הספירה לאחור
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // מניעת סגירת החלון בטעות בזמן ספירה

        timer = new Timer(1000, e -> {
            secondsRemaining--;
            updateCountdownLabel();

            if (secondsRemaining <= 0) {
                timer.stop();
                executeSend();
            }
        });
        timer.start();
    }

    private void updateCountdownLabel() {
        int m = secondsRemaining / 60;
        int s = secondsRemaining % 60;
        countdownLabel.setText(String.format("%02d:%02d", m, s));
    }

    /** מבצע את השליחה בפועל ומשנה את ממשק המשתמש למצב "הושלם" ללא עמימות */
    private void executeSend() {
        onSend.accept(poll); // הפעלת פעולת השליחה שהועברה מבחוץ

        // עדכון הממשק הגרפי למצב סיום מוצלח
        cards.show(mainPanel, "COUNTDOWN");
        statusLabel.setText("הסקר נשלח בהצלחה למשתתפים!");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 18f));
        statusLabel.setForeground(new Color(0x1B7F3B));

        countdownLabel.setText("✔");
        countdownLabel.setForeground(new Color(0x1B7F3B));

        btnClose.setVisible(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); // החזרת יכולת הסגירה
    }
}