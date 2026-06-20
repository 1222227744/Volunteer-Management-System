#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-volunteer_service}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
INIT_DB=false
WITH_DEMO=false

usage() {
  cat <<'USAGE'
Usage: scripts/setup-ubuntu.sh [options]

Options:
  --db-host HOST        MySQL host, default 127.0.0.1
  --db-port PORT        MySQL port, default 3306
  --db-name NAME        MySQL database, default volunteer_service
  --db-user USER        MySQL user, default root
  --db-password PASS    MySQL password. If omitted, the script prompts.
  --init-db             Run init.sql through mysql.
  --with-demo           After --init-db, import demo-data.sql.
  -h, --help            Show this help.

Examples:
  scripts/setup-ubuntu.sh --db-user root --db-password 'secret'
  scripts/setup-ubuntu.sh --db-user root --db-password 'secret' --init-db --with-demo
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db-host) DB_HOST="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --db-name) DB_NAME="$2"; shift 2 ;;
    --db-user) DB_USER="$2"; shift 2 ;;
    --db-password) DB_PASSWORD="$2"; shift 2 ;;
    --init-db) INIT_DB=true; shift ;;
    --with-demo) WITH_DEMO=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "${DB_PASSWORD}" ]]; then
  read -rsp "MySQL password for ${DB_USER}@${DB_HOST}: " DB_PASSWORD
  echo
fi

copy_if_missing() {
  local source_file="$1"
  local target_file="$2"
  if [[ ! -f "${target_file}" ]]; then
    cp "${source_file}" "${target_file}"
    echo "created ${target_file#${ROOT_DIR}/}"
  else
    echo "kept existing ${target_file#${ROOT_DIR}/}"
  fi
}

replace_or_append() {
  local file="$1"
  local key="$2"
  local value="$3"
  local escaped
  escaped="$(printf '%s' "${value}" | sed -e 's/[\/&]/\\&/g')"
  if grep -q "^${key}=" "${file}"; then
    sed -i "s/^${key}=.*/${key}=${escaped}/" "${file}"
  else
    printf '\n%s=%s\n' "${key}" "${value}" >> "${file}"
  fi
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
  fi
}

BACKEND_ENV="${ROOT_DIR}/backend/.env"
FRONTEND_ENV="${ROOT_DIR}/frontend/.env"

copy_if_missing "${ROOT_DIR}/backend/.env.example" "${BACKEND_ENV}"
copy_if_missing "${ROOT_DIR}/frontend/.env.example" "${FRONTEND_ENV}"

replace_or_append "${BACKEND_ENV}" "SPRING_DATASOURCE_URL" "jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"
replace_or_append "${BACKEND_ENV}" "SPRING_DATASOURCE_USERNAME" "${DB_USER}"
replace_or_append "${BACKEND_ENV}" "SPRING_DATASOURCE_PASSWORD" "${DB_PASSWORD}"
replace_or_append "${BACKEND_ENV}" "SPRING_JPA_HIBERNATE_DDL_AUTO" "none"
replace_or_append "${BACKEND_ENV}" "VMS_BOOTSTRAP_ENABLED" "false"
replace_or_append "${BACKEND_ENV}" "VMS_JWT_SECRET" "$(generate_secret)"
replace_or_append "${BACKEND_ENV}" "VMS_FILE_STORAGE_DIR" "./uploads"

replace_or_append "${FRONTEND_ENV}" "VITE_BACKEND_ORIGIN" "http://127.0.0.1:8080"

mkdir -p "${ROOT_DIR}/backend/uploads"

if [[ "${INIT_DB}" == true ]]; then
  if ! command -v mysql >/dev/null 2>&1; then
    echo "mysql command not found. Install mysql-client or run SOURCE manually." >&2
    exit 1
  fi
  echo "importing backend/src/main/resources/sql/init.sql"
  MYSQL_PWD="${DB_PASSWORD}" mysql --default-character-set=utf8mb4 -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" < "${ROOT_DIR}/backend/src/main/resources/sql/init.sql"
  if [[ "${WITH_DEMO}" == true ]]; then
    echo "importing backend/src/main/resources/sql/demo-data.sql"
    MYSQL_PWD="${DB_PASSWORD}" mysql --default-character-set=utf8mb4 -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" < "${ROOT_DIR}/backend/src/main/resources/sql/demo-data.sql"
  fi
fi

echo "setup complete"
echo "backend env: backend/.env"
echo "frontend env: frontend/.env"
echo "demo data SQL: SOURCE backend/src/main/resources/sql/demo-data.sql;"
