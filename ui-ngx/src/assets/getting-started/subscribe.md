### Subscribe

The second step is to setup your subscriptions.
For demo purpose we will use  an open source message broker <a>Eclipse Mosquitto</a>.
Use simple commands to install Mosquitto broker and subscribe for topic:


```text
sudo apt get 
```
{: .copy-code}

```text
mosquitto_sub -h localhost -p 1883 --qos 0 -i tb_mqtt_demo -P tb_mqtt_demo3 -u tb_mqtt_demo -t demo/tb_mqtt -k 100 -x 100 -V mqttv5 
```
{: .copy-code}
