package de.familyhub.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.web.ApiException;

@Component
public class CurrentMember {

    private final FamilyMemberRepository members;

    public CurrentMember(FamilyMemberRepository members) {
        this.members = members;
    }

    public FamilyMember get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FamilyUserDetails user)) {
            throw ApiException.unauthorized("Bitte zuerst anmelden.");
        }
        return members.findById(user.memberId())
                .orElseThrow(() -> ApiException.unauthorized("Das Konto existiert nicht mehr."));
    }
}
