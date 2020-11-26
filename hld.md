#### Questions and possible solutions:
- how to get all Clients that subscribe for this topic:
    - for each produced topic cache and update all client's subscriptions (need to change on each new topic and each new subscription)
    - can use Trie data structure (search should be in logarithmic time)
- what to do if broker disconnects (in **clientSession** topic we set exact broker IDs)
    - run some thread to rebalance clientSessions to different brokers (need to think more about possible downsides)
    - ?
- what to do if there's one more broker
    - probably do nothing as all the clients are the same and that new broker will have new clients

#### Startup Flow:
- in **ClientSessionService** read **clientSession** topic and populate local cache
- in **RetainMessageService** read **retainMessage** topic and populate local cache (may require too much memory)
- start **PersistedMessageCleaner** to periodically clean unneeded messages from DB
- start **ZkDiscoveryService** to listen events about other nodes


#### Publish Flow:
- receive publish message in **Session**
- with **MessageDispatcher** push message to Kafka **message** topic
- push response to Client
...
- **MessageProcessor**:
    - has all subscribed clients for this topic 
    - polls messages
    - save to DB if has persistent session
    - push message to all required **broker.downlink** topics
    - push to **retainMessage** topic if msg is retained
- **DownlinkProcessor**:
    - polls messages
    - get clients for this topic
    - if message offset is NOT next offset to stored **latestSuccessfulMessageOffset**:
        - read persisted messages from that offset
        - push them to client
        - if success push to **latestSuccessfulOffset** Kafka topic
    - push message to client with **Session**
    - if success push to **latestSuccessfulOffset** Kafka topic
  

#### Connect Flow:
- receive connect message in **Session**
- check if this Client is already connected using **ClientService**:
    - try insert into **activeClients** new Client's ID
- authenticate the device with **AuthService**:
    - get **Client** and **ClientCredentials** from DB
    - validate credentials and if Client is new - save him with correct CredentialsId
    - if auth is with TLS, save the common name of certificate
- get **ClientSession** from **ClientSessionService**
- send response to the Client
- if (doesn't have persistence session):
    - call **ClientSessionService** and save new ClientSession to Kafka **clientSession** topic
- else:
    - call **ClientSessionService** and update **clientSession** with proper **brokerId**
    - read the latest successful offset for ClientId from **latestSuccessfulOffset** Kafka topic
    - read all persisted messages with **PersistentSessionService** -> filter by TenantId -> push to Client
    - read all retained messages with **RetainMessageService** -> filter by TenantId -> push to Client


#### Disconnect Flow:
- receive disconnect message in **Session**
- if has LastWill:
    - same logic as for **Publish Flow**
- if no persistent session:
    - use **ClientSessionService** to remove session from Kafka



#### Subscribe Flow:
- receive subscribe message in **Session**
- with **ClientSessionSevice** update clientSession (locally and in Kafka) (need to update Kafka even for non-filter subs to be able to restore data after restart) 
- push response to Client
- read all retained messages with **RetainMessageService** -> filter by TenantId -> push to Client
...
- **ClientSessionListener** polls clientSession topic and updates information about subscriptions 
- **RetainMessageListener** polls retainMessage topic and updates information about retained messages 


#### Unsubscribe Flow:
- receive unsubscribe message in **Session**
- with **ClientSessionSevice** update clientSession (locally and in Kafka) 
- push response to Client



#### Kafka Topics:
 - **clientSession**: 
   - key: ClientId
   - value: {Subscription[ ], BrokerId, IsPersistenceSession, TenantId}
 - **retainMessage**:
   - key: Topic
   - value: {Message, TenantId}
 - **latestSuccessfulOffset**:
   - key: ClientId
   - value: {LastSuccessfullyPushedMessageOffset}
 - **message**:
   - key: Topic
   - value: {Message, TenantId}
 - **brokerN.downlink**:
   - key: Topic
   - value: {Message, TenantId}
   
   
   
#### DB Entities:
 - message: {ID, Topic, Timestemp, ?ClientID, ?TenantId, Payload}\
 - activeClients (needs to have proper constrains): {ClientId}
 - client: {ClientId, CredentialsId, TenantId}
 - clientCredentials: {CredentialsType, Username, Password, Certificate}
 - tenant: {TenantId, Firstname, Lastname, Email}
 
 
#### Java Services:
 - SubscriptionCache:
   - search Clients and TopicFilters by Topic
 - Session:
   - instance for each Client's session
   - reads and writes from/to Client
 - SessionManager:
   - creates new sessions for newly connected clients
 - PublishHandler:
   - persists to DB
   - pushes to Kafka brokerN.message topic
   - pushes to Kafka retainMessage topic
 - SubscribeHandler
 - ConnectHandler
 - DisconnectHandler
 - UnsubscribeHandler
 - ClientSessionListener
 - ClientSessionService
 - RetainMessageListener
 - RetainMessageService
 - AuthService
 - ClientService
 - PersistentSessionService
 - MessageDispatcher
 - DownlinkProcessor
 - PersistedMessageCleaner
 - ZkDiscoveryService
 - for DB: use async code from Thingsboard (queues for SQL and BufferedRateExecutor for Cassandra) or some other approach
 - for Kafka consumer and producers: use code from Thingsboard
