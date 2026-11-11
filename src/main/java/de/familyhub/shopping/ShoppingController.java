package de.familyhub.shopping;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.undo.UndoService;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/shopping")
@Tag(name = "Einkaufsliste")
public class ShoppingController {

    private final ShoppingItemRepository items;
    private final CurrentMember currentMember;
    private final ShoppingAccess access;
    private final Clock clock;
    private final UndoService undo;

    public ShoppingController(ShoppingItemRepository items, CurrentMember currentMember, ShoppingAccess access,
            Clock clock, UndoService undo) {
        this.items = items;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
        this.undo = undo;
    }

    public record CheckedChange(@NotNull(message = "checked ist Pflicht") Boolean checked) {
    }

    @Schema(description = "Wiederkehrende Artikel werden nicht gelöscht: bei jedem Einkauf stehen sie sofort "
            + "wieder offen auf der Liste (backOnList), mit Abstand warten sie unter \"Kommt wieder\" (waiting).")
    public record Deleted(int deleted, int backOnList, int waiting) {
    }

    @GetMapping
    @Operation(summary = "Einkaufsliste", description = "In der Reihenfolge des Hinzufügens. Offene Vorschläge nur für "
            + "die vorschlagende Person und Administratoren.")
    public List<ShoppingItem> list() {
        Predicate<ShoppingItem> visible = access.visibilityFor(currentMember.get());
        return items.findAllByOrderByCreatedAtAsc().stream().filter(visible).toList();
    }

    @PostMapping
    @Operation(summary = "Artikel hinzufügen",
            description = "Wer nur vorschlagen darf (Kinder), legt einen Vorschlag an (status proposed).")
    public ResponseEntity<ShoppingItem> create(@Valid @RequestBody ShoppingItem item) {
        FamilyMember viewer = currentMember.get();
        ShoppingItemStatus status = access.statusForNewItem(viewer);
        requireSelectable(item.category(), null);
        if (item.repeat() != null) {
            access.requireRepeat(viewer);
            requireValidRepeat(item.repeat(), item.category());
        }
        ShoppingItem saved = items.save(new ShoppingItem(null, item.name().strip(),
                ShoppingItem.blankToNull(item.quantity()), item.category(), item.urgent(), false, status, viewer.id(),
                LocalDateTime.now(clock), null, item.repeat(), null, null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Artikel ändern", description = "Name, Menge, Kategorie, dringend, Wiederholung. Ein "
            + "geänderter Abstand gilt ab dem letzten Kauf.")
    public ShoppingItem update(@PathVariable String id, @Valid @RequestBody ShoppingItem item) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireEdit(viewer);
        requireSelectable(item.category(), existing.category());
        requireValidRepeat(item.repeat(), item.category());
        LocalDate today = LocalDate.now(clock);
        ShoppingItem updated = existing.withContent(item, today);
        return items.save(updated.isDueBack(today) ? updated.reopened() : updated);
    }

    // Abstand außer bei "bei jedem Einkauf". Zutaten aus dem Essensplan setzt der Plan selbst wieder auf die Liste.
    private static void requireValidRepeat(ShoppingRepeat repeat, ShoppingCategory category) {
        if (repeat == null) {
            return;
        }
        if (category == ShoppingCategory.ZUTATEN_ESSENSPLANUNG) {
            throw ApiException.invalidField("repeat", "Zutaten aus dem Essensplan wiederholen sich nicht, "
                    + "der Essensplan setzt sie bei Bedarf wieder auf die Liste.");
        }
        if (!repeat.everyTrip() && repeat.interval() == null) {
            throw ApiException.invalidField("repeat.interval", "Abstand ist Pflicht");
        }
    }

    // „Zutaten Essensplanung“ füllt nur der Essensplan (MealShopping); sein Abgleich würde von Hand eingetragene
    // Artikel wieder löschen. Artikel, die schon dort stehen, dürfen ihre Kategorie behalten.
    private static void requireSelectable(ShoppingCategory category, ShoppingCategory current) {
        if (category == ShoppingCategory.ZUTATEN_ESSENSPLANUNG && current != ShoppingCategory.ZUTATEN_ESSENSPLANUNG) {
            throw ApiException.invalidField("category",
                    "Diese Kategorie füllt der Essensplan. Bitte eine andere wählen.");
        }
    }

    @PatchMapping("/{id}/checked")
    @Operation(summary = "Abhaken (gekauft) oder zurücknehmen",
            description = "Zurücknehmen holt einen wartenden wiederkehrenden Artikel sofort zurück auf die Liste.")
    public ShoppingItem check(@PathVariable String id, @Valid @RequestBody CheckedChange change) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireEdit(viewer);
        if (existing.isProposal()) {
            throw ApiException.conflict("Ein Vorschlag muss erst übernommen werden.");
        }
        if (existing.isWaiting() && change.checked()) {
            return existing;
        }
        return items.save(existing.withChecked(change.checked(), viewer.id(), LocalDate.now(clock)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Artikel löschen oder eigenen Vorschlag zurückziehen")
    public void delete(@PathVariable String id, HttpServletResponse response) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireDelete(viewer, existing);
        response.setHeader(UndoService.HEADER, undo.remember("shopping", viewer, List.of(existing)));
        items.deleteById(id);
    }

    @DeleteMapping("/checked")
    @Operation(summary = "Abgehakte Artikel entfernen",
            description = "Wiederkehrende Artikel bleiben: wieder offen oder unter \"Kommt wieder\".")
    public Deleted deleteChecked(HttpServletResponse response) {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        access.requireDeleteChecked(viewer);
        LocalDate today = LocalDate.now(clock);
        List<ShoppingItem> checked = items.findByCheckedTrueAndStatus(ShoppingItemStatus.APPROVED).stream()
                .filter(item -> !item.isWaiting())
                .toList();
        // Rückgängig: gelöschte Artikel zurück, wiederkehrende in ihren abgehakten Zustand
        String token = undo.remember("shopping", viewer, checked);
        if (token != null) {
            response.setHeader(UndoService.HEADER, token);
        }
        List<ShoppingItem> bought = checked.stream().filter(item -> item.repeat() == null).toList();
        List<ShoppingItem> repeating = checked.stream()
                .filter(item -> item.repeat() != null)
                .map(item -> item.afterShoppingTrip(today))
                .toList();
        items.deleteAll(bought);
        items.saveAll(repeating);
        int waiting = (int) repeating.stream().filter(ShoppingItem::isWaiting).count();
        return new Deleted(bought.size(), repeating.size() - waiting, waiting);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Vorschlag übernehmen", description = "Nur für Administratoren.")
    public ShoppingItem approve(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireDecision(viewer);
        return items.save(findProposal(id, viewer).approved());
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Vorschlag ablehnen", description = "Nur für Administratoren. Der Vorschlag wird verworfen.")
    public void reject(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireDecision(viewer);
        findProposal(id, viewer);
        items.deleteById(id);
    }

    private ShoppingItem findProposal(String id, FamilyMember viewer) {
        ShoppingItem item = findVisible(id, viewer);
        if (!item.isProposal()) {
            throw ApiException.conflict("Der Artikel ist kein offener Vorschlag.");
        }
        return item;
    }

    // Artikel, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private ShoppingItem findVisible(String id, FamilyMember viewer) {
        Predicate<ShoppingItem> visible = access.visibilityFor(viewer);
        return items.findById(id).filter(visible)
                .orElseThrow(() -> ApiException.notFound("Artikel " + id + " existiert nicht."));
    }
}
