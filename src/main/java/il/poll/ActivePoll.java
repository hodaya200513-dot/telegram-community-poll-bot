package il.poll;

import javax.swing.Timer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * מנהל את מצב הסקר הפעיל, התשובות, מגבלת הזמן (5 דקות) וסגירת הסקר.
 */
public class ActivePoll {
    private final Poll poll;
    private final List<Member> participants;

    private final Map<Long, Integer> currentQuestionIndex = new HashMap<>();
    private final Map<Long, Map<Integer, Integer>> userAnswers = new HashMap<>();
    private final Set<Long> remindedUsers = new HashSet<>();

    // ניהול זמן וסגירה (סעיף 9)
    private static final int SURVEY_DURATION_SECONDS = 5 * 60; // 5 דקות
    private int secondsRemaining = SURVEY_DURATION_SECONDS;
    private boolean isClosed = false;
    private final Timer surveyTimer;
    private Consumer<List<Member>> onReminderTrigger;

    private final List<Runnable> progressListeners = new CopyOnWriteArrayList<>();

    public ActivePoll(Poll poll, List<Member> currentCommunityMembers) {
        this.poll = poll;
        this.participants = List.copyOf(currentCommunityMembers);

        for (Member m : participants) {
            currentQuestionIndex.put(m.userId(), 0);
            userAnswers.put(m.userId(), new HashMap<>());
        }

        // הפעלת טיימר רץ שמעדכן את הזמן כל שנייה
        surveyTimer = new Timer(1000, e -> {
            if (secondsRemaining > 0 && !isClosed) {
                secondsRemaining--;
                if (secondsRemaining == 120 && !isClosed) {
                    triggerReminders();
                }
                notifyListeners();
                if (secondsRemaining <= 0) {
                    closePoll();
                }
            }
        });
        surveyTimer.start();
    }

    public void setOnReminderTrigger(Consumer<List<Member>> listener) {
        this.onReminderTrigger = listener;
    }

    public Poll getPoll() { return poll; }
    public List<Member> getParticipants() { return participants; }
    public int getSecondsRemaining() { return secondsRemaining; }
    public boolean isClosed() { return isClosed; }

    public void addProgressListener(Runnable listener) {
        progressListeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : progressListeners) {
            listener.run();
        }
    }

    public int getCurrentQuestionIndex(long userId) {
        return currentQuestionIndex.getOrDefault(userId, -1);
    }

    public boolean hasAnsweredQuestion(long userId, int questionIndex) {
        Map<Integer, Integer> answers = userAnswers.get(userId);
        return answers != null && answers.containsKey(questionIndex);
    }

    /**
     * שומר תשובה ומקדם את המשתמש, כל עוד הסקר פתוח.
     */
    public void saveAnswerAndAdvance(long userId, int questionIndex, int chosenOption) {
        if (isClosed || !currentQuestionIndex.containsKey(userId)) {
            return; // הסקר נסגר או שהמשתמש אינו משתתף
        }

        if (hasAnsweredQuestion(userId, questionIndex)) {
            return; // מניעת מענה כפול לאותה שאלה
        }

        userAnswers.get(userId).put(questionIndex, chosenOption);
        currentQuestionIndex.put(userId, questionIndex + 1);

        // בדיקה האם כל המשתתפים השלימו את כל השאלות בסקר
        checkAllFinishedAndCloseIfNeeded();

        notifyListeners();
    }

    private void triggerReminders() {
        if (isClosed || onReminderTrigger == null) {
            return;
        }

        int totalQuestions = poll.questions().size();
        List<Member> usersNeedingReminder = new java.util.ArrayList<>();

        for (Member m : participants) {
            // מי שטרם השים את כל השאלות וטרם קיבל תזכורת
            if (getAnsweredCount(m.userId()) < totalQuestions && !remindedUsers.contains(m.userId())) {
                remindedUsers.add(m.userId());
                usersNeedingReminder.add(m);
            }
        }

        if (!usersNeedingReminder.isEmpty()) {
            onReminderTrigger.accept(usersNeedingReminder);
        }
    }

    private void checkAllFinishedAndCloseIfNeeded() {
        if (isClosed) return;

        int totalQuestions = poll.questions().size();
        for (Member m : participants) {
            int answered = getAnsweredCount(m.userId());
            // אם לפחות משתמש אחד טרם סיים את כל השאלות, הסקר נשאר פתוח
            if (answered < totalQuestions) {
                return;
            }
        }
        // אם הגענו לכאן - כולם השלימו את כל השאלות! סגור את הסקר מיד.
        closePoll();
    }

    public void closePoll() {
        if (!isClosed) {
            isClosed = true;
            if (surveyTimer != null) {
                surveyTimer.stop();
            }
            notifyListeners();
        }
    }

    public int getAnsweredCount(long userId) {
        Map<Integer, Integer> answers = userAnswers.get(userId);
        return answers == null ? 0 : answers.size();
    }

    public String getUserStatus(long userId) {
        int answered = getAnsweredCount(userId);
        int total = poll.questions().size();

        if (answered == 0) {
            return "טרם ענה";
        } else if (answered >= total) {
            return "השלים";
        } else {
            return "בתהליך";
        }
    }

    /**
     * מחשבת את מספר ההצבעות לכל אפשרות תשובה עבור שאלה ספציפית (לפי האינדקס שלה).
     */
    public Map<Integer, Integer> getVoteCountsForQuestion(int questionIndex) {
        Map<Integer, Integer> counts = new HashMap<>();

        // אתחול כל האפשרויות ל-0 קולות
        int numOptions = poll.questions().get(questionIndex).options().size();
        for (int i = 0; i < numOptions; i++) {
            counts.put(i, 0);
        }

        // ספירת הקולות של המשתמשים שענו
        for (Map<Integer, Integer> answers : userAnswers.values()) {
            if (answers.containsKey(questionIndex)) {
                int chosenOption = answers.get(questionIndex);
                counts.put(chosenOption, counts.get(chosenOption) + 1);
            }
        }
        return counts;
    }
}