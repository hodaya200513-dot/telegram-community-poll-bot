package il.poll;

import java.util.List;

/**
 * סקר: שאלה אחת עד שלוש, ולכל שאלה שתיים עד ארבע תשובות (נבדק ב-{@link PollValidator}).
 */
public record Poll(List<Question> questions) {

    /** תצוגה טקסטואלית של הסקר, לצורך הצגה למשתמש. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("שאלה ").append(i + 1).append(": ").append(question.text()).append('\n');
            for (String option : question.options()) {
                sb.append("   • ").append(option).append('\n');
            }
        }
        return sb.toString();
    }
}
