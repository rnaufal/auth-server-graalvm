package com.rnaufal.authservergraalvm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.security.oauth2.authorizationserver.registration.client.client-id=test-client",
      "spring.security.oauth2.authorizationserver.registration.client.client-secret=$2a$10$.qCJPA4jAOcc3symCYNs/et01WAqpf/sFOI4g.vnxVuTpLM.4Jelu"
    })
class AuthorizationServerIntegrationTest {

  private static final String CLIENT_ID = "test-client";

  private static final String CLIENT_SECRET = "test-secret";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldGenerateValidJwtTokenUsingClientCredentials() throws Exception {
    var credentials =
        Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    var result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", "Basic " + credentials)
                    .param("grant_type", "client_credentials")
                    .param("scope", "read write")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").exists())
            .andExpect(jsonPath("$.scope").value("read write"))
            .andReturn();

    var tokenResponse =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    var accessToken = (String) tokenResponse.get("access_token");

    assertThat(accessToken).isNotNull().isNotEmpty();

    var jwt = JWTParser.parse(accessToken);

    assertThat(jwt.getJWTClaimsSet())
        .satisfies(
            jwtClaimsSet -> {
              assertThat(jwtClaimsSet.getIssuer()).isEqualTo("http://localhost:8080");
              assertThat(jwtClaimsSet.getSubject()).isEqualTo(CLIENT_ID);
              assertThat(jwtClaimsSet.getAudience()).contains(CLIENT_ID);
              assertThat(jwtClaimsSet.getExpirationTime()).isAfter(Instant.now());
              assertThat(jwtClaimsSet.getIssueTime()).isBeforeOrEqualTo(Instant.now());
              assertThat(jwtClaimsSet.getNotBeforeTime()).isBeforeOrEqualTo(Instant.now());
              assertThat(jwtClaimsSet.getJWTID()).isNotNull();
            });

    @SuppressWarnings("unchecked")
    List<String> scopes = (List<String>) jwt.getJWTClaimsSet().getClaim("scope");
    assertThat(scopes).containsExactlyInAnyOrder("read", "write");
  }

  @Test
  void shouldRejectInvalidClientCredentials() throws Exception {
    var invalidCredentials = Base64.getEncoder().encodeToString("invalid:credentials".getBytes());

    mockMvc
        .perform(
            post("/oauth2/token")
                .header("Authorization", "Basic " + invalidCredentials)
                .param("grant_type", "client_credentials")
                .param("scope", "read")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectInvalidGrantType() throws Exception {
    var credentials =
        Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    mockMvc
        .perform(
            post("/oauth2/token")
                .header("Authorization", "Basic " + credentials)
                .param("grant_type", "authorization_code")
                .param("scope", "read")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_request"));
  }

  @Test
  void shouldValidateTokenLifetime() throws Exception {
    var credentials =
        Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    var result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", "Basic " + credentials)
                    .param("grant_type", "client_credentials")
                    .param("scope", "read")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andReturn();

    var tokenResponse =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    var accessToken = (String) tokenResponse.get("access_token");
    var expiresIn = (Integer) tokenResponse.get("expires_in");

    var jwt = JWTParser.parse(accessToken);
    var claims = jwt.getJWTClaimsSet();

    assertThat(expiresIn).isEqualTo(3599L);

    long tokenLifetimeSeconds =
        claims.getExpirationTime().toInstant().getEpochSecond()
            - claims.getIssueTime().toInstant().getEpochSecond();
    assertThat(tokenLifetimeSeconds).isEqualTo(3600L);
  }
}
