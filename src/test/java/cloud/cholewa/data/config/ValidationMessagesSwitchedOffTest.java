package cloud.cholewa.data.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

//The control of ValidationMessagesTest: the same violations on the same Polish JVM, with the
//library's pinning switched off, do not read in English. Without this nothing would show that the
//other test can fail - should the Polish path ever stop producing Polish, it would stay green
//with the library gone.
@SpringBootTest(properties = "cholewa.validation.english-messages=false")
@ActiveProfiles("test")
class ValidationMessagesSwitchedOffTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl-PL");

    @Autowired
    private Validator validator;

    private Locale original;

    @BeforeEach
    void makeTheJvmPolish() {
        original = Locale.getDefault();
        Locale.setDefault(POLISH);
        LocaleContextHolder.setLocale(POLISH);
    }

    @AfterEach
    void restoreTheLocale() {
        Locale.setDefault(original);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void should_follow_the_locale_when_the_library_does_not_pin_the_messages() {
        final Set<String> messages = validator.validate(new Sample(100, " ")).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());

        assertThat(messages)
            .hasSize(2)
            .doesNotContain("must be less than or equal to 99", "must not be blank");
    }

    private record Sample(@Max(99) int point, @NotBlank String name) {
    }
}
