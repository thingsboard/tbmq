### Subscribe

Once you have a connected client, you can also publish messages to a topic:

```text
mosquitto_pub -q 0 -i tb_mqtt_demo -u tb_mqtt_demo -P tb_mqtt_demo -t demo/topic -V mqttv5 -m 'It is working' -r 
```
{: .copy-code}
