package com.doodok.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.support.IntegrationTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class JwtAuthenticationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${doodok.auth.jwt-secret}")
    private String secret;

    @Test
    void apiDocumentationIsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    @Test
    void expiredAccessTokenReturnsTokenExpiredOnProtectedRoute() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION,
                        expiredBearer("doodok", Base64.getDecoder().decode(secret))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void wrongIssuerOrSignatureDoesNotReturnTokenExpired() throws Exception {
        byte[] key = Base64.getDecoder().decode(secret);
        String wrongIssuer = expiredBearer("other", key);
        key[0] ^= 1;
        String wrongSignature = expiredBearer("doodok", key);
        for (String bearer : new String[] {wrongIssuer, wrongSignature}) {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void refreshStillHandlesTheRequestWhenAccessTokenIsExpired() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.AUTHORIZATION,
                        expiredBearer("doodok", Base64.getDecoder().decode(secret))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private String expiredBearer(String issuer, byte[] key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer(issuer).subject("42")
                        .expirationTime(Date.from(Instant.now().minusSeconds(60))).build());
        jwt.sign(new MACSigner(key));
        return "Bearer " + jwt.serialize();
    }
}
