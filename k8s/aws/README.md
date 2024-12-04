# TBMQ on AWS deployment scripts

This folder containing scripts and Kubernetes resources configurations to run TBMQ on AWS EKS cluster.

You can find the deployment guide by the [**link**](https://thingsboard.io/docs/mqtt-broker/install/cluster/aws-cluster-setup/).

Follow the steps from the guide above to deploy the cluster using the scripts from the current branch. Please, review
all the information provided before starting the cluster creation or executing other commands.

### General Purpose (GP3) storage

In order to set up the test correctly, you need to install gp3 storage class. It is used for Kafka volumes.
The default storage class is gp2. We recommend following the
official [documentation](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html) from AWS.
You will need to create an IAM role and add an Amazon EBS CSI driver as an Amazon EKS add-on.

Afterward, create storage class gp3 and make it default:

```bash
cat > gp3-def-sc.yaml
```

```
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: gp3
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
allowVolumeExpansion: true
provisioner: ebs.csi.aws.com
volumeBindingMode: WaitForFirstConsumer
parameters:
  type: gp3
```

```bash
kubectl apply -f gp3-def-sc.yaml
```

Make gp2 storage class non-default:

```bash
kubectl patch storageclass gp2 -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}'
```

Or delete legacy gp2 storage class:

```bash
kubectl delete storageclass gp2
```

Check the storage class available:

```
kubectl get sc
# NAME            PROVISIONER             RECLAIMPOLICY   VOLUMEBINDINGMODE      ALLOWVOLUMEEXPANSION   AGE
# gp2             kubernetes.io/aws-ebs   Delete          WaitForFirstConsumer   false                  46m
# gp3 (default)   ebs.csi.aws.com         Delete          WaitForFirstConsumer   true                   14s
```

### Kafka and Redis Installation Using Helm

For deploying Kafka and Redis, we recommend using Bitnami Helm charts instead of AWS-managed services like MSK or ElastiCache. 
Configuration files for both components are available in their respective folders.

Please review [kafka](/k8s/aws/kafka) folder.
You can find there next configuration files:
 - `default-values-kafka-28-3-0.yml` - default values downloaded from Bitnami artifactHub.
 - `values-kafka-28-3-0.yml` - customized version of the default values with changes required for our test case.

In the same way you can review [redis](/k8s/aws/redis) folder and find inside next configuration files:
 - `default-values-redis-10-2-5.yml` - default values downloaded from Bitnami artifactHub
 - `values-redis-10-2-5.yml` - customized version of the default values with changes required for our test case.


> **Important notice**: Before proceeding with Kafka and Redis installation, execute the following commands to create 
> the `thingsboard-mqtt-broker` namespace and set it as the current context for deployment:

```shell
kubectl apply -f tb-broker-namespace.yml
```

```shell
kubectl config set-context $(kubectl config current-context) --namespace=thingsboard-mqtt-broker
```

This ensures that all components of the stack (Kafka, Redis, etc.) will be deployed in the correct namespace. After completing these steps, you can proceed with the installation.

To install Kafka v3.7.0:

```bash
helm install tbmq-kafka oci://registry-1.docker.io/bitnamicharts/kafka --version 28.3.0 -f kafka/values-kafka-28-3-0.yml
```

To install Redis v7.2.5:

```bash
helm install tbmq-redis-cluster oci://registry-1.docker.io/bitnamicharts/redis-cluster --version 10.2.5 -f redis/values-redis-10-2-5.yml
```