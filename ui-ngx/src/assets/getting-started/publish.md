Open a new tab in the terminal and publish a message to the Thingsboard MQTT Broker for the <i>demo/topic</i> **topic** by using the following command:

```bash
mosquitto_pub -h {:hostname} -p {:port} -d -q 1 -i tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -m 'demo' -V mqttv5
{:copy-code}
```

Once you run this command, you should see the published message for the subscribed client in the terminal tab.
