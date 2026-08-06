# Work Item 02 — Configuration and Session Properties

## Goal

Define all catalog-level and session-level settings required by the MVP while following Trino configuration conventions exactly.

## Dependencies

Work Item 01.

## Files

```text
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestConfig.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestSessionProperties.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestContractSource.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestConfig.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestSessionProperties.java
```

## Mandatory repository rules

- Catalog property names use dashes.
- Session property names use snake case.
- Every `@Config` setter has `@ConfigDescription`.
- Secret-bearing setters use `@ConfigSecuritySensitive`.
- Validation annotations are placed on setters, not fields.
- Runtime components copy validated values in their constructors; do not retain `RestConfig` as a field.
- Renames use `@LegacyConfig`; removals use `@DefunctConfig`.
- Tests use Airlift `ConfigAssertions`.

## Catalog properties

Use the following initial property model. Exact Java types should use Airlift `Duration`, `DataSize`, immutable sets, and validated URI/string wrappers where appropriate.

| Property | Type | Default | Validation | Purpose |
|---|---|---:|---|---|
| `rest.contract-source` | enum | `REGISTRY` | not null | source of compiled contracts |
| `rest.contract-registry-uri` | URI | none | required for REGISTRY, HTTPS unless explicitly allowed for tests | registry base URI |
| `rest.contract-id` | String | none | non-blank | published contract identity |
| `rest.contract-version` | String | none | non-blank, immutable version or alias | contract selector |
| `rest.contract-fingerprint` | String | empty | lowercase hex when set | optional pinned fingerprint |
| `rest.registry-connect-timeout` | Duration | `5s` | minimum `1ms` | registry connect timeout |
| `rest.registry-request-timeout` | Duration | `30s` | minimum `1ms` | registry request timeout |
| `rest.registry-cache-ttl` | Duration | `10m` | minimum `0ms` | coordinator compiled-model cache TTL |
| `rest.allowed-hosts` | Set<String> | empty | normalized host names | data API host allowlist |
| `rest.allow-http` | boolean | `false` | — | permit plain HTTP; test/non-production only |
| `rest.follow-redirects` | boolean | `false` | — | remote redirect policy |
| `rest.max-redirects` | int | `0` | 0..10 | redirect cap |
| `rest.connect-timeout` | Duration | `5s` | minimum `1ms` | data API connect timeout |
| `rest.request-timeout` | Duration | `30s` | minimum `1ms` | per-request timeout |
| `rest.max-response-size` | DataSize | `32MB` | minimum `1kB` | compressed/decompressed response guard as implemented |
| `rest.max-requests-per-query` | long | `1000` | minimum 1 | hard request budget |
| `rest.max-pages-per-query` | long | `1000` | minimum 1 | hard pagination budget |
| `rest.max-rows-per-query` | long | `10000000` | minimum 1 | hard row budget |
| `rest.max-bytes-per-query` | DataSize | `1GB` | minimum `1kB` | hard bytes budget |
| `rest.max-splits-per-query` | int | `10000` | minimum 1 | split planning cap |
| `rest.max-concurrent-requests-per-host` | int | `8` | minimum 1 | worker-local host bulkhead |
| `rest.max-requests-per-second` | double | `25` | positive | worker-local token bucket |
| `rest.retry-max-attempts` | int | `4` | minimum 1 | total attempts including first |
| `rest.retry-max-duration` | Duration | `30s` | minimum `0ms` | retry elapsed-time budget |
| `rest.retry-initial-delay` | Duration | `100ms` | minimum `0ms` | initial backoff |
| `rest.dynamic-filtering-wait-timeout` | Duration | `5s` | minimum `0ms` | split planning wait |
| `rest.require-bounded-scan` | boolean | `true` | — | reject unsafe unbounded scans |
| `rest.max-json-depth` | int | `100` | minimum 1 | parser depth guard |
| `rest.max-json-string-size` | DataSize | `8MB` | minimum `1kB` | scalar string guard |
| `rest.max-row-size` | DataSize | `16MB` | minimum `1kB` | per-row guard |

Do not add authentication secrets to this class until Work Item 14 defines named profile resolution. Prefer references to secret/profile providers rather than one global token.

## Cross-field validation

Airlift bean validation cannot express every conditional rule. Add a dedicated immutable `RestRuntimeSettings` factory or validation method invoked during bootstrap.

Required checks:

- `REGISTRY` requires registry URI, contract ID, and contract version.
- contract fingerprint, when present, must be 64 lowercase hexadecimal characters unless the registry defines a different fixed format.
- `max-redirects` must be zero when redirects are disabled.
- `allow-http=false` rejects HTTP registry and data origins.
- per-query byte/row/page/request limits must be greater than or equal to corresponding per-request/page limits.
- retry duration zero with attempts greater than one is rejected or normalized explicitly.
- allowed host entries must not contain scheme, path, port wildcard, user info, or whitespace.

Cross-field errors must state the exact property names and values, without printing secrets.

## Session properties

Session settings may only reduce or tune behavior within catalog hard limits. Users must never increase a hard catalog limit.

Initial session properties:

| Session property | Type | Meaning |
|---|---|---|
| `max_requests_per_query` | BIGINT | effective request cap, maximum catalog cap |
| `max_pages_per_query` | BIGINT | effective page cap |
| `max_rows_per_query` | BIGINT | effective row cap |
| `max_bytes_per_query` | VARCHAR/DataSize-backed | effective bytes cap |
| `dynamic_filtering_wait_timeout` | duration property | per-query wait cap |
| `request_timeout` | duration property | may reduce request timeout |
| `enable_remote_projection` | BOOLEAN | disable remote projection for diagnosis |
| `enable_topn_pushdown` | BOOLEAN | disable Top-N pushdown |
| `enable_dynamic_filtering` | BOOLEAN | disable dynamic filtering |
| `explain_request_logging` | BOOLEAN | enable safe request-plan debug metadata, never headers/secrets |

Rules:

- defaults come from copied catalog settings;
- decode helpers are static, typed, and reject values above catalog hard limits;
- properties include concise descriptions;
- property values affect planning handles so equality/fixpoint behavior remains deterministic;
- do not permit users to change base URI, allowed hosts, auth profile, contract version, or retry safety.

## `RestContractSource`

Start with:

```java
public enum RestContractSource
{
    REGISTRY,
    FILE
}
```

`FILE` is optional for local development and tests only. When included:

- it reads a compiled contract, not a user-facing API definition;
- path must be configured by administrator, never SQL;
- it must not support arbitrary URLs;
- production docs must recommend registry mode.

If FILE mode is not needed for deterministic tests, omit it entirely from MVP rather than adding an unsupported path.

## Tests

### `TestRestConfig.testDefaults`

Use `recordDefaults(RestConfig.class)` and assert every default exactly.

### `TestRestConfig.testExplicitPropertyMappings`

Map every property explicitly and compare against a fully populated expected config object.

### Validation tests

Use configuration factory tests to verify:

- missing registry URI/ID/version;
- malformed fingerprint;
- invalid hosts;
- HTTP rejection;
- negative/zero limits;
- redirect inconsistency;
- retry inconsistency;
- unknown property rejection.

### Session property tests

- defaults equal catalog settings;
- lower values accepted;
- values above hard limits rejected with `INVALID_SESSION_PROPERTY` or repository-standard error;
- disabled flags change helper results;
- descriptions are non-empty;
- secret values never appear in metadata.

## Acceptance criteria

- Every property has a description and validation.
- Explicit mappings cover all properties.
- No runtime class stores `RestConfig` directly.
- Session properties cannot weaken security or raise hard budgets.
- Invalid cross-field combinations fail catalog startup.
- No credential-bearing property is introduced without `@ConfigSecuritySensitive`.
- Tests follow the repository configuration rule and pass without mocks.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest=TestRestConfig,TestRestSessionProperties test
./mvnw -pl plugin/trino-rest airstyle:check
./mvnw -pl plugin/trino-rest validate
```