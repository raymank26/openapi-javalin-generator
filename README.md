[![CI](https://github.com/raymank26/openapi-javalin-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/raymank26/openapi-javalin-generator/actions/workflows/ci.yml)

# Overview

Status: proof of concept

A gradle plugin for OpenApi client/server code generation. The tool generates type-safe client code with
`OkHttpClient` and `Javalin` server stub without annotations.

# Description

The plugin processes OpenAPI spec as an input (e.g. `sample/openapi/spec.yml`).

Based on the spec the Gradle plugin generates:

1. A client class with a number of methods mirroring a number of operations of the spec.
2. A server interface which a developer should implement in some way.
3. A controller which consumes Javalin instance and the server implementation.
4. All request/response classes.

The plugin integrates into a build step and reflects all changes of the spec file.

See `sample/src/test/kotlin/SampleTest.kt` for usage example.

# TODO

- [x] Implement GET requests with query and form parameters
- [x] Generate HTTP response classes
- [x] Generate Javalin glue code (controller)
- [ ] Implement request validation
- [ ] Support multiple OpenAPI specs in a single module
- [ ] Implement auth
- [ ] Add retry policy and circuit breaker
- [ ] Implement other than JSON content types.