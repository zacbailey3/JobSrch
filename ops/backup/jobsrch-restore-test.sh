#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

readonly BACKUP_ROOT="/var/backups/jobsrch"

if (( EUID != 0 )); then
    echo "JobSrch restoration test must run as root" >&2
    exit 1
fi
if (( $# != 2 )); then
    echo "Usage: $0 /var/backups/jobsrch/<timestamp> /path/to/age-identity.txt" >&2
    exit 2
fi

backup_dir="$(/usr/bin/realpath -- "$1")"
identity_file="$(/usr/bin/realpath -- "$2")"

case "$backup_dir" in
    "${BACKUP_ROOT}"/20??????T??????Z) ;;
    *)
        echo "Backup must be a timestamped directory beneath ${BACKUP_ROOT}" >&2
        exit 1
        ;;
esac
if [[ ! -r "$identity_file" ]]; then
    echo "Age identity is not readable: $identity_file" >&2
    exit 1
fi

for command in /usr/bin/docker /usr/bin/age /usr/bin/gzip /usr/bin/tar \
        /usr/bin/sha256sum /usr/bin/find /usr/bin/basename /usr/bin/grep; do
    if [[ ! -x "$command" ]]; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done
for backup_file in mysql.sql.gz.age resumes.tar.gz.age manifest.env SHA256SUMS; do
    if [[ ! -s "${backup_dir}/${backup_file}" ]]; then
        echo "Required backup file is missing or empty: $backup_file" >&2
        exit 1
    fi
done

restore_root="$(/usr/bin/mktemp -d /var/tmp/jobsrch-restore-test.XXXXXX)"
container_name="jobsrch-restore-test-$$_${RANDOM}"

cleanup() {
    local result=$?
    trap - EXIT INT TERM HUP

    case "$container_name" in
        jobsrch-restore-test-*)
            /usr/bin/docker rm -f "$container_name" >/dev/null 2>&1 || true
            ;;
    esac
    case "$restore_root" in
        /var/tmp/jobsrch-restore-test.*)
            /usr/bin/rm -rf -- "$restore_root"
            ;;
        *)
            echo "Refusing to remove unexpected restoration path: $restore_root" >&2
            result=1
            ;;
    esac
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

backup_timestamp="$(/usr/bin/basename -- "$backup_dir")"
if ! /usr/bin/grep --quiet --fixed-strings --line-regexp \
        "format_version=1" "${backup_dir}/manifest.env" ||
   ! /usr/bin/grep --quiet --fixed-strings --line-regexp \
        "created_utc=${backup_timestamp}" "${backup_dir}/manifest.env"; then
    echo "Backup manifest does not match its timestamped directory" >&2
    exit 1
fi

mysql_expected=""
resume_expected=""
checksum_lines=0
while read -r checksum filename extra; do
    checksum_lines=$((checksum_lines + 1))
    if [[ -n "${extra:-}" || ! "$checksum" =~ ^[0-9a-f]{64}$ ]]; then
        echo "Invalid checksum manifest entry" >&2
        exit 1
    fi
    case "$filename" in
        mysql.sql.gz.age) mysql_expected="$checksum" ;;
        resumes.tar.gz.age) resume_expected="$checksum" ;;
        *)
            echo "Unexpected checksum target: $filename" >&2
            exit 1
            ;;
    esac
done <"${backup_dir}/SHA256SUMS"

if (( checksum_lines != 2 )) || [[ -z "$mysql_expected" || -z "$resume_expected" ]]; then
    echo "Checksum manifest must contain exactly the two encrypted archives" >&2
    exit 1
fi
read -r mysql_actual _ < <(/usr/bin/sha256sum "${backup_dir}/mysql.sql.gz.age")
read -r resume_actual _ < <(/usr/bin/sha256sum "${backup_dir}/resumes.tar.gz.age")
if [[ "$mysql_actual" != "$mysql_expected" || "$resume_actual" != "$resume_expected" ]]; then
    echo "Encrypted archive checksum verification failed" >&2
    exit 1
fi
echo "Encrypted archive checksums passed"

echo "Decrypting backup into an isolated temporary directory"
/usr/bin/age --decrypt --identity "$identity_file" \
    --output "${restore_root}/mysql.sql.gz" \
    "${backup_dir}/mysql.sql.gz.age"
/usr/bin/age --decrypt --identity "$identity_file" \
    --output "${restore_root}/resumes.tar.gz" \
    "${backup_dir}/resumes.tar.gz.age"
/usr/bin/gzip --test "${restore_root}/mysql.sql.gz"
/usr/bin/gzip --test "${restore_root}/resumes.tar.gz"

while IFS= read -r archive_entry; do
    if [[ "$archive_entry" == /* || "/${archive_entry}/" == *"/../"* ]]; then
        echo "Unsafe resume archive entry: $archive_entry" >&2
        exit 1
    fi
done < <(/usr/bin/tar -tzf "${restore_root}/resumes.tar.gz")

/usr/bin/install -d -m 700 "${restore_root}/resumes"
/usr/bin/tar \
    --extract --gzip \
    --no-same-owner --no-same-permissions \
    --directory "${restore_root}/resumes" \
    --file "${restore_root}/resumes.tar.gz"

root_password="$(/usr/bin/tr -d '-' </proc/sys/kernel/random/uuid)"
echo "Starting isolated MySQL restoration container"
/usr/bin/docker run --detach \
    --name "$container_name" \
    --network none \
    --env "MYSQL_ROOT_PASSWORD=${root_password}" \
    --env MYSQL_DATABASE=jobsrch_restore \
    mysql:8.4 >/dev/null

mysql_ready=0
for _ in {1..60}; do
    if /usr/bin/docker exec \
            --env "MYSQL_PWD=${root_password}" \
            "$container_name" \
            mysqladmin --user=root ping --silent >/dev/null 2>&1; then
        mysql_ready=1
        break
    fi
    /usr/bin/sleep 2
done
if (( mysql_ready != 1 )); then
    echo "Temporary MySQL container did not become ready" >&2
    exit 1
fi

echo "Importing MySQL backup"
/usr/bin/gzip --decompress --stdout "${restore_root}/mysql.sql.gz" |
    /usr/bin/docker exec --interactive \
        --env "MYSQL_PWD=${root_password}" \
        "$container_name" \
        mysql --user=root --binary-mode jobsrch_restore

mysql_query() {
    /usr/bin/docker exec \
        --env "MYSQL_PWD=${root_password}" \
        "$container_name" \
        mysql --user=root --batch --skip-column-names \
        --database jobsrch_restore \
        --execute "$1"
}

table_count="$(mysql_query \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='jobsrch_restore';")"
if [[ ! "$table_count" =~ ^[0-9]+$ ]] || (( table_count < 1 )); then
    echo "Restored database contains no tables" >&2
    exit 1
fi

flyway_version="$(mysql_query \
    "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;")"
resume_rows="$(mysql_query "SELECT COUNT(*) FROM resumes;")"
resume_files="$(/usr/bin/find "${restore_root}/resumes" -type f | /usr/bin/wc -l)"
missing_resumes=0

while IFS= read -r stored_filename; do
    if [[ "$stored_filename" != "$(/usr/bin/basename -- "$stored_filename")" ||
          ! -f "${restore_root}/resumes/${stored_filename}" ]]; then
        echo "Restored resume file is missing or unsafe: $stored_filename" >&2
        missing_resumes=$((missing_resumes + 1))
    fi
done < <(mysql_query "SELECT stored_filename FROM resumes ORDER BY stored_filename;")

if (( missing_resumes != 0 )) || [[ "$resume_rows" != "$resume_files" ]]; then
    echo "Resume metadata/files do not match: rows=${resume_rows}, files=${resume_files}" >&2
    exit 1
fi

echo "JobSrch restoration test passed"
echo "Database tables: ${table_count}"
echo "Flyway version: ${flyway_version}"
echo "Resume records/files: ${resume_rows}/${resume_files}"
