package cloud.cholewa.data.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

//Error messages are English whatever the machine: the test makes the JVM - and the request -
//Polish, the way a developer machine is, and still expects the English wording. The validator is
//the one Spring Boot configures, so this also proves the customizer wins over the locale-aware
//interpolator Spring installs.
class ValidationMessagesConfigTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl-PL");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

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
        contextRunner.withUserConfiguration(ValidationMessagesConfig.class).run(context ->
            assertThat(messages(context.getBean(Validator.class)))
                .containsExactlyInAnyOrder("must be less than or equal to 99", "must not be blank"));
    }

    //the reason the configuration exists: without it the same violations read in Polish here
    @Test
    void should_follow_the_locale_without_the_configuration() {
        contextRunner.run(context ->
            assertThat(messages(context.getBean(Validator.class)))
                .doesNotContain("must be less than or equal to 99", "must not be blank"));
    }

    private static Set<String> messages(final Validator validator) {
        return validator.validate(new Sample(100, " ")).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    private record Sample(@Max(99) int point, @NotBlank String name) {
    }
}
