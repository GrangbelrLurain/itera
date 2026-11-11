package aerocore.backend.service;

import aerocore.backend.exception.UnauthorizedException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Single-admin authentication with stateless HMAC-signed tokens.
 * Token = base64url("subject:expiryEpochSeconds") + "." + base64url(HMAC-SHA256).
 * Credentials come from the environment (see BackendApiApplication); there is no built-in default password.
 */
public class AdminAuthService {

    public static final Duration TOKEN_TTL = Duration.ofHours(8);

    public record Login(String token, Instant expiresAt) {}

    private final String username;
    private final byte[] passwordBytes;
    private final byte[] secret;
    private final Clock clock;

    public AdminAuthService(String username, String password, String tokenSecret, Clock clock) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("admin username and password are required");
        }
        this.username = username;
        this.passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        if (tokenSecret == null || tokenSecret.isBlank()) {
            // No configured secret: tokens stay valid only until this process restarts.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
        } else {
            this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public Login login(String user, String password) {
        boolean userOk = constantTimeEquals(username.getBytes(StandardCharsets.UTF_8), bytes(user));
        boolean passOk = constantTimeEquals(passwordBytes, bytes(password));
        if (!(userOk & passOk)) { // non-short-circuit: same work for wrong user / wrong password
            throw new UnauthorizedException("Invalid username or password");
        }
        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        String body = b64(username + ":" + expiresAt.getEpochSecond());
        return new Login(body + "." + b64(sign(body)), expiresAt);
    }

    /** Returns the subject of a valid, unexpired token. */
    public String verify(String token) {
        if (token == null) throw new UnauthorizedException("Missing token");
        int dot = token.indexOf('.');
        if (dot <= 0) throw new UnauthorizedException("Malformed token");
        String body = token.substring(0, dot);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Malformed token");
        }
        if (!constantTimeEquals(sign(body), given)) throw new UnauthorizedException("Invalid token");

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Malformed token");
        }
        int colon = payload.lastIndexOf(':');
        if (colon <= 0) throw new UnauthorizedException("Malformed token");
        long exp;
        try {
            exp = Long.parseLong(payload.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Malformed token");
        }
        if (clock.instant().getEpochSecond() >= exp) throw new UnauthorizedException("Token expired");
        return payload.substring(0, colon);
    }

    /** "Bearer xyz" -> verified subject. */
    public String verifyAuthorizationHeader(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return verify(header.substring(7).trim());
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] bytes(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
