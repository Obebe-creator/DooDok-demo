package com.doodok.global.security;

import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokensTest {

    @Test
    void verifiesIssuedTokenButRejectsTokensFromAnotherKeyAndMalformedInput() throws Exception {
        MockEnvironment local = new MockEnvironment().withProperty("spring.profiles.active", "local");
        AccessTokens issuer = new AccessTokens("", local);
        AccessTokens anotherIssuer = new AccessTokens("", local);
        String token = issuer.issue(42L);

        assertThat(issuer.verify(token)).contains(42L);
        assertThat(anotherIssuer.verify(token)).isEmpty();
        assertThat(issuer.verify("not-a-jwt")).isEmpty();
    }

    @Test
    void requiresValidSigningKeyOutsideLocalAndTest() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> new AccessTokens("", environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");

        environment.setActiveProfiles("production", "local");
        for (String invalid : new String[] {"", "%%%", "YWJj"}) {
            assertThatThrownBy(() -> new AccessTokens(invalid, environment))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        }

        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        AccessTokens first = new AccessTokens(secret, environment);
        AccessTokens second = new AccessTokens(secret, environment);
        assertThat(second.verify(first.issue(42L))).contains(42L);
    }

    @Test
    void rejectsSignedTokenWithoutExpiration() throws Exception {
        byte[] key = new byte[32];
        AccessTokens issuer = new AccessTokens(Base64.getEncoder().encodeToString(key), new MockEnvironment());
        String issuedToken = issuer.issue(42L);
        SignedJWT issued = SignedJWT.parse(issuedToken);
        JWTClaimsSet claims = issued.getJWTClaimsSet();
        JWTClaimsSet claimsWithoutExpiration = new JWTClaimsSet.Builder()
                .issuer(claims.getIssuer())
                .subject(claims.getSubject())
                .issueTime(claims.getIssueTime())
                .build();
        SignedJWT withoutExpiration = new SignedJWT(issued.getHeader(), claimsWithoutExpiration);
        withoutExpiration.sign(new MACSigner(key));

        assertThat(issuer.verify(issuedToken)).contains(42L);
        assertThat(issuer.verify(withoutExpiration.serialize())).isEmpty();
    }
}
