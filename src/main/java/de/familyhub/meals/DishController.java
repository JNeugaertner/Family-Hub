package de.familyhub.meals;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
@RequestMapping("/api/dishes")
@Tag(name = "Essensplan")
public class DishController {

    private final DishRepository dishes;
    private final MealEntryRepository meals;
    private final CurrentMember currentMember;
    private final MealAccess access;
    private final Clock clock;

    public DishController(DishRepository dishes, MealEntryRepository meals, CurrentMember currentMember,
            MealAccess access, Clock clock) {
        this.dishes = dishes;
        this.meals = meals;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Gerichte-Sammlung", description = "Alphabetisch sortiert.")
    public List<Dish> list() {
        access.requireView(currentMember.get());
        return dishes.findAll().stream().sorted(Comparator.comparing(Dish::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @PostMapping
    @Operation(summary = "Gericht anlegen", description = "Für Eltern und Jugendliche.")
    public ResponseEntity<Dish> create(@Valid @RequestBody Dish dish) {
        FamilyMember viewer = currentMember.get();
        access.requireEditDishes(viewer);
        Dish saved = dishes.save(new Dish(null, null, null, viewer.id(), LocalDateTime.now(clock)).withContent(dish));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Gericht ändern", description = "Ein neuer Name gilt auch für die Einträge im Wochenplan.")
    public Dish update(@PathVariable String id, @Valid @RequestBody Dish dish) {
        access.requireEditDishes(currentMember.get());
        Dish saved = dishes.save(find(id).withContent(dish));
        meals.saveAll(meals.findByDishId(id).stream().map(m -> m.withDish(id, saved.name())).toList());
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gericht löschen",
            description = "Einträge im Wochenplan bleiben mit ihrem Namen stehen, haben dann aber keine Zutaten mehr.")
    public void delete(@PathVariable String id) {
        access.requireEditDishes(currentMember.get());
        find(id);
        meals.saveAll(meals.findByDishId(id).stream().map(m -> m.withDish(null, m.name())).toList());
        dishes.deleteById(id);
    }

    private Dish find(String id) {
        return dishes.findById(id).orElseThrow(() -> ApiException.notFound("Gericht " + id + " existiert nicht."));
    }
}
