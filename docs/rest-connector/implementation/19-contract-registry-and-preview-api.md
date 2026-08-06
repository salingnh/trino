# Work Item 19 — Contract Registry and SQL-to-Request Preview API

## Goal

Specify the external control-plane contract required by the connector: draft validation, capability inference, immutable publishing, contract retrieval, response sampling, and dry-run SQL-to-request preview.

## Scope boundary

Most of this service is outside the Trino repository. This document defines interoperability and shared models so connector implementation does not invent incompatible assumptions.

The registry/control plane owns:

- user-facing API definitions;
- draft CRUD;
- response samples/test calls;
- compiler/inference invocation;
- warnings and approval;
- immutable publish/version/audit;
- catalog provisioning workflow;
- preview orchestration.

The Trino connector owns:

- loading published compiled contract;
- metadata/planning/execution;
- pure request planner usable by preview component;
- runtime safety and metrics.

## Resource model

```text
DataSource
  id
  name
  baseUriReference / approved service identity
  authenticationProfileReference
  networkPolicyReference
  defaults

ApiDefinitionDraft
  draftId
  dataSourceId
  mutable revision
  tables[]
  createdBy/updatedBy/timestamps

PublishedContract
  contractId
  version
  fingerprint
  formatVersion
  immutable compiled JSON
  validation report
  approval/audit metadata
```

Secrets are stored in a separate secret/auth profile service. Registry stores profile reference only.

## Required endpoints

### Create/update draft

```http
POST /v1/rest-definitions/drafts
PUT /v1/rest-definitions/drafts/{draftId}
GET /v1/rest-definitions/drafts/{draftId}
```

Use optimistic concurrency with ETag/revision for updates.

### Validate/compile

```http
POST /v1/rest-definitions/drafts/{draftId}:validate
POST /v1/rest-definitions/drafts/{draftId}:compile
```

Response:

```json
{
  "successful": true,
  "diagnostics": [],
  "capabilities": {},
  "compiledContract": {},
  "fingerprint": "..."
}
```

Compile preview does not publish and may omit full compiled artifact from ordinary UI response if large; it must return deterministic fingerprint and capability model.

### Sample/test endpoint

```http
POST /v1/rest-definitions/drafts/{draftId}/tables/{table}:test
```

Request provides safe test bindings and maximum rows/bytes. Service uses server-side auth profile, network policy and strict budgets. Response includes sanitized request, status, timing, row sample, decode diagnostics, pagination metadata and request ID.

Never let browser/client submit arbitrary base URI/header auth values.

### Infer capability

```http
POST /v1/rest-definitions/drafts/{draftId}/tables/{table}:infer-capabilities
```

Returns stable capability/evidence/warning model from Work Item 08.

### SQL-to-request preview

```http
POST /v1/rest-definitions/drafts/{draftId}:explain-request
```

Input:

```json
{
  "sql": "SELECT ...",
  "session": {
    "catalog": "draft_catalog",
    "schema": "crm"
  },
  "execute": false
}
```

Output:

- parsed/validated SQL status;
- selected table;
- pushed exact predicates by column/operator, no sensitive values unless authorized;
- approximate/residual predicates;
- projected remote fields;
- limit and guarantee reason;
- sort/Top-N and guarantee reason;
- pagination initial state;
- estimated batches/splits/requests;
- sanitized HTTP request shape/body;
- diagnostics.

Preview uses the same compiler/request planner library. It must not reimplement SQL-to-HTTP mapping in UI code.

A full Trino SQL planner may be required to produce authentic `TupleDomain` and optimizer behavior. Options:

1. embedded/testing Trino planner service with draft connector model;
2. connector-exposed procedure/table function for administrator preview;
3. control-plane simplified expression planner limited to supported preview syntax.

Preferred production design: a dedicated preview service invoking real Trino planning against an isolated draft catalog, because handwritten SQL translation will diverge. MVP may provide table-level typed request preview before full SQL preview; document limitation.

### Publish

```http
POST /v1/rest-definitions/drafts/{draftId}:publish
```

Preconditions:

- latest revision/ETag;
- successful validation/compile;
- network/auth profiles valid;
- required approval when policy demands;
- deterministic fingerprint;
- no ERROR diagnostics.

Publish creates immutable numeric/semantic version. Same content may reuse fingerprint but each version/audit event behavior must be defined.

### Retrieve compiled contract

```http
GET /v1/contracts/{contractId}/versions/{version}
Accept: application/vnd.trino-rest-contract+json
If-None-Match: "..."
```

Response headers:

```text
Content-Type: application/vnd.trino-rest-contract+json
ETag: "<immutable-etag>"
X-Rest-Contract-Id: ...
X-Rest-Contract-Version: ...
X-Rest-Contract-Fingerprint: ...
Cache-Control: private, immutable
```

Body is exactly Work Item 03 format.

Alias endpoint may resolve `latest`/environment alias, but connector cluster must pin the resolved immutable version/fingerprint consistently. Prefer catalog provisioning writes immutable version, not mutable alias.

## API definition schema

A table definition minimally contains:

```yaml
schema: crm
name: users
endpoint:
  method: POST
  path: /v1/users/search
  readOnly: true
request:
  contentType: application/json
  fields:
    tenantId:
      type: string
      location: body
      path: /tenantId
      required: true
      source: sqlPredicate
    status:
      type: array<string>
      location: body
      path: /filters/status
      collectionSemantics: any
    size:
      type: integer
      location: body
      path: /page/size
      role: limit
      maximum: 500
    cursor:
      type: string
      location: body
      path: /page/cursor
      role: cursor
response:
  rowPath: /data/items
  nextCursorPath: /data/nextCursor
  fields:
    id: {type: bigint, path: /id}
    tenant_id: {type: varchar, path: /tenantId}
    status: {type: varchar, path: /status}
bindings:
  tenant_id:
    eq: {field: tenantId, enforcement: exact}
  status:
    in: {field: status, enforcement: exact}
```

The schema must be versioned separately from compiled contract format.

## Capability response

```json
{
  "table": "crm.users",
  "filters": {
    "tenant_id": {
      "EQ": {
        "supported": true,
        "enforcement": "EXACT",
        "evidence": ["CAP-EQ-001"]
      }
    }
  },
  "limit": {
    "supported": true,
    "maximum": 500,
    "guaranteedAtCompileTime": false,
    "reason": "Guarantee depends on final residual predicates and split plan"
  },
  "projection": {
    "localDecodePruning": true,
    "remote": false
  },
  "pagination": {
    "type": "CURSOR",
    "consistency": "BEST_EFFORT"
  },
  "warnings": []
}
```

Compile-time capability does not claim query-specific guarantee when guarantee depends on final handle.

## Versioning and audit

Every publish stores:

- source draft revision/fingerprint;
- compiled contract fingerprint;
- compiler version;
- contract format version;
- publisher and approver;
- timestamps;
- diagnostic report;
- linked auth/network policy versions;
- optional OpenAPI/source sample fingerprints;
- change summary.

Published contract is immutable. Deprecation/disable is separate metadata and must not mutate bytes/fingerprint.

## Schema evolution

Compare versions and classify:

```text
NON_BREAKING
BREAKING_METADATA
BREAKING_EXECUTION
SECURITY_POLICY_CHANGE
```

Examples:

- add nullable visible column: usually non-breaking;
- remove/rename/change type: breaking;
- change filter exactness/pagination/auth/base service: execution/security breaking;
- raise limits: policy review required.

Production catalog upgrade is explicit/canary. No automatic mutable refresh in active queries.

## Access control

Roles:

- draft author;
- API tester;
- publisher;
- approver;
- catalog provisioner;
- contract reader (Trino service identity);
- audit reader.

Trino registry credential should be read-only for published compiled contracts and scoped to configured contract/environment.

## Test-call safety

Test endpoint must enforce stricter bounds than production query:

- approved host/auth only;
- no arbitrary headers/URL;
- maximum one/few pages;
- small rows/bytes/time;
- redacted request/response;
- audit event;
- cancellation;
- no mutation methods;
- POST must be declared read-only.

## Catalog provisioning

Platform action may generate:

```properties
connector.name=rest
rest.contract-source=REGISTRY
rest.contract-registry-uri=https://integration-platform.internal/api
rest.contract-id=crm-api
rest.contract-version=12
rest.contract-fingerprint=<sha256>
```

Secret values are supplied separately through environment/secret manager and marked sensitive. Provisioning verifies all Trino nodes receive same config/plugin.

Smoke checks:

```sql
SHOW SCHEMAS FROM crm_api;
SHOW TABLES FROM crm_api.crm;
DESCRIBE crm_api.crm.users;
SELECT id FROM crm_api.crm.users WHERE tenant_id = '<test>' LIMIT 1;
```

Use non-sensitive test identity and avoid executing data query when policy disallows.

## Compatibility tests

Shared contract fixtures are tested by:

- registry compiler service;
- connector `ContractJsonCodec`;
- preview service;
- catalog provisioning smoke test.

Consumer-driven contract tests ensure transport envelope, headers, JSON format, fingerprint and error behavior do not drift.

## Acceptance criteria

- Registry publishes immutable, fingerprinted compiled contracts.
- Connector has read-only scoped retrieval API.
- Draft/compiled schema formats are versioned separately.
- Capability preview uses same compiler/inference code.
- SQL/request preview is sanitized and makes no call in dry-run.
- Test calls cannot bypass network/auth/budget policies.
- Catalog provisioning pins immutable version/fingerprint.
- Audit records compiler/policy/source identities.

## Deliverables outside Trino repository

Create separate service backlog for:

- registry persistence/API;
- auth/network profile service integration;
- compiler service/shared library;
- response sampling/test executor;
- preview planner service;
- UI wizard;
- approval/audit;
- Trino catalog provisioning controller.

Do not implement these unrelated services inside `plugin/trino-rest`.