package de.familyhub.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMemberRepository;

@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final FamilyMemberRepository members;

    public MemberUserDetailsService(FamilyMemberRepository members) {
        this.members = members;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return members.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .filter(member -> member.passwordHash() != null)
                .map(FamilyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
