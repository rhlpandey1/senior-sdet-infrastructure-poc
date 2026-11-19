# CIAM Interview Preparation Guide

## Lead Automation Quality Engineer / Senior SDET

**Companion document for:**
`senior-sdet-infrastructure-poc/automation/api-tests/`

**Technology stack:** Java 21, Maven, Rest Assured, TestNG, Keycloak,
Nimbus JOSE + JWT.

------------------------------------------------------------------------

## 1. POC summary

This POC demonstrates a small Customer Identity and Access Management
(CIAM) test integration using Keycloak as the Identity Provider (IdP)
and the existing Java API automation framework.

### Implemented and executed

-   Created the `ciam-demo` Keycloak realm.
-   Created the public OIDC client `ciam-demo-client`.
-   Created a local Keycloak user named `rahul`.
-   Queried the OpenID Connect discovery document.
-   Obtained an access token from the token endpoint using the Resource
    Owner Password Credentials grant (password grant) for this local
    demonstration.
-   Called the OIDC UserInfo endpoint using the access token as a Bearer
    token.
-   Automated positive and negative authentication scenarios using Rest
    Assured and TestNG.
-   Validated an access token's JWT signature using the public key
    published through Keycloak's JWKS endpoint.
-   Checked the token issuer, subject and expiry.

### Covered conceptually, but not implemented as part of this POC

-   Authorization Code Flow with PKCE.
-   Client Credentials Flow.
-   Refresh-token automation.
-   LDAP / Active Directory federation.
-   Role and scope enforcement against a protected business API.
-   MFA workflows.
-   Token revocation tests.
-   Signing-key rotation tests.

Be precise about this distinction in interviews. Do not claim that LDAP
federation or PKCE was implemented.

------------------------------------------------------------------------


## 1A. Rebuild the POC from a fresh environment

This section records the end-to-end local setup so the Keycloak environment and test data can be recreated later.

### Prerequisites

- Docker Engine running in Ubuntu/WSL (Docker Desktop is not required).
- `curl` available in the terminal.
- Java 21 and Maven installed.
- Port `8080` available on the host.
- The repository cloned locally.

Check Docker and Java:

```bash
docker version
docker compose version
java -version
mvn -version
```

If Docker is not running in Ubuntu/WSL, start the Docker service using the service manager configured in that environment, then confirm `docker ps` works.

### Step 1: Start Keycloak

The POC used Keycloak `26.3.3` in development mode. From the Ubuntu/WSL terminal:

```bash
docker run -d \
  --name ciam-keycloak \
  -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD='ChangeThisAdminPassword!' \
  -v ciam_keycloak_data:/opt/keycloak/data \
  quay.io/keycloak/keycloak:26.3.3 \
  start-dev
```

Use your own local admin password. The command above adds a named Docker volume so the Keycloak development database survives container removal/recreation. The original short-lived POC container was started without a named volume; adding the volume is a reproducibility improvement.

Check that the container is running:

```bash
docker ps --filter name=ciam-keycloak
docker logs --tail 50 ciam-keycloak
```

Wait until Keycloak has completed startup. Then open:

```text
http://localhost:8080/admin/
```

Sign in with the bootstrap administrator username `admin` and the admin password supplied in the Docker command.

The realm-specific Admin Console URL used during this POC is:

```text
http://localhost:8080/admin/master/console/#/ciam-demo
```

If the console does not load, first check `docker ps`, `docker logs ciam-keycloak`, and whether another process is using port 8080.

### Step 2: Create the `ciam-demo` realm

1. Open the Keycloak Admin Console and sign in as the administrator.
2. Open the realm selector in the top-left corner.
3. Choose **Create realm**.
4. Set the realm name to `ciam-demo`.
5. Keep the realm enabled and create it.
6. Confirm the console is now operating in the `ciam-demo` realm.

Realm-specific issuer URL:

```text
http://localhost:8080/realms/ciam-demo
```

### Step 3: Create the OIDC client

Within the `ciam-demo` realm:

1. Open **Clients**.
2. Select **Create client**.
3. Choose **OpenID Connect** as the client type/protocol if prompted.
4. Set **Client ID** to `ciam-demo-client`.
5. Save/create the client.
6. In the client settings, configure:
   - **Client authentication:** OFF (public client).
   - **Standard flow:** ON.
   - **Direct access grants:** ON (needed only because this POC's automated token test uses the password grant).
7. Save the changes.

Important:
- The public client has no client secret. Do not add a client secret to the test request.
- Direct Access Grants is enabled only to support this local demonstration. The password grant is not recommended for new production applications.
- Standard Flow is enabled so the client is also configured for the Authorization Code flow, although the browser-based PKCE flow was not implemented in this POC.
- Do not enable implicit flow for this POC.

### Step 4: Create the test user

Within the `ciam-demo` realm:

1. Open **Users**.
2. Select **Create new user**.
3. Enter:
   - **Username:** `rahul`
   - **Email:** `rahul@example.org`
   - **Email verified:** ON (optional for the demonstrated tests; useful for consistent local test data).
   - **Enabled:** ON.
4. Save/create the user.
5. Open the user's **Credentials** tab.
6. Choose **Set password** / **Reset password**.
7. Set a local test password of your choice.
8. Set **Temporary:** OFF.
9. Save/confirm the password.

The POC assertions expect the username `rahul` and email `rahul@example.org`. If you change either value, update the test configuration/assertions accordingly.

Never commit the test password. Do not paste it into source control, screenshots, logs or this guide.

### Step 5: Verify OIDC discovery

From the Ubuntu/WSL terminal:

```bash
curl -sS \
  http://localhost:8080/realms/ciam-demo/.well-known/openid-configuration
```

To check that the endpoint responds successfully and print selected metadata:

```bash
curl -sS \
  http://localhost:8080/realms/ciam-demo/.well-known/openid-configuration \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print("issuer:", d["issuer"]); print("token_endpoint:", d["token_endpoint"]); print("userinfo_endpoint:", d["userinfo_endpoint"]); print("jwks_uri:", d["jwks_uri"])'
```

Expected issuer:

```text
http://localhost:8080/realms/ciam-demo
```

The discovery document is the source of truth for endpoint URLs. The JWT test reads `jwks_uri` from this document rather than hardcoding the JWKS path.

### Step 6: Verify token generation manually (optional smoke test)

Set the local test credentials in the same terminal session:

```bash
export KEYCLOAK_URL=http://localhost:8080
export CIAM_USERNAME=rahul
read -s -p "Enter the Keycloak password for rahul: " CIAM_PASSWORD
export CIAM_PASSWORD
echo
```

Request a token:

```bash
curl -sS -X POST \
  "$KEYCLOAK_URL/realms/ciam-demo/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=ciam-demo-client" \
  --data-urlencode "username=$CIAM_USERNAME" \
  --data-urlencode "password=$CIAM_PASSWORD" \
  --data-urlencode "scope=openid profile email"
```

A successful response contains fields such as `access_token`, `expires_in` and `token_type`. Treat the response as secret material: do not save it in a file, paste it into chat, or commit it.

If the response is `invalid_grant`, check that the username/password are correct, the user is enabled, the password is not temporary, and Direct Access Grants is enabled for the client.

### Step 7: Configure the Java automation project

Go to the API automation module:

```bash
cd ~/senior-sdet-infrastructure-poc/automation/api-tests
```

The module uses Java 21, Maven, Rest Assured and TestNG. The POC also adds Nimbus JOSE + JWT for JWT signature verification. Confirm that `pom.xml` includes the dependency:

```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>10.10</version>
</dependency>
```

Set environment variables. The password is entered without echoing it to the terminal:

```bash
export KEYCLOAK_URL=http://localhost:8080
export CIAM_USERNAME=rahul
read -s -p "Enter the Keycloak password for rahul: " CIAM_PASSWORD
export CIAM_PASSWORD
echo
```

The test class defaults to `http://localhost:8080`, realm `ciam-demo` and client `ciam-demo-client`; `KEYCLOAK_URL` and `CIAM_USERNAME` can override the first two configurable values. The password is read from `CIAM_PASSWORD`.

Run the CIAM authentication tests:

```bash
mvn -Dtest=KeycloakAuthenticationTest \
    -Dsurefire.suiteXmlFiles= \
    test
```

Run the JWT validation test:

```bash
mvn -Dtest=JwtValidationTest \
    -Dsurefire.suiteXmlFiles= \
    test
```

Run the complete API automation suite:

```bash
mvn test
```

The module's `testng.xml` includes the existing Order API tests and the CIAM authentication test class. If running a single class, the Surefire override above avoids forcing the full suite XML. The JWT class is selected by `-Dtest=JwtValidationTest`.

### Step 8: Test data created for this POC

| Item | Value |
|---|---|
| Keycloak image | `quay.io/keycloak/keycloak:26.3.3` |
| Container name | `ciam-keycloak` |
| Local Keycloak URL | `http://localhost:8080` |
| Admin username | `admin` |
| Admin password | Chosen locally at container startup; do not commit |
| Realm | `ciam-demo` |
| Realm issuer | `http://localhost:8080/realms/ciam-demo` |
| Client ID | `ciam-demo-client` |
| Client type | Public (Client authentication OFF) |
| Standard Flow | ON |
| Direct Access Grants | ON for this local password-grant demo |
| Test username | `rahul` |
| Test email | `rahul@example.org` |
| Test password | Set manually in the Credentials tab; do not commit |
| Requested scopes | `openid profile email` |

The `rahul` user is a local Keycloak user in this POC. It is not sourced from LDAP or Active Directory.

### Step 9: Stop, restart and reset Keycloak

Stop the container while preserving its data:

```bash
docker stop ciam-keycloak
```

Start it again:

```bash
docker start ciam-keycloak
```

Follow startup logs:

```bash
docker logs -f ciam-keycloak
```

To remove the container and retain the named volume:

```bash
docker rm -f ciam-keycloak
```

Then recreate it using the same `docker run` command and the same volume name `ciam_keycloak_data`. The realm, client and user data should remain in that volume.

To completely reset the local Keycloak data (destructive; this deletes the realm, client and test user):

```bash
docker rm -f ciam-keycloak
docker volume rm ciam_keycloak_data
```

Then repeat Steps 1–8.

### Step 10: Common setup problems

- **Cannot open `localhost:8080`:** check `docker ps`, `docker logs ciam-keycloak`, Docker Engine status and port conflicts.
- **Container name already exists:** run `docker start ciam-keycloak`, or remove the old container before recreating it.
- **Port 8080 already allocated:** stop the process/container using it, or choose another host port and update `KEYCLOAK_URL`.
- **Admin login fails:** use the bootstrap admin username/password supplied when the container was first initialized. Changing environment variables on an existing container does not reset an already-created admin account.
- **`invalid_grant`:** verify the `rahul` password, enabled state, temporary-password setting and Direct Access Grants client setting.
- **`invalid_client`:** verify the client ID and that Client authentication is OFF for this public-client test.
- **UserInfo returns 401:** ensure the request uses the access token (not the ID token), with `Authorization: Bearer <access_token>`, and check token expiry.
- **JWT key ID not found:** retrieve the current discovery document/JWKS and verify the token's `iss` and `kid`; refresh stale JWKS data.
- **Maven dependency resolution fails:** verify network access and the dependency coordinates/version in `pom.xml`.

---

## 2. Architecture and request flow

``` text
Java 21 + Rest Assured + TestNG
              |
              | POST token request
              v
       Keycloak (IdP)
       Realm: ciam-demo
       Client: ciam-demo-client
              |
              | access_token (JWT)
              v
       OIDC UserInfo endpoint
              |
              | Authorization: Bearer <access_token>
              v
       User identity claims
```

For JWT validation, the test reads the OIDC discovery document, obtains
`jwks_uri`, downloads the JWKS, selects the public key matching the JWT
`kid`, and verifies the signature. It then checks selected claims.

### Important endpoints

Base URL used in the local POC: `http://localhost:8080`

``` text
Discovery:
GET /realms/ciam-demo/.well-known/openid-configuration

Token:
POST /realms/ciam-demo/protocol/openid-connect/token

UserInfo:
GET /realms/ciam-demo/protocol/openid-connect/userinfo

JWKS:
Read the jwks_uri value from the discovery document.
```

------------------------------------------------------------------------

## 3. Core concepts

### CIAM

Customer Identity and Access Management is the set of capabilities and
processes used to manage customer identities and control their access to
applications and services. Typical capabilities include registration,
login, authentication, authorization, consent, profile management,
federation, MFA and account recovery.

### IAM

Identity and Access Management is the broader discipline of managing
digital identities and controlling access to resources. CIAM focuses on
external customers; workforce IAM typically focuses on employees,
contractors and internal users.

### Authentication vs authorization

-   **Authentication:** establishes who the user or client is.
-   **Authorization:** determines what that authenticated identity is
    allowed to do.

A successful login does not automatically mean the user is authorized to
perform every action.

### OAuth 2.0

OAuth 2.0 is an authorization framework that allows a client to obtain
limited access to a protected resource using access tokens. OAuth 2.0 by
itself is not an authentication protocol.

### OpenID Connect

OpenID Connect (OIDC) is an identity layer built on OAuth 2.0. It
introduces the ID token and standardized identity-related endpoints and
claims.

### Keycloak

Keycloak is an open-source identity and access management solution. In
this POC it acts as the Identity Provider and authorization server. It
supports realms, clients, users, roles, authentication flows, OIDC,
OAuth 2.0 and identity brokering/federation.

### Realm

A realm is an isolated namespace in Keycloak containing users, clients,
roles, groups and authentication configuration. Our demo realm is
`ciam-demo`.

### Client

A client represents an application or service that interacts with
Keycloak. Our demo client is `ciam-demo-client`. It was configured as a
public client for the local test.

### Public vs confidential client

-   **Public client:** cannot safely keep a client secret, as with
    browser-based or native apps.
-   **Confidential client:** can protect credentials, typically a
    backend service.

A public client should use an appropriate flow such as Authorization
Code with PKCE. Client Credentials requires a confidential
client/service account configuration.

------------------------------------------------------------------------

## 4. OAuth 2.0 and OIDC flows

### 4.1 Authorization Code Flow with PKCE

This is the preferred flow for interactive user authentication in modern
public clients and is also widely used by web applications.

1.  The client creates a high-entropy `code_verifier`.
2.  It derives a `code_challenge`, normally using SHA-256 (`S256`).
3.  The browser is redirected to the authorization endpoint with the
    challenge.
4.  The user authenticates at the Identity Provider.
5.  The authorization server redirects back with a short-lived
    authorization code.
6.  The client sends the code and original verifier to the token
    endpoint.
7.  The server checks the verifier against the original challenge and
    issues tokens.

PKCE means Proof Key for Code Exchange. It helps prevent an intercepted
authorization code from being redeemed by an attacker who does not
possess the verifier.

### 4.2 Client Credentials Flow

Used for machine-to-machine communication when a service needs to call
another service without a human user participating.

The client authenticates to the token endpoint using its client
credentials and receives an access token. In Keycloak, this normally
involves a confidential client and an enabled service account.

### 4.3 Refresh Token Flow

A client can exchange a valid refresh token for a new access token,
subject to server policy, token lifetime and rotation rules. Refresh
tokens are sensitive credentials and must be stored securely.

### 4.4 Password Grant / Resource Owner Password Credentials

The POC uses this grant only to keep the local API automation
demonstration small. The client submits the user's username and password
directly to the token endpoint.

This is not recommended for new production applications. Prefer
Authorization Code with PKCE for interactive user authentication. Do not
describe the password grant as the preferred modern flow.

### 4.5 OAuth 2.0 vs OIDC

  -----------------------------------------------------------------------
  OAuth 2.0                           OpenID Connect
  ----------------------------------- -----------------------------------
  Authorization framework             Identity layer built on OAuth 2.0

  Access token used to access         Adds ID token and identity claims
  protected resources                 

  Does not define standardized user   Defines standardized authentication
  authentication by itself            and identity information

  Scopes are authorization-oriented   Includes scopes such as `openid`,
                                      `profile`, `email`
  -----------------------------------------------------------------------

### 4.6 Access token vs ID token vs refresh token

-   **Access token:** presented to a resource server to access protected
    resources.
-   **ID token:** communicates authentication/identity information to
    the OIDC client.
-   **Refresh token:** used at the authorization server to obtain new
    tokens, subject to policy.

Do not use an ID token as a substitute for an access token when calling
an API.

------------------------------------------------------------------------

## 5. JWT and JWKS

### JWT structure

A JSON Web Token (JWT) commonly has three Base64URL-encoded parts:

``` text
header.payload.signature
```

-   **Header:** token type and signing algorithm, often including `kid`.
-   **Payload:** claims such as `iss`, `sub`, `aud`, `exp`, `iat`,
    `scope` or roles.
-   **Signature:** cryptographic signature over the encoded header and
    payload.

Base64URL encoding is not encryption. A signed JWT's payload can
generally be decoded by its holder. Never put secrets in ordinary JWT
claims.

### Common claims

  Claim     Meaning
  --------- ---------------------------------------------------
  `iss`     Issuer
  `sub`     Subject identifier
  `aud`     Intended audience
  `exp`     Expiration time
  `iat`     Issued-at time
  `nbf`     Not-before time
  `jti`     JWT identifier
  `azp`     Authorized party (commonly used in OIDC contexts)
  `scope`   Granted scopes, when represented in the token

### JWKS

JSON Web Key Set (JWKS) is a JSON document containing public keys used
by clients/resource servers to verify signatures. The OIDC discovery
document publishes the `jwks_uri`.

The JWT header's `kid` (Key ID) identifies which published key should be
used. A robust validator must handle key rotation and refresh its key
set when an unfamiliar `kid` appears.

### JWT validation checklist

1.  Parse the token safely.
2.  Read the signing algorithm and `kid` from the header.
3.  Reject unexpected or disallowed algorithms.
4.  Obtain trusted keys from the configured issuer's JWKS endpoint.
5.  Select the key matching `kid`.
6.  Verify the cryptographic signature.
7.  Validate `iss`.
8.  Validate `aud` for the API/client's expected audience.
9.  Validate `exp` and `nbf`, allowing only a small justified clock-skew
    tolerance.
10. Apply application-specific checks for scopes, roles and other
    required claims.

Signature verification alone is not sufficient. A validly signed token
can still have the wrong issuer, audience or expiry.

### POC JWT code pattern

The implemented test uses Nimbus JOSE + JWT. The central verification
pattern is:

``` java
SignedJWT signedJWT = SignedJWT.parse(accessToken);

String keyId = signedJWT.getHeader().getKeyID();

JWKSet jwkSet = JWKSet.load(new URL(jwksUri));
JWK jwk = jwkSet.getKeyByKeyId(keyId);

RSAKey rsaKey = (RSAKey) jwk;
JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());

assertTrue(signedJWT.verify(verifier));

var claims = signedJWT.getJWTClaimsSet();
assertEquals(claims.getIssuer(), expectedIssuer);
assertNotNull(claims.getSubject());
assertTrue(claims.getExpirationTime().after(new Date()));
```

For a production-grade validator, also enforce expected algorithm,
audience, not-before, key type and issuer trust configuration. The POC's
sample checks should not be represented as a complete production
token-validation library.

------------------------------------------------------------------------

## 6. LDAP and federation

### LDAP

Lightweight Directory Access Protocol (LDAP) is a protocol for accessing
and managing directory information. Directories commonly store user and
group records and attributes.

### LDAP directory example

``` text
dc=company,dc=com
  |
  +-- ou=employees
  |     |
  |     +-- uid=rahul
  |     +-- uid=alex
  |
  +-- ou=groups
        |
        +-- cn=engineering
        +-- cn=qa
```

Example Distinguished Name:

``` text
uid=rahul,ou=employees,dc=company,dc=com
```

### LDAP terms

-   **DN --- Distinguished Name:** identifies an entry by its full path
    in the directory.
-   **RDN --- Relative Distinguished Name:** the component that
    identifies an entry relative to its parent.
-   **CN --- Common Name:** a common naming attribute.
-   **OU --- Organizational Unit:** groups entries within the directory
    tree.
-   **DC --- Domain Component:** a component of the directory domain
    name.
-   **UID --- User Identifier:** commonly used as a user login
    identifier.
-   **Bind:** LDAP operation used to authenticate to the directory.
-   **Search:** operation used to find directory entries based on a base
    DN and filter.
-   **Attribute:** a property stored on an LDAP entry.

### LDAP vs Active Directory

LDAP is a protocol. Microsoft Active Directory is a directory service
that supports LDAP among other protocols and services. They are related
but not interchangeable terms.

### Keycloak LDAP federation

Keycloak can connect to an external LDAP directory or Active Directory
as a user federation provider. Administrators configure connection
details, bind credentials, search base, user attribute mappings and
synchronization settings.

A typical flow:

1.  Keycloak is configured to connect to the directory.
2.  User records can be queried and, depending on configuration,
    synchronized.
3.  A user attempts to authenticate through Keycloak.
4.  Keycloak uses the configured federation provider to validate
    credentials and retrieve user information.
5.  On successful authentication, Keycloak issues tokens according to
    the configured OIDC/OAuth flow.

The exact behaviour depends on federation mode and configuration. Do not
claim that all LDAP passwords are copied into Keycloak.

**POC boundary:** no LDAP server or Keycloak LDAP federation provider
was configured in this project. This section is conceptual interview
preparation.

------------------------------------------------------------------------

## 7. HTTP status codes and security behaviour

  Status   Meaning                 Example in identity/API testing
  -------- ----------------------- --------------------------------------------
  200      OK                      Successful token or UserInfo response
  400      Bad Request             Malformed request or unsupported parameter
  401      Unauthorized            Missing or invalid authentication
  403      Forbidden               Authenticated identity lacks permission
  404      Not Found               Resource or endpoint not found
  429      Too Many Requests       Rate limiting
  500      Internal Server Error   Server-side failure
  503      Service Unavailable     Identity provider or service unavailable

A useful distinction:

-   **401:** authentication is absent or invalid.
-   **403:** authentication succeeded, but authorization is
    insufficient.

OAuth token endpoint errors may use OAuth-specific JSON error values
such as `invalid_grant`, `invalid_client`, `invalid_scope` or
`unsupported_grant_type`. The HTTP status and response body should both
be asserted according to the provider's documented behaviour.

------------------------------------------------------------------------

## 8. Interview questions and answers

### Fundamentals

**Q1. What is CIAM?**

CIAM stands for Customer Identity and Access Management. It manages
customer identities and their access to digital products and services.
It commonly includes registration, authentication, authorization,
profile management, consent, MFA, federation and account recovery.

**Q2. How is CIAM different from workforce IAM?**

CIAM is designed for external customers and often emphasizes scale,
customer experience, self-service registration, consent and privacy.
Workforce IAM manages employee and contractor access to internal
enterprise resources and is often integrated with HR and corporate
directories.

**Q3. What is the difference between authentication and authorization?**

Authentication proves an identity. Authorization determines what that
identity can access or do.

**Q4. What is an Identity Provider?**

An Identity Provider (IdP) authenticates users and provides identity
assertions or tokens to relying applications. Keycloak plays this role
in our POC.

**Q5. What is OAuth 2.0?**

OAuth 2.0 is an authorization framework that allows a client to obtain
limited access to protected resources using tokens. It does not, by
itself, standardize user authentication.

**Q6. What is OIDC?**

OpenID Connect is an identity layer on top of OAuth 2.0. It defines an
ID token and standardized identity information and endpoints.

**Q7. What is the difference between OAuth 2.0 and OIDC?**

OAuth 2.0 addresses delegated authorization. OIDC adds standardized
authentication and identity claims, including the ID token.

**Q8. What is a realm in Keycloak?**

A realm is an isolated namespace containing users, clients, roles,
groups and authentication configuration.

**Q9. What is a client in Keycloak?**

A client represents an application or service that requests
authentication or tokens from Keycloak.

**Q10. What is the difference between public and confidential clients?**

A public client cannot securely protect a secret, as with a browser or
native app. A confidential client can keep credentials in a protected
backend environment.

### OAuth2 and OIDC

**Q11. Explain Authorization Code Flow.**

The client redirects the user to the authorization server. After
authentication, the server redirects back with an authorization code.
The client exchanges the code for tokens.

**Q12. What is PKCE?**

Proof Key for Code Exchange. It binds the authorization request to the
token exchange using a verifier and challenge, reducing the risk of
authorization-code interception.

**Q13. Why use S256 for PKCE?**

S256 derives the challenge by applying SHA-256 to the verifier and
Base64URL-encoding the result. It avoids sending the original verifier
in the authorization request.

**Q14. What is Client Credentials Flow?**

A machine client authenticates with its own credentials to obtain an
access token for service-to-service access. There is no end-user login
in the flow.

**Q15. What is a refresh token?**

A credential used to request a new access token from the authorization
server. It must be protected because theft can allow an attacker to
obtain new access tokens.

**Q16. What is the password grant?**

The client directly submits the user's username and password to the
token endpoint. It is used in this local POC for simplicity, but is not
recommended for new production applications.

**Q17. What is the difference between an access token and an ID token?**

An access token is intended for accessing a resource server. An ID token
communicates authentication and identity information to the OIDC client.

**Q18. What is the UserInfo endpoint?**

An OIDC endpoint that returns claims about the authenticated user when
called with a valid access token and appropriate scopes.

**Q19. What is OIDC discovery?**

A standard metadata document, commonly at
`/.well-known/openid-configuration`, that publishes endpoint URLs and
supported capabilities for an issuer.

**Q20. What is the `openid` scope?**

It signals an OIDC authentication request. Without it, the request is an
OAuth request rather than an OIDC authentication request.

### JWT and security

**Q21. What is JWT?**

JSON Web Token. It is a compact token format that carries claims and can
be signed and/or encrypted. Common signed JWTs have header, payload and
signature parts.

**Q22. Is a JWT encrypted?**

Not necessarily. A signed JWT is generally readable by anyone holding
it. Signing protects integrity and authenticity, not confidentiality.

**Q23. What is JWKS?**

JSON Web Key Set. It is a set of cryptographic keys, commonly public
keys, published so consumers can verify token signatures.

**Q24. What is `kid`?**

Key ID. It identifies the signing key used for a JWT so the validator
can select the matching key from the JWKS.

**Q25. What is the `iss` claim?**

Issuer. It identifies the authority that issued the token and should
match the trusted issuer configured by the application.

**Q26. What is the `aud` claim?**

Audience. It identifies the intended recipient(s) of the token. A
resource server should verify that it is an intended audience.

**Q27. What is the `exp` claim?**

Expiration time. The token must not be accepted after this time,
allowing only a small justified clock-skew tolerance.

**Q28. Is decoding a JWT enough to trust it?**

No. Decoding only reads the contents. A validator must verify the
signature and validate issuer, audience, expiry and other required
claims.

**Q29. How do you validate a JWT?**

Parse it, enforce an allowed algorithm, select the trusted public key by
`kid` from JWKS, verify the signature, and validate issuer, audience,
expiry and not-before claims.

**Q30. What happens when a JWT expires?**

A protected resource should reject it. The client may obtain a new
access token using a valid refresh token or repeat the appropriate
authorization flow.

**Q31. How would you test signing-key rotation?**

Obtain a token, validate using the current JWKS key, rotate the key,
retrieve the updated JWKS, validate a newly issued token using the new
`kid`, and verify expected behaviour for older tokens during the overlap
period.

**Q32. What is token revocation?**

A mechanism by which a token or refresh-token grant is invalidated
according to the authorization server's implementation. Self-contained
access tokens may continue to validate locally until expiry unless the
resource server uses additional revocation/introspection controls.

**Q33. What is token introspection?**

An endpoint through which an authorized client can ask an authorization
server about a token's active state and metadata. It is commonly
associated with opaque tokens, but can also be used in other
deployments.

**Q34. What is the difference between 401 and 403?**

401 indicates missing or invalid authentication. 403 indicates the
caller is authenticated but lacks sufficient authorization.

### LDAP and federation

**Q35. What is LDAP?**

Lightweight Directory Access Protocol, used to access and manage
directory information.

**Q36. What is Active Directory?**

A Microsoft directory service used to manage users, computers, groups
and policies. It supports LDAP, but is not itself the LDAP protocol.

**Q37. What is a DN?**

Distinguished Name. It identifies an entry in an LDAP directory, for
example `uid=rahul,ou=employees,dc=company,dc=com`.

**Q38. What is an OU?**

Organizational Unit. It is a container used to organize directory
entries.

**Q39. What is LDAP Bind?**

An LDAP operation used to authenticate to a directory.

**Q40. What is LDAP federation in Keycloak?**

It allows Keycloak to connect to an external LDAP directory or Active
Directory as a user federation provider, mapping user attributes and
using configured directory integration for user lookup and
authentication.

**Q41. Does LDAP federation mean all users and passwords are copied into
Keycloak?**

No. User lookup, synchronization and credential validation depend on the
configured federation provider and settings. Do not assume all
credentials are copied.

### Senior SDET and automation

**Q42. How would you design a CIAM automation framework?**

Separate identity-provider clients, token acquisition, token validation
and protected-resource API tests. Externalize environment and
credentials, use reusable request specifications, avoid logging secrets,
and include positive, negative, authorization, expiry and resilience
tests. Run deterministic tests in CI with isolated test identities.

**Q43. What would you test in a login API?**

Valid and invalid credentials, locked/disabled accounts, required MFA,
rate limiting, error handling, session/token issuance, audit events,
account recovery and security controls.

**Q44. How would you test role-based access control?**

Create users with different roles, obtain tokens, call protected
endpoints and verify allowed operations succeed while disallowed
operations return the expected authorization failure. Also verify role
changes and token-refresh/session behaviour.

**Q45. How would you test scope enforcement?**

Request tokens with different scopes and call endpoints requiring
specific scopes. Verify the permitted scope succeeds and
missing/insufficient scopes are rejected.

**Q46. How would you test an expired token?**

Use a token with a short configured lifetime or a controlled test
fixture, wait until expiry or validate expiry logic deterministically,
then assert that the protected API rejects it. Avoid brittle long sleeps
in automated suites.

**Q47. How would you test a tampered JWT?**

Modify a signed token payload without re-signing it and verify that
signature validation fails. Never rely on decoded claims alone.

**Q48. How would you test MFA?**

Cover enrollment, challenge delivery, valid and invalid OTP, expired
OTP, replay attempts, recovery codes, lost-device recovery, rate limits
and step-up authentication where applicable.

**Q49. How would you test brute-force protection?**

Use controlled test accounts and bounded attempts. Verify throttling,
temporary lockout or other configured controls, response consistency,
audit logging and recovery. Avoid creating load against real customer
accounts.

**Q50. How would you test identity-provider availability failures?**

Simulate connection timeouts, 5xx responses and unavailable
discovery/JWKS endpoints in a controlled environment. Verify bounded
retries, sensible timeouts, safe error handling and that the application
fails securely without leaking credentials or tokens.

**Q51. How do you avoid leaking secrets in automation logs?**

Do not log passwords, client secrets, access tokens, refresh tokens or
authorization headers. Mask CI variables, disable request/response body
logging for sensitive calls and ensure failure diagnostics print only
safe metadata.

**Q52. What would you monitor in CIAM?**

Authentication success/failure rates, token endpoint latency, MFA
completion, lockouts, suspicious attempts, identity-provider
availability, federation errors, token-validation failures and relevant
audit events. Monitoring must avoid exposing sensitive identity data.

**Q53. How would you handle flaky identity tests?**

Use isolated test users and realms where practical, deterministic
fixtures, explicit readiness checks, bounded polling, controlled token
lifetimes, stable test data and clear separation of environment failures
from product defects. Avoid arbitrary sleeps and indiscriminate retries.

**Q54. How would you test logout?**

Test the application's local session termination and, where supported,
OIDC end-session behaviour. Verify refresh-token/session invalidation
according to provider configuration. Do not assume every already-issued
self-contained access token becomes instantly invalid.

**Q55. What are the main CIAM security risks?**

Credential stuffing, brute force, phishing, account takeover, token
theft, weak redirect URI validation, incorrect audience/scope checks,
insecure token storage, misconfigured federation, sensitive-data leakage
and insufficient rate limiting.

------------------------------------------------------------------------

## 9. Rest Assured code snippets

These are representative patterns based on the POC. Keep credentials in
environment variables and never commit real secrets.

### Obtain an access token

``` java
Response tokenResponse =
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "ciam-demo-client")
        .formParam("username", username)
        .formParam("password", password)
        .formParam("scope", "openid profile email")
    .when()
        .post(tokenEndpoint);

tokenResponse.then()
    .statusCode(200)
    .body("access_token", not(isEmptyOrNullString()))
    .body("token_type", equalToIgnoringCase("Bearer"));
```

This demonstrates the password grant used in the local POC, not a
recommended production flow.

### Call UserInfo with a Bearer token

``` java
given()
    .header("Authorization", "Bearer " + accessToken)
    .accept("application/json")
.when()
    .get(userInfoEndpoint)
.then()
    .statusCode(200)
    .body("preferred_username", equalTo(username));
```

### Negative test: missing access token

``` java
given()
    .accept("application/json")
.when()
    .get(userInfoEndpoint)
.then()
    .statusCode(401);
```

### Negative test: invalid credentials

``` java
given()
    .contentType("application/x-www-form-urlencoded")
    .formParam("grant_type", "password")
    .formParam("client_id", "ciam-demo-client")
    .formParam("username", username)
    .formParam("password", "WrongPassword123!")
.when()
    .post(tokenEndpoint)
.then()
    .statusCode(401)
    .body("error", equalTo("invalid_grant"));
```

------------------------------------------------------------------------

## 10. Troubleshooting guide

  -----------------------------------------------------------------------
  Symptom                 Possible cause          What to check
  ----------------------- ----------------------- -----------------------
  `invalid_grant`         Wrong credentials,      User enabled state,
                          disabled user or flow   password, Direct Access
                          configuration           Grants setting

  `invalid_client`        Incorrect client ID or  Client ID, client type,
                          client authentication   secret requirement

  `invalid_scope`         Requested scope not     Client scopes and
                          available               requested scopes

  UserInfo returns 401    Missing, expired or     Authorization header,
                          invalid access token    token expiry, issuer
                                                  and realm

  JWT key not found       Stale JWKS cache or     Refresh JWKS and check
                          unknown `kid`           issuer metadata

  JWT signature fails     Wrong key, wrong        `kid`, JWKS issuer,
                          algorithm or token      signing algorithm
                          tampering               

  JWT issuer mismatch     Token issued by a       Exact `iss` claim and
                          different realm/URL     expected issuer

  JWT audience mismatch   Token not intended for  Audience mapper/client
                          the API                 configuration

  Maven cannot resolve    Invalid version or      Dependency coordinates
  dependency              repository/network      and Maven Central
                          issue                   availability

  TestNG class not        Suite XML or Surefire   `testng.xml`, `-Dtest`,
  executed                configuration           Surefire settings
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 11. HTTP and OAuth error glossary

  -----------------------------------------------------------------------
  Term / code                         Meaning
  ----------------------------------- -----------------------------------
  200                                 OK

  400                                 Bad Request

  401                                 Unauthorized / authentication
                                      missing or invalid

  403                                 Forbidden / authenticated but not
                                      permitted

  404                                 Not Found

  429                                 Too Many Requests

  500                                 Internal Server Error

  503                                 Service Unavailable

  `invalid_request`                   Request is malformed or missing
                                      required parameters

  `invalid_client`                    Client authentication failed

  `invalid_grant`                     Grant or supplied credentials are
                                      invalid

  `invalid_scope`                     Requested scope is invalid or
                                      unsupported

  `unsupported_grant_type`            Authorization server does not
                                      support the requested grant
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 12. Full-form glossary

  -----------------------------------------------------------------------
  Abbreviation                        Full form
  ----------------------------------- -----------------------------------
  API                                 Application Programming Interface

  CIAM                                Customer Identity and Access
                                      Management

  IAM                                 Identity and Access Management

  IdP                                 Identity Provider

  SP                                  Service Provider

  OAuth                               Open Authorization (commonly used
                                      name for the authorization
                                      framework)

  OIDC                                OpenID Connect

  JWT                                 JSON Web Token

  JWS                                 JSON Web Signature

  JWE                                 JSON Web Encryption

  JWK                                 JSON Web Key

  JWKS                                JSON Web Key Set

  PKCE                                Proof Key for Code Exchange

  MFA                                 Multi-Factor Authentication

  2FA                                 Two-Factor Authentication

  SSO                                 Single Sign-On

  RBAC                                Role-Based Access Control

  ABAC                                Attribute-Based Access Control

  LDAP                                Lightweight Directory Access
                                      Protocol

  AD                                  Active Directory

  DN                                  Distinguished Name

  RDN                                 Relative Distinguished Name

  CN                                  Common Name

  OU                                  Organizational Unit

  DC                                  Domain Component

  UID                                 User Identifier

  TLS                                 Transport Layer Security

  HTTPS                               Hypertext Transfer Protocol Secure

  REST                                Representational State Transfer

  HTTP                                Hypertext Transfer Protocol

  URI                                 Uniform Resource Identifier

  URL                                 Uniform Resource Locator

  JSON                                JavaScript Object Notation

  API                                 Application Programming Interface

  CI                                  Continuous Integration

  CD                                  Continuous Delivery / Continuous
                                      Deployment, depending on context

  SDET                                Software Development Engineer in
                                      Test

  QA                                  Quality Assurance

  POC                                 Proof of Concept

  TDD                                 Test-Driven Development

  DAST                                Dynamic Application Security
                                      Testing

  SAST                                Static Application Security Testing

  OTP                                 One-Time Password

  TOTP                                Time-Based One-Time Password

  HMAC                                Hash-Based Message Authentication
                                      Code

  RSA                                 Rivest--Shamir--Adleman

  SHA                                 Secure Hash Algorithm

  URL                                 Uniform Resource Locator

  XML                                 Extensible Markup Language

  YAML                                YAML Ain't Markup Language

  JVM                                 Java Virtual Machine

  JDK                                 Java Development Kit

  JRE                                 Java Runtime Environment

  Maven                               Apache Maven (build and dependency
                                      management tool)

  REST Assured                        Java library for testing REST APIs

  TestNG                              Test Next Generation

  JWKS URI                            URI identifying the JSON Web Key
                                      Set endpoint
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 13. Interview-ready explanation of this POC

A concise way to describe the project:

> I extended an existing Java 21, Maven, Rest Assured and TestNG API
> automation framework with a small CIAM integration using Keycloak. I
> configured a realm, a public OIDC client and a test user, inspected
> the OIDC discovery metadata, and automated token acquisition and
> UserInfo access. I covered positive and negative authentication cases,
> including invalid credentials and missing Bearer tokens. I also used
> Nimbus JOSE + JWT to retrieve the issuer's JWKS, select the matching
> key using the JWT `kid`, verify the signature and validate selected
> claims such as issuer, subject and expiry. I reviewed Authorization
> Code with PKCE, Client Credentials and LDAP federation conceptually,
> but did not implement those integrations in this POC.

### Be ready to explain limitations

-   The local POC uses the password grant for demonstration; it is not
    the recommended production flow.
-   The user is a local Keycloak user; LDAP federation was not
    configured.
-   JWT checks demonstrate signature and selected claim validation; they
    are not a complete production validation policy.
-   Audience, role/scope enforcement, MFA, refresh-token lifecycle,
    revocation and signing-key rotation remain possible extensions.

------------------------------------------------------------------------

## 14. Final revision checklist

-   [ ] Explain CIAM vs IAM.
-   [ ] Explain authentication vs authorization.
-   [ ] Explain OAuth 2.0 vs OIDC.
-   [ ] Draw Authorization Code + PKCE flow.
-   [ ] Explain Client Credentials and Refresh Token flows.
-   [ ] Explain access token vs ID token.
-   [ ] Explain JWT header, payload and signature.
-   [ ] Explain `iss`, `sub`, `aud`, `exp` and `kid`.
-   [ ] Explain JWKS and signing-key rotation.
-   [ ] Explain LDAP, DN, OU, CN and DC.
-   [ ] Explain Keycloak LDAP federation at a conceptual level.
-   [ ] Explain the four automated CIAM test scenarios.
-   [ ] Explain the JWT validation test.
-   [ ] State clearly what was implemented versus only studied.

**Final reminder:** Never share real passwords, client secrets, access
tokens or refresh tokens in source control, interview screenshots or
logs.
