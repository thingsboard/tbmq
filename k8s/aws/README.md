# ThingsBoard MQTT Broker on AWS microservices deployment scripts

This folder containing scripts and Kubernetes resources configurations to run ThingsBoard MQTT Broker on AWS EKS
cluster.

You can find the deployment guide by the [**link
**](https://thingsboard.io/docs/mqtt-broker/install/cluster/aws-cluster-setup/).

# Custom user data for launch template

```text
MIME-Version: 1.0
Content-Type: multipart/mixed; boundary="==MYBOUNDARY=="

--==MYBOUNDARY==
Content-Type: text/x-shellscript; charset="us-ascii"

#!/bin/bash
echo "fs.file-max = 200000" >> /etc/sysctl.conf
sysctl -p
echo "* soft nofile 100000" >> /etc/security/limits.conf
echo "* hard nofile 100000" >> /etc/security/limits.conf

--==MYBOUNDARY==--
```

# To Base64 and copy to clipboard

base64 -w 0 test.sh | xclip -selection clipboard

# Create new version of launch template from source version with updated data

aws ec2 create-launch-template-version --launch-template-name
eksctl-thingsboard-mqtt-broker-cluster-nodegroup-tb-mqtt-broker --source-version 1 --launch-template-data '{"
UserData":""}'

# Set default version for the launch template

aws ec2 modify-launch-template --launch-template-name eksctl-thingsboard-mqtt-broker-cluster-nodegroup-tb-mqtt-broker
--default-version 2
