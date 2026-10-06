package il.poll;

import java.util.List;

/**
 * שאלה בסקר.
 *
 * @param text    נוסח השאלה
 * @param options אפשרויות התשובה לפי סדר ההצגה
 */
public record Question(String text, List<String> options) {
}
