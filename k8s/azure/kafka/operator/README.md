# Deploying a Kafka Cluster with Strimzi

Before deploying the Kafka cluster, you need to install the **Strimzi Cluster Operator**.
You can review the available deployment methods in
the [Strimzi documentation](https://strimzi.io/docs/operators/latest/deploying#con-strimzi-installation-methods_str).

In this guide, we will use the **Helm CLI** with the **Helm chart installation method**.

---

## Step 1: Review Configuration Files

List the operator configuration files:

```bash
ls kafka/operator/
```

You should see two files:

* **default-values-strimzi-kafka-operator.yaml**
  Default values downloaded from [Artifact Hub](https://artifacthub.io/packages/helm/strimzi/strimzi-kafka-operator).
  *This file should remain unchanged.*

* **values-strimzi-kafka-operator.yaml**
  *We recommend applying changes only here. This makes upgrades easier by allowing you to compare differences with the
  default file.*

---

## Step 2: Install the Strimzi Operator

Run the following command:

```bash
helm install tbmq-kafka -f kafka/operator/values-strimzi-kafka-operator.yaml oci://quay.io/strimzi-helm/strimzi-kafka-operator --version 0.47.0
```

Monitor the deployment:

```bash
kubectl get pod -w | grep strimzi
```

---

## Step 3: Deploy the Kafka Cluster

Apply the Kafka cluster definition:

```bash
kubectl apply -f kafka/operator/kafka-cluster.yaml
```

Wait for Kubernetes to start all required pods and services:

```bash
kubectl wait kafka/tbmq --for=condition=Ready --timeout=300s
```

⚠️ **Note:** If you have a slow internet connection, this command may time out while downloading images. If that
happens, simply run it again.

---

## Step 4: Verify the Deployment

The deployment creates a **3-node Kafka cluster** in **KRaft mode with dual roles** (each node acts as both a controller
and a broker):

```text
tbmq-kafka-dual-role-0    1/1   Running   0   4m7s
tbmq-kafka-dual-role-1    1/1   Running   0   4m7s
tbmq-kafka-dual-role-2    1/1   Running   0   4m7s
```

It also creates an **Entity Operator pod**, which includes two containers:

* **Topic Operator**
* **User Operator**

```text
tbmq-entity-operator-64fb7dd5c4-4lp75   2/2   Running   0   4m7s
```

✅ You now have a running Strimzi-managed Kafka cluster.

---

## Step 5: Configure TBMQ Components to Connect to Strimzi

Update the TBMQ configuration files to use the Strimzi Kafka cluster:

1. Open the following files:

* `tb-broker.yml`
* `tbmq-ie.yml`

2. Locate the environment variable:

   ```yaml
   TB_KAFKA_SERVERS
   ```

3. Uncomment the lines marked with:

   ```yaml
   # Uncomment the following lines to connect to Strimzi
   ```

This will configure **TBMQ** and **TBMQ Integration Executors** to use the Strimzi-managed Kafka cluster.
