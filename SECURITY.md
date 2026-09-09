# Security Policy

## Reporting a vulnerability

Please report security vulnerabilities privately — **do not open a public issue**.

- Preferred: [GitHub private vulnerability reporting](https://github.com/mszajner/beanquery/security/advisories/new)
- Or email <mszajner@rexoft.pl>

You'll get an acknowledgement within a few working days. Once a fix is ready
we'll coordinate a release and a disclosure timeline with you.

## Scope

beanquery's job is to keep unregistered fields and raw request strings away from
JPA. Reports about the whitelist being bypassed, request field names reaching
the persistence layer, `LIKE`/`ILIKE` wildcard injection, or a `QueryAuthorizer`
predicate being dropped or evadable are especially welcome.

## Supported versions

Until 1.0.0, only the latest `0.x` release receives fixes.
