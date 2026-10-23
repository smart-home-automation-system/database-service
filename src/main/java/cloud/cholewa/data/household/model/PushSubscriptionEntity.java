package cloud.cholewa.data.household.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

//A Web Push subscription of a browser of a member (V19). The endpoint is held once in the table;
//what may not be null is said by the schema, nothing validates an entity here.
@Value
@Builder
@Table("push_subscriptions")
public class PushSubscriptionEntity {

    @Id
    Long id;
    Long memberId;
    String endpoint;
    String p256dh;
    String auth;
    String userAgent;
    LocalDateTime createdAt;
    //for the sender to come (HAS-276); nothing writes it yet
    LocalDateTime lastUsedAt;
}
