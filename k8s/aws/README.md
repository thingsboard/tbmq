# TBMQ on AWS deployment scripts

This folder containing scripts and Kubernetes resources configurations to run TBMQ on AWS EKS cluster.

You can find the deployment guide by the [**link**](https://thingsboard.io/docs/mqtt-broker/install/cluster/aws-cluster-setup/).

Follow the steps from the guide above to deploy the cluster using the scripts from the current branch. Please, review
all the information provided before starting the cluster creation or executing other commands.

### General Purpose (GP3) storage

In order to set up the test correctly you need to install gp3 storage class. It is used for Kafka volumes.
The default storage class is gp2. We recommend following the
official [documentation](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html) from AWS.
You will need to create an IAM role and add an Amazon EBS CSI driver as an Amazon EKS add-on.

Afterward, create storage class gp3 and make it default:

```
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

```
kubectl apply -f gp3-def-sc.yaml
```

Make gp2 storage class non-default:

```
kubectl patch storageclass gp2 -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}'
```

Or delete legacy gp2 storage class:

```
kubectl delete storageclass gp2
```

Check the storage class available:

```
kubectl get sc
# NAME            PROVISIONER             RECLAIMPOLICY   VOLUMEBINDINGMODE      ALLOWVOLUMEEXPANSION   AGE
# gp2             kubernetes.io/aws-ebs   Delete          WaitForFirstConsumer   false                  46m
# gp3 (default)   ebs.csi.aws.com         Delete          WaitForFirstConsumer   true                   14s
```

### Kafka

**Important notice**, instead of creation of AWS MSK, we recommend deploying Bitnami Kafka from Helm.
For that, review the [kafka](/k8s/aws/kafka) folder.
You can find there `default-values-kafka.yaml` - default values downloaded from Bitnami artifactHub.
And `values-kafka.yaml` with modified values.

To add the Bitnami helm repo:

```
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

To install Kafka v3.5.1:

```
helm install kafka -f kafka/values-kafka.yaml bitnami/kafka --version 25.3.3
```

**Note**: it is recommended to execute Kafka installation after installing TBMQ DB (i.e. run `./k8s-install-tbmq.sh`).
