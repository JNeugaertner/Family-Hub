package de.familyhub.calendar;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.model.Filters;

// Seit dem 29.09.2026 können an einem Termin mehrere Personen beteiligt sein. Termine aus älteren Datenbanken haben
// noch das Feld memberId; beim Start wird daraus die Liste memberIds. Läuft vor den Beispieldaten und ist
// wiederholbar (danach gibt es kein memberId mehr).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EventMembersMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EventMembersMigration.class);

    private final MongoTemplate mongo;

    public EventMembersMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    public long migrate() {
        long changed = mongo.getCollection(mongo.getCollectionName(CalendarEvent.class))
                .updateMany(Filters.exists("memberId"), List.of(
                        new Document("$set", new Document("memberIds", List.of("$memberId"))),
                        new Document("$unset", "memberId")))
                .getModifiedCount();
        if (changed > 0) {
            log.info("{} Termine auf mehrere Beteiligte umgestellt (memberId -> memberIds).", changed);
        }
        return changed;
    }
}
