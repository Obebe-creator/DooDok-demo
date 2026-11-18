package com.doodok.global.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokensTest {

    @Test
    void verifiesIssuedTokenButRejectsTokensFromAnotherKeyAndMalformedInput() throws Exception {
        AccessTokens issuer = new AccessTokens("");
        AccessTokens anotherIssuer = new AccessTokens("");
        String token = issuer.issue(42L);

        assertThat(issuer.verify(token)).contains(42L);
        assertThat(anotherIssuer.verify(token)).isEmpty();
        assertThat(issuer.verify("not-a-jwt")).isEmpty();
    }
}
