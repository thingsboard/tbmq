# ThingsBoard MQTT Broker on AWS microservices deployment scripts

This folder contains scripts and Kubernetes resources configurations to run ThingsBoard MQTT Broker on AWS EKS
cluster.

You can find the deployment guide by the
[**link**](https://thingsboard.io/docs/mqtt-broker/install/cluster/aws-cluster-setup/).

Follow the steps from the guide above to deploy the cluster using the scripts from the current branch.
Please, review all the information provided before starting the cluster creation or executing other commands.

#### Cluster

In the `cluster.yml` file you can find the next line `version: "1.23"`. Although a much newer version is already
available, we have used this one in order to
use **dockerd** instead of default **containerd** in the latest versions.
See [Amazon EKS ended support for Dockershim doc](https://docs.aws.amazon.com/eks/latest/userguide/dockershim-deprecation.html)
for more details.
That helped us to tune the MQTT broker pods configuration. See `tb-mqtt-broker` node group and **preBootstrapCommands**
in `managedNodeGroups`.

**Untracked connections**

After the creation of the EKS cluster, we recommend making an additional step in the configuration of created security
groups
if you plan to conduct the test on a large scale. You shall make the connections **untracked** inside the AWS EKS.
See the
next [doc](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-connection-tracking.html#untracked-connections)
for more information.

To do that:

1. Open and log in to the AWS console.
2. Go to EC2, Security Groups.
3. Find the auto-created SG with a similar description - "EKS created security group applied to ENI that is attached to
   EKS Control Plane master nodes, as well as any managed workloads."
4. Click on "Edit inbound rules" and add a new rule similar to the one shown in the screenshot below.

<img src="./images/sg.png?raw=true" width="100" height="100">

**General Purpose (GP3) storage**

As for the newer versions of AWS EKS the gp3 storage is enabled by default, in the 1.23 version the gp2 storage is the
default one.
GP2 storage with a small volume size is quite slow relative to the modern GP3.
GP3 provides 3000 IOPs and 125 MB/s throughput for each persistent volume with no additional cost. In most cases, this
is enough to gain the
desired performance needs. GP2 configured for the same characteristics will probably cost more.

We recommend to search for the official doc on how to install gp3 inside your cluster. However, here are some helpful
links and steps you can do to achieve this.
Follow each step carefully in order to not face any issues.
Review the following [doc](https://github.com/kubernetes-sigs/aws-ebs-csi-driver/blob/master/docs/install.md) to learn
more about the aws-ebs-csi-driver.

Add the aws-ebs-csi-driver Helm repository.

```bash
helm repo add aws-ebs-csi-driver https://kubernetes-sigs.github.io/aws-ebs-csi-driver
helm repo update
```

```bash
eksctl utils associate-iam-oidc-provider --cluster=thingsboard-mqtt-broker-cluster --approve

eksctl create iamserviceaccount \
  --name ebs-csi-controller-sa \
  --namespace kube-system \
  --cluster thingsboard-mqtt-broker-cluster \
  --attach-policy-arn arn:aws:iam::{YOUR_ACCOUNT_ID}:policy/AmazonEKS_EBS_CSI_Driver_Policy \
  --approve
  
aws cloudformation describe-stacks \
  --stack-name eksctl-thingsboard-mqtt-broker-cluster-addon-iamserviceaccount-kube-system-ebs-csi-controller-sa \
  --query='Stacks[].Outputs[?OutputKey==`Role1`].OutputValue' \
  --output text
```

Install the latest release of the driver.

```bash
helm upgrade --install aws-ebs-csi-driver \
    --namespace kube-system \
    aws-ebs-csi-driver/aws-ebs-csi-driver
```

Create storage class gp3 and make it default:

```bash
cat > gp3-def-sc.yaml
```

```yaml
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

```bash
kubectl get sc
# NAME            PROVISIONER             RECLAIMPOLICY   VOLUMEBINDINGMODE      ALLOWVOLUMEEXPANSION   AGE
# gp2             kubernetes.io/aws-ebs   Delete          WaitForFirstConsumer   false                  46m
# gp3 (default)   ebs.csi.aws.com         Delete          WaitForFirstConsumer   true                   14s
```

#### Kafka

**Important notice,** instead of creation of AWS MSK, we recommend deploying Bitnami Kafka from Helm.
For that, review the [kafka](/k8s/aws/kafka) folder.
You can find there `default-values-kafka.yaml` - default values downloaded
from [Bitnami artifactHub](https://artifacthub.io/packages/helm/bitnami/kafka).
And `values-kafka.yaml` with modified values.

To add the Bitnami helm repo:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

Before the Kafka installation, we need to create custom Storage Class for Kafka with modified disk type and throughput.

```bash
cat > kafka-sc.yaml
```

```yaml
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: kafka-gp3
  annotations:
    storageclass.kubernetes.io/is-default-class: "false"
allowVolumeExpansion: true
provisioner: ebs.csi.aws.com
volumeBindingMode: WaitForFirstConsumer
parameters:
  type: gp3
  throughput: "150"
```

```bash
kubectl apply -f kafka-sc.yaml
```

To install Bitnami Kafka execute the following command:

```bash
helm install kafka -f kafka/values-kafka.yaml bitnami/kafka --version 21.4.4
```

#### Helpful commands

Create cluster:

```bash
eksctl create cluster -f cluster.yml
```

Update kube-proxy, coredns and aws-node addons:

```bash
eksctl utils update-coredns --cluster thingsboard-mqtt-broker-cluster
eksctl utils update-kube-proxy --approve --cluster thingsboard-mqtt-broker-cluster
eksctl utils update-aws-node --approve --cluster thingsboard-mqtt-broker-cluster
```

Create node group (see [eksctl docs](https://eksctl.io/usage/managing-nodegroups/#include-and-exclude-rules) for more
details):

```bash
eksctl create nodegroup --config-file=cluster.yml
```

In order to scale the node groups from current capacity (desiredCapacity: 1) to X execute the following commands:

```bash
eksctl scale nodegroup --cluster=thingsboard-mqtt-broker-cluster --nodes=X tb-mqtt-broker
```

Delete node group:

```bash
eksctl delete nodegroup --cluster=thingsboard-mqtt-broker-cluster --name=tb-mqtt-broker
```

Delete cluster:

```bash
eksctl delete cluster -r eu-west-1 -n thingsboard-mqtt-broker-cluster -w
```
