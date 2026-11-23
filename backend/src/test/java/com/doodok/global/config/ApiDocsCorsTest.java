package com.doodok.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestPropertySource(properties = {
        "API_DOCS_ENABLED=true",
        "CORS_ALLOWED_ORIGINS=http://localhost:3000, https://doodok.xyz"
})
class ApiDocsCorsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publishesAuthContractAndSwaggerUiWithoutAccessToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/email-verifications'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['429'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").isArray());
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void allowsCredentialedPreflightBeforeJwtAuthentication() throws Exception {
        mvc.perform(options("/api/users/me")
                        .header(HttpHeaders.ORIGIN, "https://doodok.xyz")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://doodok.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization"));
        mvc.perform(get("/api/users/me").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void rejectsPreflightFromAnUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://unlisted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
