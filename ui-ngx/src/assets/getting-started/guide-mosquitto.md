### Mosquitto

##### Prerequisites

```bash
install mosquitto{:copy-code}
```

##### Subscribe
To subscribe <a target='_blank' href='https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/#application-client'>Application</a>
client to the MQTT topic `tbmq/demo/+` we will use the <a href='https://mosquitto.org/man/mosquitto_sub-1.html' target="_blank">mosquitto_sub</a> MQTT client.
Please copy and paste the following code into a terminal tab:

<br>

```bash
mosquitto_sub -h {:hostname} -p {:port} -d -u tbmq_app -P tbmq_app -t tbmq/demo/+ -q 1 -c -i tbmq -v -V mqttv5{:copy-code}
```

##### Publish

To publish a message from the <a target='_blank' href='https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/#device-client'>Device</a>
client to the topic `tbmq/demo/topic` open a new tab in the terminal and paste the following command:

<br>

```bash
mosquitto_pub -h {:hostname} -p {:port} -d -u tbmq_dev -P tbmq_dev -t tbmq/demo/topic -m 'Hello World' -q 1 -V mqttv5{:copy-code}
```

<br>

Once you run this command, you should see the published message in the terminal tab of the subscribed client.


#### Next steps

