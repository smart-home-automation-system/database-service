package cloud.cholewa.data.household.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

//The public VAPID key of the household's Web Push (HAS-272), handed to the installed application so
//that it can subscribe its browser. Public by design - the private half stays with the sender,
//notification-service. Optional: until the manifest gives one, the application is told there is
//none and offers no push, and this service starts as before. A key that is set is checked at the
//start: a P-256 point of 65 bytes in base64url, as the browser wants it
@Validated
@ConfigurationProperties("household.push")
public record HouseholdPushProperties(
    @Pattern(regexp = "^[A-Za-z0-9_-]{87}=?$", message = "household.push.public-key is a base64url P-256 public key")
    String publicKey
) {
}
