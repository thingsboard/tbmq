### Java using WebSocket
This guide will walk you through setting up an MQTT over WebSocket client in Java using the Eclipse Paho library. 
You'll learn how to install the Paho library, connect to an MQTT broker using WebSocket, subscribe to a topic, and publish a message using Java.

##### Prerequisites
In order to use Paho library in your Maven project, please make sure you have added the following dependency to your `pom.xml` file:

```bash
<dependencies>
    <dependency>
        <groupId>org.eclipse.paho</groupId>
        <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
        <version>1.2.5</version>
    </dependency>
</dependencies>
{:copy-code}
```

##### Connect to the TBMQ

The Java code below is an example of how to establish a connection to the TBMQ using default credentials `TBMQ WebSockets MQTT Credentials`, subscribe to a topic, and publish a message.

In case you have changed the `TBMQ WebSockets MQTT Credentials`, don't forget to update the client ID, username, and password in the guide.

```bash
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

import java.nio.charset.StandardCharsets;

public class TBMQMain {
  
   public static void main(String[] args) throws Exception {
       final String serverURI = "ws://{:wsHost}:{:wsPort}";
       final String clientId = "tbmq_test_client";
       final String username = "tbmq_websockets_username";
       final String password = "";

       System.out.println("Connecting to TBMQ...");

       // Create MQTT 5 client
       MqttClient client = new MqttClient(serverURI, clientId);

       // Set connection options
       MqttConnectionOptions options = new MqttConnectionOptions();
       options.setCleanStart(true);
       options.setUserName(username);
       options.setPassword(password.getBytes(StandardCharsets.UTF_8));

       // Connect to the TBMQ
       client.connect(options);
       System.out.println("Connected successfully");

       // Set subscriptions for the client
       MqttSubscription[] subscriptions = {new MqttSubscription("tbmq/demo/+", 1)};
       // Set listener that is handling the messages received from the broker
       IMqttMessageListener[] listeners = {(topic, msg) -> {
           System.out.println("Received message for topic: " + topic + " with payload: " + msg);
           //System.out.println("Disconnecting the client...");

           // Disconnect client once the message is received
           //client.disconnect();
           //client.close();
       }};

       System.out.println("Subscribing to topic: " + subscriptions[0].getTopic());

       // Subscribe to the topic "tbmq/demo/+" with QoS 1
       client.subscribe(subscriptions, listeners);

       System.out.println("Publishing message...");

       // Send a message to the topic "tbmq/demo/topic" with payload "Hello, TBMQ!" and QoS 1
       client.publish("tbmq/demo/topic", "Hello, TBMQ!".getBytes(StandardCharsets.UTF_8), 1, false);
   }
}

{:copy-code}
```

After setting up your credentials and server details, you can run the application from your IDE. 
The output should confirm that the client successfully connected to the broker, subscribed to a topic, and published a message.
You'll also see the received message echoed back in the console.

Example output:

```bash
Connecting to TBMQ...
Connected successfully
Subscribing to topic: tbmq/demo/+
Publishing message...
Received message for topic: tbmq/demo/topic with payload: Hello, TBMQ!
```

#### See also

For more details and advanced usage, refer to the Eclipse Paho client [documentation on GitHub](https://github.com/eclipse/paho.mqtt.java).
