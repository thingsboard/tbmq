Open a new tab in the terminal and publish a message to the ThingsBoard MQTT Broker for the `demo/topic` **topic** by using the following command:

```bash
mosquitto_pub -h {:hostname} -p {:port} -d -u tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -m 'Thingsboard MQTT Broker works!' -V mqttv5
{:copy-code}
```

Once you run this command, you should see the published message for the subscribed client in the terminal tab.
