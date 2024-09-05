### Arduino
[Arduino](https://en.wikipedia.org/wiki/Arduino) is an open-source prototyping platform based on easy-to-use hardware and software.
Arduino boards are able to read inputs from sensor or buttons, process it and turn it into an output. The applications in this samples that are running on Arduino are developed using [Arduino IDE](https://www.arduino.cc/en/Main/Software).

In this guide to establish MQTT communication between clients and TBMQ broker we will use an Arduino-based library - PubSubClient.
The MQTT client will connect to the broker, subscribe to a topic and publish a message.

The Arduino source code was developed using [Arduino IDE](https://www.arduino.cc/en/software).

##### Prerequisites

In order to start programming you will need Arduino IDE installed and all related software.

###### Step 1. Arduino UNO and Arduino IDE setup
Download and install [Arduino IDE](https://www.arduino.cc/en/Main/Software).

To learn how to connect your Uno board to the computer and upload your first sketch please follow this [guide](https://www.arduino.cc/en/Guide/ArduinoUno).

###### Step 2. Install Arduino libraries

Open Arduino IDE and go to **Sketch -> Include Library -> Manage Libraries**.
Find and install the following libraries:

- [PubSubClient by Nick O'Leary](http://pubsubclient.knolleary.net/).
- [ArduinoJson by Benoit Blanchon](https://github.com/bblanchon/ArduinoJson)

**Note** that this tutorial was tested with the following versions of the libraries:

- PubSubClient 2.6
- ArduinoJson 5.8.0 

##### Connect to the TBMQ

The code snippet below provides an example on how to:
1. Connect to a TBMQ broker using default credentials `TBMQ WebSockets MQTT Credentials`.
2. Subscribe for a topic.
3. Publish a message.
4. Handle received message.

Please do not forget to edit following constants and variables in the sketch:

- `WIFI_AP` - name of your access point
- `WIFI_PASSWORD` - access point password
- `MQTT_HOST` - the address of the server
- `MQTT_PORT` - the port to connect to

```bash
#include <WiFi.h>
#include <PubSubClient.h>

constexpr char WIFI_AP[] = "WIFI_AP";
constexpr char WIFI_PASSWORD[] = "WIFI_PASSWORD";
constexpr char MQTT_HOST[] = "MQTT_HOST"; // In case of working locally use you public IP address
const int MQTT_PORT = MQTT_PORT; // default TBMQ port is 1883

constexpr uint32_t SERIAL_DEBUG_BAUD = 115200U;
constexpr char TOPIC[] = "tbmq/demo";
constexpr char MESSAGE[] = "Hello World";
constexpr char USERNAME[] = "tbmq_websockets_username";
constexpr char PASSWORD[] = "";
String CLIENT_ID = "tbmq_websockets_client_id";

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
    Serial.println("Topic: ");
    Serial.print(topic);
    Serial.println();
}

void loop() {
    client.loop();
}

void initWiFi() {
  Serial.println("Connecting to AP ...");
  // Attempting to establish a connection to the given WiFi network
  WiFi.begin(WIFI_AP, WIFI_PASSWORD);
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

Connect your Arduino UNO device via USB cable and select `Arduino/Genuino Uno` port in Arduino IDE. Compile and upload your sketch to the device using `Upload` button.

After application will be uploaded and started it will try to connect MQTT client to the TBMQ broker, subscribe for a topic and publish a message.

##### Troubleshooting

When the application is running you can select `Arduino/Genuino Uno` port in Arduino IDE and open `Serial Monitor` in order to view debug information produced by serial output.

##### See also

- [Arduino wiki](https://en.wikipedia.org/wiki/Arduino)
- [Official page](https://www.arduino.cc/en/Main/ArduinoBoardUno)
