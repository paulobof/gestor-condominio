package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwt;
  private UUID userId;

  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties();
    props.setIssuer("gestor-condominio");
    props.setAudience("gestor-condominio-web");
    props.setAccessTtl(Duration.ofMinutes(15));
    props.setRefreshTtl(Duration.ofDays(7));
    props.setActiveKid("v1");
    props.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    jwt = new JwtService(props);
    userId = UUID.randomUUID();
  }

  @Test
  void signsAndParsesAccessToken() {
    String token =
        jwt.signAccessToken(userId, List.of("RESIDENT"), List.of("USER_VIEW"), null, true);
    JwtService.ParsedAccessToken p = jwt.parseAccessToken(token);
    assertThat(p.userId()).isEqualTo(userId);
    assertThat(p.roles()).containsExactly("RESIDENT");
    assertThat(p.authorities()).containsExactly("USER_VIEW");
    assertThat(p.isUnitMaster()).isTrue();
    assertThat(p.unitId()).isNull();
  }

  @Test
  void rejectsTokenWithWrongIssuer() {
    String token = jwt.signAccessToken(userId, List.of(), List.of(), null, false);
    JwtProperties other = new JwtProperties();
    other.setIssuer("other");
    other.setAudience("gestor-condominio-web");
    other.setAccessTtl(Duration.ofMinutes(15));
    other.setActiveKid("v1");
    other.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    JwtService otherJwt = new JwtService(other);
    assertThatThrownBy(() -> otherJwt.parseAccessToken(token))
        .isInstanceOf(io.jsonwebtoken.JwtException.class);
  }

  @Test
  void rejectsExpiredToken() throws InterruptedException {
    JwtProperties props = new JwtProperties();
    props.setIssuer("gestor-condominio");
    props.setAudience("gestor-condominio-web");
    props.setAccessTtl(Duration.ofMillis(50));
    props.setActiveKid("v1");
    props.setKeys(List.of("v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
    JwtService shortLived = new JwtService(props);
    String token = shortLived.signAccessToken(userId, List.of(), List.of(), null, false);
    Thread.sleep(120);
    assertThatThrownBy(() -> shortLived.parseAccessToken(token))
        .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
  }

  @Test
  void supportsKeyRotationParsesWithAnyConfiguredKey() {
    String tokenV1 = jwt.signAccessToken(userId, List.of(), List.of(), null, false);
    JwtProperties propsWithBoth = new JwtProperties();
    propsWithBoth.setIssuer("gestor-condominio");
    propsWithBoth.setAudience("gestor-condominio-web");
    propsWithBoth.setAccessTtl(Duration.ofMinutes(15));
    propsWithBoth.setActiveKid("v2");
    propsWithBoth.setKeys(
        List.of(
            "v1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            "v2:AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="));
    JwtService rotated = new JwtService(propsWithBoth);
    JwtService.ParsedAccessToken p = rotated.parseAccessToken(tokenV1);
    assertThat(p.userId()).isEqualTo(userId);
  }
}
