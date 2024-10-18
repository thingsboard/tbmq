## PostgreSQL Backup and Restore in Kubernetes using Pods

This guide provides step-by-step instructions for backing up and restoring your PostgreSQL database running in a
**Kubernetes** environment, using **Pods** that perform one-time tasks for backup and restore.

### 1. Stop the TBMQ StatefulSet Before Backup

Before performing any backup or restore, it’s important to stop the TBMQ StatefulSet to avoid any inconsistencies or
write operations to the database during the process.

#### Command to Scale Down TBMQ StatefulSet:

```bash
kubectl scale statefulset tb-broker --replicas=0
```

This command will stop the TBMQ StatefulSet by scaling it down to 0 replicas, ensuring no traffic hits the database
during backup/restore.

### 2. PostgreSQL Backup Pod

The **`postgres-backup`** pod uses the `pg_dump` command to create a backup of the PostgreSQL database.

#### Pod YAML (`postgres-backup`):

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: postgres-backup
  namespace: thingsboard-mqtt-broker
spec:
  containers:
    - name: postgres-backup
      image: postgres:15
      command:
        - /bin/bash
        - -ec
        - |
          echo "$(date) Backup started!"
          pg_dump -Fc -v -h tb-database -U postgres -d thingsboard_mqtt_broker > /backups/tbmq_backup.dump
          echo "$(date) Backup completed!"
      env:
        - name: PGPASSWORD
          value: "postgres"
      volumeMounts:
        - name: backup-volume
          mountPath: /backups
  restartPolicy: Never
  volumes:
    - name: backup-volume
      persistentVolumeClaim:
        claimName: postgres-pv-claim
```

#### Explanation:

- **`pg_dump` command**: This command creates a custom-format PostgreSQL database backup and saves it in
  `/backups/tbmq_backup.dump`.
- **Environment Variable**: `PGPASSWORD` is used to provide the PostgreSQL password.
- **Volume Mount (`backup-volume`)**: The pod mounts a PersistentVolumeClaim (PVC) where the backup file will be
  stored (`/backups`).
- **Restart Policy**: The pod will not restart automatically after the backup completes.

#### To Run the Backup Pod:

1. **Apply the Backup Pod YAML**:
   ```bash
   kubectl apply -f backup-restore/backup.yml
   ```

2. **Check the Pod Status**:
   ```bash
   kubectl get pods
   ```

3. **View Logs to Confirm Backup Completion**:
   Once the pod completes, check the logs to verify the backup was successful:
   ```bash
   kubectl logs postgres-backup
   ```

4. **Clean Up the Backup Pod** (Optional):
   After completion, you can delete the backup pod if needed:
   ```bash
   kubectl delete pod postgres-backup
   ```

### 3. PostgreSQL Restore Pod

The **`postgres-restore`** pod uses the `pg_restore` command to restore the PostgreSQL database from a backup.

#### Pod YAML (`postgres-restore`):

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: postgres-restore
  namespace: thingsboard-mqtt-broker
spec:
  containers:
    - name: postgres-restore
      image: postgres:15
      command:
        - /bin/bash
        - -ec
        - |
          echo "$(date) Restore started!"
          pg_restore -c -v -h tb-database -U postgres -d thingsboard_mqtt_broker /backups/tbmq_backup.dump || true
          echo "$(date) Restore completed!"
      env:
        - name: PGPASSWORD
          value: "postgres"
      volumeMounts:
        - name: restore-volume
          mountPath: /backups
      workingDir: /backups
  restartPolicy: Never
  volumes:
    - name: restore-volume
      persistentVolumeClaim:
        claimName: postgres-pv-claim
```

#### Explanation:

- **`pg_restore` command**: Restores the PostgreSQL database from the backup file (`/backups/tbmq_backup.dump`).
- **Volume Mount (`restore-volume`)**: The pod mounts the PVC where the backup file is stored.
- **Error Handling**: The `|| true` ensures the pod doesn't exit with an error even if certain constraints or tables
  can't be dropped (e.g., inherited constraints).
- **Restart Policy**: The pod will not restart automatically after completing the restore operation.

#### To Run the Restore Pod:

1. **Apply the Restore Pod YAML**:
   ```bash
   kubectl apply -f backup-restore/restore.yml
   ```

2. **Check the Pod Status**:
   ```bash
   kubectl get pods
   ```

3. **View Logs to Confirm Restore Completion**:
   Once the restore process completes, check the logs:
   ```bash
   kubectl logs postgres-restore
   ```

4. **Verify Restore Success**:
   Check the database directly to ensure the restore was successful, or review the logs for any errors.

5. **Clean Up the Restore Pod** (Optional):
   After completion, you can delete the restore pod:
   ```bash
   kubectl delete pod postgres-restore
   ```

### 4. Start the TBMQ StatefulSet After Restore

Once the restore operation is complete, start the TBMQ StatefulSet by scaling it back up to its previous replica count.

#### Command to Scale Up TBMQ StatefulSet:

```bash
kubectl scale statefulset tb-broker --replicas=2
```

This will bring the TBMQ StatefulSet back online and resume normal operations.
