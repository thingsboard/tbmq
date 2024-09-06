To publish a message from the <a target='_blank' href='https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/#device-client'>Device</a> 
client to the topic `tbmq/demo/topic` open a new tab in the terminal and paste the following command:

<br>

```bash
mosquitto_pub -h {:mqttHost} -p {:mqttPort} -d -u tbmq_dev -P tbmq_dev -t tbmq/demo/topic -m "Hello World" -q 1 -V mqttv5{:copy-code}
```

<br>

Once you run this command, you should see the published message in the terminal tab of the subscribed client.
