# Ride Buddy Backend — Setup

Local development (macOS / Windows) and production on a Linux VPS — with or without Docker.

Repos

- [Requirements](#requirements)
- [Repository layout](#repository-layout)
- [Local setup — macOS](#local-setup--macos)
- [Local setup — Windows](#local-setup--windows)
- [Environment variables](#environment-variables)
- [Production — Linux VPS (Docker)](#production--linux-vps-docker)
- [Production — Linux VPS (without Docker)](#production--linux-vps-without-docker)
- [SQL migrations](#sql-migrations)
- [Verify the install](#verify-the-install)
- [Troubleshooting](#troubleshooting)

---

## Requirements

| Piece | Version / notes |
|-------|-----------------|
| Java | **17** (Temurin / OpenJDK) |
| Maven | **3.9+** (or use Docker-only builds) |
| PostgreSQL | **16** with **PostGIS** (app uses PostGIS image locally) |
| Docker | Optional locally; recommended on VPS |
| Git | To clone [alnlabs/ride-buddy-backend](https://github.com/alnlabs/ride-buddy-backend) |

Dev default: **mock OTP `123456`** (`APP_AUTH_MOCK_OTP=true`). Turn this **off** in production.

---

## Repository layout

```
ride-buddy-backend/
├── sql/                 # Versioned SQL migrations (source of truth)
├── scripts/
│   ├── run-local.sh     # Docker Postgres + migrate + spring-boot:run
│   └── migrate.sh       # Apply sql/* via host psql
├── docker-compose.yml   # Dev Postgres (host port 5433 → container 5432)
├── Dockerfile           # Multi-stage JAR image
├── .env.example
└── src/
```

Default JDBC URL in `application.yml`:

`jdbc:postgresql://localhost:5433/ridebuddy`  
(compose maps container `5432` → host **`5433`** so a local Postgres on `5432` does not clash.)

---

## Local setup — macOS

### 1. Install tools

```bash
# Homebrew
brew install openjdk@17 maven git
# Optional: Docker Desktop for Postgres
# https://www.docker.com/products/docker-desktop/
```

Ensure `java -version` prints 17 and `mvn -v` works. On Apple Silicon, Docker Desktop’s PostGIS service uses `platform: linux/amd64` as defined in `docker-compose.yml`.

### 2. Clone and env

```bash
git clone https://github.com/alnlabs/ride-buddy-backend.git
cd ride-buddy-backend
cp .env.example .env
# Edit .env if needed — defaults match docker-compose
```

### 3. Run (recommended — one command)

Docker Desktop must be running.

```bash
./run-local.sh
# or: ./scripts/run-local.sh
```

This starts PostGIS, applies every file under `sql/`, then `mvn spring-boot:run`.

### 4. Run step-by-step

```bash
docker compose up -d postgres

# Migrations via Docker (no host psql required)
docker compose exec -T -e PGPASSWORD=ridebuddy postgres \
  psql -U ridebuddy -d ridebuddy -v ON_ERROR_STOP=1 -f /sql/000_schema_migrations.sql
# Prefer ./scripts/run-local.sh for the full migration loop, or install psql:
#   brew install libpq && brew link --force libpq
./scripts/migrate.sh

export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

### 5. Point the Flutter app at the API

- iOS Simulator: `API_BASE_URL=http://127.0.0.1:8080/api/v1`
- Android emulator: `API_BASE_URL=http://10.0.2.2:8080/api/v1`
- Physical phone: `http://<Mac-LAN-IP>:8080/api/v1` (same Wi‑Fi / hotspot)

---

## Local setup — Windows

### 1. Install tools

1. **Git for Windows** — https://git-scm.com/download/win  
2. **Temurin 17** — https://adoptium.net/  
3. **Maven** — https://maven.apache.org/download.cgi (add `bin` to `PATH`)  
4. **Docker Desktop for Windows** — https://www.docker.com/products/docker-desktop/  

In PowerShell:

```powershell
java -version   # 17
mvn -v
docker version
```

### 2. Clone and env

```powershell
git clone https://github.com/alnlabs/ride-buddy-backend.git
cd ride-buddy-backend
copy .env.example .env
```

### 3. Run with Docker Postgres

Git Bash (installed with Git for Windows) can run the shell scripts:

```bash
# Git Bash
./scripts/run-local.sh
```

Or PowerShell without the shell script:

```powershell
docker compose up -d postgres

# Wait until healthy, then apply SQL (files are mounted at /sql in the container)
docker compose exec -T -e PGPASSWORD=ridebuddy postgres `
  psql -U ridebuddy -d ridebuddy -v ON_ERROR_STOP=1 -f /sql/000_schema_migrations.sql

# Repeat for each sql/*.sql not yet in schema_migrations, or use Git Bash + migrate.sh
# Then start the API:
$env:DB_HOST="localhost"
$env:DB_PORT="5433"
$env:DB_NAME="ridebuddy"
$env:DB_USER="ridebuddy"
$env:DB_PASSWORD="ridebuddy"
$env:APP_AUTH_MOCK_OTP="true"
$env:JWT_SECRET="ride-buddy-dev-secret-key-must-be-at-least-32-chars-long"
mvn spring-boot:run
```

### 4. Android emulator on Windows

In the mobile app `.env`:

```env
API_BASE_URL=http://10.0.2.2:8080/api/v1
```

---

## Environment variables

Copy from [`.env.example`](.env.example):

| Variable | Local default | Production |
|----------|---------------|------------|
| `DB_HOST` | `localhost` | Postgres hostname / `postgres` in Compose |
| `DB_PORT` | `5433` | `5432` if connecting inside Docker network |
| `DB_NAME` | `ridebuddy` | same or dedicated DB |
| `DB_USER` / `DB_PASSWORD` | `ridebuddy` | **strong unique password** |
| `JWT_SECRET` | long dev string | **≥32 random chars**, rotate periodically |
| `APP_AUTH_MOCK_OTP` | `true` | **`false`** (+ real SMS/OTP provider later) |
| `APP_CORS_ORIGINS` | `*` | Your app / web origins only |
| `SERVER_PORT` | `8080` | `8080` or behind reverse proxy |
| Share base URL | `application.yml` → `app.share.ride-base-url` | Your public ride link domain |

Never commit a real `.env` (gitignored).

---

## Production — Linux VPS (Docker)

Assumes Ubuntu 22.04/24.04 (or similar), root or sudo user, and a domain pointed at the VPS (optional but recommended for TLS).

### 1. Server basics

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl ca-certificates
# Docker Engine + Compose plugin:
# https://docs.docker.com/engine/install/ubuntu/
sudo usermod -aG docker $USER   # log out/in after
```

### 2. Production Compose file

Create `docker-compose.prod.yml` on the server (or in the repo clone):

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME:-ridebuddy}
      POSTGRES_USER: ${DB_USER:-ridebuddy}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - ride_buddy_pgdata:/var/lib/postgresql/data
      - ./sql:/sql:ro
    # Do NOT publish 5432 to the public internet.
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 10

  api:
    build: .
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME:-ridebuddy}
      DB_USER: ${DB_USER:-ridebuddy}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      APP_AUTH_MOCK_OTP: "false"
      APP_CORS_ORIGINS: ${APP_CORS_ORIGINS}
      SERVER_PORT: "8080"
    ports:
      - "127.0.0.1:8080:8080"   # expose only via reverse proxy

volumes:
  ride_buddy_pgdata:
```

Server `.env` (same directory as Compose):

```bash
DB_NAME=ridebuddy
DB_USER=ridebuddy
DB_PASSWORD=REPLACE_WITH_STRONG_PASSWORD
JWT_SECRET=REPLACE_WITH_LONG_RANDOM_SECRET
APP_CORS_ORIGINS=https://your-app-domain.example
```

### 3. Deploy

```bash
git clone https://github.com/alnlabs/ride-buddy-backend.git
cd ride-buddy-backend
# place docker-compose.prod.yml + .env

docker compose -f docker-compose.prod.yml up -d postgres
# Wait for healthy, then migrate:
docker compose -f docker-compose.prod.yml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
  psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f /sql/000_schema_migrations.sql

# Apply remaining SQL files once each (track in schema_migrations), e.g. loop:
for f in sql/*.sql; do
  name=$(basename "$f")
  applied=$(docker compose -f docker-compose.prod.yml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
    psql -U "$DB_USER" -d "$DB_NAME" -tAc "SELECT 1 FROM schema_migrations WHERE filename='$name'" | tr -d '[:space:]')
  if [ "$applied" = "1" ]; then echo "skip $name"; continue; fi
  echo "apply $name"
  docker compose -f docker-compose.prod.yml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "/sql/$name"
  docker compose -f docker-compose.prod.yml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
    psql -U "$DB_USER" -d "$DB_NAME" -c "INSERT INTO schema_migrations (filename) VALUES ('$name') ON CONFLICT DO NOTHING;"
done

docker compose -f docker-compose.prod.yml up -d --build api
```

### 4. TLS reverse proxy (Caddy example)

```bash
sudo apt install -y caddy
```

`/etc/caddy/Caddyfile`:

```
api.yourdomain.com {
    reverse_proxy 127.0.0.1:8080
}
```

```bash
sudo systemctl reload caddy
```

Mobile `API_BASE_URL` → `https://api.yourdomain.com/api/v1`.

### 5. Updates

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build api
# Run any new sql/*.sql files the same way as above
```

---

## Production — Linux VPS (without Docker)

### 1. Install Java, Maven, Nginx

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless maven nginx git
java -version
```

### 2. PostgreSQL + PostGIS

```bash
sudo apt install -y postgresql postgresql-contrib postgis postgresql-16-postgis-3
# Package name may vary by Ubuntu version — use apt-cache search postgis

sudo -u postgres createuser -P ridebuddy     # choose a strong password
sudo -u postgres createdb -O ridebuddy ridebuddy
sudo -u postgres psql -d ridebuddy -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

Listen only on localhost unless you know you need remote DB access. Edit `pg_hba.conf` accordingly.

### 3. App user and code

```bash
sudo useradd -r -m -d /opt/ridebuddy -s /bin/bash ridebuddy || true
sudo mkdir -p /opt/ridebuddy/app
sudo chown -R "$USER":"$USER" /opt/ridebuddy/app
cd /opt/ridebuddy/app
git clone https://github.com/alnlabs/ride-buddy-backend.git .
```

### 4. Migrate

```bash
export PGPASSWORD='YOUR_DB_PASSWORD'
# Host Postgres usually uses 5432
export DB_HOST=localhost DB_PORT=5432 DB_NAME=ridebuddy DB_USER=ridebuddy

# If scripts/migrate.sh is available and psql is installed:
./scripts/migrate.sh

# Or apply manually:
psql -h localhost -U ridebuddy -d ridebuddy -v ON_ERROR_STOP=1 -f sql/000_schema_migrations.sql
for f in sql/*.sql; do
  name=$(basename "$f")
  applied=$(psql -h localhost -U ridebuddy -d ridebuddy -tAc "SELECT 1 FROM schema_migrations WHERE filename='$name'" | tr -d '[:space:]')
  [ "$applied" = "1" ] && continue
  psql -h localhost -U ridebuddy -d ridebuddy -v ON_ERROR_STOP=1 -f "$f"
  psql -h localhost -U ridebuddy -d ridebuddy -c "INSERT INTO schema_migrations (filename) VALUES ('$name') ON CONFLICT DO NOTHING;"
done
```

### 5. Build the JAR

```bash
mvn -DskipTests package
ls target/*.jar
```

### 6. systemd unit

`/etc/systemd/system/ridebuddy-api.service`:

```ini
[Unit]
Description=Ride Buddy API
After=network.target postgresql.service

[Service]
User=ridebuddy
WorkingDirectory=/opt/ridebuddy/app
Environment=DB_HOST=localhost
Environment=DB_PORT=5432
Environment=DB_NAME=ridebuddy
Environment=DB_USER=ridebuddy
Environment=DB_PASSWORD=YOUR_DB_PASSWORD
Environment=JWT_SECRET=YOUR_LONG_RANDOM_SECRET
Environment=APP_AUTH_MOCK_OTP=false
Environment=APP_CORS_ORIGINS=https://your-app-domain.example
Environment=SERVER_PORT=8080
ExecStart=/usr/bin/java -jar /opt/ridebuddy/app/target/ride-buddy-backend-0.1.0-SNAPSHOT.jar
Restart=on-failure
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
sudo chown -R ridebuddy:ridebuddy /opt/ridebuddy/app
sudo systemctl daemon-reload
sudo systemctl enable --now ridebuddy-api
sudo systemctl status ridebuddy-api
```

JAR name follows `pom.xml` version — adjust `ExecStart` if the artifact name differs.

### 7. Nginx reverse proxy + TLS

`/etc/nginx/sites-available/ridebuddy-api`:

```nginx
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/ridebuddy-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

### 8. Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
# Do not expose raw Postgres (5432) publicly.
```

---

## SQL migrations

- Location: [`sql/`](sql/) — numbered `000_…`, `001_…`, …
- Tracking table: `schema_migrations`
- Local helper: [`scripts/migrate.sh`](scripts/migrate.sh) (needs `psql` on `PATH`)
- Docker local path: `./scripts/run-local.sh` applies migrations inside the Postgres container

Always run new migration files on production **before** (or right after) deploying a JAR/image that depends on them. Hibernate `ddl-auto` is **`none`**.

---

## Verify the install

```bash
curl -s http://127.0.0.1:8080/actuator/health
# {"status":"UP", ...}

# Swagger (if exposed):
open http://127.0.0.1:8080/swagger-ui.html   # macOS
# Windows: start http://127.0.0.1:8080/swagger-ui.html
```

Auth smoke (mock OTP enabled):

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/otp/request \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210"}'

curl -s -X POST http://127.0.0.1:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210","code":"123456","displayName":"Dev"}'
```

---

## Troubleshooting

| Issue | What to check |
|-------|----------------|
| Port `5432` already in use | Dev Compose uses host **`5433`**. Keep `DB_PORT=5433` for local. |
| `psql: command not found` | Use Docker exec to run SQL, or install client tools (`libpq` / `postgresql-client`). |
| Flutter can’t reach API | Emulator ≠ `localhost`. Use `10.0.2.2` (Android) or LAN IP (device). Allow firewall for `8080` on the laptop. |
| JWT / 401 after restart | Same `JWT_SECRET` must persist across deploys. |
| Production OTP always `123456` | Set `APP_AUTH_MOCK_OTP=false` and integrate a real OTP channel. |
| Mail / office verification | Placeholder only — codes are logged / mocked until SMTP is wired. |

---

## Related docs

- [README.md](README.md) — quick start and API overview  
- [plan.md](plan.md) — product / API terminology  
- [`.env.example`](.env.example) — env template  
