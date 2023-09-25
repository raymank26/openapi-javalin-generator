# Overview

Status: proof of concept

A gradle plugin for OpenApi client/server code generation. The tool generates type-safe client code with
`OkHttpClient` and `Javalin` server stub without annotations.

See `sample/src/test/kotlin/SampleTest.kt` for usage example.

# TODO

- [x] Implement GET requests with query and form parameters
- [x] Generate HTTP response classes
- [x] Generate Javalin glue code (controller)
- [ ] Implement request validation
- [ ] Support multiple OpenAPI spec in single module
- [ ] Add retry policy and circuit breaker