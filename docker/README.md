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
[haproxy.cfg](/docker/haproxy/config/haproxy.cfg) or [logback.xml](/docker/tb-mqtt-broker/conf/logback.xml) file.
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

After that execute the following commands:

```
./scripts/docker-stop-services.sh
```

```
./scripts/docker-upgrade-tbmq.sh --fromVersion=FROM_VERSION
```

```
./scripts/docker-start-services.sh
```

Where `FROM_VERSION` - from which version upgrade should be started.
See [Upgrade Instructions](https://thingsboard.io/docs/mqtt-broker/install/upgrade-instructions/) for
valid `fromVersion` values.
