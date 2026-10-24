package cloud.cholewa.data.household.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

//binds household.push.* (HAS-272) next to the properties it binds, not on a controller that reads them
@Configuration
@EnableConfigurationProperties(HouseholdPushProperties.class)
public class HouseholdPushConfig {
}
