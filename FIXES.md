# What was fixed

## Startup crashes (the errors you were seeing)
1. `application.properties` used `${APP_JWT_SECRET}` with **no default** -> `Could not resolve placeholder 'app.security.jwt-secret'`. Now has a dev-only default.
2. `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}` had **no default** -> would have crashed right after #1. Now defaults to `postgres`.
3. `JwtAuthenticationFilter` was a `@Component`, so Spring Boot also registered it as a raw servlet filter and built it (and `JwtService`) during Tomcat start-up. It is now created inside `SecurityConfig` only.

## Backend behaviour
- Unauthenticated/expired token now returns **401** (was a confusing 403).
- Real error messages reach the UI (`GlobalExceptionHandler`) instead of "Request failed (400)"; duplicate/too-long data returns 409 instead of 500.
- `GET /api/group-orders/{id}/items` (lists farmer ids) now requires login; `/actuator/health/**` is public for probes.
- Group-order create/join/close are `@Transactional`.
- Finance sum queries no longer rely on `coalesce(sum(), 0)` type inference (service already handles null).
- CORS origins configurable via `app.cors.allowed-origins`.
- Added unit tests (JWT service, finance summary, and a guard that fails if any property placeholder lacks a default).

## Frontend
- `package.json` pinned (`latest` for every dependency was a future breakage).
- API base is now same-origin `/api` + Vite dev proxy (old default caused CORS failures in Docker at port 3000).
- Expired/missing token returns you to sign-in; network failure gives a clear message.
- Fixed React warning (`useEffect` returning a promise), unhandled promise rejections, errors hidden behind modals, income form sending a stray `category`, empty date handling.

## Infra
- docker-compose: Postgres 18 volume path fixed (`/var/lib/postgresql`), DB bound to localhost, defaults aligned with the app.
- Dockerfile runs as a non-root user.
- CI: image is only pushed on `push` (not on pull requests); `chmod +x mvnw`.
- `.gitignore` now covers `node_modules`, `dist`, IDE folders.

## Known limitations (design, not bugs)
- Any logged-in farmer can create/edit/delete suppliers, products and close group orders, and read any supplier's notifications (no supplier/admin role exists yet).
- Joining a group order is not protected against two simultaneous joins overshooting the target.
- Deleting a supplier does not remove its products/group orders (no DB foreign keys are created by `ddl-auto=update`; `database_schema.sql` has them).
