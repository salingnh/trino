# Work Item 05 — Contract Compiler and Validation Pipeline

## Goal

Compile the user-facing API definition into the immutable compiled contract described in Work Item 03. This logic must be deterministic, explainable, conservative, and reusable by the external control plane.

## Dependencies

Work Items 03–04.

## Scope boundary

The Trino runtime does not compile raw user definitions during worker execution. The compiler may live in a reusable Java library/module shared with the registry, or in the connector module initially for specification/testing. Runtime connector code consumes only compiled contracts.

OpenAPI import is an optional adapter that generates a draft API definition. It is not the compiler's primary model and not a runtime dependency for request execution.

## Packages and classes

```text
io.trino.plugin.rest.definition.ApiDefinition
io.trino.plugin.rest.definition.ApiDataSourceDefinition
io.trino.plugin.rest.definition.ApiTableDefinition
io.trino.plugin.rest.definition.ApiRequestDefinition
io.trino.plugin.rest.definition.ApiRequestFieldDefinition
io.trino.plugin.rest.definition.ApiResponseDefinition
io.trino.plugin.rest.definition.ApiResponseFieldDefinition
io.trino.plugin.rest.definition.ApiBindingDefinition
io.trino.plugin.rest.definition.ApiSemanticOverride
io.trino.plugin.rest.compiler.RestContractCompiler
io.trino.plugin.rest.compiler.CompilationContext
io.trino.plugin.rest.compiler.CompilationResult
io.trino.plugin.rest.compiler.CompilationDiagnostic
io.trino.plugin.rest.compiler.NameNormalizer
io.trino.plugin.rest.compiler.RequestFieldCompiler
io.trino.plugin.rest.compiler.ResponseFieldCompiler
io.trino.plugin.rest.compiler.BindingCompiler
io.trino.plugin.rest.compiler.PaginationCompiler
io.trino.plugin.rest.compiler.SortCompiler
io.trino.plugin.rest.compiler.GuardrailCompiler
```

## Input definition model

The user-facing model describes API facts:

- base service identity/reference;
- schema/table name;
- method/path;
- request fields, locations, paths, types, source and semantic role;
- response row pointer and fields;
- SQL column/operator to request-field bindings;
- pagination continuation fields;
- exact/approximate semantics;
- stable ordering and consistency facts;
- optional overrides.

It must not expose SPI concepts such as `TupleDomain`, `ConnectorTableHandle`, `LimitApplicationResult`, or split classes.

## Compiler phases

Implement a fixed pipeline:

```text
1. Parse and basic schema validation
2. Normalize identifiers and paths
3. Resolve request/response field references
4. Compile Trino types
5. Compile request bindings
6. Infer capabilities
7. Apply explicit semantic overrides
8. Compile pagination and sorting
9. Compile guardrails/security references
10. Cross-component validation
11. Canonicalize ordering
12. Generate fingerprint
13. Emit diagnostics and compiled contract
```

Each phase returns a new immutable intermediate model. Avoid one mutable builder passed through all phases.

## Determinism requirements

Given semantically equivalent input, output JSON and fingerprint must be identical regardless of:

- input JSON key order;
- map implementation;
- order of declarations that are explicitly unordered;
- locale of the JVM;
- host timezone;
- thread scheduling.

Use `Locale.ENGLISH` for normalization where case conversion is required. Sort maps/sets by stable keys before final model creation.

## Name normalization

Rules:

- preserve explicitly valid SQL names when possible;
- normalize fallback names to lowercase snake case;
- reject empty names;
- detect case-insensitive collisions after normalization;
- never silently append numeric suffixes to resolve collisions in production compilation;
- diagnostics must point to both conflicting source paths;
- reserved/system names are either rejected or escaped through an explicit policy.

## Request field compilation

For every request field validate:

- unique logical name;
- valid location;
- path fields match placeholders exactly;
- body fields have valid JSON Pointer and compatible content type;
- header fields are in the business-header allowlist and exclude `Authorization`, `Cookie`, `Host`, forwarding headers and hop-by-hop headers;
- required field has a valid source (`SQL_PREDICATE`, `SYSTEM`, `CONSTANT`, `SESSION`);
- constants have values compatible with declared type;
- session source refers to an allowed connector session property;
- array/repeated encoding declares collection semantics and maximum values;
- query/path encoding is explicit for arrays and reserved characters.

## Response field compilation

For every field validate:

- unique case-insensitive SQL name;
- valid Trino type text;
- valid row-relative JSON Pointer;
- nullability explicitly resolved;
- hidden/input-only fields are not confused with response fields;
- `remoteFieldName` exists when remote projection/sorting needs it;
- nested fallback policy is explicit (`ROW`, `ARRAY`, `MAP`, `JSON`);
- unknown/polymorphic shapes compile to `JSON`, not an invented fixed row.

## Binding compilation

A binding is valid only when:

- SQL column exists;
- request field exists;
- operator is supported by both column type and request type;
- equality/range values can be encoded without loss;
- `IN` request field declares OR-like collection semantics;
- inclusivity matches (`gt`, `gte`, `lt`, `lte`);
- null binding declares remote missing/null behavior;
- approximate semantics are explicit for fuzzy/full-text/contains/regex unless exact equivalence can be proven by policy.

Do not infer operator semantics from request field names.

## Capability inference integration

The compiler invokes Work Item 08's inference engine. It then applies overrides with strict rules:

- overrides may reduce capability safely;
- enabling a stronger guarantee requires explicit semantic evidence fields;
- no override can bypass type incompatibility or security validation;
- warnings remain attached to the compiled capability as explanations.

Examples:

- `limit.guaranteed=false` may always be forced;
- `limit.guaranteed=true` requires explicit API guarantee and compatible execution semantics;
- sort support may be disabled;
- exact equality cannot be forced for a generic fuzzy search binding without an explicit exact-search contract fact.

## Pagination compilation

Compile only one strategy per table. Reject ambiguous combinations such as cursor plus offset unless an explicit composite strategy is designed outside MVP.

### No pagination

Requires a bounded endpoint or hard response/row guardrails.

### Page

Requires page field, optional page-size/limit field, first-page value, and at least one reliable termination rule.

### Offset

Requires offset and limit/page-size fields; stable ordering warning required when absent.

### Cursor

Requires cursor request field and next-cursor response pointer; cursor is opaque string/bytes and never parsed by connector.

### Next link

Requires source (body pointer or Link header relation), relative-link policy and stop conditions; security host rules are runtime catalog policy.

## Diagnostics

```java
public record CompilationDiagnostic(
        DiagnosticSeverity severity,
        String code,
        String definitionPointer,
        Optional<String> relatedPointer,
        String message,
        Optional<String> suggestion) {}
```

Stable codes are required for UI/tests, for example:

```text
REST-C001 duplicate-table
REST-C002 duplicate-column
REST-C010 missing-path-binding
REST-C020 incompatible-filter-binding
REST-C030 ambiguous-pagination
REST-C040 unsafe-header
REST-C050 unbounded-scan
REST-C060 unsupported-guarantee
```

Messages are safe and contain no secret/credential values.

## Compilation result

```java
public record CompilationResult(
        Optional<CompiledRestContract> contract,
        ImmutableList<CompilationDiagnostic> diagnostics)
{
    public boolean successful();
}
```

A contract is present only when there are no ERROR diagnostics. WARN diagnostics are preserved in contract metadata/capability explanations when relevant.

## Validation examples

Reject:

- POST endpoint not declared read-only;
- path placeholder without exactly one path request field;
- required SQL-bound field with no filter binding;
- `status IN (...)` mapped to scalar request field without repeat/list encoding;
- generic `q` field claimed as exact equality without exact semantics;
- cursor pagination without next cursor pointer;
- no pagination with no required filter and no safe row/request bound;
- sort guarantee without stable ordering/tie breaker as required;
- remote projection field names missing;
- arbitrary authorization header field;
- contract guardrail above platform maximum.

Warn:

- cursor consistency unspecified (`BEST_EFFORT`);
- page/offset pagination without stable ordering;
- approximate predicate remains residual;
- limit can only be a hint;
- nested field falls back to JSON;
- no remote projection but local pruning is available.

## Test strategy

Create fixture directories:

```text
src/test/resources/definitions/valid/
src/test/resources/definitions/invalid/
src/test/resources/definitions/warnings/
src/test/resources/compiled/
```

Tests:

- one golden compiled contract per major feature;
- deterministic output under shuffled declaration order;
- all diagnostic codes and pointers;
- every operator/type compatibility pair;
- all pagination strategies;
- override strengthening/reduction rules;
- exact/approximate enforcement;
- schema/table/column collision behavior;
- no locale/timezone dependence;
- OpenAPI adapter output, when added, compiles through the same input model.

## Acceptance criteria

- Compiler output is deterministic and fingerprint-stable.
- Invalid ambiguity is rejected before catalog publication.
- All inferred capabilities include a reason/evidence trail.
- OpenAPI is isolated behind an optional adapter.
- Compiler has no network access and no secret access.
- The same compiler library can be used by registry preview and connector validation tests.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestContractCompiler,Test*Compiler*,Test*Definition*' test
./mvnw -pl plugin/trino-rest airstyle:check
```