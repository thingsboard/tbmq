# Kubernetes resources configuration for ThingsBoard MQTT Broker

This folder containing scripts and Kubernetes resources configurations to run ThingsBoard in Microservices mode.

## Prerequisites

ThingsBoard Microservices are running on Kubernetes cluster.
You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster.
If you do not already have a cluster, you can create one by using [Minikube](https://kubernetes.io/docs/setup/minikube).

## Installation

```
./k8s-install-tb-mqtt-broker.sh
```

## Running

Execute the following command to deploy third-party resources:

```
./k8s-deploy-tb-broker.sh
```

After a while when all resources will be successfully started you can open `http://{your-cluster-ip}:30001` in your browser (for ex. `http://172.17.0.3:30001`).
You should see the ThingsBoard login page.
**Note:** you can check your Minikube IP with this command:

```
minikube ip
```

Use the following default credentials:

- **System Administrator**: sysadmin@thingsboard.org / sysadmin

In case of any issues, you can examine service logs for errors.
For example to see ThingsBoard node logs execute the following commands:

1) Get the list of the running tb-node pods:

```
kubectl get pods -l app=tb-broker
```

2) Fetch logs of the tb-node pod:

`
kubectl logs -f [tb-broker-pod-name]
`

Where:

- `tb-broker-pod-name` - tb-node pod name obtained from the list of the running tb-node pods.

Or use `kubectl get pods` to see the state of all the pods.
Or use `kubectl get services` to see the state of all the services.
Or use `kubectl get deployments` to see the state of all the deployments.
See [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/) command reference for details.

Execute the following command to delete ThingsBoard MQTT Broker nodes:

`
./k8s-delete-tb-broker.sh
`

Execute the following command to delete all resources (including database):

`
./k8s-delete-all.sh
`