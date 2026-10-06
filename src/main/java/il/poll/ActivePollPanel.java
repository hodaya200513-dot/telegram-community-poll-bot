package il.poll;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * פאנל מעקב חי אחר הסקר הפעיל (דרישה 8).
 * מציג נתונים סטטיסטיים וטבלה מתעדכנת בזמן אמת ללא צורך ב-Refresh ידני.
 */
public class ActivePollPanel extends JPanel {

    private final ActivePoll activePoll;
    private final DefaultTableModel tableModel;
    private final JLabel lblTimeRemaining = new JLabel();
    private boolean resultsShown = false;

    // רכיבי הסטטיסטיקה העליונים
    private final JLabel lblParticipants = new JLabel();
    private final JLabel lblCompleted = new JLabel();
    private final JLabel lblPending = new JLabel();

    private static final ComponentOrientation RTL = ComponentOrientation.RIGHT_TO_LEFT;

    public ActivePollPanel(ActivePoll activePoll) {
        this.activePoll = activePoll;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        applyComponentOrientation(RTL);

        // 1. פאנל עליון: נתונים כלליים על הסקר
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("נתוני הסקר הפעיל"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        statsPanel.applyComponentOrientation(RTL);

        styleStatLabel(lblParticipants, new Color(0x0277BD));
        styleStatLabel(lblCompleted, new Color(0x1B7F3B));
        styleStatLabel(lblPending, new Color(0xE65100));
        styleStatLabel(lblTimeRemaining, new Color(0xC2185B)); // צבע בולט לזמן

        statsPanel.add(lblParticipants);
        statsPanel.add(lblCompleted);
        statsPanel.add(lblPending);
        statsPanel.add(lblTimeRemaining); // הוספה לפאנל
        add(statsPanel, BorderLayout.NORTH);

        // 2. טבלה מרכזית: מעקב אישי אחר כל משתתף
        String[] columns = {"שם", "התקדמות", "מצב"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(tableModel);
        table.setComponentOrientation(RTL);
        table.getTableHeader().setComponentOrientation(RTL);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centered);
        table.getColumnModel().getColumn(2).setCellRenderer(centered);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setComponentOrientation(RTL);
        add(scroll, BorderLayout.CENTER);

        // 3. טעינת נתונים ראשונית והרשמה להאזנה לעדכונים חיים מהבוט
        refreshView();
        activePoll.addProgressListener(() -> SwingUtilities.invokeLater(this::refreshView));
    }

    private void styleStatLabel(JLabel label, Color color) {
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setForeground(color);
        label.setHorizontalAlignment(SwingConstants.CENTER);
    }

    /** מעדכן את הנתונים בטבלה ובסטטיסטיקה בזמן אמת */
    private void refreshView() {
        if (activePoll.isClosed() && !resultsShown) {
            resultsShown = true;
            removeAll(); // מנקים את טבלת המעקב
            add(new PollResultsPanel(activePoll), BorderLayout.CENTER); // מוסיפים את התוצאות
            revalidate();
            repaint();
            return; // עוצרים כאן, אין צורך לרענן יותר את הטבלה
        }
        tableModel.setRowCount(0);
        int total = activePoll.getParticipants().size();
        int completedCount = 0;
        int mins = activePoll.getSecondsRemaining() / 60;
        int secs = activePoll.getSecondsRemaining() % 60;
        lblTimeRemaining.setText(String.format("זמן שנותר: %02d:%02d", mins, secs));

        if (activePoll.isClosed()) {
            lblTimeRemaining.setText("הסקר נסגר ⏹");
        }

        for (Member member : activePoll.getParticipants()) {
            int answered = activePoll.getAnsweredCount(member.userId());
            int totalQuestions = activePoll.getPoll().questions().size();
            String progress = answered + "/" + totalQuestions;
            String status = activePoll.getUserStatus(member.userId());

            if ("השלים".equals(status)) {
                completedCount++;
            }

            tableModel.addRow(new Object[]{
                    member.name(),
                    progress,
                    status
            });
        }

        lblParticipants.setText("משתתפים בסקר: " + total);
        lblCompleted.setText("השלימו: " + completedCount);
        lblPending.setText("טרם השלימו: " + (total - completedCount));
    }
}
