# Work Item 07 — Connector Metadata and Serializable Handles

## Goal

Expose compiled contracts as stable Trino schemas/tables/columns and define immutable handles that carry planning decisions across coordinator and workers.

## Dependencies

Work Items 03–06.

## Files

```text
io.trino.plugin.rest.RestMetadata
io.trino.plugin.rest.RestTableHandle
io.trino.plugin.rest.RestColumnHandle
io.trino.plugin.rest.RestSplit
io.trino.plugin.rest.RestTableInfo
io.trino.plugin.rest.RestHandleResolver
io.trino.plugin.rest.RestCatalogModel
io.trino.plugin.rest.RestSchemaModel
io.trino.plugin.rest.RestTableModel
io.trino.plugin.rest.RestColumnModel
src/test/java/io/trino/plugin/rest/TestRestMetadata.java
src/test/java/io/trino/plugin/rest/TestRestHandles.java
```

## Runtime catalog model

`RestCatalogModel` is compiled once from `CompiledRestContract` and `TypeManager`.

It contains:

- immutable map of schema name to schema model;
- immutable map of `SchemaTableName` to table model;
- contract identity/fingerprint;
- precompiled request/response descriptors;
- no HTTP clients, secrets, sessions, mutable caches, or worker state.

Lookup methods return immutable models and must be deterministic.

## Metadata methods

Implement at minimum:

```java
List<String> listSchemaNames(ConnectorSession session);

List<SchemaTableName> listTables(
        ConnectorSession session,
        Optional<String> schemaName);

ConnectorTableHandle getTableHandle(
        ConnectorSession session,
        SchemaTableName tableName,
        Optional<ConnectorTableVersion> startVersion,
        Optional<ConnectorTableVersion> endVersion);

ConnectorTableMetadata getTableMetadata(
        ConnectorSession session,
        ConnectorTableHandle tableHandle);

Map<String, ColumnHandle> getColumnHandles(
        ConnectorSession session,
        ConnectorTableHandle tableHandle);

ColumnMetadata getColumnMetadata(
        ConnectorSession session,
        ConnectorTableHandle tableHandle,
        ColumnHandle columnHandle);

Iterator<RelationColumnsMetadata> streamRelationColumns(...);

Optional<Object> getInfo(
        ConnectorSession session,
        ConnectorTableHandle tableHandle);
```

Follow current `ConnectorMetadata` signatures in the target branch exactly.

## Table version semantics

MVP does not expose time travel or branches. Reject or return null/empty according to SPI contract when start/end versions are supplied. Do not silently ignore version arguments if that would imply incorrect semantics.

## `RestColumnHandle`

Recommended record:

```java
public record RestColumnHandle(
        int ordinal,
        String name,
        Type type,
        RestTypeDescriptor typeDescriptor,
        JsonPointerPath responsePath,
        boolean hidden,
        Optional<String> remoteFieldName,
        ColumnRole role)
        implements ColumnHandle {}
```

If `Type` is not safely serialized by current handle JSON infrastructure, serialize only type signature/descriptor and resolve runtime Type through a handle resolver/module. Match current Trino patterns.

Column roles:

```text
OUTPUT
INPUT_ONLY
SYNTHETIC_DEREFERENCE
SYNTHETIC_PROJECTION
```

Rules:

- equality/hash include all semantic fields;
- no mutable list/map;
- no secret/constant sensitive value;
- ordinal stable for a contract fingerprint;
- hidden input-only columns may be exposed in metadata only when required for SQL predicates and documented.

## `RestTableHandle`

Recommended planning state:

```java
public record RestTableHandle(
        ContractFingerprint contractFingerprint,
        SchemaTableName tableName,
        String operationId,
        TupleDomain<RestColumnHandle> enforcedConstraint,
        ImmutableMap<RequestBindingKey, BoundValueSet> requestBindings,
        ImmutableList<RestColumnHandle> projectedColumns,
        OptionalLong limit,
        boolean limitGuaranteed,
        ImmutableList<RestSortItem> sortItems,
        boolean topNGuaranteed,
        Optional<RemoteProjection> remoteProjection,
        RestScanMode scanMode)
        implements ConnectorTableHandle {}
```

Do not store raw `ConnectorExpression` unless current serialization support and necessity are proven. Preserve only connector-owned immutable planning state.

### Handle invariants

- fingerprint/table/operation never change during optimizer derivation;
- `enforcedConstraint` only contains exact predicates fully enforced remotely;
- approximate predicates are not recorded as enforced; their remote prefilter bindings may be stored separately;
- projected columns are ordered/deduplicated deterministically;
- a new optimizer result is returned only if new handle is not equal to old handle;
- lower repeated limit wins; a larger/equal repeated limit causes no change;
- sort list equality includes direction and null ordering;
- scan mode captures collection/point/batch variant only when compiled table explicitly supports it.

Provide `with...` methods that canonicalize and return `this` when no semantic change is necessary if record/class design permits. Avoid generic builder mutation.

## `RestSplit`

Split contains one independent remote partition, not one cursor page.

```java
public record RestSplit(
        ContractFingerprint contractFingerprint,
        SchemaTableName tableName,
        String operationId,
        RestPartition partition,
        ImmutableMap<RequestBindingKey, BoundValueSet> splitBindings,
        Optional<HostAddress> preferredAddress,
        OptionalLong splitRowEstimate)
        implements ConnectorSplit {}
```

`RestPartition` variants may include:

```text
SINGLE
KEY_BATCH
TIME_WINDOW
OFFSET_RANGE
PAGE_RANGE
EXPLICIT_PARTITION
```

Cursor continuation is page-source state and must not appear as pre-generated split tokens except the initial cursor defined by contract/request.

Split must not contain:

- authorization headers/tokens;
- HTTP client/request objects;
- complete response schema when resolvable by fingerprint/table;
- mutable pagination state;
- registry URL/credentials.

## JSON serialization

Use current Trino/Jackson annotations/modules. Add round-trip tests for all handle/split variants.

Required test assertions:

- round-trip equality;
- deterministic JSON field order only if repository tests rely on it;
- unknown/missing required fields fail;
- no secret-like strings;
- serialized size stays bounded for large `IN` batches;
- type descriptors survive round trip;
- fingerprint mismatch is detected before execution.

## Metadata behavior

### Schemas/tables

- names come only from compiled model;
- invalid/unknown schema returns empty list;
- unknown table returns null from `getTableHandle` per SPI convention;
- list order deterministic.

### Columns

- visible response columns first in contract ordinal order;
- hidden input columns follow deterministic order or remain available only through explicit contract policy;
- duplicate case-insensitive names rejected during compilation;
- comments propagated safely;
- column metadata nullability matches contract where Trino metadata supports it.

### Table info

`getInfo` returns a safe record for `EXPLAIN`/diagnostics:

- contract ID/version/fingerprint prefix;
- operation ID/method/path template;
- pushed operators summary;
- pagination type;
- no full URL, parameter values, cursor, headers, or secret profile data.

## Contract fingerprint enforcement

Every metadata operation resolving a handle must verify:

```text
handle.fingerprint == active/resolvable model fingerprint
```

If an in-flight query refers to a previous immutable model retained in cache, resolve by fingerprint. If unavailable, fail with a connector-specific stale-contract error rather than interpreting the handle against a new schema.

## Tests

- schema/table listing;
- unknown table behavior;
- table version rejection;
- visible/hidden columns;
- comments and types;
- deterministic ordering;
- table/column handle JSON round trips;
- every split variant round trip;
- equality-stable `with` operations;
- stale/mismatched fingerprint;
- no writes/DDL support exposed;
- `getInfo` redaction.

## Acceptance criteria

- All metadata is served from immutable compiled model with no registry/remote calls per method.
- Handles/splits serialize across coordinator/worker correctly.
- Optimizer fixpoint equality is deterministic.
- Contract fingerprint prevents schema reinterpretation.
- No secret or mutable runtime object is serialized.
- Cursor pages are not represented as independent preplanned splits.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestMetadata,TestRestHandles' test
./mvnw -pl plugin/trino-rest airstyle:check
```