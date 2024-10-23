To publish a message from the <a target='_blank' href='https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/#device-client'>Device</a> 
client to the topic `tbmq/demo/topic` open a new tab in the terminal and paste the following command:

<br>

```bash
mosquitto_pub -d -q 1 -h {:mqttHost} -p {:mqttPort} -t tbmq/demo/topic -u tbmq_dev -P tbmq_dev -m "Hello World"{:copy-code}
```

<br>

Once you run this command, you should see the published message in the terminal tab of the subscribed client.
