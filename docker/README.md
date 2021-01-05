# Docker configuration for ThingsBoard MQTT Broker

This folder containing scripts and Docker Compose configurations to run ThingsBoard MQTT Broker.

## Prerequisites

ThingsBoard Microservices are running in dockerized environment.
Before starting please make sure [Docker CE](https://docs.docker.com/install/) and [Docker Compose](https://docs.docker.com/compose/install/) are installed in your system.

## Installation

Execute the following command to create log folders for the services and chown of these folders to the docker container users. 
To be able to change user, **chown** command is used, which requires sudo permissions (script will request password for a sudo access): 

`
$ ./scripts/docker-create-log-folders.sh
`

Execute the following command to run installation:

`
$ ./scripts/docker-install-tb-mqtt-broker.sh
`

## Running

Execute the following command to start services:

`
$ ./scripts/docker-start-services.sh
`

After a while when all services will be successfully started you can make requests to `http://{your-host-ip}:8083` in you browser (for ex. `http://localhost:8083`).

In case of any issues you can examine service logs for errors.
For example to see ThingsBoard Mqtt Broker logs execute the following command:

`
$ docker-compose logs -f tb-mqtt-broker
`

Or use `docker-compose ps` to see the state of all the containers.
Use `docker-compose logs --f` to inspect the logs of all running services.
See [docker-compose logs](https://docs.docker.com/compose/reference/logs/) command reference for details.

Execute the following command to stop services:

`
$ ./scripts/docker-stop-services.sh
`

Execute the following command to stop and completely remove deployed docker containers:

`
$ ./scripts/docker-remove-services.sh
`