package com.example.api.ciam;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

public class JwtValidationTest {

    private static final String BASE_URL =
            System.getenv().getOrDefault("KEYCLOAK_URL", "http://localhost:8080");

    private static final String REALM = "ciam-demo";
    private static final String ISSUER = BASE_URL + "/realms/" + REALM;
    private static final String CLIENT_ID = "ciam-demo-client";

    @Test
    public void shouldValidateAccessTokenSignatureAndClaims() throws Exception {

        String username = System.getenv().getOrDefault("CIAM_USERNAME", "rahul");
        String password = System.getenv("CIAM_PASSWORD");

        assertNotNull(password, "CIAM_PASSWORD environment variable is required");

        // 1. Request an access token
        Response tokenResponse =
                given()
                        .contentType("application/x-www-form-urlencoded")
                        .formParam("grant_type", "password")
                        .formParam("client_id", CLIENT_ID)
                        .formParam("username", username)
                        .formParam("password", password)
                        .formParam("scope", "openid profile email")
                .when()
                        .post(ISSUER + "/protocol/openid-connect/token");

        tokenResponse.then().statusCode(200);

        String accessToken = tokenResponse.jsonPath().getString("access_token");

        // 2. Parse the JWT
        SignedJWT signedJWT = SignedJWT.parse(accessToken);
        String keyId = signedJWT.getHeader().getKeyID();

        assertNotNull(keyId, "JWT must contain a key ID (kid)");

        // 3. Fetch Keycloak's public signing keys
        String discoveryUrl = ISSUER + "/.well-known/openid-configuration";

        String jwksUri = given()
                .when()
                .get(discoveryUrl)
                .then()
                .statusCode(200)
                .extract()
                .path("jwks_uri");

        JWKSet jwkSet = JWKSet.load(new URL(jwksUri));
        JWK jwk = jwkSet.getKeyByKeyId(keyId);

        assertNotNull(jwk, "Matching signing key must exist in JWKS");

        RSAKey rsaKey = (RSAKey) jwk;

        // 4. Verify the JWT signature using the public key
        JWSVerifier verifier =
                new RSASSAVerifier(rsaKey.toRSAPublicKey());

        assertTrue(signedJWT.verify(verifier),
                "JWT signature validation failed");

        // 5. Validate important claims
        var claims = signedJWT.getJWTClaimsSet();

        assertEquals(claims.getIssuer(), ISSUER, "Invalid issuer");
        assertNotNull(claims.getSubject(), "Subject must be present");
        assertTrue(claims.getExpirationTime().after(new Date()),
                "JWT has expired");

        System.out.println("JWT signature: VALID");
        System.out.println("JWT issuer: " + claims.getIssuer());
        System.out.println("JWT subject: " + claims.getSubject());
        System.out.println("JWT expiry: " + claims.getExpirationTime());
    }
}
