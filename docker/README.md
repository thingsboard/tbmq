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

## Running

Execute the following command to start services:

`
$ ./scripts/docker-start-services.sh
`

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