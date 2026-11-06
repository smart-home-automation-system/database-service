package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.InvalidMemberRequestException;

import java.util.regex.Pattern;

//What a household request must be (HAS-409) - no I/O, pinned by MemberRequestRulesTest. The body is not
//checked by @Valid: a refusal here carries the code INVALID_REQUEST and the reason, which the dashboard
//words, where Bean Validation would answer a bare 400. The bounds are those of MemberRequest of the SDK
//(1.17.0) and of the checks of V21: the text stripped of the spaces around it, 1 to 100 UTF-16 units (the
//count of @Size, and of a JavaScript string), one line - no line break, tab or other control character
final class MemberRequestRules {

    static final int TEXT_MAX = 100;
    private static final Pattern ONE_LINE = Pattern.compile("[^\\p{Cc}\\p{Zl}\\p{Zp}]+");

    private MemberRequestRules() {
    }

    //the text as it is stored: without the spaces around it
    static String text(final String raw) {
        if (raw == null) {
            throw new InvalidMemberRequestException("The text of a request is required");
        }
        final String text = raw.strip();
        //a no-break space is no whitespace to strip(), and a text of them would reach the check of V21
        if (text.codePoints().allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
            throw new InvalidMemberRequestException("The text of a request is empty");
        }
        if (text.length() > TEXT_MAX) {
            throw new InvalidMemberRequestException("The text of a request is at most " + TEXT_MAX + " characters, not "
                + text.length());
        }
        if (!ONE_LINE.matcher(text).matches()) {
            throw new InvalidMemberRequestException(
                "The text of a request is one line, without a line break, a tab or another control character");
        }
        return text;
    }

    //a name a client sends for the author or for who ticked a request off; whether it is a member is the
    //registry's to say
    static String name(final String raw, final String what) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidMemberRequestException("The " + what + " of a request is required");
        }
        return raw.strip();
    }
}
