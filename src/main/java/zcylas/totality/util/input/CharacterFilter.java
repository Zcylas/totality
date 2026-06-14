package zcylas.totality.util.input;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A whitelist/blacklist filter for individual characters, useful for constraining
 * text-field input to specific character sets (digits only, filenames, URLs, etc.).
 *
 * <p>Ported from Konkrete (keksuccino).</p>
 *
 * <p>Logic: if the allowed list is non-empty, only those characters pass.
 * Otherwise all characters pass except those in the forbidden list.</p>
 *
 * <p>Uses {@link LinkedHashSet} for O(1) {@code contains()} instead of O(n) — important
 * because {@code isAllowed()} is called on every keystroke for every character in input.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Only accept numeric input in a stat-value text field:
 * CharacterFilter filter = CharacterFilter.integers();
 * editBox.setFilter(s -> s.chars().allMatch(c -> filter.isAllowed((char) c)));
 * }</pre>
 */
public class CharacterFilter {

    private final Set<Character> allowed   = new LinkedHashSet<>();
    private final Set<Character> forbidden = new LinkedHashSet<>();

    // -----------------------------------------------------------------------
    // Core logic
    // -----------------------------------------------------------------------

    public boolean isAllowed(char c) {
        if (!allowed.isEmpty()) return allowed.contains(c);
        return !forbidden.contains(c);
    }

    public boolean isAllowed(@NotNull String charString) {
        if (charString.isEmpty()) return true;
        return isAllowed(charString.charAt(0));
    }

    /**
     * Returns a copy of {@code text} with all disallowed characters removed.
     */
    public String filter(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (isAllowed(c)) sb.append(c);
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Mutation
    // -----------------------------------------------------------------------

    public CharacterFilter allow(char... chars) {
        for (char c : chars) allowed.add(c);
        return this;
    }

    public CharacterFilter allow(@NotNull String... chars) {
        for (String s : chars) {
            if (!s.isEmpty()) allowed.add(s.charAt(0));
        }
        return this;
    }

    public CharacterFilter forbid(char... chars) {
        for (char c : chars) forbidden.add(c);
        return this;
    }

    public CharacterFilter forbid(@NotNull String... chars) {
        for (String s : chars) {
            if (!s.isEmpty()) forbidden.add(s.charAt(0));
        }
        return this;
    }

    // -----------------------------------------------------------------------
    // Preset factories
    // -----------------------------------------------------------------------

    /** Allows {@code 0-9}, {@code -}, {@code +}. */
    public static CharacterFilter integers() {
        return new CharacterFilter().allow('0','1','2','3','4','5','6','7','8','9','-','+');
    }

    /** Allows {@code 0-9}, {@code .}, {@code -}, {@code +}. */
    public static CharacterFilter doubles() {
        return new CharacterFilter().allow('0','1','2','3','4','5','6','7','8','9','.','-','+');
    }

    /** Allows lowercase alphanumerics, {@code .}, {@code _}, {@code -} (safe filenames). */
    public static CharacterFilter filename() {
        CharacterFilter f = new CharacterFilter();
        f.allow('a','b','c','d','e','f','g','h','i','j','k','l','m',
                'n','o','p','q','r','s','t','u','v','w','x','y','z');
        f.allow('0','1','2','3','4','5','6','7','8','9');
        f.allow('.','_','-');
        return f;
    }

    /** Like {@link #filename()} but also allows uppercase letters. */
    public static CharacterFilter filenameWithUppercase() {
        CharacterFilter f = filename();
        f.allow('A','B','C','D','E','F','G','H','I','J','K','L','M',
                'N','O','P','Q','R','S','T','U','V','W','X','Y','Z');
        return f;
    }

    /** Allows characters valid in a URL (alphanumeric + common punctuation). */
    public static CharacterFilter url() {
        CharacterFilter f = filenameWithUppercase();
        f.allow('~','+','#',',','%','&','=','*',';',':','@','?','/','\\');
        return f;
    }

    /**
     * Allows only printable ASCII characters (space through tilde, 0x20-0x7E).
     * Useful as a general text field that rejects control characters.
     */
    public static CharacterFilter printableAscii() {
        CharacterFilter f = new CharacterFilter();
        for (char c = 0x20; c <= 0x7E; c++) f.allow(c);
        return f;
    }
}