package il.poll;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * בוט הטלגרם. מטפל בהצטרפות לקהילה, שליחת סקרים פעילים, וקבלת תשובות דרך כפתורים.
 */
public class CommunityBot extends TelegramLongPollingBot {

    private final CommunityService community;
    private final String username;

    // שומר את הסקר שרץ כרגע
    private ActivePoll currentActivePoll;

    public CommunityBot(String token, String username, CommunityService community) {
        super(token);
        this.username = username;
        this.community = community;


    }



    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(org.telegram.telegrambots.meta.api.objects.Update update) {
        // 1. קודם כל בודקים אם מדובר בלחיצה על כפתור של סקר
        if (update.hasCallbackQuery()) {
            handlePollAnswer(update.getCallbackQuery());
            return;
        }

        // 2. אם זו הודעת טקסט רגילה (הצטרפות וכו')
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        org.telegram.telegrambots.meta.api.objects.Message message = update.getMessage();
        org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();
        if (from == null || from.getIsBot()) {
            return;
        }

        long chatId = message.getChatId();
        String text = message.getText().trim();

        // --- התוספת החדשה: חסימת הודעות טקסט אם המשתמש באמצע סקר פעיל ---
        if (hasActivePoll()) {
            int userIndex = currentActivePoll.getCurrentQuestionIndex(chatId);

            // בודקים אם המשתמש משתתף בסקר (שונה מ-1-) וטרם סיים לענות (קטן מכמות השאלות)
            if (userIndex != -1 && userIndex < currentActivePoll.getPoll().questions().size()) {
                try {
                    execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("יש סקר פעיל כרגע בקהילה! 📊\nאנא השב עליו באמצעות הכפתורים בהודעת הסקר.")
                            .build());
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    System.err.println("שגיאה בשליחת תזכורת: " + e.getMessage());
                }
                return; // עוצרים כאן כדי לא לעבד את ההודעה הרגילה
            }
        }
        // ---------------------------------------------------------------

        // 3. המשך טיפול רגיל בהודעה
        if (isJoinRequest(text)) {
            handleJoin(chatId, from);
        } else {
            handleOther(chatId, from);
        }
    }

    /** פונקציה חיצונית שנקראת מה-Swing כשמתחילים סקר חדש */
    public void startPoll(ActivePoll activePoll) {
        this.currentActivePoll = activePoll;

        activePoll.setOnReminderTrigger(usersToRemind -> {
            for (Member m : usersToRemind) {
                sendReminderMessage(m.userId());
            }
        });

        // שינוי: במקום לשלוח את שאלה 0, שולחים הזמנה להשתתף
        for (Member participant : activePoll.getParticipants()) {
            sendPollInvitation(participant.userId());
        }
    }

    /** שולח הודעת הזמנה לסקר עם כפתור התחלה */
    private void sendPollInvitation(long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("📊 סקר חדש הופעל בקהילה!\nהאם תרצה להתחיל לענות עליו כעת?")
                .build();

        // יצירת כפתור "התחל סקר" שהמידע שלו (CallbackData) הוא המילה "START_POLL"
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        List<InlineKeyboardButton> row = new java.util.ArrayList<>();

        InlineKeyboardButton startBtn = new InlineKeyboardButton();
        startBtn.setText("התחל סקר ▶️");
        startBtn.setCallbackData("START_POLL");
        row.add(startBtn);

        rows.add(row);
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("שגיאה בשליחת הזמנה לסקר: " + e.getMessage());
        }
    }

    /** מטפל בלחיצה על תשובה בסקר */
    private void handlePollAnswer(CallbackQuery callback) {
        long chatId = callback.getMessage().getChatId();
        String data = callback.getData();
        int messageId = callback.getMessage().getMessageId();

        // 1. בדיקה אם הסקר עדיין פתוח ואם המשתמש משתתף בו
        if (currentActivePoll == null || currentActivePoll.isClosed() || currentActivePoll.getCurrentQuestionIndex(chatId) == -1) {
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(messageId)
                        .text("הסקר נסגר ולא ניתן לקבל תשובות נוספות ❌")
                        .build());
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ignored) {}
            return;
        }

        // 2. אם המשתמש לחץ על "התחל סקר" - מחליפים את ההזמנה בשאלה הראשונה
        if ("START_POLL".equals(data)) {
            sendQuestion(chatId, 0, messageId);
            return;
        }

        // 3. אם מדובר בלחיצה על תשובה - מפענחים את מספר התשובה שנבחרה
        int chosenOption;
        try {
            chosenOption = Integer.parseInt(data);
        } catch (NumberFormatException e) {
            return; // נתונים לא תקינים (למשל לחיצה ישנה), מתעלמים
        }

        // 4. שומרים את התשובה בזיכרון (מה שמעדכן את ממשק ה-Swing בזמן אמת!)
        int currentQuestionIndex = currentActivePoll.getCurrentQuestionIndex(chatId);
        currentActivePoll.saveAnswerAndAdvance(chatId, currentQuestionIndex, chosenOption);

        // 5. בודקים מה מצבו של המשתמש עכשיו (האם נשארו שאלות)
        int nextQuestionIndex = currentActivePoll.getCurrentQuestionIndex(chatId);
        int totalQuestions = currentActivePoll.getPoll().questions().size();

        if (nextQuestionIndex < totalQuestions) {
            // יש עוד שאלות - עורכים את בועת ההודעה לשאלה הבאה
            sendQuestion(chatId, nextQuestionIndex, messageId);
        } else {
            // המשתמש סיים את כל השאלות בסקר - מחליפים את הבועה להודעת סיום אישית
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(messageId)
                        .text("תודה רבה! ענית על כל השאלות בסקר בהצלחה. 🎉")
                        .build());
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                System.err.println("שגיאה בשליחת הודעת סיום: " + e.getMessage());
            }
        }
    }
    /**
     * שולח שאלה על ידי עריכת הודעה קיימת (מחליף את ההזמנה או את השאלה הקודמת)
     */
    private void sendQuestion(long chatId, int questionIndex, int messageId) {
        if (currentActivePoll == null || currentActivePoll.isClosed()) return;

        Question q = currentActivePoll.getPoll().questions().get(questionIndex);
        int totalQuestions = currentActivePoll.getPoll().questions().size();

        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup =
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();

        for (int i = 0; i < q.options().size(); i++) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn =
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            btn.setText(q.options().get(i));
            btn.setCallbackData(String.valueOf(i));
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);

        org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editMessage =
                org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(messageId)
                        .text("שאלה " + (questionIndex + 1) + " מתוך " + totalQuestions + ":\n\n" + q.text())
                        .replyMarkup(markup)
                        .build();

        try {
            execute(editMessage);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            System.err.println("שגיאה בעריכת השאלה: " + e.getMessage());
        }
    }
    /** שולח הודעת תזכורת למשתמש שטרם סיים את הסקר */
    private void sendReminderMessage(long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("⏰ תזכורת: יש לך סקר פעיל שממתין לתשובתך! אנא השלם את השאלות שנותרו.")
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("שליחת תזכורת ל-" + chatId + " נכשלה: " + e.getMessage());
        }
    }

    /** שולח שאלה כהודעה חדשה (משמש בתחילת הסקר) */
    private void sendQuestion(long chatId, int questionIndex) {
        Question q = currentActivePoll.getPoll().questions().get(questionIndex);

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("שאלה " + (questionIndex + 1) + " מתוך " + currentActivePoll.getPoll().questions().size() + ":\n*" + q.text() + "*")
                .parseMode("Markdown")
                .replyMarkup(buildKeyboardForQuestion(q, questionIndex))
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("שליחה ל-" + chatId + " נכשלה: " + e.getMessage());
        }
    }
    /**
     * שולח את השאלה על ידי עריכת הודעה קיימת (מחליף את כפתור "התחל סקר" בשאלה)
     */


    /** עורך הודעה קיימת ומציג בה את השאלה הבאה (מונע הצפת הצ'אט) */
    private void updateMessageToQuestion(long chatId, int messageId, int questionIndex) {
        Question q = currentActivePoll.getPoll().questions().get(questionIndex);

        EditMessageText edit = EditMessageText.builder()
                .chatId(String.valueOf(chatId))
                .messageId(messageId)
                .text("שאלה " + (questionIndex + 1) + " מתוך " + currentActivePoll.getPoll().questions().size() + ":\n*" + q.text() + "*")
                .parseMode("Markdown")
                .replyMarkup(buildKeyboardForQuestion(q, questionIndex))
                .build();

        try { execute(edit); } catch (TelegramApiException ignored) {}
    }

    /** מעדכן את ההודעה להודעת סיום לאחר שהמשתמש ענה על כל השאלות */
    private void finishPollForUser(long chatId, int messageId) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(String.valueOf(chatId))
                .messageId(messageId)
                .text("תודה רבה! סיימת את הסקר. ✅")
                .build();

        try { execute(edit); } catch (TelegramApiException ignored) {}
    }

    /** בונה את כפתורי התשובות (Inline Keyboard) עבור שאלה ספציפית */
    private InlineKeyboardMarkup buildKeyboardForQuestion(Question q, int questionIndex) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(q.options().get(i))
                    .callbackData("ans_" + questionIndex + "_" + i)
                    .build();
            rows.add(List.of(btn)); // כל תשובה בשורה חדשה
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    static boolean isJoinRequest(String text) {
        String firstWord = text.split("\\s+")[0];
        boolean isStart = firstWord.equals("/start") || firstWord.startsWith("/start@");
        return isStart || text.equals("היי") || text.equals("Hi");
    }

    private void handleJoin(long chatId, User from) {
        String name = displayName(from);
        Optional<Member> joined = community.join(from.getId(), name, from.getUserName());

        if (joined.isEmpty()) {
            send(chatId, "אתה כבר חבר בקהילה ✅\nכשיישלח סקר חדש, תקבל אותו כאן.");
            return;
        }

        int size = community.size();
        send(chatId, "ברוך הבא לקהילה, " + name + "! 🎉\n"
                + "הקהילה מונה עכשיו " + size + " חברים.\n"
                + "כשיישלח סקר חדש, תקבל אותו כאן.");

        String announcement = "👋 " + name + " הצטרף/ה לקהילה!\n"
                + "הקהילה מונה עכשיו " + size + " חברים.";
        for (Member other : community.getMembers()) {
            if (other.userId() != from.getId()) {
                send(other.userId(), announcement);
            }
        }
    }

    private void handleOther(long chatId, User from) {
        if (community.isMember(from.getId())) {
            send(chatId, "אין כרגע סקר פעיל 📭\nכשיישלח סקר חדש, תקבל אותו כאן.");
        } else {
            send(chatId, "כדי להצטרף לקהילה לחצו על Start או שלחו \"היי\" / \"Hi\" 👋");
        }
    }

    private static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return user.getUserName() != null ? user.getUserName() : "משתמש";
    }

    /**
     * סעיף 12: בדיקה האם קיים כרגע סקר פעיל במערכת.
     */
    public boolean hasActivePoll() {
        return currentActivePoll != null && !currentActivePoll.isClosed();
    }

    private void send(long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            System.err.println("שליחה ל-" + chatId + " נכשלה: " + e.getMessage());
        }
    }
}