# Production backup and restoration

JobSrch uses matching, timestamped backups for the MySQL database and private
resume volume. The backup job briefly stops only the backend while capturing
both resources, restarts it before uploading, encrypts every data archive with
`age`, retains seven daily sets locally, and uploads write-once copies to a
private OCI Object Storage bucket. OCI lifecycle management deletes remote sets
after 30 days.

The expected recovery point objective is 24 hours. While the project remains
small, a routine restoration should take less than one hour. Deleted account or
resume data can remain in encrypted backups for at most 30 days.

## Security model

- Generate the `age` identity on a trusted computer, not the VM. Store the
  `AGE-SECRET-KEY` in a password manager or offline recovery location. Only the
  public `age1...` recipient belongs on the VM.
- Keep the Object Storage bucket private and use Oracle-managed encryption in
  addition to the client-side `age` encryption.
- Authorize the one production VM through an OCI dynamic group. Do not create a
  long-lived IAM user API key on the VM.
- Give the VM write-once `OBJECT_CREATE` permission only for the `daily/*`
  prefix in the backup bucket. It cannot list, read, overwrite, or delete cloud
  backups. OCI's lifecycle service owns expiration.
- `/etc/jobsrch-backup.conf` is root-owned mode `600`; local backup directories
  are mode `700`.

## One-time OCI configuration

1. Create a private Standard-tier bucket named `jobsrch-backups`. Leave object
   versioning disabled because every backup has a unique timestamp.
2. Record the Object Storage namespace shown on the bucket details page.
3. Create a dynamic group named `jobsrch-backup-vm` with an exact-instance rule:

   ```text
   instance.id = '<production-instance-OCID>'
   ```

4. In the tenancy root, create a policy for the dynamic group. Tenancies using
   identity domains normally qualify the group as
   `<identity-domain>/jobsrch-backup-vm`:

   ```text
   Allow dynamic-group <jobsrch-backup-vm> to manage objects in compartment <backup-compartment> where all {target.bucket.name = 'jobsrch-backups', target.object.name = 'daily/*', request.permission = 'OBJECT_CREATE'}
   ```

5. Authorize Object Storage lifecycle processing in that region. Create this
   policy in the tenancy root, scoped to the bucket's compartment:

   ```text
   Allow service objectstorage-<region-identifier> to manage object-family in compartment <backup-compartment>
   ```

6. On the bucket's **Policies** page, create a lifecycle rule that deletes
   objects with the `daily/` prefix after 30 days. The Console checks whether
   the service policy is present.

IAM changes can take several minutes to propagate.

## Trusted-computer encryption key

On Windows PowerShell, install `age` from its official Winget package and create
the identity outside the repository:

```powershell
winget install --exact --id FiloSottile.age
$keyDirectory = Join-Path $env:USERPROFILE '.config\jobsrch-backup'
New-Item -ItemType Directory -Force $keyDirectory | Out-Null
$identityPath = Join-Path $keyDirectory 'age-identity.txt'
age-keygen -o $identityPath
age-keygen -y $identityPath
```

The last command prints the public `age1...` recipient that goes in the VM
configuration. Never paste or upload the `AGE-SECRET-KEY-...` line.

## VM installation

Install `age` and the OCI CLI, then verify the paths expected by the backup
script:

```bash
sudo apt update
sudo apt install -y age
age --version
sudo /usr/local/bin/oci --version
```

Install the OCI CLI from Oracle's documented Linux installer so the executable
is `/usr/local/bin/oci`. Instance-principal authentication does not need an OCI
user configuration or private API key.

Install the configuration and systemd units:

```bash
cd /opt/jobsrch
sudo install -d -m 700 /var/backups/jobsrch
sudo install -o root -g root -m 600 \
  ops/backup/jobsrch-backup.conf.example /etc/jobsrch-backup.conf
sudoedit /etc/jobsrch-backup.conf
sudo install -o root -g root -m 750 \
  ops/backup/jobsrch-backup.sh /usr/local/sbin/jobsrch-backup
sudo install -o root -g root -m 750 \
  ops/backup/jobsrch-restore-test.sh /usr/local/sbin/jobsrch-restore-test
sudo install -o root -g root -m 644 \
  ops/systemd/jobsrch-backup.service /etc/systemd/system/jobsrch-backup.service
sudo install -o root -g root -m 644 \
  ops/systemd/jobsrch-backup.timer /etc/systemd/system/jobsrch-backup.timer
sudo systemd-analyze verify \
  /etc/systemd/system/jobsrch-backup.service \
  /etc/systemd/system/jobsrch-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now jobsrch-backup.timer
```

The service executes only the root-owned `/usr/local/sbin` copy. It does not
execute a script or Compose file owned by the normal VM user. After a reviewed
repository update changes either backup script, reinstall the root-owned copies
before the next scheduled run.

## First backup verification

Run the service once and inspect only status, filenames, sizes, and checksums:

```bash
sudo systemctl start jobsrch-backup.service
sudo systemctl show jobsrch-backup.service -p Result -p ExecMainStatus
sudo journalctl -u jobsrch-backup.service -n 50 --no-pager
sudo find /var/backups/jobsrch -maxdepth 2 -type f \
  -printf '%M %u:%g %s %p\n'
sudo systemctl list-timers jobsrch-backup.timer --no-pager
```

Confirm the matching timestamp directory and four objects appear beneath the
bucket's `daily/` prefix. Do not display `.env.production`, the database dump,
the resume archive, cookies, or encryption identities.

## Real restoration test

Copy one timestamped encrypted backup and the `age` identity to the VM only for
the duration of the test. Store the identity in a root-only temporary file.
Run:

```bash
sudo /usr/local/sbin/jobsrch-restore-test \
  /var/backups/jobsrch/<timestamp> \
  /root/<temporary-age-identity-file>
```

The script verifies checksums, authenticated decryption, gzip/tar integrity,
imports the SQL into an isolated `mysql:8.4` container with no network, verifies
Flyway and table state, and matches every resume record to a restored file. It
removes the temporary container and plaintext restoration directory on exit;
production containers and volumes are never modified.

Afterward, delete the temporary identity from the VM and confirm it is absent.
Retain the original identity only in the trusted off-VM recovery locations.
Perform this test after initial setup, every three months, and after material
database or resume-storage changes.

## Operations

Check the timer and last result periodically:

```bash
sudo systemctl list-timers jobsrch-backup.timer --no-pager
sudo systemctl show jobsrch-backup.service -p Result -p ExecMainStatus
sudo journalctl -u jobsrch-backup.service --since '2 days ago' --no-pager
```

Create an extra backup before migrations, destructive maintenance, or large
deployments. A backup is not considered successful until an encrypted local set
exists, the matching cloud objects exist, and a restoration test has passed.
