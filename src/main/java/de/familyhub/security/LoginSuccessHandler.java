package de.familyhub.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Antwort auf eine erfolgreiche Anmeldung: dieselben Daten wie GET /api/auth/me statt einer Weiterleitung.
@Component
class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final FamilyMemberRepository members;
    private final MeResponses meResponses;
    private final ProblemResponses responses;

    LoginSuccessHandler(FamilyMemberRepository members, MeResponses meResponses, ProblemResponses responses) {
        this.members = members;
        this.meResponses = meResponses;
        this.responses = responses;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        FamilyUserDetails user = (FamilyUserDetails) authentication.getPrincipal();
        FamilyMember member = members.findById(user.memberId()).orElseThrow();
        responses.writeJson(response, meResponses.of(member));
    }
}
