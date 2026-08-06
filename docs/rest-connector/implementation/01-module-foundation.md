# Work Item 01 — Module Foundation and Connector Lifecycle

## Goal

Create a compilable `plugin/trino-rest` module that follows Trino plugin conventions and exposes a read-only connector skeleton without implementing remote execution.

## Dependencies

None. This is the first implementation item.

## Repository changes

Create or update:

```text
pom.xml
plugin/trino-rest/pom.xml
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestPlugin.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestConnectorFactory.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestConnectorModule.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestConnector.java
plugin/trino-rest/src/main/java/io/trino/plugin/rest/RestTransactionHandle.java
plugin/trino-rest/src/main/resources/META-INF/services/io.trino.spi.Plugin
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestPlugin.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestConnectorFactory.java
```

Do not add write SPI classes in this item.

## Maven module

Use packaging and dependency patterns from another native Trino connector in the same repository. Requirements:

- module artifact name `trino-rest`;
- use repository-managed dependency versions;
- depend on `trino-spi` with the same scope used by sibling plugins;
- add Airlift bootstrap/configuration dependencies only when needed by the factory/module;
- add `trino-testing` and AssertJ for tests following sibling module patterns;
- keep dependency and XML ordering valid for `sortpom`;
- register `plugin/trino-rest` in the root reactor in alphabetical order expected by the repository.

## Plugin

Implement:

```java
public final class RestPlugin
        implements Plugin
{
    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new RestConnectorFactory());
    }
}
```

Constraints:

- no mutable static state;
- no public constructor unless needed;
- connector factory name is exactly `rest`;
- service provider file contains exactly `io.trino.plugin.rest.RestPlugin` followed by a newline.

## Connector factory

Implement:

```java
public final class RestConnectorFactory
        implements ConnectorFactory
{
    @Override
    public String getName();

    @Override
    public ConnectorHandleResolver getHandleResolver();

    @Override
    public Connector create(
            String catalogName,
            Map<String, String> config,
            ConnectorContext context);
}
```

Factory responsibilities:

1. Validate non-null arguments.
2. Invoke the repository's strict SPI version compatibility check if the current connector pattern requires it.
3. Build an Airlift `Bootstrap` with `JsonModule`, connector handle JSON modules if required by current Trino, and `RestConnectorModule`.
4. Disable duplicate logging initialization.
5. Apply required configuration properties.
6. Initialize Guice.
7. Return `RestConnector` from the injector.

Factory must not:

- make network calls;
- load a contract directly;
- resolve secrets;
- create global executors outside Guice lifecycle management.

## Guice module

`RestConnectorModule` establishes bindings only. Initial bindings:

- `RestConnector` singleton;
- placeholder `RestMetadata` singleton;
- placeholder `RestSplitManager` singleton;
- placeholder `RestPageSourceProvider` singleton;
- configuration class from Work Item 02 after that item lands;
- lifecycle-managed executors/HTTP client only in later work items.

The module constructor may receive stable context services such as `TypeManager`, `NodeManager`, or `CatalogHandle` only when required by current Trino patterns. Do not wrap the entire `ConnectorContext` in a singleton.

## Connector lifecycle

Implement a read-only connector exposing:

```java
public final class RestConnector
        implements Connector
{
    @Override
    public ConnectorTransactionHandle beginTransaction(
            IsolationLevel isolationLevel,
            boolean readOnly,
            boolean autoCommit);

    @Override
    public ConnectorMetadata getMetadata(
            ConnectorSession session,
            ConnectorTransactionHandle transactionHandle);

    @Override
    public ConnectorSplitManager getSplitManager();

    @Override
    public ConnectorPageSourceProvider getPageSourceProvider();

    @Override
    public void shutdown();
}
```

Rules:

- support only read-only transactions;
- reject unsupported isolation modes only if current Trino connector contracts require an explicit check;
- use one stateless transaction-handle singleton or enum;
- `shutdown()` delegates to `LifeCycleManager.stop()` and logs a warning without rethrowing shutdown failures, following sibling connector conventions;
- connector components are constructor-injected and final;
- no contract/model mutable state exists yet.

## Placeholder components

The module must compile before full implementation. Placeholder behavior:

- metadata returns no schemas/tables;
- split manager returns an empty split source only for an impossible placeholder handle, otherwise fail with an internal implementation error;
- page source provider fails with an internal implementation error until Work Item 16;
- placeholders must not accidentally expose a usable unbounded connector.

Prefer adding explicit TODO-free exceptions such as `UnsupportedOperationException("REST connector execution is not implemented")` only in temporary skeleton classes. These placeholders must be removed by the work item that implements the component.

## Handle resolver

Determine whether the current branch still requires a custom `ConnectorHandleResolver`. If required, register only concrete classes that already exist. Do not return broad interface types or unstable compiler model classes. If current Trino derives handles through Jackson annotations/modules instead, document that choice in code comments only where non-obvious.

## Tests

### `TestRestPlugin`

Verify:

- plugin exposes exactly one factory;
- factory name is `rest`;
- service loader can instantiate `RestPlugin` from the plugin artifact or test classpath pattern used by sibling connectors.

### `TestRestConnectorFactory`

Verify:

- unknown catalog property fails initialization as an unused/invalid property;
- minimal valid configuration creates a connector once Work Item 02 defines defaults;
- factory rejects null catalog name/context/config through normal validation;
- connector can be shut down twice without propagating a failure if sibling connectors establish idempotent shutdown semantics.

### Packaging check

Use the existing plugin packaging test pattern to verify the service provider is present and the plugin class is discoverable.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -am test -DskipTests=false
./mvnw -pl plugin/trino-rest airstyle:check
./mvnw -pl plugin/trino-rest validate
./mvnw sortpom:verify
```

Use the exact commands supported by the repository; do not add new Maven wrappers.

## Acceptance criteria

- The root reactor includes `plugin/trino-rest` in valid order.
- `ServiceLoader<Plugin>` discovers `RestPlugin`.
- `RestConnectorFactory.getName()` returns `rest`.
- Connector starts and shuts down without making any remote call.
- The connector exposes no write capabilities.
- The module passes formatting, configuration binding, packaging, and focused tests.
- No existing module is modified except root build registration and shared dependency management when demonstrably required.

## Review checklist

- Confirm all files have license headers.
- Confirm there is no copied OpenAPI implementation code.
- Confirm no secrets or URLs appear in source defaults.
- Confirm lifecycle-owned resources have a clear future binding point.
- Confirm placeholder behavior cannot result in a silent empty production scan.