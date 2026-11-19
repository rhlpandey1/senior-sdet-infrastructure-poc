package com.example.api.ciam;

import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class KeycloakAuthenticationTest {

    private static final String KEYCLOAK_URL =
            System.getenv().getOrDefault("KEYCLOAK_URL", "http://localhost:8080");

    private static final String REALM = "ciam-demo";
    private static final String CLIENT_ID = "ciam-demo-client";

    private static final String USERNAME =
            System.getenv().getOrDefault("CIAM_USERNAME", "rahul");

    private static final String PASSWORD =
            System.getenv().getOrDefault("CIAM_PASSWORD", "");

    private static final String TOKEN_ENDPOINT =
            KEYCLOAK_URL + "/realms/" + REALM
                    + "/protocol/openid-connect/token";

    private static final String USERINFO_ENDPOINT =
            KEYCLOAK_URL + "/realms/" + REALM
                    + "/protocol/openid-connect/userinfo";

    @Test
    public void shouldAuthenticateUserAndAccessUserInfo() {

        if (PASSWORD.isBlank()) {
            throw new IllegalStateException(
                    "Set the CIAM_PASSWORD environment variable before running tests.");
        }

        // Step 1: Obtain OAuth2/OIDC tokens
        Response tokenResponse =
                given()
                        .contentType("application/x-www-form-urlencoded")
                        .formParam("grant_type", "password")
                        .formParam("client_id", CLIENT_ID)
                        .formParam("username", USERNAME)
                        .formParam("password", PASSWORD)
                        .formParam("scope", "openid profile email")
                .when()
                        .post(TOKEN_ENDPOINT);
System.out.println("Token HTTP Status: "
        + tokenResponse.getStatusCode());

System.out.println("Token Error Response: "
        + tokenResponse.asString());

        tokenResponse.then()
                .statusCode(200)
                .body("access_token", not(isEmptyOrNullString()))
                .body("token_type", equalToIgnoringCase("Bearer"))
                .body("expires_in", greaterThan(0));

        String accessToken = tokenResponse.jsonPath()
                .getString("access_token");

        // Step 2: Use access token to access protected UserInfo endpoint
        given()
                .header("Authorization", "Bearer " + accessToken)
                .accept("application/json")
        .when()
                .get(USERINFO_ENDPOINT)
        .then()
                .statusCode(200)
                .body("preferred_username", equalTo(USERNAME))
                .body("email", equalTo("rahul@example.org"));
    }

    @Test
    public void shouldRejectInvalidUserCredentials() {

        Response response =
                given()
                        .contentType("application/x-www-form-urlencoded")
                        .formParam("grant_type", "password")
                        .formParam("client_id", CLIENT_ID)
                        .formParam("username", USERNAME)
                        .formParam("password", "WrongPassword123!")
                        .formParam("scope", "openid")
                .when()
                        .post(TOKEN_ENDPOINT);

        response.then()
                .statusCode(401)
                .body("error", equalTo("invalid_grant"));
    }

    @Test
    public void shouldRejectUserInfoRequestWithoutAccessToken() {

        given()
                .accept("application/json")
        .when()
                .get(USERINFO_ENDPOINT)
        .then()
                .statusCode(401);
    }
}
