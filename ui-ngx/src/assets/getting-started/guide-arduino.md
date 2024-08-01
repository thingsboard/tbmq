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
#include <WiFi.h>
#include <PubSubClient.h>

constexpr char WIFI_SSID[] = "ThingsBoard_Guest";
constexpr char WIFI_PASSWORD[] = "4Friends123!";
constexpr uint32_t SERIAL_DEBUG_BAUD = 115200U;

constexpr char MQTT_HOST[] = "10.7.3.73"; // In case of working locally use you public IP address 
const int MQTT_PORT = 11883;

constexpr char TOPIC[] = "tbmq/demo";
constexpr char MESSAGE[] = "Hello World";

constexpr char USERNAME[] = "tbmq_websockets_username1";
constexpr char PASSWORD[] = "";
String CLIENT_ID = "test";

WiFiClient wifiClient;
PubSubClient client(wifiClient);

void setup() {
  // put your setup code here, to run once:
  Serial.begin(SERIAL_DEBUG_BAUD);
  initWiFi();
  connectMqttBroker();
  
}

void callback(char *topic, byte *payload, unsigned int length) {
    Serial.print("Received message: ");
    for (int i = 0; i < length; i++) {
        Serial.print((char) payload[i]);
    }
    Serial.println("\nTopic: ");
    Serial.print(topic);
    Serial.println();
}

void loop() {
    client.loop();
}

void initWiFi() {
  Serial.println("Connecting to AP ...");
  // Attempting to establish a connection to the given WiFi network
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    // Delay 500ms until a connection has been succesfully established
    delay(500);
    Serial.print(".");
  }
  Serial.println("Connected to AP");
}

void connectMqttBroker() {
  client.setServer(MQTT_HOST, MQTT_PORT);
  client.setCallback(callback);
  while (!client.connected()) {
      if (client.connect(CLIENT_ID.c_str(), USERNAME, PASSWORD)) {
          Serial.println("Client connected!");
          pubSub();  
      } else {
          Serial.print("Client not connected, reason code: ");
          Serial.print(client.state());
          delay(3000);
      }
  }  
}

void pubSub() {
  client.subscribe(TOPIC);
  client.publish(TOPIC, MESSAGE);
}

{:copy-code}
```

Connect USB-TTL adapter to PC and select the corresponding port in Arduino IDE. Compile and Upload your sketch to the device using "Upload" button.

#### Next steps

