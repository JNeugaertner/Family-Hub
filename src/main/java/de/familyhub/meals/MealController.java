package de.familyhub.meals;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/meals")
@Tag(name = "Essensplan")
public class MealController {

    // Der Plan wird wochenweise geladen; etwas Spielraum für Monatsansichten.
    static final int MAX_RANGE_DAYS = 62;

    private static final Comparator<MealEntry> PLAN_ORDER = Comparator.comparing(MealEntry::date)
            .thenComparing(MealEntry::type)
            .thenComparing(MealEntry::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final MealEntryRepository meals;
    private final DishRepository dishes;
    private final MealShopping shopping;
    private final CurrentMember currentMember;
    private final MealAccess access;
    private final Clock clock;

    public MealController(MealEntryRepository meals, DishRepository dishes, MealShopping shopping,
            CurrentMember currentMember, MealAccess access, Clock clock) {
        this.meals = meals;
        this.dishes = dishes;
        this.shopping = shopping;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
    }

    public record MealRequest(
            @NotNull(message = "Datum ist Pflicht") LocalDate date,
            @NotNull(message = "Mahlzeit ist Pflicht") MealType type,
            @Schema(description = "Gericht aus der Sammlung; ohne Gericht zählt name als Freitext") String dishId,
            @Size(max = 80, message = "Freitext darf höchstens 80 Zeichen lang sein") String name) {
    }

    public record Range(
            @NotNull(message = "from ist Pflicht") LocalDate from,
            @NotNull(message = "to ist Pflicht") LocalDate to) {
    }

    @GetMapping
    @Operation(summary = "Essensplan für einen Zeitraum", description = "from und to einschließlich, höchstens "
            + MAX_RANGE_DAYS + " Tage. Offene Wünsche nur für die Person, die sie geäußert hat, und Administratoren.")
    public List<MealEntry> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Predicate<MealEntry> visible = access.visibilityFor(currentMember.get());
        return inRange(from, to).stream().filter(visible).toList();
    }

    @PostMapping
    @Operation(summary = "Mahlzeit eintragen oder Wunsch äußern",
            description = "Eltern und Jugendliche tragen direkt ein; ein vorhandener Eintrag dieser Mahlzeit wird "
                    + "ersetzt. Wer nur vorschlagen darf (Kinder), äußert einen Wunsch (status proposed).")
    public ResponseEntity<MealEntry> create(@Valid @RequestBody MealRequest request) {
        FamilyMember viewer = currentMember.get();
        MealStatus status = access.statusForNewEntry(viewer);
        String name;
        if (request.dishId() != null && !request.dishId().isBlank()) {
            Dish dish = dishes.findById(request.dishId())
                    .orElseThrow(() -> ApiException.invalidField("dishId", "Das Gericht existiert nicht."));
            name = dish.name();
        } else if (request.name() != null && !request.name().isBlank()) {
            name = request.name().strip();
        } else {
            throw ApiException.invalidField("name", "Bitte ein Gericht auswählen oder etwas eintragen.");
        }
        String dishId = request.dishId() == null || request.dishId().isBlank() ? null : request.dishId();
        if (status == MealStatus.APPROVED) {
            meals.deleteAll(meals.findByDateAndTypeAndStatus(request.date(), request.type(), MealStatus.APPROVED));
        }
        MealEntry saved = meals.save(new MealEntry(null, request.date(), request.type(), dishId, name, status,
                viewer.id(), LocalDateTime.now(clock)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eintrag entfernen oder eigenen Wunsch zurückziehen")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        MealEntry existing = findVisible(id, viewer);
        access.requireDelete(viewer, existing);
        meals.deleteById(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Wunsch übernehmen",
            description = "Nur für Administratoren. Ersetzt den bisherigen Eintrag dieser Mahlzeit.")
    public MealEntry approve(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireDecision(viewer);
        MealEntry wish = findProposal(id, viewer);
        meals.deleteAll(meals.findByDateAndTypeAndStatus(wish.date(), wish.type(), MealStatus.APPROVED));
        return meals.save(wish.approved());
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Wunsch ablehnen", description = "Nur für Administratoren. Der Wunsch wird verworfen.")
    public void reject(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireDecision(viewer);
        findProposal(id, viewer);
        meals.deleteById(id);
    }

    @PostMapping("/{id}/shopping")
    @Operation(summary = "Zutaten eines Gerichts auf die Einkaufsliste",
            description = "Was schon offen auf der Liste steht, wird übersprungen.")
    public MealShopping.Transfer toShopping(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        MealEntry entry = findVisible(id, viewer);
        if (entry.isProposal()) {
            throw ApiException.conflict("Ein Wunsch muss erst übernommen werden.");
        }
        return shopping.transfer(List.of(entry), viewer);
    }

    @PostMapping("/shopping")
    @Operation(summary = "Zutaten eines Zeitraums auf die Einkaufsliste",
            description = "Alle eingetragenen Gerichte von from bis to (einschließlich), z. B. die ganze Woche. "
                    + "Was schon offen auf der Liste steht, wird übersprungen.")
    public MealShopping.Transfer rangeToShopping(@Valid @RequestBody Range range) {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        return shopping.transfer(inRange(range.from(), range.to()).stream().filter(e -> !e.isProposal()).toList(),
                viewer);
    }

    private List<MealEntry> inRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.invalidField("to", "to darf nicht vor from liegen.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw ApiException.invalidField("to", "Höchstens " + MAX_RANGE_DAYS + " Tage auf einmal.");
        }
        return meals.findInRange(from, to).stream().sorted(PLAN_ORDER).toList();
    }

    private MealEntry findProposal(String id, FamilyMember viewer) {
        MealEntry entry = findVisible(id, viewer);
        if (!entry.isProposal()) {
            throw ApiException.conflict("Der Eintrag ist kein offener Wunsch.");
        }
        return entry;
    }

    // Einträge, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private MealEntry findVisible(String id, FamilyMember viewer) {
        Predicate<MealEntry> visible = access.visibilityFor(viewer);
        return meals.findById(id).filter(visible)
                .orElseThrow(() -> ApiException.notFound("Eintrag " + id + " existiert nicht."));
    }
}
