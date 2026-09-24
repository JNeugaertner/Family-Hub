package de.familyhub.calendar;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {

    List<CalendarEvent> findByMemberIdOrderByStartAsc(String memberId);

    long countByMemberId(String memberId);

    // Alle Termine, die den Zeitraum [from, to) berühren, auch wenn sie davor beginnen oder danach enden.
    @Query(value = "{ 'start': { $lt: ?1 }, 'end': { $gt: ?0 } }", sort = "{ 'start': 1 }")
    List<CalendarEvent> findOverlapping(LocalDateTime from, LocalDateTime to);

    @Query(value = "{ 'memberId': ?0, 'start': { $lt: ?2 }, 'end': { $gt: ?1 } }", sort = "{ 'start': 1 }")
    List<CalendarEvent> findOverlappingForMember(String memberId, LocalDateTime from, LocalDateTime to);
}
