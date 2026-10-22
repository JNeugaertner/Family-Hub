package de.familyhub.inbox;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

// Gemeinsamer Eingang für Nachrichten von außen: Die Kanäle legen hier ab, die Oberfläche und später der
// KI-Service lesen hier, jeweils nur, was die Person sehen darf (InboxAccess).
@Service
public class Inbox {

    static final int LIMIT = 100;

    private final InboundMessageRepository repository;
    private final MongoTemplate mongo;
    private final InboxAccess access;
    private final List<SenderIdentity> identities;
    private final FamilyMemberRepository members;
    private final ApplicationEventPublisher events;

    public Inbox(InboundMessageRepository repository, MongoTemplate mongo, InboxAccess access,
            List<SenderIdentity> identities, FamilyMemberRepository members, ApplicationEventPublisher events) {
        this.repository = repository;
        this.mongo = mongo;
        this.access = access;
        this.identities = identities;
        this.members = members;
        this.events = events;
    }

    // Kanäle stellen dieselbe Nachricht manchmal mehrfach zu; false, wenn sie schon gespeichert war.
    public boolean store(InboundMessage message) {
        if (repository.existsById(message.id())) {
            return false;
        }
        repository.save(message);
        events.publishEvent(new InboundMessageReceived(message));
        return true;
    }

    // Das Familienmitglied, das die Nachricht geschrieben hat; leer bei Absendern, die keinem zugeordnet sind
    public Optional<FamilyMember> senderOf(InboundMessage message) {
        return members.findAll().stream()
                .filter(member -> identities.stream()
                        .anyMatch(identity -> identity.senderKeyOf(member).filter(message.senderKey()::equals)
                                .isPresent()))
                .findFirst();
    }

    // Die neuesten Nachrichten, die die Person sehen darf; channel null: alle Kanäle.
    public List<InboundMessage> visibleFor(FamilyMember viewer, Channel channel) {
        return visibility(viewer, channel)
                .map(criteria -> mongo.find(Query.query(criteria)
                        .with(Sort.by(Sort.Direction.DESC, "receivedAt", "_id")).limit(LIMIT), InboundMessage.class))
                .orElse(List.of());
    }

    public long countVisibleFor(FamilyMember viewer, Channel channel) {
        return visibility(viewer, channel)
                .map(criteria -> mongo.count(Query.query(criteria), InboundMessage.class))
                .orElse(0L);
    }

    // Leer, wenn die Person nichts sehen darf oder in keinem Kanal bekannt ist
    private Optional<Criteria> visibility(FamilyMember viewer, Channel channel) {
        Criteria criteria = channel == null ? new Criteria() : Criteria.where("channel").is(channel);
        if (access.seesAll(viewer)) {
            return Optional.of(criteria);
        }
        if (!access.seesOwn(viewer)) {
            return Optional.empty();
        }
        List<String> ownKeys = identities.stream()
                .map(identity -> identity.senderKeyOf(viewer))
                .flatMap(Optional::stream)
                .toList();
        return ownKeys.isEmpty() ? Optional.empty() : Optional.of(criteria.and("senderKey").in(ownKeys));
    }
}
