package com.doodok.global.security;

import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Base64;
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

    @Test
    void rejectsSignedTokenWithoutExpiration() throws Exception {
        byte[] key = new byte[32];
        AccessTokens issuer = new AccessTokens(Base64.getEncoder().encodeToString(key));
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
