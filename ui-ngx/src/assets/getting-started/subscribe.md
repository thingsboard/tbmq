To subscribe <a target='_blank' href='https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type/#application-client'>Application</a> 
client to the MQTT topic `tbmq/demo/+` we will use the <a href='https://mosquitto.org/man/mosquitto_sub-1.html' target="_blank">mosquitto_sub</a> MQTT client.

Please copy and paste the following code into a terminal tab:

```bash
mosquitto_sub -h {:hostname} -p {:port} -d -u tbmq_app -P tbmq_app -t tbmq/demo/+ -q 1 -c -i tbmq -v -V mqttv5
{:copy-code}
```
