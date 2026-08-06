# Work Item 04 — Contract Registry Client and Coordinator Model Cache

## Goal

Load one immutable compiled contract on the coordinator, verify its identity/fingerprint, validate it, and expose a cached runtime catalog model. Workers must never call the registry.

## Dependencies

Work Items 01–03.

## Files

```text
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/RestContractRegistryClient.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/HttpRestContractRegistryClient.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/ContractRequest.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/ContractResponse.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/ContractRegistryException.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/ContractModelProvider.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/CachedContractModelProvider.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/ActiveContractSnapshot.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/registry/RegistryClientModule.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/registry/TestHttpRestContractRegistryClient.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/registry/TestCachedContractModelProvider.java
```

## Registry API assumed by connector

The detailed external API is specified in Work Item 19. Runtime needs a minimal read contract:

```http
GET /v1/contracts/{contractId}/versions/{version}
Accept: application/vnd.trino-rest-contract+json
If-None-Match: "<etag>"
```

Expected response metadata:

- immutable contract JSON;
- contract ID;
- resolved version;
- SHA-256 semantic fingerprint;
- ETag or equivalent immutable revision;
- content type and format version.

The registry client does not publish, mutate, sample, infer, or execute preview requests.

## Client interface

```java
public interface RestContractRegistryClient
{
    ContractResponse fetch(
            ContractRequest request,
            Optional<String> previousEtag);
}
```

`ContractRequest` includes only:

- configured contract ID;
- configured version/alias;
- optional configured fingerprint;
- catalog identity for diagnostics;
- request deadline derived from registry timeout.

It must not include a `ConnectorSession`, SQL text, or data API credentials.

## HTTP client separation

Use a dedicated named Airlift HTTP client for registry traffic.

Rules:

- registry HTTP configuration is separate from data API HTTP configuration;
- registry auth/filter is separate from endpoint auth profiles;
- redirect policy defaults to disabled;
- registry origin is fixed at catalog bootstrap;
- response size is bounded independently because contracts should be small;
- response is buffered only after the size cap because this is control-plane metadata, not row data;
- TLS hostname verification remains enabled;
- logs redact Authorization, cookies, query parameters marked sensitive, and response body.

## Response verification order

`HttpRestContractRegistryClient` performs:

1. Validate HTTP status.
2. Validate content type.
3. Enforce maximum body size before/parsing while reading.
4. Parse transport envelope if one exists.
5. Deserialize compiled contract with strict codec.
6. Verify requested contract ID equals contract content ID.
7. Verify resolved version rules.
8. Recalculate semantic fingerprint.
9. Compare recalculated fingerprint with response fingerprint.
10. Compare with catalog-pinned fingerprint when configured.
11. Run `CompiledContractValidator`.
12. Return immutable `ContractResponse`.

Do not expose metadata to Trino before all checks pass.

## HTTP status behavior

| Status | Behavior |
|---|---|
| 200 | verify and return model |
| 304 | return `NotModified` only when previous ETag was sent and a current snapshot exists |
| 400/422 | catalog configuration/contract request error; not retryable |
| 401 | registry authentication failure; not repeated indefinitely |
| 403 | registry authorization failure |
| 404 | contract/version not found; fail catalog load |
| 409 | mutable alias/version conflict; fail unless registry contract documents deterministic resolution |
| 429 | bounded retry according to registry policy and `Retry-After` |
| 5xx | bounded retry for GET |
| invalid/missing status | external service failure |

Every failure uses connector error codes from Work Item 17 and includes catalog, registry origin, contract ID, and version, but no response body or secret.

## Active snapshot

```java
public record ActiveContractSnapshot(
        CompiledRestContract contract,
        RestCatalogModel catalogModel,
        String etag,
        Instant loadedAt,
        Instant expiresAt) {}
```

The snapshot is immutable and atomically replaced.

The provider interface:

```java
public interface ContractModelProvider
{
    ActiveContractSnapshot current();

    ActiveContractSnapshot refresh();
}
```

MVP behavior:

- load synchronously during connector startup;
- no periodic refresh required unless explicitly enabled;
- query path reads an already active snapshot;
- refresh compiles/validates a candidate off to the side, then swaps atomically;
- failed refresh leaves previous snapshot active;
- existing table handles retain contract fingerprint and remain resolvable only against matching immutable snapshot/cache entry.

## Cache model

Use a bounded cache keyed by:

```text
(contractId, resolvedVersion, fingerprint)
```

Do not key only by alias such as `latest`.

MVP may keep:

- one active snapshot;
- a small bounded set of previous immutable snapshots needed by in-flight queries;
- expiry based on query lifetime or bounded TTL.

Required API:

```java
public final class ContractModelCache
{
    public RestCatalogModel require(ContractFingerprint fingerprint);

    public void activate(ActiveContractSnapshot snapshot);

    public Optional<RestCatalogModel> find(ContractFingerprint fingerprint);
}
```

If a worker-side handle refers to a fingerprint unavailable on the worker, that indicates deployment/serialization architecture error. Workers should receive sufficient derived execution model in handles/splits or have connector bootstrap load the same published contract locally through coordinator-distributed mechanisms supported by Trino. The implementation design must explicitly select one approach before coding Work Item 07:

### Preferred approach for MVP

- every Trino node loads the same immutable compiled contract at connector startup using node-local connector initialization;
- only coordinator uses registry refresh and metadata publication;
- workers may load the configured immutable version, but never resolve mutable aliases independently;
- coordinator resolves alias to immutable version/fingerprint and configuration/distribution must make that identity consistent cluster-wide.

### Alternative

- serialize a compact execution model in handles/splits sufficient for workers;
- this increases plan size and must be bounded/measured.

Do not leave this decision implicit. Record it in `RestConnectorModule` and tests.

## Coordinator versus worker detection

Use `NodeManager.getCurrentNode().isCoordinator()` or current repository-supported mechanism.

Coordinator:

- loads registry alias/version;
- validates model;
- publishes metadata;
- may refresh.

Worker:

- must not independently resolve a mutable alias;
- may load an exact immutable version/fingerprint if node-local initialization is selected;
- executes splits only after fingerprint equality checks.

## Concurrency

- one single-flight load per contract key;
- concurrent metadata calls reuse the active snapshot;
- refresh cannot expose partially compiled state;
- shutdown cancels outstanding registry requests;
- interruption/cancellation is preserved.

Avoid synchronized network calls while holding broad connector locks. Use a small lock or atomic reference around snapshot publication only.

## Tests

Use a real deterministic test HTTP server, not mocks.

Cover:

- valid load;
- content type mismatch;
- oversized response;
- wrong contract ID/version;
- transport fingerprint mismatch;
- configured fingerprint mismatch;
- invalid contract model;
- ETag and 304 reuse;
- 304 without existing snapshot;
- retryable 429/503 and bounded attempts;
- 401/403/404 classification;
- refresh success atomic swap;
- refresh failure preserves prior snapshot;
- concurrent loads use one request;
- shutdown/cancellation closes request;
- authorization values absent from exceptions/log capture;
- worker role does not resolve mutable alias.

## Acceptance criteria

- Metadata is unavailable until a verified contract model is active.
- Contract identity and semantic fingerprint are verified independently.
- Registry failure cannot corrupt/replace an active valid snapshot.
- Query execution does not make registry calls.
- Registry and data API HTTP/auth boundaries are separate.
- Alias resolution cannot produce different versions on different nodes.
- Tests prove single-flight behavior and atomic refresh.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='Test*Registry*,Test*ContractModel*' test
./mvnw -pl plugin/trino-rest airstyle:check
```