package il.poll;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * שירות ניהול הקהילה - עובד על הזיכרון בלבד (מתאפס בכל ריצה).
 */
public class CommunityService {

    private final List<Member> members = new ArrayList<>();

    // רשימת מאזינים שמעדכנת את ממשק ה-Swing כשיש חבר חדש
    private final List<Consumer<Member>> joinListeners = new CopyOnWriteArrayList<>();

    public CommunityService() {
        // אין צורך בטעינה מקובץ - המערכת עולה נקייה
    }

    /** מאפשר ל-CommunityWindow להאזין להצטרפות חברים חדשים */
    public void addJoinListener(Consumer<Member> listener) {
        joinListeners.add(listener);
    }

    private void notifyJoinListeners(Member newMember) {
        for (Consumer<Member> listener : joinListeners) {
            listener.accept(newMember);
        }
    }

    public synchronized Optional<Member> join(long userId, String name, String username) {
        if (isMember(userId)) {
            return Optional.empty();
        }

        // יצירת החבר החדש עם זמן ההצטרפות הנוכחי (4 פרמטרים כפי שהוגדר)
        Member newMember = new Member(userId, name, username, LocalDateTime.now());
        members.add(newMember);

        // מודיע ל-Swing שיש חבר חדש כדי שיוסיף שורה לטבלה
        notifyJoinListeners(newMember);

        return Optional.of(newMember);
    }

    public synchronized List<Member> getMembers() {
        return new ArrayList<>(members);
    }

    public synchronized boolean isMember(long userId) {
        return members.stream().anyMatch(m -> m.userId() == userId);
    }

    public synchronized int size() {
        return members.size();
    }
}