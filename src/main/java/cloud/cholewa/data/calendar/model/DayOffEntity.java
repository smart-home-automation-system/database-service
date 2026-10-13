package cloud.cholewa.data.calendar.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

//A day off the household added to the calendar. The day itself is the key - there is no id to
//look up, and a day is stored at most once (V15).
@Value
@Builder
@Table("days_off")
public class DayOffEntity {

    @Id
    LocalDate date;
    String reason;
    LocalDateTime createdAt;
}
