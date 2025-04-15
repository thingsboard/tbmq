## Backup and Restore Guide for TBMQ Using Docker Compose

### 1. Stop the TBMQ Service

Before performing a backup of the PostgreSQL database, it’s a good practice to stop the TBMQ service to avoid any
interruptions or data inconsistencies.

#### Command:

```bash
docker compose stop tbmq
```

#### Explanation:

- `docker compose stop tbmq`: This command stops the TBMQ container while keeping other services (like PostgreSQL and
  Kafka) running. It ensures that no new data is written to the database during the backup process.

---

### 2. Identify the Docker Network

TBMQ and PostgreSQL are running on a Docker network. You need to identify the name of this network to ensure the backup
container can communicate with the PostgreSQL container.

#### Commands:

```bash
docker network ls
docker network inspect <network_name>
```

#### Explanation:

- `docker network ls`: Lists all available Docker networks, including those created by Docker Compose.
    - Identify the network associated with your TBMQ project. It is typically named `<project_name>_default`, where
      `<project_name>` is the name of the directory containing your `docker-compose.yml` file.
- `docker network inspect <network_name>`: Replace `<network_name>` with the name identified from the previous command.
  This will show you the details of the network, including the IP addresses and containers connected to it.

---

### 3. Perform the Backup

Once the network is identified, you can perform a PostgreSQL database backup using the `pg_dump` command inside a Docker
container. This backup will be saved in the local `./backups` directory.

#### Command:

```bash
docker run --rm --network <network_name> \
-v tbmq-postgres-data:/var/lib/postgresql/data \
-v $(pwd)/backups:/backups \
-e PGPASSWORD=postgres \
postgres:16 \
sh -c 'pg_dump -Fc -v -h postgres -U postgres -d thingsboard_mqtt_broker > /backups/tbmq_backup.dump'
```

#### Command for Windows PowerShell:

```bash
docker run --rm --network <network_name> `
-v tbmq-postgres-data:/var/lib/postgresql/data `
-v ${PWD}/backups:/backups `
-e PGPASSWORD=postgres `
postgres:16 `
sh -c "pg_dump -Fc -v -h postgres -U postgres -d thingsboard_mqtt_broker > /backups/tbmq_backup.dump"
```

#### Explanation:

- `docker run --rm`: Runs a one-time Docker container that will be removed after it completes the backup.
- `--network <network_name>`: Connects the container to the Docker Compose network where PostgreSQL is running. Replace
  `<network_name>` with the name of the Docker network identified earlier (e.g., `tbmq_default`).
- `-v tbmq-postgres-data:/var/lib/postgresql/data`: Mounts the PostgreSQL data volume.
- `-v $(pwd)/backups:/backups`: Mounts a local directory (`./backups`) to store the backup file.
- `-e PGPASSWORD=postgres`: Provides the PostgreSQL password as an environment variable.
- `postgres:16`: The Docker image for PostgreSQL version 16.
- `sh -c 'pg_dump ...'`: The shell command to run the `pg_dump` tool, which creates a compressed custom-format (`-Fc`)
  backup of the `thingsboard_mqtt_broker` database and saves it as `tbmq_backup.dump`.

---

### 4. Restore the Database (If Needed)

If something goes wrong during the upgrade process, and you need to restore the database from the backup, use the
`pg_restore` command inside a Docker container. **Make sure TBMQ is stopped** during the restore process.

#### Command:

```bash
docker run --rm --network <network_name> \
-v tbmq-postgres-data:/var/lib/postgresql/data \
-v $(pwd)/backups:/backups \
-e PGPASSWORD=postgres \
postgres:16 \
sh -c 'pg_restore -c -h postgres -U postgres -d thingsboard_mqtt_broker -v /backups/tbmq_backup.dump'
```

#### Command for Windows PowerShell:

```bash
docker run --rm --network <network_name> `
-v tbmq-postgres-data:/var/lib/postgresql/data `
-v ${PWD}/backups:/backups `
-e PGPASSWORD=postgres `
postgres:16 `
sh -c "pg_restore -c -h postgres -U postgres -d thingsboard_mqtt_broker -v /backups/tbmq_backup.dump"
```

#### Explanation:

- `docker run --rm`: Runs a temporary Docker container and removes it after completion.
- `--network <network_name>`: Connects the container to the same Docker Compose network as PostgreSQL.
- `-v tbmq-postgres-data:/var/lib/postgresql/data`: Mounts the PostgreSQL data volume.
- `-v $(pwd)/backups:/backups`: Mounts the local `./backups` directory where the backup file is stored.
- `-e PGPASSWORD=postgres`: Supplies the PostgreSQL password.
- `postgres:16`: Uses the PostgreSQL 16 Docker image.
- `sh -c 'pg_restore ...'`: The shell command runs `pg_restore` to restore the database from the `tbmq_backup.dump`
  file.
    - `-c`: Cleans (drops) existing database objects before restoring.
    - `-v`: Runs the command in verbose mode to provide detailed output.

---

### 5. Start the TBMQ Service

Once the upgrade, backup or restore process is completed, you can start the TBMQ service again.

#### Command:

```bash
docker compose start tbmq
```

#### Explanation:

- `docker compose start tbmq`: This starts the previously stopped TBMQ service, allowing it to resume normal operation.
