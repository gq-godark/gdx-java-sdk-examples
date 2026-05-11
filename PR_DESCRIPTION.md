# gdx-java-sdk-examples bootstrap

## Summary

Initial examples repo: vendored `godark-*-all.jar`, Gradle sample runners, `scripts/package.sh` +
`release.yml` (mirrors `gdx-python-sdk-examples`), and `auto-bump-sdk-pin.yml` for
`repository_dispatch` from `gdx-java-sdk`.

## Required GitHub Actions secret

- **`GDX_JAVA_SDK_TOKEN`** — PAT or fine-grained token with `contents: read` on `gq-godark/gdx-java-sdk`
  (same pattern as `GDX_PYTHON_SDK_TOKEN` in the Python examples repo).

## Manual GitHub setup

See [`gdx-java-sdk` / `PR_DESCRIPTION.md`](https://github.com/gq-godark/gdx-java-sdk/blob/main/PR_DESCRIPTION.md)
for `GDX_APP` scopes and labels shared across the automation chain.
