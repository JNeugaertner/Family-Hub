package de.familyhub.assistant.settings;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.RoleResolver;

// Tageslimit für Fragen an den Assistenten (Entscheidung vom 22.10.2026: je Rolle). Gezählt wird vor dem Aufruf des
// Sprachmodells; scheitert der Aufruf, gibt refund die Frage zurück.
@Component
public class AssistantQuota {

    private final MongoTemplate mongo;
    private final AssistantSettingsService settings;
    private final RoleResolver roles;
    private final Clock clock;

    public AssistantQuota(MongoTemplate mongo, AssistantSettingsService settings, RoleResolver roles, Clock clock) {
        this.mongo = mongo;
        this.settings = settings;
        this.roles = roles;
        this.clock = clock;
    }

    // 0: unbegrenzt
    public int limitFor(FamilyMember member) {
        return settings.current().dailyLimits().forRole(roles.effectiveRole(member));
    }

    // true, wenn die Person heute noch eine Frage frei hat; die Frage zählt dann schon mit. Auch bei zwei Fragen
    // gleichzeitig wird das Limit nicht überschritten: Ist es erreicht, findet die Abfrage keinen Zähler mehr, und das
    // Anlegen eines zweiten mit derselben id scheitert.
    public boolean tryUse(FamilyMember member) {
        LocalDate today = LocalDate.now(clock);
        int limit = limitFor(member);
        Criteria criteria = Criteria.where("_id").is(id(member.id(), today));
        if (limit > 0) {
            criteria = criteria.and("questions").lt(limit);
        }
        Update update = new Update().inc("questions", 1)
                .setOnInsert("memberId", member.id())
                .setOnInsert("day", today)
                .setOnInsert("createdAt", clock.instant());
        try {
            mongo.upsert(Query.query(criteria), update, AssistantUsage.class);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void refund(FamilyMember member) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id(member.id(), LocalDate.now(clock)))
                .and("questions").gt(0)), new Update().inc("questions", -1), AssistantUsage.class);
    }

    // Zähler ab first (einschließlich), z. B. für die Admin-Karte
    public List<AssistantUsage> since(LocalDate first) {
        return mongo.find(Query.query(Criteria.where("day").gte(first)), AssistantUsage.class);
    }

    public void forgetMember(String memberId) {
        mongo.remove(Query.query(Criteria.where("memberId").is(memberId)), AssistantUsage.class);
    }

    private static String id(String memberId, LocalDate day) {
        return memberId + ":" + day;
    }
}
