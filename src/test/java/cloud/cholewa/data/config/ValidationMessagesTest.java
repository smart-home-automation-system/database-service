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

//Error messages are English whatever the machine: the test makes the JVM - and the request -
//Polish, the way a developer machine is, and still expects the English wording. The service has no
//code for it any more: cholewa-commons pins the messages (ValidationMessagesAutoConfiguration,
//since 1.6.0), and the whole application context is started here to prove it does so in this
//service, as it is wired - a slice would not load the library's auto-configuration. Without it
//the same violations read in Polish on this JVM, so the test fails the day the library is
//downgraded or switched off with cholewa.validation.english-messages.
@SpringBootTest
@ActiveProfiles("test")
class ValidationMessagesTest {

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
    void should_word_a_violated_constraint_in_english_on_a_polish_jvm() {
        final Set<String> messages = validator.validate(new Sample(100, " ")).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());

        assertThat(messages).containsExactlyInAnyOrder("must be less than or equal to 99", "must not be blank");
    }

    private record Sample(@Max(99) int point, @NotBlank String name) {
    }
}
