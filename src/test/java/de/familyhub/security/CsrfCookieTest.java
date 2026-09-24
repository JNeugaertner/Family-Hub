package de.familyhub.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Eigener Spring-Kontext (Property unten): csrf() aus spring-security-test ersetzt im gemeinsam genutzten
// Kontext den Cookie-Speicher des CSRF-Filters, danach würden hier keine Cookies mehr geschrieben.
@SpringBootTest(properties = "familyhub.test.context=csrf-cookie")
@AutoConfigureMockMvc
class CsrfCookieTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousRequestReceivesReadableCsrfCookieForTheFrontend() throws Exception {
        // CookieCsrfTokenRepository schreibt das Cookie als Set-Cookie-Header
        mvc.perform(get("/api/auth/status"))
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")))
                .andExpect(header().string("Set-Cookie", not(containsString("HttpOnly"))));
    }
}
