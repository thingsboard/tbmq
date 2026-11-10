# Docker configuration for TBMQ

This folder containing scripts and Docker Compose configurations to run TBMQ.

## Prerequisites

Before starting please make sure [Docker](https://docs.docker.com/install/) is installed in your system.

## Installation

Execute the following command to create volumes for the services.

```
./scripts/docker-create-volumes.sh
```

Execute the following command to run installation:

```
./scripts/docker-install-tbmq.sh
```

## Running

Execute the following command to start services:

```
./scripts/docker-start-services.sh
```

After a while when all services will be successfully started you can make requests to `http://{your-host-ip}:8083` in
you browser (e.g. [http://localhost:8083](http://localhost:8083))
and connect using MQTT protocol on 1883 port.

In case of any issues you can examine service logs for errors.
For example to see TBMQ logs execute the following command:

```
docker compose logs -f tbmq1
```

Or use `docker compose ps` to see the state of all the containers.
Use `docker compose logs -f` to inspect the logs of all running services.
See [docker compose logs](https://docs.docker.com/compose/reference/logs/) command reference for details.

Execute the following command to stop services:

```
./scripts/docker-stop-services.sh
```

Execute the following command to stop and completely remove deployed docker containers:

```
./scripts/docker-remove-services.sh
```

In case you want to remove docker volumes for all the containers please execute the following:

```
./scripts/docker-remove-volumes.sh
```

**Note:** the above command will remove all your data, so be careful before executing it.

It could be useful to update logs (enable DEBUG/TRACE logs) in runtime or change TBMQ or HAProxy configs. In order to do
this you need to make changes, for example, to the
[haproxy.cfg](/docker/haproxy/config/haproxy.cfg) or [logback.xml](/docker/tbmq/conf/logback.xml) file.
Afterward, execute the next command to apply the changes for the container:

```
./scripts/docker-refresh-config.sh
```

To reload HAProxy's configuration without restarting the Docker container you can send the HUP signal to this process (
PID 1):

```
docker exec -it haproxy-certbot sh -c "kill -HUP 1"
```

## Upgrading

**Note**: Make sure `TBMQ_VERSION` in .env file is set to the target version.

After that execute the upgrade script:

```
./scripts/docker-upgrade-tbmq.sh
```

The script upgrades the database, then brings the whole cluster up on the new version. It runs in one of two modes:

* `rolling` — the database is upgraded while the current nodes keep serving clients, then `tbmq1`, `tbmq2` and the
  integration executors are replaced one at a time. HAProxy sends clients to the node that is still up.
* `stop` — the TBMQ nodes and integration executors are stopped, the database is upgraded, then the cluster is started.

By default the script uses the mode the target release supports (`ROLLING_UPGRADE_SUPPORTED` in the script), and
switches to `stop` when the TBMQ nodes are not running. To stop the cluster even when a rolling upgrade is supported,
run `./scripts/docker-upgrade-tbmq.sh --mode=stop`.

A rolling upgrade relies on the HAProxy settings `option redispatch` and `fastinter` from `haproxy/config/haproxy.cfg`.
If your HAProxy configuration predates them, apply it with `./scripts/docker-refresh-config.sh` and reload HAProxy
before the upgrade.

See [Upgrade Instructions](https://tbmq.io/docs/installation/upgrade-instructions/) for more details.
