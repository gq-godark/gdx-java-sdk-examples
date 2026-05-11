# GoDark Java SDK — reference (v0.1)

## Packages

- `exchange.godark.gdx.GodarkClient` — constructor surface aligned with Python (`api_key` or
  `api_key_id` + `api_secret`, `base_url`, env fallbacks, symbol map, stream buffer sizes).
- `exchange.godark.gdx.Proto` — protobuf wire builders (`buildPlaceOrderProto`, `buildCancelOrderProto`, …).
- `exchange.godark.gdx.Types` — minimal public records (`OrderAck`, …).
- `exchange.godark.gdx.TransportConfig` — reserved for TLS / transport hooks.

## Generated code

Protobuf/gRPC classes live under the `gdx.*` Java package inside the uber-JAR (generated from
`gdx-proto`). Regeneration is driven by the upstream `gdx-java-sdk` Gradle build — consumers of
this ZIP treat the JAR as an **opaque binary**.

## Versioning

The JAR file name embeds the SDK version from upstream `gradle.properties` (e.g. `0.1.0`). The
exact git pin is recorded in `sdk/UPSTREAM_REF`.
