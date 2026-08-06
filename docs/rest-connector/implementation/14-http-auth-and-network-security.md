# Work Item 14 — HTTP Client, Authentication, and Network Security

## Goal

Build a worker-safe HTTP transport boundary with named authentication profiles, strict host/origin policy, TLS verification, redirect/next-link controls, and complete secret isolation.

## Dependencies

Work Items 02, 04, 11–13.

## Files

```text
io.trino.plugin.rest.http.RestHttpClient
io.trino.plugin.rest.http.AirliftRestHttpClient
io.trino.plugin.rest.http.RestHttpRequest
io.trino.plugin.rest.http.RestHttpResponse
io.trino.plugin.rest.http.RestHttpClientModule
io.trino.plugin.rest.security.NetworkPolicy
io.trino.plugin.rest.security.AllowedOrigin
io.trino.plugin.rest.security.UriSecurityValidator
io.trino.plugin.rest.security.AuthenticationProfileRegistry
io.trino.plugin.rest.security.AuthenticationProfile
io.trino.plugin.rest.security.ApiKeyAuthentication
io.trino.plugin.rest.security.BasicAuthentication
io.trino.plugin.rest.security.BearerAuthentication
io.trino.plugin.rest.security.OAuth2ClientCredentialsAuthentication
io.trino.plugin.rest.security.MutualTlsAuthentication
io.trino.plugin.rest.security.OAuthTokenProvider
io.trino.plugin.rest.security.SecretRedactor
src/test/java/io/trino/plugin/rest/http/TestAirliftRestHttpClient.java
src/test/java/io/trino/plugin/rest/security/TestUriSecurityValidator.java
src/test/java/io/trino/plugin/rest/security/TestAuthenticationProfiles.java
```

## HTTP client choice

Use Airlift HTTP client unless a target-branch connector pattern establishes a better compatible option. Reasons:

- lifecycle integration;
- connection pooling;
- timeout/TLS configuration;
- metrics/JMX support;
- dependency/classloader consistency.

Registry and data API use separate named clients.

## Transport interface

```java
public interface RestHttpClient
{
    CompletableFuture<RestHttpResponse> execute(
            RestHttpRequest request,
            RequestExecutionContext context);
}
```

The actual Airlift async response handler should expose a streaming input source/body consumer and headers/status without buffering the row response.

`RestHttpResponse` owns the body stream and must be closed.

## Request pipeline

```text
pure request plan
  -> final URI network validation
  -> business header validation
  -> auth profile lookup
  -> auth material application
  -> trace headers according to policy
  -> rate/concurrency acquisition
  -> transport execution
  -> redirect handling under policy
  -> status/content checks
  -> streaming response
```

Authentication is applied as late as possible and never written back into `RestRequestPlan`, handles or splits.

## Authentication profiles

Contract references profile name only. Profile definitions come from administrator-controlled configuration/secret provider.

### API key

Support header by default. Query/cookie API keys should be omitted from MVP unless required and fully redacted.

Fields:

- header name allowlisted and not a standard auth/framing conflict;
- secret reference/value marked `@ConfigSecuritySensitive` if configured locally;
- no logging or metadata exposure.

### Basic

- HTTPS required;
- username/password from secret source;
- standard Authorization header;
- no redirect credential forwarding outside exact approved origin.

### Static bearer

- HTTPS required;
- token from secret source;
- rotation behavior documented;
- token absent from cache keys/metrics/errors.

### OAuth2 client credentials

Profile:

```text
token URI
client ID
client secret
scopes/audience
optional mTLS client
refresh skew
```

Rules:

- use token endpoint, not authorization endpoint;
- grant type `client_credentials`;
- token cache key includes issuer/token URI, client identity reference, scopes, audience and mTLS identity;
- cache expiry from `expires_in` minus bounded skew;
- single-flight refresh;
- on 401, invalidate/refresh at most once per logical request;
- never retry endless authentication loops;
- token endpoint has separate host allowlist and HTTP settings;
- token response size/content bounded;
- token values redacted.

### mTLS

- keystore/truststore references and passwords are sensitive;
- hostname verification remains enabled;
- named HTTP client may be required per mTLS identity;
- do not select certificate from SQL values;
- certificate/key data never enters contract or handles.

### Combined authentication

Support combinations only when profile explicitly defines AND semantics, e.g. OAuth + mTLS. Do not choose one scheme from a required set.

## Profile registry

```java
public interface AuthenticationProfileRegistry
{
    AuthenticationProfile require(String name);
}
```

Validation at catalog startup:

- every referenced profile exists;
- profile type supported;
- required secrets available;
- token/data origins approved;
- profile usable on all worker nodes;
- duplicate profile names rejected;
- no profile values exposed through connector metadata.

## Network policy

`UriSecurityValidator` validates every URI category:

```text
REGISTRY
DATA_BASE
DATA_REQUEST
REDIRECT
NEXT_LINK
OAUTH_TOKEN
```

Validation steps:

1. Parse canonical URI.
2. Require `https` unless test-only HTTP is enabled.
3. Reject user info and fragments.
4. Normalize hostname using IDNA rules and lowercase.
5. Check explicit allowed host/origin/port policy.
6. Reject loopback, unspecified, link-local, multicast, private ranges unless explicitly approved for on-prem use.
7. Resolve DNS under rebinding-aware policy where feasible.
8. Validate every resolved address, not only first.
9. Revalidate redirect/next-link target before auth forwarding.
10. Restrict path to configured base path when policy requires.

On-prem private APIs require private ranges; therefore policy must be allowlist-first rather than blanket private-IP rejection. Explicit approved hostnames/CIDRs are allowed; metadata-service/link-local and loopback remain denied unless a test policy enables them.

## DNS rebinding considerations

MVP options:

- validate host against allowlist and resolved addresses before connection;
- configure HTTP client/DNS resolver to connect to validated address set when supported;
- revalidate on each request/redirect;
- cache DNS briefly within policy;
- tests simulate host resolving to approved then forbidden addresses.

Document residual risk if Airlift client cannot pin validated addresses. Do not claim complete rebinding protection without implementation evidence.

## Redirects

Default disabled.

When enabled:

- maximum count enforced;
- only safe/read-only methods;
- 301/302/303 method rewrite behavior must be explicit; preferably reject rewrites for POST read operations;
- 307/308 preserve method/body only when replay safe;
- validate target URI before request;
- strip auth/cookies/sensitive headers on origin change;
- cross-origin redirect default rejected;
- redirect loop detected;
- redirect request counts against budget.

## Next links

Handled by pagination but transported here. Next links:

- undergo same URI validation;
- do not inherit arbitrary server-provided headers;
- auth applied only when target remains in profile's approved origin boundary;
- cross-origin default rejected;
- count against budgets/rate limits.

## TLS

- hostname verification always on;
- truststore configurable administratively;
- no `trust-all` production option;
- TLS protocol/cipher settings follow Airlift defaults/repository policy;
- certificate errors are external security failures, not retry storms;
- log only safe host/profile identity.

## Secret redaction

`SecretRedactor` handles structured request/response diagnostics:

- Authorization/Proxy-Authorization;
- API-key headers;
- Cookie/Set-Cookie;
- token request/response fields;
- sensitive query parameters;
- sensitive JSON pointers from contract/profile;
- cursor/next-link values where they may embed state.

Redaction occurs before formatting/logging. Avoid creating unredacted strings first when possible.

## Tests

Use local TLS HTTP servers/test certificates following repository test utilities. No mocks.

Authentication tests:

- API key header;
- Basic/bearer;
- OAuth token acquisition/cache/expiry/single-flight/401 refresh;
- mTLS success/failure;
- combined auth;
- missing profile/secret;
- secret absent from captured logs/exceptions/toString.

Network tests:

- approved HTTPS host;
- disallowed host/port/scheme/path;
- user info/fragment;
- loopback/link-local/metadata address;
- approved private on-prem host;
- DNS rebinding simulation where possible;
- redirect same-origin/cross-origin/method rewrite/loop;
- next-link validation and auth stripping;
- TLS hostname/trust failure.

## Acceptance criteria

- Every outbound URI is validated by category before transport.
- Authentication material exists only in late transport scope.
- Registry and data auth/client boundaries are separate.
- OAuth cache is expiry-aware and single-flight.
- Redirect/next-link cannot leak credentials or bypass allowlist.
- Private on-prem APIs are supported only through explicit allowlisting.
- No trust-all or hostname-verification bypass is present.
- Tests prove secret redaction and network rejection.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestAirliftRestHttpClient,TestUriSecurityValidator,TestAuthenticationProfiles' test
./mvnw -pl plugin/trino-rest airstyle:check
```