package cloud.cholewa.data.config;

import jakarta.validation.MessageInterpolator;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

//Error messages are English, always. Bean Validation words a violated constraint in the locale of
//the JVM (or of the request), so the same request would be answered in Polish on a developer
//machine and in English in the cluster. The interpolator is replaced by one that ignores whatever
//locale it is handed; a constraint with its own message = "..." is not affected either way.
@Configuration
public class ValidationMessagesConfig {

    static final Locale MESSAGE_LOCALE = Locale.ENGLISH;

    //Applied after Spring has installed its own locale-aware interpolator, which is the one this
    //has to replace: LocalValidatorFactoryBean runs the customizers last.
    @Bean
    ValidationConfigurationCustomizer englishValidationMessages() {
        return configuration -> {
            final MessageInterpolator interpolator = configuration.getDefaultMessageInterpolator();

            configuration.messageInterpolator(new MessageInterpolator() {

                @Override
                public String interpolate(final String messageTemplate, final Context context) {
                    return interpolator.interpolate(messageTemplate, context, MESSAGE_LOCALE);
                }

                @Override
                public String interpolate(final String messageTemplate, final Context context, final Locale locale) {
                    return interpolator.interpolate(messageTemplate, context, MESSAGE_LOCALE);
                }
            });
        };
    }
}
