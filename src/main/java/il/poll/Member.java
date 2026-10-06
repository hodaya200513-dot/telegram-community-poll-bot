package il.poll;

import java.time.LocalDateTime;

/**
 * חבר בקהילה הגלובלית.
 *
 * @param userId   מזהה המשתמש בטלגרם (גם מזהה הצ'אט הפרטי איתו)
 * @param name     שם תצוגה (שם פרטי + משפחה)
 * @param username שם המשתמש בטלגרם ללא @, או null אם אין
 * @param joinedAt מועד ההצטרפות
 */
public record Member(long userId, String name, String username, LocalDateTime joinedAt) {

    /** שם המשתמש בפורמט להצגה, או מקף אם לא קיים. */
    public String usernameLabel() {
        return (username == null || username.isBlank()) ? "—" : "@" + username;
    }
}
