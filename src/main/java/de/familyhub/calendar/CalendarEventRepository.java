package de.familyhub.calendar;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

// memberIds ist eine Liste; { 'memberIds': x } findet alle Termine, an denen x beteiligt ist.
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {

    @Query(value = "{ 'memberIds': ?0 }", sort = "{ 'start': 1 }")
    List<CalendarEvent> findByMemberOrderByStartAsc(String memberId);

    // Eigene Termine ohne importierte (z. B. aus Google)
    @Query("{ 'memberIds': ?0, 'external': null }")
    List<CalendarEvent> findOwnByMember(String memberId);

    @Query("{ 'memberIds': ?0, 'external': { $ne: null } }")
    List<CalendarEvent> findImportedByMember(String memberId);

    @Query(value = "{ 'memberIds': ?0, 'external': { $ne: null } }", delete = true)
    void deleteImportedByMember(String memberId);

    // Alle Termine, die den Zeitraum [from, to) berühren, auch wenn sie davor beginnen oder danach enden.
    @Query(value = "{ 'start': { $lt: ?1 }, 'end': { $gt: ?0 } }", sort = "{ 'start': 1 }")
    List<CalendarEvent> findOverlapping(LocalDateTime from, LocalDateTime to);

    @Query(value = "{ 'memberIds': ?0, 'start': { $lt: ?2 }, 'end': { $gt: ?1 } }", sort = "{ 'start': 1 }")
    List<CalendarEvent> findOverlappingForMember(String memberId, LocalDateTime from, LocalDateTime to);
}
