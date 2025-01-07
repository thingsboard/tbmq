#
# Copyright © 2016-2023 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

function compose_version {
    # Checking Compose V1 availability
    $status_v1 = Test-Path "C:\Program Files\Docker\Docker\resources\bin\docker-compose.exe"

    # Checking Compose V2 availability
    $status_v2 = Test-Path "C:\Program Files\Docker\Docker\resources\bin\docker.exe"

    $COMPOSE_VERSION = ""

    if ($status_v2) {
        $COMPOSE_VERSION = "V2"
    } elseif ($status_v1) {
        $COMPOSE_VERSION = "V1"
    } else {
        Write-Host "Docker Compose is not detected. Please check your environment." -ForegroundColor Red
        exit 1
    }

    Write-Output $COMPOSE_VERSION
}

function volume_exists {
    param (
        [string]$volume_name
    )

    $existingVolumes = docker volume ls --format "{{.Name}}"
    return $existingVolumes -contains $volume_name
}

function create_volume_if_not_exists {
    param (
        [string]$volume_name
    )

    if (volume_exists $volume_name) {
        Write-Host "Volume '$volume_name' already exists."
    } else {
        docker volume create $volume_name
        Write-Host "Volume '$volume_name' created."
    }
}

# Check if docker-compose.yml is present
if (Test-Path "docker-compose.yml") {
    Write-Host "docker-compose.yml is already present in the current directory. Skipping download."
} else {
    Write-Host "docker-compose.yml is absent in the current directory. Downloading the file..."
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/thingsboard/tbmq/release-2.0.1/msa/tbmq/configs/docker-compose.yml" -OutFile "docker-compose.yml"
}

$COMPOSE_VERSION = compose_version
Write-Host "Docker Compose version is: $COMPOSE_VERSION"

# Define the string to search for
$search_string = "thingsboard/tbmq"
# Check if the Docker Compose file contains the search_string
if (Select-String -Path "docker-compose.yml" -Pattern $search_string) {
    Write-Host "The Docker Compose file is ok, checking volumes..."
} else {
    Write-Host "The Docker Compose file missing tbmq. Seems the file is invalid for tbmq configuration." -ForegroundColor Red
    exit 1
}

create_volume_if_not_exists "tbmq-postgres-data"
create_volume_if_not_exists "tbmq-kafka-data"
create_volume_if_not_exists "tbmq-redis-data"
create_volume_if_not_exists "tbmq-logs"
create_volume_if_not_exists "tbmq-data"

Write-Host "Starting TBMQ!"
switch ($COMPOSE_VERSION) {
    "V2" {
        docker compose up -d
        docker compose logs -f
    }
    "V1" {
        docker-compose up -d
        docker-compose logs -f
    }
    default {
        # unknown option
    }
}
