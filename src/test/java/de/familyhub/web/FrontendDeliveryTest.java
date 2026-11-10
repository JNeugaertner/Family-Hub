package de.familyhub.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Im Docker-Image liefert das Backend die gebaute Oberfläche mit aus. Die Tests nutzen dafür kleine Platzhalter
// unter src/test/resources/static.
@SpringBootTest
@AutoConfigureMockMvc
class FrontendDeliveryTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void startPageOpensWithoutLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    // Sonst sähe man nach einem Update weiter die alte Oberfläche
    @Test
    void indexPageIsNotCached() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FamilyHub AI")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    // Vite hängt einen Hash des Inhalts an den Namen, eine geänderte Datei heißt also anders
    @Test
    void builtAssetsMayStayInTheBrowserForAYear() throws Exception {
        mvc.perform(get("/assets/index-test.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    void appManifestIsPublicSoTheAppCanBeInstalled() throws Exception {
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
    }

    @Test
    void otherFilesAndTheApiStillNeedLogin() throws Exception {
        mvc.perform(get("/geheim.txt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }
}
