# Work Item 06 — Type System and Value Codecs

## Goal

Provide deterministic conversion between compiled contract types, SQL predicate values, HTTP request representations, streamed JSON tokens, and Trino blocks.

## Dependencies

Work Items 03 and 05.

## Packages and files

```text
io.trino.plugin.rest.type.RestTypeManager
io.trino.plugin.rest.type.RestTypeDescriptor
io.trino.plugin.rest.type.RestValueCodec
io.trino.plugin.rest.type.RestValueEncoder
io.trino.plugin.rest.type.RestValueDecoder
io.trino.plugin.rest.type.RestValueCodecFactory
io.trino.plugin.rest.type.JsonPointerPath
io.trino.plugin.rest.type.JsonTokenReader
io.trino.plugin.rest.type.ScalarValueCodecs
io.trino.plugin.rest.type.ArrayValueCodec
io.trino.plugin.rest.type.MapValueCodec
io.trino.plugin.rest.type.RowValueCodec
io.trino.plugin.rest.type.JsonValueCodec
io.trino.plugin.rest.type.ValueConversionException
```

Prefer a small immutable descriptor tree that is serializable in column handles. Runtime codec objects are constructed on workers from descriptors and `TypeManager`.

## Type descriptor

Recommended representation:

```java
public sealed interface RestTypeDescriptor
        permits ScalarTypeDescriptor,
                ArrayTypeDescriptor,
                MapTypeDescriptor,
                RowTypeDescriptor,
                JsonTypeDescriptor
{
    String trinoTypeSignature();
}
```

Do not serialize arbitrary `Type` implementation objects in contracts. Resolve and validate type signatures on coordinator; construct runtime `Type` through `TypeManager` where current Trino patterns require it.

## Supported MVP types

### Scalar

- `BOOLEAN`
- `TINYINT`, `SMALLINT`, `INTEGER`, `BIGINT`
- `REAL`, `DOUBLE`
- `DECIMAL(p,s)`
- `VARCHAR` and bounded `VARCHAR(n)` when supported by contract policy
- `VARBINARY`
- `DATE`
- `TIME(p)` only when remote format is explicitly configured
- `TIMESTAMP(p)`
- `TIMESTAMP(p) WITH TIME ZONE`
- `UUID`
- `JSON`

### Structural

- `ARRAY(T)`
- `MAP(VARCHAR, T)` for homogeneous dictionaries
- `ROW(...)` for stable fixed objects

Unsupported or heterogeneous structures fall back to `JSON` only when the compiled contract says so. Runtime must not silently change an expected typed column into JSON or VARCHAR after decode failure.

## Request encoding model

Request encoding depends on location:

```text
PATH    -> percent-encoded path segment, never raw slash injection
QUERY   -> scalar/repeated/delimited encoding declared by ParameterEncoding
HEADER  -> validated scalar/list encoding, CR/LF forbidden
BODY    -> JSON token written at configured pointer
```

Create an immutable request value:

```java
public sealed interface RestRequestValue
        permits ScalarRequestValue,
                ListRequestValue,
                NullRequestValue {}
```

`RestValueEncoder` receives a native Trino value and returns a typed request value. Location-specific serializers own escaping and layout.

## Scalar encoding rules

### Boolean

- canonical JSON `true`/`false`;
- path/query/header text configurable only between documented exact values;
- reject numeric boolean unless contract explicitly defines it.

### Integral

- use exact range checks for target remote type;
- never convert through floating point;
- reject overflow before creating request.

### Real/double

- reject NaN/infinity unless remote contract explicitly supports a textual representation;
- use locale-independent representation.

### Decimal

- preserve scale according to contract encoding policy;
- reject rounding unless explicit rounding mode exists in contract;
- body JSON may use number or string only as declared.

### Date/time/timestamp

- use explicit formatter from compiled contract or a supported named format;
- default date format ISO local date;
- offset-aware values map to `TIMESTAMP WITH TIME ZONE`;
- do not discard remote offsets silently;
- epoch encodings declare unit and timezone assumptions;
- formatter construction is cached and thread-safe.

### UUID

- canonical lower-case textual representation unless remote format is explicit;
- decode validates complete UUID.

### Binary

- Base64/base64url/hex declared explicitly;
- response size guard applies before decoding and after decoded allocation estimation.

### VARCHAR

- query/path encoding uses UTF-8;
- reject invalid surrogate sequences according to JSON writer behavior;
- enforce configured maximum scalar size;
- no implicit trim, lowercase, Unicode normalization, or locale transform unless the binding declares it and correctness implications are handled.

## Null and missing semantics

Keep distinct concepts:

```text
SQL NULL
JSON null
missing JSON property
omitted request parameter
empty string
empty array
```

Request binding must declare one behavior for SQL null:

```text
OMIT
SEND_JSON_NULL
SEND_EMPTY_STRING
SEND_LITERAL
UNSUPPORTED
```

Filter pushdown for `IS NULL` is allowed only when the remote API can represent equivalent matching semantics. Merely omitting a parameter means “do not filter” and is not SQL `IS NULL`.

Response decode generally maps missing and JSON null to SQL null for nullable ordinary columns. For non-nullable columns, both are decode errors unless a contract default exists; MVP should avoid defaults unless explicitly required.

## JSON streaming decoder interface

```java
public interface RestValueDecoder
{
    void decode(
            JsonParser parser,
            BlockBuilder output,
            DecodeContext context)
            throws IOException;
}
```

`DecodeContext` includes:

- table/column identity;
- expected JSON pointer;
- row/page counters;
- size/depth limits;
- contract fingerprint;
- coercion policy.

Decoder must consume exactly one JSON value token and leave parser at a documented position.

## Row extraction architecture

Avoid parsing each row into `JsonNode`.

Preferred process:

1. streaming parser navigates to configured row array;
2. for each object row, a row-field dispatcher maps field names/pointers to selected codecs;
3. unknown/unprojected subtrees are skipped with `skipChildren()`;
4. selected scalar/structural values are decoded directly to temporary row state or block builders;
5. missing selected fields are finalized as null/error after object end;
6. nested pointer support is implemented through a compiled field trie, not repeated string pointer scans.

Create:

```java
public final class CompiledFieldTrie
{
    // immutable tree of remote object field segments to projected column decoders
}
```

For arrays/rows/maps, decode recursively while enforcing nesting and element limits.

## Block writing

Use the current Trino type APIs and sibling connector patterns.

Rules:

- write native values with the expected `Type` methods;
- use `BlockBuilder.appendNull()` for null;
- use structural entry builders correctly;
- avoid one Java object allocation per primitive value where possible;
- keep per-row scratch structures bounded and reusable;
- report retained memory to page-source `MemoryContext` in Work Item 16.

## Coercion policy

Default `STRICT`:

- JSON number to numeric type only when exact/range-compatible;
- JSON string is not parsed as number/boolean unless column explicitly enables textual coercion;
- numeric value is not converted to string unless explicit;
- object/array mismatch fails;
- malformed date/time fails;
- extra object fields are skipped;
- missing nullable field becomes null.

Optional `LENIENT` mode is out of MVP unless a concrete requirement is approved. Do not add broad try-convert behavior.

## Error reporting

`ValueConversionException` captures safe structured fields:

- schema/table/column;
- response pointer;
- expected Trino type;
- actual JSON token type;
- page and row ordinal;
- operation ID;
- truncated/redacted sample only if explicitly safe and bounded.

Never include entire response body, request headers, cursor token, API key, bearer token, or sensitive field value.

## Tests

### Scalar round trips

For every supported type:

- valid minimum/maximum/representative values;
- overflow;
- malformed text;
- null/missing;
- exact JSON token type;
- request path/query/body encoding.

### Temporal tests

- UTC and non-UTC offsets;
- daylight-saving transitions where named zones are supported;
- fractional precision truncation/rejection policy;
- epoch unit boundaries;
- offset preservation.

### Structural tests

- nested arrays/rows/maps;
- null elements/fields;
- depth limit;
- element/row-size limits;
- unknown field skipping;
- selected nested field extraction;
- duplicate JSON field-name policy.

### Streaming behavior

- first row decoded before complete response is available;
- unprojected 10MB subtree is skipped without materialization;
- malformed later row fails after prior pages without leaking resources;
- parser consumes exactly expected tokens.

### Property-based tests

Where repository dependencies permit, generate bounded values for scalar codecs and verify encode/decode invariants. Do not introduce a new property-testing library solely for this item without approval.

## Acceptance criteria

- No row-path execution uses `JsonNode`, full string bodies, or `ObjectMapper.readTree`.
- Type mapping is deterministic and strict.
- Null/missing semantics are explicit.
- All selected fields can be decoded directly into Trino blocks.
- Unselected fields are skipped efficiently.
- Decode errors are actionable and sanitized.
- Tests cover every supported type and boundary.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='Test*Codec*,Test*Type*,Test*Json*' test
./mvnw -pl plugin/trino-rest airstyle:check
```