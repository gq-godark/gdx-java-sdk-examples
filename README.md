# gdx-java-sdk-examples

Runnable **examples** and a **tagged ZIP release** (prebuilt `godark-*-all.jar` + samples) for the
[`gdx-java-sdk`](https://github.com/gq-godark/gdx-java-sdk) Java client.

## Repo layout

- `examples/` — Gradle runner (`runPlaceOrder`, `runCancelOrder`, …)
- `sdk/lib/` — vendored uber-JAR built from the upstream commit in `sdk/UPSTREAM_REF`
- `sdk/shared/symbols.json` — snapshot copied from the SDK
- `scripts/refresh_sdk.sh` — rebuild `sdk/lib` from a **local** `gdx-java-sdk` checkout + bump pin
- `scripts/package.sh` — assemble the MM ZIP (parity-checked against the pin)
- `.github/workflows/release.yml` — on push to `main`, build ZIP + GitHub Release

## Refresh vendored JAR (maintainers)

From a clean `gdx-java-sdk` checkout at the commit you want to ship:

```bash
bash scripts/refresh_sdk.sh /path/to/gdx-java-sdk
git add sdk/ UPSTREAM_REF
git commit -m "refresh: sync vendored jar with upstream"
```

## Local package smoke test

```bash
UPSTREAM_SRC=/path/to/gdx-java-sdk bash scripts/package.sh
```

## Releases

Download the latest **`gdx-java-sdk-examples-*.zip`** from **GitHub Releases**, unzip, and follow
`README.md` inside the bundle.
