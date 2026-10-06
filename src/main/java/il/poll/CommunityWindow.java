package il.poll;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.time.format.DateTimeFormatter;

/**
 * חלון הניהול. מחולק ללשוניות: קהילה (מתעדכנת בזמן אמת) ויצירת סקר.
 * כל עדכון של רכיבי ה-UI מתבצע על ה-Event Dispatch Thread.
 */
public class CommunityWindow extends JFrame {

    private final CommunityService community;
    private final CommunityBot bot;
    private final JTabbedPane tabs = new JTabbedPane(); // מוגדר כאן כדי שנוכל לגשת אליו מכל מקום בחלון

    private static final ComponentOrientation RTL = ComponentOrientation.RIGHT_TO_LEFT;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] COLUMNS = {"שם", "Telegram Username", "מועד הצטרפות"};

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel countLabel = new JLabel();
    private final JLabel emptyHint = new JLabel(
            "עדיין אין חברים בקהילה. שלחו Hi לבוט כדי להצטרף.", SwingConstants.CENTER);

    // הבנאי המעודכן שמקבל גם את הקהילה וגם את הבוט ושומר אותם
    public CommunityWindow(CommunityService community, CommunityBot bot) {
        super("ניהול סקרים");
        this.community = community;
        this.bot = bot;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 14f));
        tabs.addTab("קהילה", buildCommunityTab(community));
        tabs.addTab("יצירת סקר", new PollCreationPanel(new ChatGptClient(), this::showPollReady));
        add(tabs, BorderLayout.CENTER);

        setPreferredSize(new Dimension(780, 660));
        pack();
        setLocationRelativeTo(null);
        getContentPane().applyComponentOrientation(RTL);
    }
    private JPanel buildCommunityTab(CommunityService community) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        // כותרת + מונה חברים
        JLabel title = new JLabel("חברי הקהילה");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 14f));
        countLabel.setForeground(new Color(0x555555));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        header.add(title, BorderLayout.LINE_START);
        header.add(countLabel, BorderLayout.LINE_END);
        panel.add(header, BorderLayout.NORTH);

        // טבלה
        JTable table = new JTable(tableModel);
        table.setComponentOrientation(RTL);
        table.getTableHeader().setComponentOrientation(RTL);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centered);
        table.getColumnModel().getColumn(2).setCellRenderer(centered);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setComponentOrientation(RTL);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));

        // הודעת "ריק" מעל הטבלה עד שמצטרף החבר הראשון
        emptyHint.setForeground(new Color(0x777777));
        emptyHint.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        JPanel center = new JPanel(new BorderLayout());
        center.add(emptyHint, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        // טעינת חברים שכבר קיימים, ואז האזנה להצטרפויות חדשות
        for (Member member : community.getMembers()) {
            addRow(member);
        }
        refreshCount(community.size());
        community.addJoinListener(member -> SwingUtilities.invokeLater(() -> {
            addRow(member);
            refreshCount(community.size());
        }));

        return panel;
    }

    /** נקרא כשהסקר עבר ולידציה. שליחת הסקר והתזמון יתווספו בשלב הבא. */
    /** נקרא כשהסקר עבר ולידציה. מבצע בדיקת חברים ופותח את חלון התזמון. */
    private void showPollReady(Poll poll) {

        // --- סעיף 12: מניעת סקר כפול ---
        if (bot.hasActivePoll()) {
            JOptionPane.showMessageDialog(this,
                    "קיים סקר פעיל במערכת.\nאין אפשרות להתחיל סקר נוסף עד שהסקר הנוכחי יסתיים.",
                    "חסימת פעולה", JOptionPane.WARNING_MESSAGE);
            return; // עוצרים כאן ולא נותנים לו להמשיך
        }
        // אכיפת מינימום 3 חברים בקהילה
        if (community.size() < 3) {
            JOptionPane.showMessageDialog(this,
                    "לא ניתן להתחיל את הסקר. נדרשים לפחות 3 חברים בקהילה.",
                    "שגיאת הרשאה", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // פתיחת חלון התזמון
        PollSchedulingDialog dialog = new PollSchedulingDialog(this, poll, p -> {

            // כאן הקוד ששאלת עליו ממוקם:
            ActivePoll activePoll = new ActivePoll(p, community.getMembers());
            bot.startPoll(activePoll); // הבוט כבר ידאג לשאר (גם לשאלות וגם לתזכורות)

            // הוספת לשונית מעקב חיה לממשק ה-Swing
            tabs.addTab("📊 סקר פעיל", new ActivePollPanel(activePoll));
            tabs.setSelectedComponent(tabs.getComponentAt(tabs.getTabCount() - 1));
        });

        dialog.setVisible(true);
    }

    private void addRow(Member member) {
        tableModel.addRow(new Object[]{
                member.name(),
                member.usernameLabel(),
                member.joinedAt().format(TIME_FORMAT)
        });
        emptyHint.setVisible(false);
    }



    private void refreshCount(int size) {
        countLabel.setText("סה\"כ חברים: " + size);
        emptyHint.setVisible(size == 0);
    }
}

