package il.poll;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * לשונית יצירת סקר.
 * מעוצבת בסגנון מודרני עם פתרון לבאג הרינדור בריצת Swing.
 */
public class PollCreationPanel extends JPanel {

    private static final Color ERROR_COLOR = new Color(0xB00020);
    private static final Color SUCCESS = new Color(0x1B7F3B);
    private static final Color MUTED = new Color(0x666666);

    // צבעי עיצוב חדשים לכפתורים
    private static final Color PRIMARY_BTN = new Color(0, 120, 215); // כחול מודרני
    private static final Color SECONDARY_BTN = new Color(225, 225, 225); // אפור בהיר
    private static final Color ACTION_BTN = new Color(230, 245, 255); // כחול בהיר (להוספה)
    private static final Color AI_BTN = new Color(0, 150, 136); // ירוק-כחול (ל-ChatGPT)

    private static final ComponentOrientation RTL = ComponentOrientation.RIGHT_TO_LEFT;
    private static final Dimension LABEL_SIZE = new Dimension(90, 26);

    private final ChatGptClient chatGpt;
    private final Consumer<Poll> onPollReady;
    private final List<QuestionEditor> editors = new ArrayList<>();
    private boolean aiResultShown = false;

    private final JRadioButton manualMode = new JRadioButton("כתיבה ידנית", true);
    private final JRadioButton aiMode = new JRadioButton("יצירה באמצעות ChatGPT");

    private final JPanel aiPanel = new JPanel(new BorderLayout(0, 4));
    private final JTextField topicField = new JTextField();
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 3, 1));
    private final JButton generateButton = new JButton("יצירת שאלות");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel aiStatus = new JLabel(" ");

    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JPanel questionsContainer = new JPanel();
    private final JButton addQuestionButton = new JButton("+ הוספת שאלה");

    private final JLabel feedback = new JLabel(" ");
    private final JButton submitButton = new JButton("אישור הסקר");
    private final JButton clearButton = new JButton("ניקוי");

    public PollCreationPanel(ChatGptClient chatGpt, Consumer<Poll> onPollReady) {
        this.chatGpt = chatGpt;
        this.onPollReady = onPollReady;
        setLayout(new BorderLayout(0, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // עיצוב כפתורים ראשיים
        styleButton(submitButton, PRIMARY_BTN, Color.WHITE);
        styleButton(clearButton, SECONDARY_BTN, Color.BLACK);
        styleButton(addQuestionButton, ACTION_BTN, new Color(0, 100, 200));

        // --- עליון: בחירת דרך יצירה + אזור ChatGPT ---
        ButtonGroup group = new ButtonGroup();
        group.add(manualMode);
        group.add(aiMode);
        JLabel modeLabel = new JLabel("דרך היצירה:");
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 15f));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
        modeRow.add(modeLabel);
        modeRow.add(manualMode);
        modeRow.add(aiMode);

        buildAiPanel();
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(modeRow, BorderLayout.NORTH);
        top.add(aiPanel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // --- מרכז: הנחיה או עורך שאלות ---
        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        questionsContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel holder = new JPanel(new BorderLayout());
        holder.add(questionsContainer, BorderLayout.NORTH);

        JScrollPane editorScroll = new JScrollPane(holder);
        editorScroll.getVerticalScrollBar().setUnitIncrement(16);
        editorScroll.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));

        JLabel hint = new JLabel("הזינו נושא ולחצו \"יצירת שאלות\". השאלות יופיעו כאן, ואפשר יהיה לערוך אותן לפני האישור.",
                SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(14f));
        hint.setForeground(MUTED);
        JPanel hintCard = new JPanel(new GridBagLayout());
        hintCard.add(hint);

        center.add(hintCard, "hint");
        center.add(editorScroll, "editor");
        add(center, BorderLayout.CENTER);

        // --- תחתית: הוספת שאלה, משוב, כפתורי פעולה ---
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        addRow.add(addQuestionButton);

        feedback.setFont(feedback.getFont().deriveFont(Font.BOLD, 14f));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        actions.add(submitButton);
        actions.add(clearButton);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.add(addRow, BorderLayout.NORTH);
        bottom.add(feedback, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        // --- חיווט ---
        manualMode.addActionListener(e -> updateMode());
        aiMode.addActionListener(e -> updateMode());
        addQuestionButton.addActionListener(e -> addQuestion(true));
        submitButton.addActionListener(e -> submit());
        clearButton.addActionListener(e -> clearAll());

        addQuestion(false);
        updateMode();
        applyComponentOrientation(RTL);

        // הפתרון המוחלט לבאג התצוגה של סווינג (Lazy Rendering):
        // מאזין שמוודא שברגע שהפאנל מוצג *בפועל* על המסך (ולא רק נטען ברקע) - הוא יאלץ ציור מחדש
        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> {
                    questionsContainer.revalidate();
                    questionsContainer.repaint();
                    // מעבר על כל השאלות ואילוץ רינדור פנימי של שדות התשובות שלהן
                    for (java.awt.Component comp : questionsContainer.getComponents()) {
                        comp.revalidate();
                        comp.repaint();
                    }
                });
            }
        });
    }

    /** פונקציית עזר לעיצוב כפתורים מודרני ושטוח */
    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 14f));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    private void styleTextField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
    }

    private void buildAiPanel() {
        JLabel topicLabel = new JLabel("נושא הסקר:");
        topicLabel.setPreferredSize(LABEL_SIZE);
        topicLabel.setFont(topicLabel.getFont().deriveFont(Font.BOLD));

        styleTextField(topicField);
        topicField.setToolTipText("למשל: העדפות טכנולוגיות בקרב מהנדסי תוכנה");
        topicField.addActionListener(e -> generate());

        JSpinner.DefaultEditor spinnerEditor = (JSpinner.DefaultEditor) countSpinner.getEditor();
        spinnerEditor.getTextField().setColumns(2);
        countSpinner.setToolTipText("כמה שאלות ליצור (1 עד 3)");

        styleButton(generateButton, AI_BTN, Color.WHITE);
        generateButton.addActionListener(e -> generate());

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        options.add(new JLabel("מספר שאלות:"));
        options.add(countSpinner);
        options.add(generateButton);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.add(topicLabel, BorderLayout.LINE_START);
        row.add(topicField, BorderLayout.CENTER);
        row.add(options, BorderLayout.LINE_END);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        JPanel status = new JPanel(new BorderLayout(0, 5));
        status.add(progress, BorderLayout.NORTH);
        status.add(aiStatus, BorderLayout.CENTER);

        aiPanel.add(row, BorderLayout.NORTH);
        aiPanel.add(status, BorderLayout.CENTER);
    }

    private void updateMode() {
        boolean ai = aiMode.isSelected();
        boolean awaitingAi = ai && !aiResultShown;
        aiPanel.setVisible(ai);
        cards.show(center, awaitingAi ? "hint" : "editor");
        addQuestionButton.setVisible(!awaitingAi);
        submitButton.setEnabled(!awaitingAi);
        clearButton.setEnabled(!awaitingAi);
        showFeedback(" ", MUTED);
    }

    private void setGenerating(boolean generating) {
        generateButton.setEnabled(!generating);
        topicField.setEnabled(!generating);
        countSpinner.setEnabled(!generating);
        manualMode.setEnabled(!generating);
        aiMode.setEnabled(!generating);
        progress.setVisible(generating);
        if (generating) {
            showAiStatus("מייצר שאלות, זה עשוי להימשך כמה שניות...", MUTED);
        }
    }

    private void showFeedback(String text, Color color) {
        feedback.setText(text.isBlank() ? " " : text);
        feedback.setForeground(color);
    }

    private void showAiStatus(String text, Color color) {
        aiStatus.setText(text.isBlank() ? " " : text);
        aiStatus.setForeground(color);
    }

    private void generate() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            showAiStatus("⚠ יש להזין נושא לסקר.", ERROR_COLOR);
            topicField.requestFocusInWindow();
            return;
        }
        if (hasContent() && !confirm("יצירה חדשה תחליף את השאלות והתשובות שכבר הוזנו.\nלהמשיך?",
                "החלפת שאלות", "כן, להחליף")) {
            return;
        }

        int count = (Integer) countSpinner.getValue();
        setGenerating(true);
        new SwingWorker<List<Question>, Void>() {
            @Override
            protected List<Question> doInBackground() throws Exception {
                return chatGpt.generate(topic, count);
            }

            @Override
            protected void done() {
                setGenerating(false);
                try {
                    List<Question> result = get();
                    setQuestions(result);
                    aiResultShown = true;
                    updateMode();
                    showAiStatus("✓ נוצרו " + result.size() + " שאלות. אפשר לערוך אותן לפני האישור.", SUCCESS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String message = cause instanceof ChatGptException
                            ? cause.getMessage() : "אירעה שגיאה לא צפויה. נסו שוב.";
                    showAiStatus("⚠ " + message, ERROR_COLOR);
                }
            }
        }.execute();
    }

    private void submit() {
        List<Question> questions = collectQuestions();
        Optional<String> error = PollValidator.validate(questions);
        if (error.isPresent()) {
            showFeedback("⚠ " + error.get(), ERROR_COLOR);
            return;
        }
        showFeedback("✓ הסקר תקין ומוכן.", SUCCESS);
        onPollReady.accept(new Poll(questions));
    }

    private void clearAll() {
        if (hasContent() && !confirm("לנקות את כל השאלות והתשובות?", "ניקוי הסקר", "כן, לנקות")) {
            return;
        }
        setQuestions(List.of());
        aiResultShown = false;
        showAiStatus(" ", MUTED);
        updateMode();
    }

    private boolean confirm(String message, String title, String yesLabel) {
        Object[] choices = {yesLabel, "ביטול"};
        int choice = JOptionPane.showOptionDialog(this, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, choices, choices[1]);
        return choice == 0;
    }

    void setQuestions(List<Question> questions) {
        editors.clear();
        questionsContainer.removeAll();
        for (Question question : questions) {
            addQuestion(false).fill(question);
        }
        if (editors.isEmpty()) {
            addQuestion(false);
        }
        renumber();
    }

    List<Question> collectQuestions() {
        List<Question> questions = new ArrayList<>();
        for (QuestionEditor editor : editors) {
            questions.add(editor.toQuestion());
        }
        return questions;
    }

    private boolean hasContent() {
        for (QuestionEditor editor : editors) {
            if (editor.hasContent()) {
                return true;
            }
        }
        return false;
    }

    private QuestionEditor addQuestion(boolean focus) {
        if (editors.size() >= PollValidator.MAX_QUESTIONS) {
            return editors.get(editors.size() - 1);
        }
        QuestionEditor editor = new QuestionEditor();
        editor.applyComponentOrientation(RTL);
        editors.add(editor);
        questionsContainer.add(editor);
        renumber();
        if (focus) {
            editor.questionField.requestFocusInWindow();
        }
        return editor;
    }

    private void removeQuestion(QuestionEditor editor) {
        if (editors.size() <= PollValidator.MIN_QUESTIONS) {
            return;
        }
        editors.remove(editor);
        questionsContainer.remove(editor);
        renumber();
    }

    private void renumber() {
        for (int i = 0; i < editors.size(); i++) {
            editors.get(i).setIndex(i + 1, editors.size() > PollValidator.MIN_QUESTIONS);
        }
        boolean canAdd = editors.size() < PollValidator.MAX_QUESTIONS;
        addQuestionButton.setEnabled(canAdd);
        addQuestionButton.setToolTipText(canAdd ? null : "ניתן להוסיף עד " + PollValidator.MAX_QUESTIONS + " שאלות");
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    /** כרטיס של שאלה אחת מרווח ומעוצב */
    private class QuestionEditor extends JPanel {
        private final JLabel title = new JLabel();
        private final JButton removeButton = new JButton("מחיקת שאלה");
        private final JTextField questionField = new JTextField(20);
        private final JPanel optionsPanel = new JPanel();
        private final JButton addOptionButton = new JButton("+ הוספת תשובה");
        private final List<OptionRow> rows = new ArrayList<>();

        QuestionEditor() {
            super(new BorderLayout(0, 10));
            // מסגרת מעוגלת ועדינה לכל שאלה
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(10, 10, 10, 10),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                            BorderFactory.createEmptyBorder(15, 15, 15, 15))));

            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

            styleButton(removeButton, new Color(255, 235, 238), ERROR_COLOR);
            removeButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            removeButton.addActionListener(e -> removeQuestion(this));

            JPanel header = new JPanel(new BorderLayout());
            header.add(title, BorderLayout.LINE_START);
            header.add(removeButton, BorderLayout.LINE_END);

            JLabel questionLabel = new JLabel("נוסח השאלה:");
            questionLabel.setPreferredSize(LABEL_SIZE);
            questionLabel.setFont(questionLabel.getFont().deriveFont(Font.BOLD, 13f));

            styleTextField(questionField);
            questionField.addActionListener(e -> questionField.transferFocus());

            JPanel questionRow = new JPanel(new BorderLayout(10, 0));
            questionRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            questionRow.add(questionLabel, BorderLayout.LINE_START);
            questionRow.add(questionField, BorderLayout.CENTER);

            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            JPanel body = new JPanel(new BorderLayout());
            body.add(questionRow, BorderLayout.NORTH);
            body.add(optionsPanel, BorderLayout.CENTER);

            styleButton(addOptionButton, ACTION_BTN, new Color(0, 100, 200));
            addOptionButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            addOptionButton.addActionListener(e -> addOption(true));

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
            footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            footer.add(addOptionButton);

            add(header, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);

            for (int i = 0; i < PollValidator.MIN_OPTIONS; i++) {
                addOption(false);
            }
        }

        void setIndex(int number, boolean canRemove) {
            title.setText("שאלה " + number);
            removeButton.setVisible(canRemove);
        }

        void fill(Question question) {
            questionField.setText(question.text());
            rows.clear();
            optionsPanel.removeAll();
            int size = question.options().size();
            int rowCount = Math.max(PollValidator.MIN_OPTIONS, Math.min(PollValidator.MAX_OPTIONS, size));
            for (int i = 0; i < rowCount; i++) {
                addOption(false);
            }
            for (int i = 0; i < size && i < rowCount; i++) {
                rows.get(i).field.setText(question.options().get(i));
            }
        }

        Question toQuestion() {
            List<String> options = new ArrayList<>();
            for (OptionRow row : rows) {
                options.add(row.field.getText().trim());
            }
            return new Question(questionField.getText().trim(), options);
        }

        boolean hasContent() {
            if (!questionField.getText().isBlank()) {
                return true;
            }
            for (OptionRow row : rows) {
                if (!row.field.getText().isBlank()) {
                    return true;
                }
            }
            return false;
        }

        private void addOption(boolean focus) {
            if (rows.size() >= PollValidator.MAX_OPTIONS) {
                return;
            }
            OptionRow row = new OptionRow(this::removeOption);
            row.applyComponentOrientation(RTL);
            rows.add(row);
            optionsPanel.add(row);
            relayout();
            if (focus) {
                row.field.requestFocusInWindow();
            }
        }

        private void removeOption(OptionRow row) {
            if (rows.size() <= PollValidator.MIN_OPTIONS) {
                return;
            }
            rows.remove(row);
            optionsPanel.remove(row);
            relayout();
        }

        private void relayout() {
            boolean canRemove = rows.size() > PollValidator.MIN_OPTIONS;
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setNumber(i + 1);
                rows.get(i).removeButton.setEnabled(canRemove);
            }
            boolean canAdd = rows.size() < PollValidator.MAX_OPTIONS;
            addOptionButton.setEnabled(canAdd);
            addOptionButton.setToolTipText(canAdd ? null : "ניתן להוסיף עד " + PollValidator.MAX_OPTIONS + " תשובות");
            optionsPanel.revalidate();
            optionsPanel.repaint();
            questionsContainer.revalidate();
            questionsContainer.repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** שורת תשובה אחת: תווית ממוספרת, שדה טקסט וכפתור הסרה מעוצב */
    private class OptionRow extends JPanel {
        private final JLabel label = new JLabel();
        private final JTextField field = new JTextField(20);
        private final JButton removeButton = new JButton("✖"); // איקס יפה יותר

        OptionRow(Consumer<OptionRow> onRemove) {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

            label.setPreferredSize(LABEL_SIZE);
            label.setFont(label.getFont().deriveFont(13f));

            styleTextField(field);
            field.addActionListener(e -> field.transferFocus());

            styleButton(removeButton, Color.WHITE, ERROR_COLOR);
            removeButton.setToolTipText("הסרת תשובה");
            removeButton.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            removeButton.addActionListener(e -> onRemove.accept(this));

            add(label, BorderLayout.LINE_START);
            add(field, BorderLayout.CENTER);
            add(removeButton, BorderLayout.LINE_END);
        }

        void setNumber(int number) {
            label.setText("תשובה " + number + ":");
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}