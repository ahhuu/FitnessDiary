# FitnessDiary CloudBase PostgreSQL schema

The Android app uses its verified CloudBase access token to call PostgreSQL
REST/RPC directly. No database password, management key, or service credential
belongs in the APK.

## Deploy the active schema

In the CloudBase console, open the PostgreSQL SQL editor and execute:

```text
src/main/resources/db/migration/V1__create_social_schema.sql
```

The schema stores only account/social metadata and explicitly confirmed health
summaries. Local health records remain in Room. All app operations use the
`fd_*` RPC functions; authenticated clients have no direct table write access.

Images are intentionally disabled in the current Android client. The nullable
image column remains reserved for a future, separately reviewed upload flow.

The current personal build calls AI providers directly from the private APK.
Provider credentials are configured only in the developer's ignored
`local.properties`; no AI quota or membership data is stored in this schema.

## Verification

Run the static SQL contract checks against a disposable CloudBase PostgreSQL
database after applying the schema:

```text
psql "$CLOUDBASE_DB_URL" -v ON_ERROR_STOP=1 -f tests/social_security_regression.sql
```

The behavioral cases at the end of that file must be exercised with two
authenticated test identities before promoting a schema change.
