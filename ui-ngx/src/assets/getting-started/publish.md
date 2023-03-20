Open a new tab in the terminal and publish a message for a **topic** <i>demo/topic</i> to the Thingsboard MQTT Broker:

```bash
mosquitto_pub -h {:hostname} -p {:port} -d -q 1 -i tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -m 'demo' -V mqttv5
{:copy-code}
```

In the terminal tab you should see the published message for the subscribed client.
