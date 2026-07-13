# CloudBase SQL regression checks

Apply the active schema first, then run `social_security_regression.sql` against
a disposable CloudBase PostgreSQL database. The script checks function
signatures, authentication guards, health-summary validation, feed visibility,
direct-table privileges, and RPC execution grants. The behavioral cases at the
end must also be exercised with two authenticated test identities before a
schema is promoted.
