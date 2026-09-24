package de.familyhub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

// Anmeldung mit Benutzername und Passwort, Sitzung per HttpOnly-Cookie, CSRF-Schutz über das
// Cookie XSRF-TOKEN und den Header X-XSRF-TOKEN (Entscheidung vom 24.09.2026).
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccess,
            ProblemResponses problems) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/setup").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(loginSuccess)
                        .failureHandler((request, response, exception) -> problems.write(request, response, 401,
                                "Anmeldung fehlgeschlagen", "Benutzername oder Passwort ist falsch."))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // Reine API ohne Weiterleitung nach dem Login: Anfragen nicht in der Sitzung merken
                .requestCache(cache -> cache.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problems.write(request, response,
                                401, "Nicht angemeldet", "Bitte zuerst anmelden."))
                        .accessDeniedHandler((request, response, exception) -> problems.write(request, response,
                                403, "Zugriff verweigert", exception instanceof CsrfException
                                        ? "Sicherheits-Token (CSRF) fehlt oder ist abgelaufen. Bitte die Seite neu laden."
                                        : "Für diese Aktion fehlt die Berechtigung.")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
