package il.poll;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * בדיקת תקינות של שאלות סקר. משמש גם את טופס היצירה הידנית וגם את התשובה שמתקבלת מ-ChatGPT,
 * כך שאותם כללים חלים על שניהם. הודעות השגיאה כתובות למשתמש.
 */
public final class PollValidator {

    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 20;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;
    public static final int MAX_QUESTION_LENGTH = 200;
    public static final int MAX_OPTION_LENGTH = 60;

    private PollValidator() {
    }

    /** @return הודעת השגיאה הראשונה, או Optional ריק אם הכול תקין */
    public static Optional<String> validate(List<Question> questions) {
        if (questions == null || questions.size() < MIN_QUESTIONS) {
            return Optional.of("יש להוסיף לפחות שאלה אחת.");
        }
        if (questions.size() > MAX_QUESTIONS) {
            return Optional.of("ניתן ליצור עד " + MAX_QUESTIONS + " שאלות בסקר.");
        }

        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            String where = "שאלה " + (i + 1);

            String text = question.text();
            if (text == null || text.isBlank()) {
                return Optional.of(where + ": חסר נוסח השאלה.");
            }
            if (text.trim().length() > MAX_QUESTION_LENGTH) {
                return Optional.of(where + ": נוסח השאלה ארוך מדי (עד " + MAX_QUESTION_LENGTH + " תווים).");
            }

            List<String> options = question.options();
            if (options == null || options.size() < MIN_OPTIONS) {
                return Optional.of(where + ": יש להזין לפחות " + MIN_OPTIONS + " תשובות.");
            }
            if (options.size() > MAX_OPTIONS) {
                return Optional.of(where + ": ניתן להזין עד " + MAX_OPTIONS + " תשובות.");
            }

            Set<String> seen = new HashSet<>();
            for (int j = 0; j < options.size(); j++) {
                String option = options.get(j);
                if (option == null || option.isBlank()) {
                    return Optional.of(where + ": תשובה " + (j + 1) + " ריקה.");
                }
                if (option.trim().length() > MAX_OPTION_LENGTH) {
                    return Optional.of(where + ": תשובה " + (j + 1)
                            + " ארוכה מדי (עד " + MAX_OPTION_LENGTH + " תווים).");
                }
                if (!seen.add(option.trim().toLowerCase())) {
                    return Optional.of(where + ": יש שתי תשובות זהות (\"" + option.trim() + "\").");
                }
            }
        }
        return Optional.empty();
    }
}
