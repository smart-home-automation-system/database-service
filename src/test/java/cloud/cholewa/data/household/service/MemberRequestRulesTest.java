package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.InvalidMemberRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//What a household request must be (HAS-409): the text stripped, 1 to 100, one line; a name given
class MemberRequestRulesTest {

    @Test
    void should_store_the_text_without_the_spaces_around_it() {
        assertThat(MemberRequestRules.text("  Kup chleb i mleko  ")).isEqualTo("Kup chleb i mleko");
        assertThat(MemberRequestRules.text("x")).isEqualTo("x");
    }

    @Test
    void should_take_a_text_of_one_hundred_and_refuse_one_more() {
        assertThat(MemberRequestRules.text("a".repeat(100))).hasSize(100);
        final String tooLong = "a".repeat(101);
        assertThatThrownBy(() -> MemberRequestRules.text(tooLong))
            .isInstanceOf(InvalidMemberRequestException.class)
            .hasMessage("The text of a request is at most 100 characters, not 101");
    }

    @Test
    void should_refuse_no_text_and_a_text_of_spaces() {
        assertThatThrownBy(() -> MemberRequestRules.text(null)).hasMessage("The text of a request is required");
        assertThatThrownBy(() -> MemberRequestRules.text("   ")).hasMessage("The text of a request is empty");
        assertThatThrownBy(() -> MemberRequestRules.text("\u00A0\u2007\u202F")).hasMessage("The text of a request is empty");
        assertThat(MemberRequestRules.text("\u00A0Kup")).isEqualTo("\u00A0Kup");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Kup\nchleb", "Kup\rchleb", "Kup\tchleb", "Kup\u0000chleb", "Kup\u0085chleb", "Kup chleb",
        "Kup chleb", "Kup\u007Fchleb"})
    void should_refuse_a_text_of_more_than_one_line(final String text) {
        assertThatThrownBy(() -> MemberRequestRules.text(text))
            .isInstanceOf(InvalidMemberRequestException.class)
            .hasMessage("The text of a request is one line, without a line break, a tab or another control character");
    }

    @Test
    void should_require_a_name_and_strip_it() {
        assertThat(MemberRequestRules.name(" Anna ", "author")).isEqualTo("Anna");
        assertThatThrownBy(() -> MemberRequestRules.name(null, "author")).hasMessage("The author of a request is required");
        assertThatThrownBy(() -> MemberRequestRules.name(" ", "author")).hasMessage("The author of a request is required");
    }
}
