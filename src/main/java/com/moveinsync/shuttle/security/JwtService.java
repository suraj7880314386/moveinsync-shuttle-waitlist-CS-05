package com.moveinsync.shuttle.security;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.domain.AppUser;
import com.moveinsync.shuttle.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMinutes;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) { // fail fast at startup instead of running with a weak key
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = props.jwt().expirationMinutes();
        this.clock = clock;
    }

    public long ttlMinutes() { return ttlMinutes; }

    public String generate(AppUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long uid = ((Number) c.get("uid")).longValue();
            Role role = Role.valueOf(c.get("role", String.class));
            return Optional.of(new AuthPrincipal(uid, c.getSubject(), role));
        } catch (JwtException | IllegalArgumentException | NullPointerException | ClassCastException ex) {
            return Optional.empty();
        }
    }
}
