package br.com.condominio.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

  private final JwtProperties props;
  private final Map<String, SecretKey> keysByKid;
  private final SecretKey activeKey;

  public JwtService(JwtProperties props) {
    this.props = props;
    this.keysByKid = new HashMap<>();
    for (String entry : props.getKeys()) {
      String[] parts = entry.split(":", 2);
      if (parts.length != 2) throw new IllegalStateException("Invalid JWT key entry: " + entry);
      byte[] keyBytes = Base64.getDecoder().decode(parts[1]);
      if (keyBytes.length < 32)
        throw new IllegalStateException("JWT key " + parts[0] + " too short");
      keysByKid.put(parts[0], Keys.hmacShaKeyFor(keyBytes));
    }
    this.activeKey = keysByKid.get(props.getActiveKid());
    if (activeKey == null) {
      throw new IllegalStateException("Active JWT kid '" + props.getActiveKid() + "' not in keys");
    }
  }

  public String signAccessToken(
      UUID userId,
      List<String> roles,
      List<String> authorities,
      UUID unitId,
      boolean isUnitMaster) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(props.getActiveKid())
        .and()
        .issuer(props.getIssuer())
        .audience()
        .add(props.getAudience())
        .and()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(props.getAccessTtl())))
        .id(UUID.randomUUID().toString())
        .claim("roles", roles)
        .claim("authorities", authorities)
        .claim("unitId", unitId == null ? null : unitId.toString())
        .claim("isUnitMaster", isUnitMaster)
        .signWith(activeKey)
        .compact();
  }

  public ParsedAccessToken parseAccessToken(String token) {
    Jws<Claims> jws =
        Jwts.parser()
            .keyLocator(
                header -> {
                  String kid = header.get("kid").toString();
                  SecretKey key = keysByKid.get(kid);
                  if (key == null) throw new JwtException("Unknown kid: " + kid);
                  return key;
                })
            .requireIssuer(props.getIssuer())
            .requireAudience(props.getAudience())
            .build()
            .parseSignedClaims(token);
    Claims c = jws.getPayload();
    return new ParsedAccessToken(
        UUID.fromString(c.getSubject()),
        listClaim(c, "roles"),
        listClaim(c, "authorities"),
        c.get("unitId") == null ? null : UUID.fromString(c.get("unitId", String.class)),
        Boolean.TRUE.equals(c.get("isUnitMaster", Boolean.class)));
  }

  private List<String> listClaim(Claims c, String name) {
    Object v = c.get(name);
    if (v instanceof List<?> list) return list.stream().map(Object::toString).toList();
    return List.of();
  }

  public record ParsedAccessToken(
      UUID userId,
      List<String> roles,
      List<String> authorities,
      UUID unitId,
      boolean isUnitMaster) {}
}
