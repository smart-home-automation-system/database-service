package cloud.cholewa.data.household.api;

import com.fasterxml.jackson.annotation.JsonInclude;

//What the installed application needs to subscribe its browser. A key that is not configured is left
//out - "missing is unknown" for the dashboard, which then offers no push. A record of this service,
//not an SDK model: one field read by one client
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushConfig(String publicKey) {
}
