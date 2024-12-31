#!/bin/bash
#
# Copyright © 2016-2025 The Thingsboard Authors
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

# configure namespace
kubectl apply -f tb-broker-namespace.yml
kubectl config set-context $(kubectl config current-context) --namespace=thingsboard-mqtt-broker

# install ThingsBoard MQTT Broker
kubectl apply -f tb-broker-configmap.yml
kubectl apply -f tb-broker-db-configmap.yml
kubectl apply -f tb-broker-cache-configmap.yml

kubectl apply -f database-setup.yml &&
kubectl wait --for=condition=Ready pod/tb-db-setup --timeout=120s &&
kubectl exec tb-db-setup -- sh -c 'export INSTALL_TB=true; start-tb-mqtt-broker.sh; touch /tmp/install-finished;'

kubectl delete pod tb-db-setup

