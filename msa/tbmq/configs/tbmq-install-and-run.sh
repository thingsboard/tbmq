#!/bin/bash
set -u

# Function to check if a directory exists
check_directory_exists() {
  if [ -d "$1" ]; then
    return 0
  else
    return 1
  fi
}

# Function to check if all subdirectories are present
check_subdirectories_exist() {
  local subdirs=("kafka" "log" "data" "postgres")
  for subdir in "${subdirs[@]}"; do
    if ! check_directory_exists "$1/$subdir"; then
      echo "$subdir directory is missing!"
      return 1
    fi
  done
  return 0
}

# Function to check directory permissions
check_directory_permissions() {
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    local uid=$(stat -c "%u" "$1")
    local gid=$(stat -c "%g" "$1")
    if [[ "$uid" == "799" && "$gid" == "799" ]]; then
      return 0
    else
      return 1
    fi
  else
    return 0
  fi
}

# Check if docker-compose.yml is present
if [ -f "docker-compose.yml" ]; then
  echo "docker-compose.yml is already present in the current directory. Skipping download."
else
  echo "docker-compose.yml is absent in the current directory. Downloading the file..."
  wget https://raw.githubusercontent.com/thingsboard/tbmq/main/msa/tbmq/configs/docker-compose.yml
fi

# Check if ~/.tb-mqtt-broker-data directory exists
if check_directory_exists "$HOME/.tb-mqtt-broker-data"; then
  # Check if all subdirectories are present
  if check_subdirectories_exist "$HOME/.tb-mqtt-broker-data"; then
    # Check directory permissions
    if check_directory_permissions "$HOME/.tb-mqtt-broker-data"; then
      echo "Directories are present and permissions are correct."
    else
      read -r -p "Directory permissions are incorrect. Do you want to correct them? (y/n): " response
      if [[ "$response" =~ ^[Yy]$ ]]; then
        echo "Correcting permissions..."
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
          sudo chown -R 799:799 "$HOME/.tb-mqtt-broker-data/log"
          sudo chown -R 799:799 "$HOME/.tb-mqtt-broker-data/data"
        fi
        echo "Permissions corrected."
      else
        echo "Skipping permission correction."
      fi
    fi
  else
    echo "Some subdirectories are missing within ~/.tb-mqtt-broker-data. Please create them manually with correct permissions and rerun the script."
    exit 1
  fi
else
  echo "Creating necessary directories..."
  mkdir -p "$HOME/.tb-mqtt-broker-data/kafka" "$HOME/.tb-mqtt-broker-data/log" "$HOME/.tb-mqtt-broker-data/data" "$HOME/.tb-mqtt-broker-data/postgres"
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    sudo chown -R 799:799 "$HOME/.tb-mqtt-broker-data/log"
    sudo chown -R 799:799 "$HOME/.tb-mqtt-broker-data/data"
  fi
  echo "Directories created."
fi

echo "Starting TBMQ!"
# Check if "docker-compose" or "docker compose" command should be used
if command -v docker-compose >/dev/null 2>&1; then
  docker-compose up -d
else
  docker compose up -d
fi
