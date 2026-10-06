package cloud.cholewa.data.error;

import cloud.cholewa.commons.error.model.ErrorId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

//The names are the codes of the error responses, and callers branch on them: amx-service relays a
//404 only with NOT_FOUND_DEVICE_CONFIGURATION. A rename compiles and passes every other test here,
//so the names are written out once more - a failure of this test is a breaking change of the API,
//to be made together with the callers, not a test to adjust.
class CustomErrorDescriptionTest {

    @Test
    void should_keep_the_codes_callers_rely_on() {
        assertThat(Arrays.stream(CustomErrorDescription.values()).map(ErrorId::codeOf))
            .containsExactlyInAnyOrder(
                "CONFIGURATION_EXIST",
                "NOT_FOUND_DEVICE_CONFIGURATION",
                "UNKNOWN_GATEWAY",
                "NOT_FOUND_HOUSEHOLD_MEMBER",
                "HOUSEHOLD_CONFLICT",
                "DEVICE_EXIST",
                "NOT_FOUND_MEMBER_DEVICE"
            );
    }
}
