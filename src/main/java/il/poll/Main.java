package il.poll;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * נקודת הכניסה.
 */
public class Main {

    public static void main(String[] args) {
        String token = "8695184420:AAEdk2mV94adf4rouTcWnbst6itlNgJH8DM";
        String botUsername = "HodayaAchvaBot";

        if (token == null || token.isBlank() || botUsername == null || botUsername.isBlank()) {
            fail("חסרים פרטי הבוט.\nיש להגדיר משתני סביבה BOT_TOKEN ו-BOT_USERNAME ולהפעיל מחדש.");
            return;
        }

        // 1. הגדרת שירות הקהילה (השורה שהייתה חסרה)
        CommunityService community = new CommunityService();

        // 2. יצירת הבוט והעברת הקהילה אליו
        CommunityBot bot = new CommunityBot(token, botUsername, community);

        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
        } catch (TelegramApiException e) {
            fail("ההתחברות לטלגרם נכשלה:\n" + e.getMessage()
                    + "\nבדקו את הטוקן ואת החיבור לאינטרנט.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // נשארים עם ה-Look and Feel ברירת המחדל
            }

            // 3. יצירת החלון והעברת הקהילה והבוט אליו כדי שיוכלו לדבר אחד עם השני
            new CommunityWindow(community, bot).setVisible(true);
        });
    }

    private static void fail(String message) {
        System.err.println(message);
        try {
            JOptionPane.showMessageDialog(null, message, "שגיאה", JOptionPane.ERROR_MESSAGE);
        } catch (java.awt.HeadlessException ignored) {
            // סביבה ללא מסך: ההודעה כבר הודפסה ל-stderr
        }
        System.exit(1);
    }
}