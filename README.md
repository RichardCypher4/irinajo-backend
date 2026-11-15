# Irin Ajo backend

Spring Boot 3.5 · Java 21 · Maven · PostgreSQL · Flyway · deploys to Railway.

## Run locally

```bash
docker run -d --name irinajo-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=irinajo -p 5432:5432 postgres:16
mvn spring-boot:run            # profile "dev": codes are printed to the log, test numbers get 123456
```

Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI JSON: `/v3/api-docs`

Quick sign-in with a dev test number:

```bash
curl -X POST localhost:8080/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"+2348000000001","channel":"sms"}'
curl -X POST localhost:8080/auth/otp/verify  -H 'Content-Type: application/json' -d '{"phone":"+2348000000001","code":"123456","name":"Tolu"}'
```

## Deploy to Railway

1. Push this folder to a GitHub repo (`.env` files are git-ignored; secrets live only in Railway).
2. Railway → New Project → **Deploy from GitHub repo**. Railway picks up `railway.json` and the `Dockerfile`.
3. In the project add **Database → PostgreSQL**.
4. On the backend service → **Variables**, add everything from `.env.example`. For the database use
   references: `PGHOST=${{Postgres.PGHOST}}`, `PGPORT=${{Postgres.PGPORT}}`, etc.
5. Generate secrets: `openssl rand -base64 48` (JWT_SECRET) and `openssl rand -hex 32` (OTP_PEPPER).
6. Settings → Networking → **Generate domain** (or add `api.irinajo.ng`), and set `PUBLIC_BASE_URL` to it.
7. Create two environments (Railway → Environments): `staging` (`SPRING_PROFILES_ACTIVE=staging`, test numbers on)
   and `production` (`SPRING_PROFILES_ACTIVE=prod`, test numbers forced off, Swagger off).

Flyway creates the schema on first boot. Health check: `/actuator/health/readiness`.

Make an admin: `UPDATE users SET role = 'ADMIN' WHERE phone = '+234...';` then sign in again.

## Endpoints

| Area | Endpoint | Notes |
|---|---|---|
| Auth | `POST /auth/otp/request` `{phone, channel}` | sms or whatsapp. Send `X-Device-Id` header for per-device limits |
| | `POST /auth/otp/verify` `{phone, code, name?}` | returns accessToken (15 min), refreshToken (60 days, sliding), user, profile |
| | `POST /auth/google` `{idToken}` · `POST /auth/apple` `{identityToken, fullName?}` | |
| | `POST /auth/refresh` `{refreshToken}` · `POST /auth/logout` `{refreshToken, everywhere?}` | |
| Account | `GET /me` · `PATCH /me/profile` | partial update + `updatedAt` (last write wins) |
| | `POST /me/photos/upload-url` `{kind: avatar\|cover}` | PUT the JPEG to `uploadUrl`, then PATCH profile `{avatarKey}` |
| | `POST /me/link/phone\|google\|apple` | add sign-in methods to one account |
| | `GET /me/export` · `DELETE /me` | ZIP with JSON + GPX; full deletion |
| | `GET /account/delete` | web deletion page for Google Play |
| Sync | `POST /sync/journeys` `{journeys:[...], keepsakes:[{id, earnedAt}]}` | idempotent; per-item `applied/duplicate/stale` |
| | `GET /sync/changes?since=0&limit=200&scope=recent\|all` | restore with a cursor; area names arrive here |
| Walk Safe | `GET/POST /walk-safe/contacts` · `PATCH/DELETE /walk-safe/contacts/{id}` | max 3 |
| | `POST /walk-safe/contacts/{id}/reset-link` | old link stops working |
| | `POST /walk-safe/sessions` `{clientSessionId}` · `POST .../{id}/points` `{points:[{lat,lng,t}]}` · `POST .../{id}/end` | |
| | `GET /t/{token}` · `/t/{token}/live.json` · `/t/{token}/map.png` | public page, noindex |
| Explore | `GET /walks/nearby?lat&lng&category&hour` | nearby, parks, cafes, landmarks, quiet |
| | `GET /walks/search?q&lat&lng` · `POST /routes/suggest` `{lat,lng,distanceKm,hour}` | |
| Other | `GET /weather?lat&lng` · `GET /content/tips?version` · `GET /content/voice-packs` | |
| | `POST /devices/push-token` `{token, platform, deviceId}` | Expo push |
| Admin | `/admin/tips`, `/admin/walks`, `POST /admin/voice-packs` (multipart) | ROLE_ADMIN |

Errors always look like `{"error":{"code":"OTP_EXPIRED","message":"...","retryable":false}}`;
429 and 502 responses are retryable and include `Retry-After`.

## Security summary

- HTTPS only (`REQUIRE_HTTPS`), HSTS, no cookies, stateless JWT (HS256) on every private endpoint.
- OTP: 6 digits, 10 min, 5 tries, 60 s resend; stored hashed with a pepper; Nigerian mobile ranges only;
  limits per phone / device / IP; hourly volume alarm to a webhook (SMS-pumping protection).
- Google/Apple tokens verified against their published keys, issuer and our client IDs.
- Refresh tokens stored as SHA-256 only; logout revokes; "everywhere" revokes all.
- Per-IP rate limits on all routes, tighter on `/auth`.
- Gzip in both directions, with a 5 MB cap on inflated request bodies.
- Logs mask phone numbers and never contain routes.
- Location privacy: first/last 200 m trimmed on anything another person sees; Walk Safe points deleted 24 h after the walk.

## Known limits / next steps

- Rate limits and caches are in memory: fine for one Railway instance. Move to Redis before scaling out.
- Public Nominatim/Overpass are rate-limited; self-host or use Mapbox geocoding at volume.
- Safety ratings: Felt Safe answers are aggregated per street cell and hour and adjust each walk's
  base rating. The OSM lighting/footpath scoring that sets those base ratings is an offline job not included here.
- Route suggestions use curated loops, plus GraphHopper round trips when `GRAPHHOPPER_KEY` is set.
