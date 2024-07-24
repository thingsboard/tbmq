### Arduino
Arduino is an open-source prototyping platform based on easy-to-use hardware and software. 
In this guide MQTT client connects MQTT client, subscribes to a topic, publishes a message.
The source code was developed using [Arduino IDE](https://www.arduino.cc/en/software).

##### Prerequisites

In order to start programming ESP8266 device, you will need Arduino IDE installed and all related software.

###### Step 1. Arduino UNO and Arduino IDE setup.
In order to start programming the Arduino UNO device, you will need Arduino IDE and all related software installed.

Download and install [Arduino IDE](https://www.arduino.cc/en/Main/Software).

To learn how to connect your Uno board to the computer and upload your first sketch please follow this [guide](https://www.arduino.cc/en/Guide/ArduinoUno).

###### Step 2. Install Arduino libraries.

Open Arduino IDE and go to **Sketch -> Include Library -> Manage Libraries**.
Find and install the following libraries:

- [PubSubClient by Nick O'Leary](http://pubsubclient.knolleary.net/).
- [ArduinoJson by Benoit Blanchon](https://github.com/bblanchon/ArduinoJson)

**Note** that this tutorial was tested with the following versions of the libraries:

- PubSubClient 2.6
- ArduinoJson 5.8.0 

##### Prepare and upload a sketch.

**Note** You need to edit following constants and variables in the sketch:

- WIFI_AP - name of your access point
- WIFI_PASSWORD - access point password

```bash
#include <ESP8266WiFi.h>
#include <PubSubClient.h>

// WiFi settings
const char *wifi_app = "WIFI_AP"; // Replace with your WiFi name
const char *wifi_password = "WIFI_PASSWORD";  // Replace with your WiFi password

// MQTT Broker settings
const int port = 8084; // MQTT port (TCP)
const char *host = "localhost"; // EMQX broker endpoint

const char *topic = "emqx/esp8266";   // MQTT topic
const char *username = "tbmq_websockets_username1"; // MQTT username for authentication
const char *password = NULL; // MQTT password for authentication

WiFiClient espClient;
PubSubClient mqtt_client(espClient);

void connectToWiFi();

void connectToMQTTBroker();

void mqttCallback(char *topic, byte *payload, unsigned int length);

void setup() {
  Serial.begin(115200);
  connectToWiFi();
  mqtt_client.setServer(host, port);
  mqtt_client.setCallback(mqttCallback);
  connectToMQTTBroker();
}

void connectToWiFi() {
  WiFi.begin(wifi_app, wifi_password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
   }
  Serial.println("\nConnected to the WiFi network");
}

void connectToMQTTBroker() {
  while (!mqtt_client.connected()) {
    String client_id = "esp8266-client-" + String(WiFi.macAddress());
    Serial.printf("Connecting to MQTT Broker as %s.....\n", client_id.c_str());
    if (mqtt_client.connect(client_id.c_str(), username, password)) {
      Serial.println("Connected to MQTT broker");
      mqtt_client.subscribe(topic);
      // Publish message upon successful connection
      mqtt_client.publish(topic, "Hi EMQX I'm ESP8266 ^^");
     } else {
      Serial.print("Failed to connect to MQTT broker, rc=");
      Serial.print(mqtt_client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
     }
   }
}

void mqttCallback(char *topic, byte *payload, unsigned int length) {
  Serial.print("Message received on topic: ");
  Serial.println(topic);
  Serial.print("Message:");
  for (unsigned int i = 0; i < length; i++) {
    Serial.print((char) payload[i]);
   }
  Serial.println();
  Serial.println("-----------------------");
}

void loop() {
  if (!mqtt_client.connected()) {
    connectToMQTTBroker();
   }
  mqtt_client.loop();
}

{:copy-code}
```

Connect USB-TTL adapter to PC and select the corresponding port in Arduino IDE. Compile and Upload your sketch to the device using "Upload" button.

#### Next steps

