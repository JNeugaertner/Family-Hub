package de.familyhub.shopping;

import java.net.URI;
import java.time.Clock;
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
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public ShoppingController(ShoppingItemRepository items, CurrentMember currentMember, ShoppingAccess access,
            Clock clock) {
        this.items = items;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
    }

    public record CheckedChange(@NotNull(message = "checked ist Pflicht") Boolean checked) {
    }

    public record Deleted(int deleted) {
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
        ShoppingItem saved = items.save(new ShoppingItem(null, item.name().strip(),
                ShoppingItem.blankToNull(item.quantity()), item.category(), item.urgent(), false, status, viewer.id(),
                LocalDateTime.now(clock), null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Artikel ändern", description = "Name, Menge, Kategorie, dringend.")
    public ShoppingItem update(@PathVariable String id, @Valid @RequestBody ShoppingItem item) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireEdit(viewer);
        return items.save(existing.withContent(item));
    }

    @PatchMapping("/{id}/checked")
    @Operation(summary = "Abhaken (gekauft) oder zurücknehmen")
    public ShoppingItem check(@PathVariable String id, @Valid @RequestBody CheckedChange change) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireEdit(viewer);
        if (existing.isProposal()) {
            throw ApiException.conflict("Ein Vorschlag muss erst übernommen werden.");
        }
        return items.save(existing.withChecked(change.checked(), viewer.id()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Artikel löschen oder eigenen Vorschlag zurückziehen")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        ShoppingItem existing = findVisible(id, viewer);
        access.requireDelete(viewer, existing);
        items.deleteById(id);
    }

    @DeleteMapping("/checked")
    @Operation(summary = "Abgehakte Artikel entfernen")
    public Deleted deleteChecked() {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        access.requireDeleteChecked(viewer);
        List<ShoppingItem> checked = items.findByCheckedTrueAndStatus(ShoppingItemStatus.APPROVED);
        items.deleteAll(checked);
        return new Deleted(checked.size());
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
