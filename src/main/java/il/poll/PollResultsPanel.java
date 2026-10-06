package il.poll;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * מציג את התוצאות הסופיות של הסקר, ממוינות לפי שכיחות ההצבעות (דרישה 11).
 */
public class PollResultsPanel extends JPanel {

    // מחלקת עזר פנימית לשמירת נתוני אפשרות תשובה ומיון שלה
    private static class OptionResult implements Comparable<OptionResult> {
        String text;
        int votes;
        double percentage;

        public OptionResult(String text, int votes, double percentage) {
            this.text = text;
            this.votes = votes;
            this.percentage = percentage;
        }

        @Override
        public int compareTo(OptionResult other) {
            // מיון יורד: התשובה עם הכי הרבה קולות תופיע ראשונה
            return Integer.compare(other.votes, this.votes);
        }
    }

    public PollResultsPanel(ActivePoll activePoll) {
        setLayout(new BorderLayout());
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        container.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        // כותרת
        JLabel header = new JLabel("תוצאות הסקר הסופיות");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 22f));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(header);
        container.add(Box.createRigidArea(new Dimension(0, 20)));

        List<Question> questions = activePoll.getPoll().questions();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Map<Integer, Integer> counts = activePoll.getVoteCountsForQuestion(i);

            // חישוב סך הקולות הכשרים לשאלה זו
            int totalVotes = counts.values().stream().mapToInt(Integer::intValue).sum();

            // בניית רשימת התוצאות
            List<OptionResult> results = new ArrayList<>();
            List<String> options = q.options();
            for (int j = 0; j < options.size(); j++) {
                int votes = counts.get(j);
                double pct = (totalVotes == 0) ? 0.0 : ((double) votes / totalVotes) * 100.0;
                results.add(new OptionResult(options.get(j), votes, pct));
            }

            // מיון התוצאות (סעיף 11: מיון לפי שכיחות)
            Collections.sort(results);

            // בניית הפאנל לשאלה הספציפית
            JPanel qPanel = new JPanel();
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.GRAY, 1, true),
                    "שאלה " + (i + 1) + ": " + q.text()
            ));
            qPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

            // הצגת האפשרויות עם מד התקדמות (Progress Bar)
            for (OptionResult res : results) {
                JPanel row = new JPanel(new BorderLayout(15, 0));
                row.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                row.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

                JLabel lblText = new JLabel(res.text);
                lblText.setPreferredSize(new Dimension(150, 25));
                lblText.setFont(lblText.getFont().deriveFont(14f));

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue((int) res.percentage);
                bar.setStringPainted(true);
                bar.setString(String.format("%.1f%% (%d קולות)", res.percentage, res.votes));
                // צביעת המדד לירוק אם זו התשובה המובילה, אחרת כחול
                bar.setForeground(res.votes > 0 && res.votes == results.get(0).votes ? new Color(0x2E7D32) : new Color(0x1976D2));

                row.add(lblText, BorderLayout.EAST);
                row.add(bar, BorderLayout.CENTER);
                qPanel.add(row);
            }

            container.add(qPanel);
            container.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }
}