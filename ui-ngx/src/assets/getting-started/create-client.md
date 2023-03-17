### Create Client

The first step is to create the MQTT Client with a simple Authentication method based on Username and Password.
Please click on the button below and fill the following fields:
* name: 'TB MQTT Demo' (can be any)
* username: 'tb_mqtt_demo'
* password: 'tb_mqtt_demo'

<See Clients>

<-- Additional info-->
(Info)
Note that Thingsboard MQTT Broker has different Authentication and Authorization methods.
<Read docs>

```bash
mosquitto_sub -h localhost -p 1883 --qos 0 -i tb_mqtt_demo -P tb_mqtt_demo3 -u tb_mqtt_demo -t demo/tb_mqtt -k 100 -x 100 -V mqttv5
{:copy-code}
```
