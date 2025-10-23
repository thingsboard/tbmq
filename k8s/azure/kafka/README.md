# Deploying an Apache Kafka Cluster

This guide will deploy an **Apache Kafka cluster** with 3 pods running in **KRaft mode**, where each node acts as both a
**controller** and a **broker**.

---

## Step 1: Review the Configuration File

Check the Kafka cluster definition file:

```bash
ls kafka/tbmq-kafka.yml
```

---

## Step 2: Deploy the Kafka Cluster

Apply the cluster manifest:

```bash
kubectl apply -f kafka/tbmq-kafka.yml
```

Monitor the deployment:

```bash
kubectl get pod -w | grep tbmq-kafka
```

Wait for all pods in the StatefulSet to become ready:

```bash
kubectl rollout status statefulset/tbmq-kafka
```

---

## Step 3: Verify the Deployment

Once deployment is complete, you should see a **3-node Kafka cluster** running in **KRaft dual-role mode**:

```text
tbmq-kafka-0    1/1   Running   0   4m7s
tbmq-kafka-1    1/1   Running   0   4m7s
tbmq-kafka-2    1/1   Running   0   4m7s
```

✅ You now have a fully running Apache Kafka cluster.

---

## Step 4: Configure TBMQ to Connect to Kafka

Update the TBMQ components to use the newly deployed Kafka cluster:

1. Open the following configuration files:

* `tbmq.yml`
* `tbmq-ie.yml`

2. Locate the environment variable:

   ```yaml
   TB_KAFKA_SERVERS
   ```

3. Uncomment the section marked as:

   ```yaml
   # Uncomment the following lines to connect to Apache Kafka
   ```

This configures **TBMQ** and the **TBMQ Integration Executors** to connect to your Apache Kafka cluster.
