package cloud.cholewa.data.household.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

//A household request (V21; the marks of the reminders and the announcements since V23, HAS-415): an errand for
//the member of memberId. author and doneBy are names as the
//registry spelled them when written; what may not be null is said by the schema, nothing validates an
//entity here
@Value
@Builder
@With
@Table("member_requests")
public class MemberRequestEntity {

    @Id
    Long id;
    Long memberId;
    String text;
    String author;
    LocalDateTime createdAt;
    LocalDateTime sentAt;
    LocalDateTime doneAt;
    String doneBy;
    LocalDateTime remindedAt;
    LocalDateTime doneAnnouncedAt;
    LocalDateTime staleAnnouncedAt;
}
