package com.doodok.global.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;

@Component
public class AccessTokens {

    public static final Duration TTL = Duration.ofMinutes(30);
    private static final String ISSUER = "doodok";
    private final MACSigner signer;
    private final MACVerifier verifier;

    public AccessTokens(@Value("${doodok.auth.jwt-secret:}") String secret) throws JOSEException {
        byte[] key;
        if (secret.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = Base64.getDecoder().decode(secret);
        }
        signer = new MACSigner(key);
        verifier = new MACVerifier(key);
    }

    public String issue(Long userId) {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer(ISSUER).subject(userId.toString())
                        .issueTime(Date.from(now)).expirationTime(Date.from(now.plus(TTL))).build());
        try {
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public Optional<Long> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer()) || claims.getExpirationTime() == null) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(claims.getSubject());
            if (!claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
                throw new CredentialsExpiredException("Access token expired");
            }
            return Optional.of(userId);
        } catch (JOSEException | ParseException | NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
