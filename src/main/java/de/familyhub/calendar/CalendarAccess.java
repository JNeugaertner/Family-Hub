package de.familyhub.calendar;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.KALENDER;

import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.web.ApiException;

// Wer welche Termine sehen und ändern darf. Eigene Termine (memberId = angemeldete Person) brauchen
// Rechte im Geltungsbereich "eigen", Termine anderer Familienmitglieder im Bereich "familie".
// Vorschläge sieht nur, wer sie gemacht hat oder freigeben darf; private Termine nur die Beteiligten und
// Administratoren; Gäste nur Termine aus den freigegebenen Kategorien.
@Component
public class CalendarAccess {

    private final Permissions permissions;
    private final FamilySettingsRepository settings;

    public CalendarAccess(Permissions permissions, FamilySettingsRepository settings) {
        this.permissions = permissions;
        this.settings = settings;
    }

    // Einmal pro Anfrage bauen, damit Rechte und Gast-Freigaben nicht für jeden Termin neu geladen werden.
    public Predicate<CalendarEvent> visibilityFor(FamilyMember viewer) {
        boolean family = can(viewer, ANSEHEN, Scope.FAMILIE);
        boolean own = can(viewer, ANSEHEN, Scope.EIGEN);
        boolean released = can(viewer, ANSEHEN, Scope.FREIGEGEBEN);
        boolean seesPrivate = can(viewer, VERWALTEN, Scope.FAMILIE);
        boolean seesProposals = can(viewer, FREIGEBEN, Scope.FAMILIE);
        Set<EventCategory> guestCategories = released && !family ? settings.current().guestCategories() : Set.of();

        return event -> {
            if (event.isProposal()) {
                return isCreator(viewer, event) || seesProposals;
            }
            boolean mine = isOwn(viewer, event.memberId()) || isCreator(viewer, event);
            if (event.privateEvent()) {
                return (mine && own) || seesPrivate;
            }
            if (family || (mine && own)) {
                return true;
            }
            return released && guestCategories.contains(event.category());
        };
    }

    public boolean canSee(FamilyMember viewer, CalendarEvent event) {
        return visibilityFor(viewer).test(event);
    }

    // Termine für andere werden zum Vorschlag, wenn jemand dort nicht anlegen, aber vorschlagen darf.
    public EventStatus statusForNewEvent(FamilyMember viewer, CalendarEvent event) {
        Scope scope = scopeOf(viewer, event.memberId());
        if (can(viewer, ERSTELLEN, scope)) {
            return EventStatus.APPROVED;
        }
        if (scope == Scope.FAMILIE && can(viewer, VORSCHLAGEN, Scope.FAMILIE)) {
            return EventStatus.PROPOSED;
        }
        throw denied(viewer, ERSTELLEN, scope, "Termine anzulegen", "anlegen");
    }

    public void requireUpdate(FamilyMember viewer, CalendarEvent existing, CalendarEvent changed) {
        if (existing.isProposal()) {
            if ((isCreator(viewer, existing) && can(viewer, VORSCHLAGEN, Scope.FAMILIE))
                    || can(viewer, BEARBEITEN, Scope.FAMILIE)) {
                return;
            }
            throw ApiException.forbidden("Einen Vorschlag darf nur ändern, wer ihn gemacht hat, oder ein Administrator.");
        }
        Scope scope = isOwn(viewer, existing.memberId()) && isOwn(viewer, changed.memberId())
                ? Scope.EIGEN : Scope.FAMILIE;
        if (!can(viewer, BEARBEITEN, scope)) {
            throw denied(viewer, BEARBEITEN, scope, "Termine zu ändern", "ändern");
        }
    }

    public void requireDelete(FamilyMember viewer, CalendarEvent existing) {
        if (existing.isProposal()) {
            if (isCreator(viewer, existing) || can(viewer, FREIGEBEN, Scope.FAMILIE)) {
                return;
            }
            throw ApiException.forbidden("Einen Vorschlag darf nur zurückziehen, wer ihn gemacht hat.");
        }
        Scope scope = scopeOf(viewer, existing.memberId());
        if (!can(viewer, LOESCHEN, scope)) {
            throw denied(viewer, LOESCHEN, scope, "Termine zu löschen", "löschen");
        }
    }

    public void requireDecision(FamilyMember viewer) {
        if (!can(viewer, FREIGEBEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Nur Administratoren dürfen Vorschläge freigeben oder ablehnen.");
        }
    }

    private ApiException denied(FamilyMember viewer, Action action, Scope scope, String what, String verb) {
        if (scope == Scope.FAMILIE && can(viewer, action, Scope.EIGEN)) {
            return ApiException.forbidden("Du darfst nur deine eigenen Termine " + verb + ".");
        }
        return ApiException.forbidden("Keine Berechtigung, " + what + ".");
    }

    private boolean can(FamilyMember viewer, Action action, Scope scope) {
        return permissions.can(viewer, KALENDER, action, scope);
    }

    private static Scope scopeOf(FamilyMember viewer, String memberId) {
        return isOwn(viewer, memberId) ? Scope.EIGEN : Scope.FAMILIE;
    }

    private static boolean isOwn(FamilyMember viewer, String memberId) {
        return viewer.id().equals(memberId);
    }

    private static boolean isCreator(FamilyMember viewer, CalendarEvent event) {
        return viewer.id().equals(event.createdBy());
    }
}
