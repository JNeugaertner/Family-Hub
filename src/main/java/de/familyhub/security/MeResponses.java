package de.familyhub.security;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.MemberResponse;
import de.familyhub.family.MemberResponses;

@Component
public class MeResponses {

    private final MemberResponses memberResponses;

    public MeResponses(MemberResponses memberResponses) {
        this.memberResponses = memberResponses;
    }

    public MemberResponse of(FamilyMember member) {
        return memberResponses.of(member, member);
    }
}
