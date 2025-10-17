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

try {
    $COMPOSE_VERSION = compose_version
    Write-Host "Docker Compose version is: $COMPOSE_VERSION"

    # Define TBMQ versions
    $old_version = "2.1.0"
    $new_version = "2.2.0"

    # Define TBMQ images
    $old_image = "image: `"thingsboard/tbmq:$old_version`""
    $new_image = "image: `"thingsboard/tbmq:$new_version`""

    # Define TBMQ IE images
    $old_ie_image = "image: `"thingsboard/tbmq-integration-executor:$old_version`""
    $new_ie_image = "image: `"thingsboard/tbmq-integration-executor:$new_version`""

    # Define DB variables
    $db_url = "jdbc:postgresql://postgres:5432/thingsboard_mqtt_broker"
    $db_username = "postgres"
    $db_password = "postgres"
    $redis_url = "redis"

    # Pull the new TBMQ image
    docker pull "thingsboard/tbmq:$new_version"

    # Backup the original Docker Compose file
    Copy-Item -Path "docker-compose.yml" -Destination "docker-compose.yml.bak" -ErrorAction Stop
    Write-Host "Docker Compose file backup created: docker-compose.yml.bak"

    # Replace the TBMQ image version using PowerShell's Get-Content and Set-Content
    $composeFileContent = Get-Content -Path "docker-compose.yml"
    $updatedComposeContent = $composeFileContent -replace [regex]::Escape($old_image), $new_image
    $updatedComposeContent | Set-Content -Path "docker-compose.yml"

    # Replace the TBMQ IE image version using PowerShell's Get-Content and Set-Content
    $composeFileContent = Get-Content -Path "docker-compose.yml"
    $updatedComposeContent = $composeFileContent -replace [regex]::Escape($old_ie_image), $new_ie_image
    $updatedComposeContent | Set-Content -Path "docker-compose.yml"

    if (Select-String -Path "docker-compose.yml" -Pattern $new_image) {
        Write-Host "TBMQ image line updated in docker-compose.yml with the new version"
    } else {
        Write-Host "Failed to replace the image version. Please, update the version manually and re-run the script" -ForegroundColor Red
        exit 1
    }

    # Check if .tbmq-upgrade.env is present
    if (Test-Path ".tbmq-upgrade.env") {
        Write-Host "Found .tbmq-upgrade.env. Proceeding with upgrade..."
    } else {
        Write-Host ".tbmq-upgrade.env not found in current directory. Please create it before running upgrade."
        exit 1
    }

    switch ($COMPOSE_VERSION) {
        "V2" {
            docker compose stop tbmq

            $postgresContainerName = (docker compose ps | Select-String "postgres").ToString().Split()[0]

            $composeNetworkId = (docker inspect -f '{{ range .NetworkSettings.Networks }}{{ .NetworkID }}{{ end }}' $postgresContainerName)

            docker run -it --network=$composeNetworkId `
            --env-file .tbmq-upgrade.env `
            -e SPRING_DATASOURCE_URL=$db_url `
            -e SPRING_DATASOURCE_USERNAME=$db_username `
            -e SPRING_DATASOURCE_PASSWORD=$db_password `
            -e REDIS_HOST=$redis_url `
            -v tbmq-data:/data `
            --rm `
            "thingsboard/tbmq:$new_version" upgrade-tbmq.sh

            docker compose rm tbmq

            docker compose up -d tbmq --no-deps
        }
        "V1" {
            docker-compose stop tbmq

            $postgresContainerName = (docker-compose ps | Select-String "postgres").ToString().Split()[0]

            $composeNetworkId = (docker inspect -f '{{ range .NetworkSettings.Networks }}{{ .NetworkID }}{{ end }}' $postgresContainerName)

            docker run -it --network=$composeNetworkId `
            --env-file .tbmq-upgrade.env `
            -e SPRING_DATASOURCE_URL=$db_url `
            -e SPRING_DATASOURCE_USERNAME=$db_username `
            -e SPRING_DATASOURCE_PASSWORD=$db_password `
            -e REDIS_HOST=$redis_url `
            -v tbmq-data:/data `
            --rm `
            "thingsboard/tbmq:$new_version" upgrade-tbmq.sh

            docker-compose rm tbmq

            docker-compose up -d tbmq --no-deps
        }
        default {
            # unknown option
        }
    }
} catch {
    Write-Host "An error occurred: $_" -ForegroundColor Red
    exit 1
}
