#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

readonly APP_DIR="/opt/jobsrch"
readonly CONFIG_FILE="/etc/jobsrch-backup.conf"
readonly BACKUP_DIR="/var/backups/jobsrch"
readonly OCI_BIN="/usr/local/bin/oci"
readonly COMPOSE_PROJECT="jobsrch"

if (( EUID != 0 )); then
    echo "JobSrch backup must run as root" >&2
    exit 1
fi

for required_file in "$CONFIG_FILE"; do
    if [[ ! -r "$required_file" ]]; then
        echo "Required file is not readable: $required_file" >&2
        exit 1
    fi
done

# shellcheck source=/dev/null
source "$CONFIG_FILE"

: "${AGE_RECIPIENT:?Set AGE_RECIPIENT in $CONFIG_FILE}"
: "${OCI_NAMESPACE:?Set OCI_NAMESPACE in $CONFIG_FILE}"
: "${OCI_BUCKET_NAME:?Set OCI_BUCKET_NAME in $CONFIG_FILE}"
: "${OCI_OBJECT_PREFIX:?Set OCI_OBJECT_PREFIX in $CONFIG_FILE}"
: "${LOCAL_RETENTION_DAYS:?Set LOCAL_RETENTION_DAYS in $CONFIG_FILE}"

if [[ ! "$AGE_RECIPIENT" =~ ^age1[0-9a-z]+$ ]]; then
    echo "AGE_RECIPIENT is not a valid age recipient" >&2
    exit 1
fi
if [[ ! "$OCI_NAMESPACE" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "OCI_NAMESPACE contains unsupported characters" >&2
    exit 1
fi
if [[ ! "$OCI_BUCKET_NAME" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "OCI_BUCKET_NAME contains unsupported characters" >&2
    exit 1
fi
if [[ "$OCI_OBJECT_PREFIX" == /* || "$OCI_OBJECT_PREFIX" == *".."* ||
      ! "$OCI_OBJECT_PREFIX" =~ ^[A-Za-z0-9._/-]+$ ]]; then
    echo "OCI_OBJECT_PREFIX is unsafe" >&2
    exit 1
fi
if [[ ! "$LOCAL_RETENTION_DAYS" =~ ^[0-9]+$ ]] ||
   (( LOCAL_RETENTION_DAYS < 1 || LOCAL_RETENTION_DAYS > 30 )); then
    echo "LOCAL_RETENTION_DAYS must be between 1 and 30" >&2
    exit 1
fi

for command in /usr/bin/docker /usr/bin/gzip /usr/bin/sha256sum /usr/bin/flock \
        /usr/bin/git /usr/bin/age "$OCI_BIN"; do
    if [[ ! -x "$command" ]]; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

/usr/bin/install -d -m 700 "$BACKUP_DIR"
exec 9>/run/lock/jobsrch-backup.lock
if ! /usr/bin/flock -n 9; then
    echo "Another JobSrch backup is already running" >&2
    exit 1
fi

readonly timestamp="$(/usr/bin/date -u +%Y%m%dT%H%M%SZ)"
readonly final_dir="${BACKUP_DIR}/${timestamp}"
work_dir=""
backend_was_running=0
backend_id=""
mysql_id=""

container_id_for_service() {
    local service="$1"
    local ids
    ids="$(/usr/bin/docker ps --all --quiet \
        --filter "label=com.docker.compose.project=${COMPOSE_PROJECT}" \
        --filter "label=com.docker.compose.service=${service}")"
    if [[ -z "$ids" || "$ids" == *$'\n'* ]]; then
        echo "Expected exactly one ${COMPOSE_PROJECT}/${service} container" >&2
        return 1
    fi
    echo "$ids"
}

start_backend_if_needed() {
    if (( backend_was_running == 1 )); then
        echo "Restarting the JobSrch backend"
        /usr/bin/docker start "$backend_id" >/dev/null
        backend_was_running=0
    fi
}

cleanup() {
    local result=$?
    trap - EXIT INT TERM HUP

    if ! start_backend_if_needed; then
        echo "Failed to restart the JobSrch backend" >&2
        result=1
    fi

    if [[ -n "${work_dir:-}" && -d "$work_dir" ]]; then
        case "$work_dir" in
            "${BACKUP_DIR}"/.partial-*) /usr/bin/rm -rf -- "$work_dir" ;;
            *) echo "Refusing to remove unexpected temporary path: $work_dir" >&2 ;;
        esac
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

backend_id="$(container_id_for_service backend)"
mysql_id="$(container_id_for_service mysql)"
work_dir="$(/usr/bin/mktemp -d "${BACKUP_DIR}/.partial-${timestamp}.XXXXXX")"

if [[ "$(/usr/bin/docker inspect --format '{{.State.Running}}' "$backend_id")" == "true" ]]; then
    backend_was_running=1
    echo "Pausing the JobSrch backend for a consistent database/file backup"
    /usr/bin/docker stop --time 30 "$backend_id" >/dev/null
fi

if [[ "$(/usr/bin/docker inspect --format '{{.State.Running}}' "$mysql_id")" != "true" ]]; then
    echo "The JobSrch MySQL container is not running" >&2
    exit 1
fi

echo "Creating encrypted MySQL backup"
/usr/bin/docker exec "$mysql_id" /bin/sh -eu -c '
    export MYSQL_PWD="$MYSQL_PASSWORD"
    exec mysqldump \
        --user="$MYSQL_USER" \
        --single-transaction \
        --quick \
        --skip-lock-tables \
        --no-tablespaces \
        --set-gtid-purged=OFF \
        --routines \
        --events \
        --triggers \
        --hex-blob \
        "$MYSQL_DATABASE"
' | /usr/bin/gzip -9 | /usr/bin/age \
        --recipient "$AGE_RECIPIENT" \
        --output "${work_dir}/mysql.sql.gz.age"

echo "Creating encrypted resume-file backup"
/usr/bin/docker cp "${backend_id}:/app/data/resumes/." - |
    /usr/bin/gzip -9 |
    /usr/bin/age \
        --recipient "$AGE_RECIPIENT" \
        --output "${work_dir}/resumes.tar.gz.age"

start_backend_if_needed

for archive in mysql.sql.gz.age resumes.tar.gz.age; do
    if [[ ! -s "${work_dir}/${archive}" ]]; then
        echo "Backup archive is empty: $archive" >&2
        exit 1
    fi
done

{
    echo "format_version=1"
    echo "created_utc=${timestamp}"
    echo "git_commit=$(/usr/bin/git -C "$APP_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
    echo "database_archive=mysql.sql.gz.age"
    echo "resume_archive=resumes.tar.gz.age"
} >"${work_dir}/manifest.env"

(
    cd "$work_dir"
    /usr/bin/sha256sum mysql.sql.gz.age resumes.tar.gz.age >SHA256SUMS
)

if [[ -e "$final_dir" ]]; then
    echo "Backup directory already exists: $final_dir" >&2
    exit 1
fi
/usr/bin/mv -- "$work_dir" "$final_dir"
work_dir=""

echo "Uploading encrypted backup to OCI Object Storage"
for filename in mysql.sql.gz.age resumes.tar.gz.age manifest.env SHA256SUMS; do
    "$OCI_BIN" os object put \
        --auth instance_principal \
        --namespace-name "$OCI_NAMESPACE" \
        --bucket-name "$OCI_BUCKET_NAME" \
        --name "${OCI_OBJECT_PREFIX}/${timestamp}/${filename}" \
        --file "${final_dir}/${filename}" \
        --no-multipart \
        --no-overwrite \
        --verify-checksum \
        >/dev/null
    echo "Uploaded ${filename}"
done

echo "Removing local backup sets older than ${LOCAL_RETENTION_DAYS} days"
while IFS= read -r -d '' expired_dir; do
    case "$expired_dir" in
        "${BACKUP_DIR}"/20??????T??????Z)
            /usr/bin/rm -rf -- "$expired_dir"
            ;;
        *)
            echo "Refusing to remove unexpected backup path: $expired_dir" >&2
            exit 1
            ;;
    esac
done < <(
    /usr/bin/find "$BACKUP_DIR" \
        -mindepth 1 -maxdepth 1 -type d \
        -name '20??????T??????Z' \
        -mmin "+$((LOCAL_RETENTION_DAYS * 1440))" \
        -print0
)

echo "JobSrch backup completed: ${timestamp}"
