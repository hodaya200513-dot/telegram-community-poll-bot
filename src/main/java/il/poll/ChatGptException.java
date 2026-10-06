package il.poll;

/** כשל ביצירת שאלות מ-ChatGPT. ההודעה כתובה בעברית ומיועדת להצגה ישירה למשתמש. */
public class ChatGptException extends Exception {

    public ChatGptException(String userMessage) {
        super(userMessage);
    }
}
