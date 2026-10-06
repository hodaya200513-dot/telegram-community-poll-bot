package il.poll;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * יצירת שאלות לסקר באמצעות ה-API של OpenAI (Chat Completions).
 *
 * המפתח נקרא ממשתנה הסביבה OPENAI_API_KEY. לבדיקה מקומית אפשר גם לכתוב אותו ישר בשורה של
 * API_KEY למטה, אבל חשוב למחוק אותו לפני הגשה או העלאה ל-Git.
 * שם המודל נקרא מ-OPENAI_MODEL, ואם לא הוגדר משתמשים בברירת המחדל DEFAULT_MODEL.
 */
public class ChatGptClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "API_KEY";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * מבקש מ-ChatGPT שאלות ותשובות לנושא נתון. נקרא מ-thread רקע (לא מה-EDT), כי הוא חוסם.
     *
     * @param topic         נושא כללי לסקר
     * @param questionCount כמה שאלות ליצור (1 עד 3)
     */
    public List<Question> generate(String topic, int questionCount) throws ChatGptException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new ChatGptException("לא הוגדר מפתח OpenAI. יש להגדיר את OPENAI_API_KEY "
                    + "(או לכתוב אותו ב-ChatGptClient) ולהפעיל מחדש.");
        }
        int count = Math.max(PollValidator.MIN_QUESTIONS, Math.min(PollValidator.MAX_QUESTIONS, questionCount));

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(topic, count), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ChatGptException("ChatGPT לא הגיב בזמן. נסו שוב.");
        } catch (IOException e) {
            throw new ChatGptException("אין חיבור ל-ChatGPT. בדקו את החיבור לאינטרנט ונסו שוב.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatGptException("הפעולה הופסקה.");
        }

        switch (response.statusCode()) {
            case 200 -> {
                return parseQuestions(extractContent(response.body()), count);
            }
            case 401 -> throw new ChatGptException("מפתח ה-API של OpenAI אינו תקין.");
            case 429 -> throw new ChatGptException(
                    "חרגתם ממכסת השימוש או ששלחתם יותר מדי בקשות. נסו שוב בעוד רגע.");
            default -> throw new ChatGptException("ChatGPT החזיר שגיאה (קוד " + response.statusCode() + ").");
        }
    }

    private static String buildRequestBody(String topic, int count) {
        String model = System.getenv().getOrDefault("OPENAI_MODEL", DEFAULT_MODEL);

        String system = "אתה עוזר ליצירת סקרים לקהילה. החזר אך ורק אובייקט JSON תקין, בלי טקסט נוסף, "
                + "במבנה הבא: {\"questions\":[{\"text\":\"נוסח השאלה\",\"options\":[\"תשובה\",\"תשובה\"]}]}. "
                + "כתוב בעברית. צור בדיוק " + count + " שאלות. "
                + "לכל שאלה 2 עד 4 אפשרויות תשובה קצרות (עד " + PollValidator.MAX_OPTION_LENGTH + " תווים), "
                + "שונות זו מזו, בלי מספור. נוסח כל שאלה ברור, נייטרלי ועד "
                + PollValidator.MAX_QUESTION_LENGTH + " תווים.";

        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", "נושא הסקר: " + topic));

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_object");

        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("response_format", format);
        root.add("messages", messages);
        return root.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    /** שולף את טקסט התשובה מתוך מעטפת התשובה של ה-API. */
    static String extractContent(String responseBody) throws ChatGptException {
        try {
            return JsonParser.parseString(responseBody).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            throw new ChatGptException("התשובה מ-ChatGPT הגיעה בפורמט לא צפוי. נסו שוב.");
        }
    }

    /**
     * ממיר את ה-JSON שהמודל החזיר לשאלות, חותך עודפים (יותר מדי שאלות או תשובות)
     * ומריץ את אותה ולידציה כמו בטופס הידני.
     */
    static List<Question> parseQuestions(String content, int maxQuestions) throws ChatGptException {
        List<Question> result = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(stripCodeFence(content))
                    .getAsJsonObject().getAsJsonArray("questions");
            for (JsonElement element : array) {
                if (result.size() == maxQuestions) {
                    break;
                }
                JsonObject object = element.getAsJsonObject();
                List<String> options = new ArrayList<>();
                for (JsonElement option : object.getAsJsonArray("options")) {
                    if (options.size() == PollValidator.MAX_OPTIONS) {
                        break;
                    }
                    options.add(option.getAsString().trim());
                }
                result.add(new Question(object.get("text").getAsString().trim(), options));
            }
        } catch (RuntimeException e) {
            throw new ChatGptException("התשובה מ-ChatGPT הגיעה בפורמט לא צפוי. נסו שוב.");
        }

        Optional<String> error = PollValidator.validate(result);
        if (error.isPresent()) {
            throw new ChatGptException("ChatGPT החזיר שאלות לא תקינות (" + error.get() + ") נסו שוב.");
        }
        return result;
    }

    /** מסיר גדר קוד (```json ... ```) אם המודל עטף בה את התשובה. */
    private static String stripCodeFence(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            text = newline >= 0 ? text.substring(newline + 1) : text;
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }
}
