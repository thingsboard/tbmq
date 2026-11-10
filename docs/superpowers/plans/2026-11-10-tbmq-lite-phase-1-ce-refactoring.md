# TBMQ Lite Phase 1 (CE refactoring) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare TBMQ CE for a second, Kafka-free edition without changing how today's cluster edition behaves: the Kafka and Valkey code moves into its own modules, the edition comes from the classpath, and the seams Phase 2 plugs into exist.

**Architecture:** Kafka types leave the queue interfaces, then the Kafka and Valkey implementations move into `common/queue-kafka` and `common/cache-redis`. `application` keeps them as runtime-scope dependencies, so the cluster packages stay the same while `application`'s own code can no longer compile against them. The edition comes from a classpath marker that `TbmqEditionCondition` reads, and `@ClusterComponent`/`@StandaloneComponent` gate beans. `@StandaloneComponent` beans never load in CE 2.5.0; they are the hooks Phase 2 builds on.

**Tech Stack:** Java 25, Spring Boot 3.5.16, Maven, kafka-clients 3.9.2, Jedis/Lettuce, bucket4j 8.13.0, JUnit 5 (JUnit 4 stays for the existing integration base), Mockito, AssertJ, Testcontainers 1.21.4, ArchUnit 1.4.1, maven-enforcer-plugin 3.5.0, Angular 21 with Vitest for the new UI unit tests.

**Spec:** `docs/superpowers/specs/2026-10-14-tbmq-lite-design.md`: D8, D11, §4.2 to §4.6, §7.1 to §7.3, §8.1, §8.2, §9 items 1, 2 and 9, §10 Phase 1. File and line evidence: `docs/superpowers/research/2026-10-14-tbmq-lite-code-check.md` §1.4 to §1.7, §2, §4, §14. Line numbers there refer to d9be3f994 and drift; class names are stable.

## Global Constraints

- Phase 1 is "CE refactoring, no behaviour change" (spec §10). For the cluster edition, Kafka topics, keys, Valkey keys, REST paths, the UI and INFO-level logs stay as they are. The only intended API change is two additive fields in `GET /api/app/config`: `edition` and `capabilities`.
- No `tbmq.mode` property and no environment variable. The edition is read from the classpath resource `META-INF/tbmq-edition`. Its value is `cluster` in `common/queue-kafka`; `lite` arrives with `dao-rocksdb` in Phase 2 (D8, §4.2).
- `TbmqEdition`, `TbmqEditionCondition`, `@ClusterComponent` and `@StandaloneComponent` live in `common/util`, in package `org.thingsboard.mqtt.broker.common.util.edition` (§4.2).
- `application` depends on `queue-kafka` and `cache-redis` with `<scope>runtime</scope>`, and its main code compiles without them. The cluster boot jar, deb, rpm and Windows packages, and where they are built, stay as they are. `msa/tbmq` and `msa/mqtt-broker` keep installing the deb (§4.3).
- Moved classes keep their Java package; only the Maven module changes. Move them with `git mv` so history follows and imports in CE, PE and the open PRs stay valid.
- Beans inside `queue-kafka` and `cache-redis` need no edition annotation, because those modules are only ever on cluster classpaths. A bean in a shared module gets `@ClusterComponent` only when a task here says so; the full sweep is Phase 2.
- Capability names are `applicationClients`, `kafka`, `cluster`, `integrations`, `nodeDrain` and `bridge`. The cluster edition sets all of them `true` except `bridge`; the lite edition sets all of them `false` (§8.1).
- SCRAM follows RFC 5802 and RFC 7677 with SHA-256 and SHA-512. It reads the stored credential format unchanged: salt, storedKey, serverKey and 4096 iterations (§7.3).
- New tests use JUnit 5 with AssertJ and Mockito. Every new source file carries the license header; run `mvn license:format -pl <module>` before committing.
- Verify with targeted module builds only, never a full local reactor build; TeamCity runs the full suite on every PR. Before `mvn -pl application test`, install the upstream modules the task changed (`mvn -pl <changed modules> install -DskipTests`). Testing `application` also needs `ui-ngx` in `~/.m2`; if it is missing, run `mvn -pl ui-ngx install -DskipTests` once. Integration tests (`*TestCase`, controller tests) need Docker plus the TeamCity PostgreSQL, so leave them to TeamCity unless both are available locally.
- Commit messages use the repo's `type(scope): summary` style. PRs target `develop/2.x` and fill the `.github` PR template. (Decided 2026-11-10: new-release work goes to `develop/2.x`, cut from `main` 5dfc5dc3b, and `main` stays the stable line. PRs 1 and 2 were merged into `main` before that.) Each PR description lists the classes it moved and the interfaces it added, so #370, #385, #400, #401 and #404 can rebase (§10, §13). Ask the user before pushing or opening a PR.
- Phase 1 ships in CE 2.5.0, then merges into PE (which drops `msa/tbmq-lite` and adapts its executor) and into the demo (D11, §10).

## Review Focus

These are the inputs most likely to hurt a user that the spec implies but does not test. The test for each one sits in the task named in brackets.

1. **Fresh install and upgrade of the cluster images after the bootstrap changes.** Auto-configuration moves, the infrastructure modules become runtime-scope and the marker joins the boot jar. A fresh `thingsboard/tbmq` must still install and start, and a 2.4.1 data volume must still upgrade with credentials and queued DEVICE messages intact. [Task 11 pins the annotations; Task 22 runs both on real images.]
2. **SCRAM inputs from real clients.** These include escaped usernames (`=2C`, `=3D`), an authzid equal or not equal to the username, `y`/`p=` channel-binding flags, a tampered nonce, proof or channel binding, and a credential stored for the other algorithm. Each must get the same accept or reject outcome as the Kafka-based server, and every failure must be a `SaslException`, never a runtime exception escaping into the session handler. [Task 1]
3. **SCRAM credentials stored by 2.4.x** (base64 salt, storedKey and serverKey JSON) must authenticate unchanged after the upgrade. [Task 2]
4. **UI deep links and reloads on a route whose capability is off, or before `/api/app/config` has loaded.** The app must redirect to `/home` exactly once, with no loop and no console error. The home-page config table must not list `edition` or `capabilities`. [Task 18]
5. **Integration cleanup when a topic or consumer group no longer exists.** This must still log at debug, not warn, and the other topic must still be deleted. Any other failure must still propagate. [Task 4]

## PR map

| PR | Tasks | Needs | Branch (from `origin/develop/2.x`; PRs 1 and 2 were from `main`) |
|---|---|---|---|
| 1 SCRAM without kafka-clients | 1, 2 | — | `feature/lite-p1-scram` |
| 2 Kafka-free queue interfaces | 3, 4 | — | `feature/lite-p1-queue-api` |
| 3 `common/queue-kafka` | 5 | PR 1, PR 2 | `feature/lite-p1-queue-kafka` |
| 4 `common/cache-redis` | 6, 7 | PR 3 | `feature/lite-p1-cache-redis` |
| 5 Edition foundation | 8, 9, 10 | PR 3 | `feature/lite-p1-edition` |
| 6 Spring bootstrap | 11, 12 | PR 4 | `feature/lite-p1-bootstrap` |
| 7 Cluster seams, APPLICATION stand-ins | 13, 14, 15 | PR 5 | `feature/lite-p1-seams` |
| 8 `AppController` split, capabilities, UI | 16, 17, 18 | PR 5 | `feature/lite-p1-capabilities` |
| 9 Edition-neutral test base | 19 | PR 4 | `feature/lite-p1-test-base` |
| 10 Enforcement | 20, 21 | PRs 1 to 9 | `feature/lite-p1-enforcement` |
| Exit | 22, 23, 24 | all | — |

PR 1 must land before PR 3, because SCRAM is the last `application` code that needs `kafka-clients` at compile time. PRs 1 and 2 can run in parallel, as can PRs 6, 7, 8 and 9 once their prerequisites have landed.

## Not in this plan (later phases)

- Phase 2: the full `@ClusterComponent` sweep and every standalone implementation (local session events, disconnects, notifications, registry, quotas, pipeline); the lite test module, `application`'s test-jar, and excluding the cluster test setup from that jar.
- Phase 3: `application`'s imports of dao internals (`ProtocolUtil`, `UserServiceImpl`, `SqlQueueStatsManager`) and of `org.springframework.jdbc` in the install services; the JPA part of the `dao` ArchUnit rule; the Hibernate and PostgreSQL bans in the Lite Enforcer rule.
- Phase 4: the UI page adaptations in spec Appendix B, `api/openapi-lite.json`, and the image-level edition tests.

---

## PR 1: SCRAM without kafka-clients

### Task 1: A SCRAM SASL server of our own

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/service/auth/enhanced/TbScramSaslServer.java`
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/service/auth/enhanced/ScramCredential.java`
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/service/auth/enhanced/ScramCredentialCallback.java`
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/service/auth/enhanced/TbScramSaslServerTest.java`

**Interfaces:**
- Consumes: `ScramAlgorithm` (common/data: `fromMqttName`, `getHmacAlgorithm`, `getHashAlgorithm`, `getHashByteSize`, `getMqttAlgorithmName`).
- Produces:
```java
public record ScramCredential(byte[] salt, byte[] storedKey, byte[] serverKey, int iterations) {}

public class ScramCredentialCallback implements javax.security.auth.callback.Callback {
    public ScramCredential getScramCredential();
    public void setScramCredential(ScramCredential scramCredential);
}

public final class TbScramSaslServer implements javax.security.sasl.SaslServer {
    /** Empty unless mechanism is "SCRAM-SHA-256" or "SCRAM-SHA-512". */
    public static Optional<TbScramSaslServer> create(String mechanism, CallbackHandler callbackHandler);
    TbScramSaslServer(ScramAlgorithm algorithm, CallbackHandler callbackHandler, Supplier<String> serverNonceSupplier); // tests
    // SaslServer: getMechanismName, evaluateResponse, isComplete, getAuthorizationID, unwrap, wrap, getNegotiatedProperty, dispose
}
```
- Callback contract, kept identical to Kafka's server so `ScramAuthCallbackHandler` keeps working: on the client-first message, call `callbackHandler.handle(new Callback[]{new NameCallback("username", username), new ScramCredentialCallback()})`.

- [ ] **Step 1: Write the failing tests**

The RFC 7677 §3 example (SCRAM-SHA-256). Build the credential from password `pencil`, salt `Base64("W22ZaJ0SNY7soEsUEjb6gQ==")` and 4096 iterations with a test-local helper (PBKDF2, then HMAC "Client Key"/"Server Key", then hash). The server nonce supplier returns `%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0`.

```java
@Test void rfc7677Sha256Exchange() throws Exception {
    byte[] serverFirst = server.evaluateResponse(utf8("n,,n=user,r=rOprNGfwEbeRWgbNEkqO"));
    assertThat(new String(serverFirst, UTF_8)).isEqualTo(
        "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096");
    byte[] serverFinal = server.evaluateResponse(utf8(
        "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ="));
    assertThat(new String(serverFinal, UTF_8)).isEqualTo("v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=");
    assertThat(server.isComplete()).isTrue();
    assertThat(server.getAuthorizationID()).isEqualTo("user");
}
```

The rest of the test class is one test per case, each asserting `SaslException` (`assertThatThrownBy(...).isInstanceOf(SaslException.class)`) unless the case says otherwise:
- `sha256AndSha512RoundTripWithKafkaScramClient`: Kafka's `ScramSaslClientProvider` client, which is on the test classpath, completes against our server for both mechanisms. Copy the client set-up from `Mqtt5AuthIntegrationTestCase.createScramSaslClient`.
- `escapedUsernameIsDecodedBeforeLookup`: `n,,n=a=2Cb=3Dc,r=x`. The `NameCallback` default name is `a,b=c`.
- `authzidEqualToUsernameIsAccepted`: `n,a=user,n=user,r=x`, then `c=` set to base64 of `n,a=user,`. The exchange completes.
- `authzidDifferentFromUsernameFails`.
- `channelBindingFlagOtherThanNFails`: both `y,,n=user,r=x` and `p=tls-unique,,n=user,r=x`. Kafka's server accepted only `n`.
- `mandatoryExtensionFails`: `n,,m=ext,n=user,r=x`.
- `tokenauthExtensionFails`: `n,,n=user,r=x,tokenauth=true`.
- `wrongProofFails`: flip one byte of the RFC proof; `isComplete()` stays `false`.
- `wrongNonceFails`.
- `wrongChannelBindingInFinalFails`: `c=` set to base64 of `y,,`.
- `credentialForOtherAlgorithmFails`: a SHA-512 credential with mechanism `SCRAM-SHA-256`.
- `lookupFailureBecomesSaslException`: the handler throws `RuntimeException`, and the `SaslException`'s cause is that exception.
- `callsAfterCompletionOrFailureThrowSaslException`.
- `createIsEmptyForUnsupportedMechanisms`: `"SCRAM-SHA-1"`, `"PLAIN"` and `null`.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl application test -Dtest=TbScramSaslServerTest`
Expected: compilation failure, because `TbScramSaslServer` does not exist.

- [ ] **Step 3: Implement the three classes**

Use a state machine: receive client-first, then receive client-final, then COMPLETE or FAILED. Parse with RFC 5802 §7 grammar:
- Use a gs2 flag of `n` only, an optional `a=` that must equal the username, `saslname` unescaping, ignored `k=v` extensions except `tokenauth=true` (which fails), and failure on `m=`.
- The default nonce is `new BigInteger(130, secureRandom).toString(Character.MAX_RADIX)`, the same as Kafka's formatter.
- The proof is valid when `H(ClientProof XOR HMAC(StoredKey, AuthMessage))` equals StoredKey, compared with `MessageDigest.isEqual`. A proof whose length is not the hash size fails.
- Clear the credential arrays on completion, on failure and in `dispose`.
- `wrap`/`unwrap` throw `IllegalStateException`, because SCRAM has no security layer. `getNegotiatedProperty` returns `null` once complete.

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -pl application test -Dtest=TbScramSaslServerTest`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/org/thingsboard/mqtt/broker/service/auth/enhanced/{TbScramSaslServer,ScramCredential,ScramCredentialCallback}.java application/src/test/java/org/thingsboard/mqtt/broker/service/auth/enhanced/TbScramSaslServerTest.java
git commit -m "feat(auth): add a SCRAM SASL server that does not depend on kafka-clients"
```

### Task 2: Run MQTT 5 SCRAM on the new server

**Files:**
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/auth/DefaultEnhancedAuthenticationService.java` (`init()`, `initiateScramServerWithCallback`, `createSaslServer`)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/auth/enhanced/ScramAuthCallbackHandler.java`
- Modify: `application/src/test/java/org/thingsboard/mqtt/broker/service/auth/DefaultEnhancedAuthenticationServiceTest.java`, `application/src/test/java/org/thingsboard/mqtt/broker/service/auth/enhanced/ScramAuthCallbackHandlerTest.java`
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/service/auth/enhanced/ScramStoredCredentialsCompatibilityTest.java`

**Interfaces:**
- Consumes: `TbScramSaslServer.create(String, CallbackHandler)`, `ScramCredentialCallback` and `ScramCredential` (Task 1).
- Produces: `SaslServer DefaultEnhancedAuthenticationService.createSaslServer(String authMethod, ScramAuthCallbackHandler handler)`, which returns `TbScramSaslServer.create(authMethod, handler).orElse(null)`. Its signature is unchanged; a `null` result still leads to `FAILED_TO_INIT_SCRAM_SERVER`.

- [ ] **Step 1: Write the failing compatibility test**

The test uses a credential exactly as 2.4.x stores it:
1. Build `MqttClientCredentials` with type `SCRAM`, client type `DEVICE` and `credentialsValue = JacksonUtil.toString(ScramMqttCredentials.newInstance("tbmq-user", "secret", alg, List.of(".*")))`.
2. Return it from a mocked `MqttClientCredentialsService.findMatchingCredentials(List.of("scram|tbmq-user"))`, and stub `AuthorizationRuleService.parseAuthorizationRule`.
3. Run Kafka's SCRAM client against `TbScramSaslServer.create(alg.getMqttAlgorithmName(), new ScramAuthCallbackHandler(service, ruleService)).orElseThrow()`.

```java
@ParameterizedTest @EnumSource(ScramAlgorithm.class)
void credentialStoredBy24xAuthenticates(ScramAlgorithm alg) { /* exchange completes; handler.getClientType() == ClientType.DEVICE */ }
@ParameterizedTest @EnumSource(ScramAlgorithm.class)
void wrongPasswordIsRejected(ScramAlgorithm alg) { /* client uses "wrong": server throws SaslException on client-final */ }
```

In `DefaultEnhancedAuthenticationServiceTest`:
- Replace `verifyScramSaslServerProviderInitiated` with `givenScramSha256AuthMethod_whenCreateSaslServer_thenReturnsTbScramSaslServer`, which asserts `isInstanceOf(TbScramSaslServer.class)`.
- Add `givenUnsupportedAuthMethod_whenOnClientConnectMsg_thenFailedToInitScramServer`.
- Change the `ScramSaslServer` mocks to `SaslServer`.

In `ScramAuthCallbackHandlerTest`, switch to the new callback and credential types; the asserted messages stay the same.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl application test -Dtest='ScramStoredCredentialsCompatibilityTest,DefaultEnhancedAuthenticationServiceTest,ScramAuthCallbackHandlerTest'`
Expected: compilation failures in the handler test, and the compatibility test fails because the handler still expects Kafka's callback.

- [ ] **Step 3: Switch the service and the handler**

- Delete `ScramSaslServerProvider.initialize()` and the `instanceof ScramSaslServer` branch.
- `ScramAuthCallbackHandler` fills `ScramCredentialCallback` with `new ScramCredential(salt, storedKey, serverKey, ITERATIONS_COUNT)`.
- Keep every exception message.

- [ ] **Step 4: Run them, and grep for Kafka imports**

Run: `mvn -pl application test -Dtest='TbScramSaslServerTest,ScramStoredCredentialsCompatibilityTest,DefaultEnhancedAuthenticationServiceTest,ScramAuthCallbackHandlerTest'`
Expected: all tests pass.
Run: `grep -rn 'org.apache.kafka' application/src/main/java/org/thingsboard/mqtt/broker/service/auth`
Expected: no output. `Mqtt5AuthIntegrationTestCase` runs on TeamCity.

- [ ] **Step 5: Commit, then ask the user to push and open PR 1**

```bash
git add -A application/src
git commit -m "refactor(auth): run MQTT 5 SCRAM on the TBMQ SASL server"
```

## PR 2: Kafka-free queue interfaces

### Task 3: Partition and rebalance types without Kafka

**Files:**
- Create: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbTopicPartition.java`
- Create: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueRebalanceListener.java`
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueConsumer.java` (`subscribe(ConsumerRebalanceListener)`)
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueControlledOffsetConsumer.java` (`seekToTheBeginning(Collection<TopicPartition>)`, `getEndOffset(Collection<TopicPartition>)`)
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/common/AbstractTbQueueConsumerTemplate.java`, `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/kafka/TbKafkaConsumerTemplate.java`
- Modify: `integration/executor/src/main/java/org/thingsboard/mqtt/broker/integration/service/DefaultClusterIntegrationService.java` (the partition maps, `newTopicPartition`, `TbConsumerRebalanceListener`)
- Test: `common/queue/src/test/java/org/thingsboard/mqtt/broker/queue/TbTopicPartitionTest.java`, `common/queue/src/test/java/org/thingsboard/mqtt/broker/queue/kafka/TbKafkaRebalanceListenerAdapterTest.java`

**Interfaces:**
- Produces:
```java
public record TbTopicPartition(String topic, int partition) {
    @Override public String toString() { return topic + "-" + partition; } // Kafka's format, so executor logs stay identical
}
public interface TbQueueRebalanceListener {
    void onPartitionsRevoked(Collection<TbTopicPartition> partitions);
    void onPartitionsAssigned(Collection<TbTopicPartition> partitions);
}
// TbQueueConsumer:                 void subscribe(TbQueueRebalanceListener listener);
// TbQueueControlledOffsetConsumer: void seekToTheBeginning(Collection<TbTopicPartition> partitions);
//                                  Map<TbTopicPartition, Long> getEndOffset(Collection<TbTopicPartition> partitions);
// TbKafkaConsumerTemplate (package-private, static):
static ConsumerRebalanceListener toKafkaListener(TbQueueRebalanceListener listener); // converts each collection into a List, preserving order
```

- [ ] **Step 1: Write the failing tests**

```java
assertThat(new TbTopicPartition("tbmq.ie.downlink.http", 3)).hasToString("tbmq.ie.downlink.http-3");
assertThat(new TbTopicPartition("t", 1)).isEqualTo(new TbTopicPartition("t", 1)).hasSameHashCodeAs(new TbTopicPartition("t", 1));

TbQueueRebalanceListener listener = mock(TbQueueRebalanceListener.class);
ConsumerRebalanceListener kafka = TbKafkaConsumerTemplate.toKafkaListener(listener);
kafka.onPartitionsAssigned(List.of(new TopicPartition("t", 0), new TopicPartition("t", 2)));
verify(listener).onPartitionsAssigned(List.of(new TbTopicPartition("t", 0), new TbTopicPartition("t", 2)));
kafka.onPartitionsLost(List.of(new TopicPartition("t", 1)));   // Kafka's default delegates to revoked
verify(listener).onPartitionsRevoked(List.of(new TbTopicPartition("t", 1)));
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/queue test -Dtest='TbTopicPartitionTest,TbKafkaRebalanceListenerAdapterTest'`
Expected: compilation failure.

- [ ] **Step 3: Change the interfaces, the template, the Kafka adapter and the executor**

The executor's maps are keyed by `TbTopicPartition`, and `TbConsumerRebalanceListener` implements `TbQueueRebalanceListener`.

- [ ] **Step 4: Run the tests, then compile the executor**

Run: `mvn -pl common/queue install -DskipTests && mvn -pl common/queue test && mvn -pl integration/executor test`
Expected: BUILD SUCCESS. The executor's container tests need Docker.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(queue): replace Kafka partition and rebalance types in the consumer interfaces"
```

### Task 4: Admin, metadata and not-found errors without Kafka

**Files:**
- Create: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueTopicNotFoundException.java`, `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueConsumerGroupNotFoundException.java`
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueMsgMetadata.java` (it becomes a method-less type; `KafkaTbQueueMsgMetadata` keeps `getMetadata()`)
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/TbQueueAdmin.java` (remove `getNodes()` and `listConsumerGroupOffsets(String)`; Javadoc the two exceptions on `deleteTopic` and `deleteConsumerGroup`)
- Modify: `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/kafka/TbKafkaAdmin.java` (keep both removed methods as plain public methods; map the failures), `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/kafka/stats/TbKafkaConsumerStatsService.java` (inject `TbKafkaAdmin`)
- Modify: `common/integration/cluster-integration-api/src/main/java/org/thingsboard/mqtt/broker/service/health/KafkaHealthIndicator.java` (inject `TbKafkaAdmin`), `common/integration/cluster-integration-api/src/main/java/org/thingsboard/mqtt/broker/service/queue/IntegrationTopicServiceImpl.java` (both `GroupIdNotFoundException` checks)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/integration/IntegrationCleanupServiceImpl.java` (the `UnknownTopicOrPartitionException` check)
- Test: `common/queue/src/test/java/org/thingsboard/mqtt/broker/queue/kafka/TbKafkaAdminFailureMappingTest.java`; modify `common/integration/cluster-integration-api/src/test/java/org/thingsboard/mqtt/broker/service/queue/IntegrationTopicServiceImplTest.java` and `application/src/test/java/org/thingsboard/mqtt/broker/service/integration/IntegrationCleanupServiceImplTest.java` (the `doThrow` near line 158)

**Interfaces:**
- Produces:
```java
public class TbQueueTopicNotFoundException extends RuntimeException {
    public TbQueueTopicNotFoundException(String topic, Throwable cause);          // message "Topic not found: " + topic
}
public class TbQueueConsumerGroupNotFoundException extends RuntimeException {
    public TbQueueConsumerGroupNotFoundException(String groupId, Throwable cause); // message "Consumer group not found: " + groupId
}
// TbKafkaAdmin, package-private static:
static Throwable mapDeleteTopicFailure(String topic, Throwable failure);                    // UnknownTopicOrPartitionException anywhere in the chain -> TbQueueTopicNotFoundException
static Exception mapDeleteConsumerGroupFailure(String groupId, ExecutionException failure); // cause GroupIdNotFoundException -> TbQueueConsumerGroupNotFoundException, else failure itself
```
- `deleteTopic` passes the mapped throwable to `callback.onFailure`. `deleteConsumerGroup` throws the mapped exception when it is a `RuntimeException`, and otherwise rethrows the original `ExecutionException`. The error text of the hidden `DELETE /api/app/consumer-group` endpoint changes for a missing group; record that in the PR description.

- [ ] **Step 1: Write the failing tests**

```java
assertThat(TbKafkaAdmin.mapDeleteTopicFailure("t", new CompletionException(new UnknownTopicOrPartitionException("x"))))
    .isInstanceOf(TbQueueTopicNotFoundException.class).hasMessage("Topic not found: t");
Throwable other = new TimeoutException();
assertThat(TbKafkaAdmin.mapDeleteTopicFailure("t", other)).isSameAs(other);
assertThat(TbKafkaAdmin.mapDeleteConsumerGroupFailure("g", new ExecutionException(new GroupIdNotFoundException("x"))))
    .isInstanceOf(TbQueueConsumerGroupNotFoundException.class).hasMessage("Consumer group not found: g");
ExecutionException timeout = new ExecutionException(new TimeoutException());
assertThat(TbKafkaAdmin.mapDeleteConsumerGroupFailure("g", timeout)).isSameAs(timeout);
```

In `IntegrationTopicServiceImplTest`, add two tests, reusing the class's existing constants:
- `givenMissingConsumerGroup_whenDeleteTopic_thenTopicIsStillDeleted`: `deleteConsumerGroup` throws `TbQueueConsumerGroupNotFoundException`; verify `queueAdmin.deleteTopic(topic, callback)`. Do the same for `deleteEventTopic`.
- `givenConsumerGroupDeleteTimeout_whenDeleteTopic_thenRethrowsAndKeepsTopic`: `deleteConsumerGroup` throws `TimeoutException`. The call throws a `RuntimeException` whose cause is that `TimeoutException`, and `deleteTopic` is never called.

In `IntegrationCleanupServiceImplTest`, the missing-topic case throws `new TbQueueTopicNotFoundException("topic", null)`. Its existing assertions stay: logged at debug, and the other topic is still deleted.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/queue test -Dtest=TbKafkaAdminFailureMappingTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the interface changes, the mappings and the three callers**

- [ ] **Step 4: Run the tests**

Run: `mvn -pl common/queue install -DskipTests && mvn -pl common/queue test && mvn -pl common/integration/integration-api,common/integration/cluster-integration-api install && mvn -pl application test -Dtest='IntegrationCleanupServiceImplTest,RestPublishServiceImplTest'`
Expected: all tests pass.
Run: `grep -rn 'import org.apache.kafka' common/integration application/src/main | grep -v '/auth/'`
Expected: no output.

- [ ] **Step 5: Commit, then ask the user to push and open PR 2**

```bash
git commit -am "refactor(queue): drop Kafka types from the admin and metadata interfaces"
```

## PR 3: `common/queue-kafka`

### Task 5: Move the Kafka implementation into its own module

**Files:**
- Create: `common/queue-kafka/pom.xml`. Base it on `common/queue/pom.xml`: parent `org.thingsboard.mqtt-broker:common`, groupId `org.thingsboard.mqtt-broker.common`, artifactId `queue-kafka`, name `TBMQ Kafka Queue`. Its dependencies are `queue`, `kafka-clients`, `spring-boot-autoconfigure`, slf4j/logback, and `spring-boot-starter-test` plus `awaitility` for tests. Leave out the protobuf plugin, because the protos stay in `queue`.
- Move with `git mv` from `common/queue/src/main/java/org/thingsboard/mqtt/broker/queue/` to `common/queue-kafka/src/main/java/org/thingsboard/mqtt/broker/queue/`:
  - `kafka/**` (41 files);
  - `common/AbstractTbQueueConsumerTemplate.java`;
  - `provider/AbstractQueueFactory.java` and the 14 `provider/Kafka*QueueFactory.java`;
  - `provider/integration/KafkaHttpIntegrationDownlinkQueueFactory.java`, `KafkaIntegrationDownlinkQueueFactoryImpl.java`, `KafkaIntegrationMsgQueueFactory.java`, `KafkaIntegrationUplinkNotificationsQueueFactory.java`, `KafkaIntegrationUplinkQueueFactory.java`, `KafkaMqttIntegrationDownlinkQueueFactory.java`;
  - `integration/IntegrationUplinkNotificationsHelperImpl.java`.

  Keep `provider/integration/KafkaIntegrationDownlinkQueueFactory.java` in `queue`: it is the neutral interface for the KAFKA integration type.
- Move with `git mv`: `common/queue/src/test/java/org/thingsboard/mqtt/broker/queue/kafka/**` (from Tasks 3 and 4) to the same path under `common/queue-kafka`.
- Move with `git mv`: `common/integration/cluster-integration-api/src/main/java/org/thingsboard/mqtt/broker/service/health/KafkaHealthIndicator.java` to `common/queue-kafka/src/main/java/org/thingsboard/mqtt/broker/service/health/KafkaHealthIndicator.java`.
- Modify: `common/pom.xml` (add `<module>queue-kafka</module>` after `queue`); the root `pom.xml` `<dependencyManagement>` (add `queue-kafka` at `${project.version}`); `common/queue/pom.xml` (remove `kafka-clients`); `application/pom.xml` (add `queue-kafka` with `<scope>runtime</scope>`); `integration/executor/pom.xml` (add `queue-kafka` at compile scope).
- Modify the four per-node topic helpers in `application`, so they read their properties with `@Value` instead of the Kafka settings classes. The topic names stay identical.
  - `service/mqtt/client/disconnect/DisconnectClientCommandHelperImpl.java`: `${queue.kafka.disconnect-client-command.topic-prefix}`
  - `service/processing/downlink/DownLinkPublisherHelperImpl.java`: `${queue.kafka.basic-downlink-msg.topic-prefix}` and `${queue.kafka.persisted-downlink-msg.topic-prefix}`
  - `service/notification/InternodeNotificationsHelperImpl.java`: `${queue.kafka.internode-notifications.topic-prefix}`
  - `service/historical/stats/HistoricalStatsTotalHelperImpl.java`: `${queue.kafka.historical-data-total.topic}`
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/service/PerNodeTopicHelpersTest.java`; update `InternodeNotificationsHelperImplTest` to set fields instead of mocking settings.

**Interfaces:**
- Produces: the Maven artifact `org.thingsboard.mqtt-broker.common:queue-kafka`. All moved classes keep their names and packages.

- [ ] **Step 1: Write the failing helper test**

Set the `@Value` fields with `ReflectionTestUtils` to the YAML defaults (`tbmq.client.disconnect`, `tbmq.msg.downlink.basic`, `tbmq.msg.downlink.persisted`, `tbmq.sys.internode.notifications`, `tbmq.sys.historical.data`) and `kafkaPrefix` set to `""`, then to `"acme."`, and call `init()`:

```java
assertThat(disconnectHelper.getServiceTopic("node-1")).isEqualTo("tbmq.client.disconnect.node-1");
assertThat(downLinkHelper.getBasicDownLinkServiceTopic("node-1")).isEqualTo("tbmq.msg.downlink.basic.node-1");
assertThat(downLinkHelper.getPersistentDownLinkServiceTopic("node-1")).isEqualTo("tbmq.msg.downlink.persisted.node-1");
assertThat(internodeHelper.getServiceTopic("node-1")).isEqualTo("tbmq.sys.internode.notifications.node-1");
assertThat(historicalHelper.getTopic()).isEqualTo("tbmq.sys.historical.data");
// with kafkaPrefix "acme.": every name starts with "acme."
```

As shipped in #411, after review: setting the fields by reflection cannot catch two swapped keys, and producers and consumers take the topic from the same helper, so integration tests cannot catch it either. The test therefore resolves the `@Value` keys against the shipped `thingsboard-mqtt-broker.yml` in an `ApplicationContextRunner`, loaded with `YamlPropertySourceLoader`. The initializer removes the system-properties and system-environment sources first, so `TB_KAFKA_*` variables or `-D` flags on the build machine cannot change the expected names. Use the same pattern whenever a test pins values that come from the shipped config.

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=PerNodeTopicHelpersTest`
Expected: failure, because the helpers still require the settings beans.

- [ ] **Step 3: Create the module, move the files, change the poms and the helpers**

- [ ] **Step 4: Verify the split**

Run: `mvn -pl common/queue dependency:tree -Dincludes=org.apache.kafka`
Expected: no kafka line.
Run: `mvn -pl common/queue,common/queue-kafka,common/integration/integration-api,common/integration/cluster-integration-api,integration/executor install -DskipTests && mvn -pl application compile`
Expected: BUILD SUCCESS. That proves `application`'s main code compiles without Kafka.
Run: `mvn -pl common/queue,common/queue-kafka,common/integration/cluster-integration-api test`
Expected: all tests pass.
Run: `mvn -pl application dependency:tree -Dscope=runtime -Dincludes=org.thingsboard.mqtt-broker.common:queue-kafka`
Expected: `queue-kafka:jar:<version>:runtime`, so the boot jar and the deb still carry it.
Run: `mvn -pl application test -Dtest='PerNodeTopicHelpersTest,InternodeNotificationsHelperImplTest,InternodeNotificationsServiceImplTest,HistoricalStatsTotalConsumerTest'`
Expected: all tests pass.

- [ ] **Step 5: Commit, then ask the user to push and open PR 3**

The PR description lists every moved class.

```bash
git add -A common application integration pom.xml
git commit -m "build(queue): move the Kafka implementation into common/queue-kafka"
```

## PR 4: `common/cache-redis`

### Task 6: The node registry behind `ServiceRegistryStore`

**Files:**
- Create: `common/cache/src/main/java/org/thingsboard/mqtt/broker/cache/ServiceRegistryStore.java`
- Create: `common/cache/src/main/java/org/thingsboard/mqtt/broker/cache/RedisServiceRegistryStore.java` (moves to `cache-redis` in Task 7)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/system/TbmqSystemInfoService.java`
- Test: `common/cache/src/test/java/org/thingsboard/mqtt/broker/cache/RedisServiceRegistryStoreTest.java`; modify `application/src/test/java/org/thingsboard/mqtt/broker/service/system/TbmqSystemInfoServiceTest.java`

**Interfaces:**
- Produces a storage primitive rather than registry logic. That way PE's own registry logic (typed fields, legacy-field cleanup) sits on it unchanged.
```java
public interface ServiceRegistryStore {
    void put(String field, String value);
    Optional<String> get(String field);
    Map<String, String> entries();   // never null; empty when nothing is registered
    long delete(String... fields);   // number of fields removed
}
```
- `RedisServiceRegistryStore` is a `@Component` over `RedisTemplate<String, Object>`. Its key is `cacheProperties.prefixKey(CacheConstants.SERVICE_REGISTRY_KEY)`, computed in `@PostConstruct`. Keys and values are stringified with `String.valueOf`, and a null hash means empty or 0.
- `TbmqSystemInfoService` loses `RedisTemplate`, `CacheProperties`, `serviceRegistryKey` and its setter, and keeps its behaviour and messages. `removeServiceInfo` throws `ITEM_NOT_FOUND` when `delete(serviceId) == 0`.

- [ ] **Step 1: Write the failing tests**

`RedisServiceRegistryStoreTest` (mock `RedisTemplate` and `HashOperations`, cache prefix empty):

```java
store.put("svc1", "TBMQ");            verify(hashOps).put("tbmq:service:registry", "svc1", "TBMQ");
when(hashOps.entries("tbmq:service:registry")).thenReturn(Map.of("svc1", "TBMQ"));
assertThat(store.entries()).containsExactly(entry("svc1", "TBMQ"));
when(hashOps.delete("tbmq:service:registry", "svc3")).thenReturn(1L);   assertThat(store.delete("svc3")).isEqualTo(1L);
when(hashOps.get("tbmq:service:registry", "svc9")).thenReturn(null);     assertThat(store.get("svc9")).isEmpty();
```

`TbmqSystemInfoServiceTest`: move each existing scenario from `HashOperations` mocks to `ServiceRegistryStore` mocks, with the same assertions.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/cache test -Dtest=RedisServiceRegistryStoreTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the store and switch `TbmqSystemInfoService`**

- [ ] **Step 4: Run the tests**

Run: `mvn -pl common/cache install && mvn -pl application test -Dtest='TbmqSystemInfoServiceTest,InternodeNotificationsHelperImplTest'`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(system): read the node registry through ServiceRegistryStore"
```

### Task 7: Move the Valkey implementation into its own module

**Files:**
- Create: `common/cache-redis/pom.xml`: parent `org.thingsboard.mqtt-broker:common` (as in `common/cache/pom.xml`), groupId `org.thingsboard.mqtt-broker.common`, artifactId `cache-redis`, name `TBMQ Redis Cache`. Dependencies are `cache`, `dao-api`, `util`, `data`, `spring-data-redis`, `jedis`, `lettuce-core`, `bucket4j_jdk17-jedis` and `bucket4j_jdk17-redis-common`. Test dependencies are `spring-boot-starter-test`, `testcontainers`, `bcpkix-jdk18on`, `junit` and `awaitility`.
- Move with `git mv` from `common/cache` to `common/cache-redis`, keeping package `org.thingsboard.mqtt.broker.cache`:
  - `AbstractLettuceConnectionManager`, `ClusterAwareLettuceConnectionManager`, `DefaultLettuceConnectionManager`, `LettuceConfig`, `LettuceConnectionManager`, `RedisSslCredentials`, `TBRedisCacheConfiguration`, `TBRedisClusterConfiguration`, `TBRedisSentinelConfiguration`, `TBRedisStandaloneConfiguration`, `TbRedisStatsLogger`, `RedisServiceRegistryStore`;
  - every test under `common/cache/src/test`.
- Move with `git mv` from `dao` to `common/cache-redis`, keeping package `org.thingsboard.mqtt.broker.dao.messages`: `DeviceMsgServiceImpl`, `ClientIdLastPacketIdCacheKey`, `ClientIdMessagesCacheKey`.
- Move with `git mv` from `application` to `common/cache-redis`: `service/limits/RateLimitRedisCacheServiceImpl.java`, plus its tests `service/limits/RateLimitRedisCacheServiceImplTest.java` and `service/limits/RateLimitBucketConfigurationInjectionTest.java`.
- Move with `git mv` from `application` to `common/cache` (the interfaces and configuration the Redis implementation needs): `service/limits/RateLimitCacheService.java`, `config/ClientsLimitProperties.java`, `config/AbstractMsgsRateLimitsConfiguration.java`, `config/DevicePersistedMsgsRateLimitsConfiguration.java`, `config/TotalMsgsRateLimitsConfiguration.java`.
- Modify: `common/pom.xml` (add the module); the root `pom.xml` `<dependencyManagement>`; `common/cache/pom.xml` (remove `spring-data-redis`, `jedis`, `lettuce-core` and the Redis-only test dependencies); `common/util/pom.xml` (remove `bucket4j_jdk17-jedis` and `bucket4j_jdk17-redis-common`); `dao/pom.xml` (add `cache-redis` with `<scope>test</scope>`, so the dao suites keep running against Valkey and `DeviceMsgServiceTest` stays in `dao` until Phase 3); `application/pom.xml` (add `cache-redis` with `<scope>runtime</scope>`).

**Interfaces:**
- Produces: the Maven artifact `org.thingsboard.mqtt-broker.common:cache-redis`. `RateLimitCacheService`, `ServiceRegistryStore` and `DeviceMsgService` are now implemented only there; Phase 2 adds local implementations.

- [ ] **Step 1: The guard that must fail before and pass after**

Run: `mvn -pl common/cache,common/util dependency:tree -Dincludes='redis.clients,io.lettuce,org.springframework.data:spring-data-redis,com.bucket4j:bucket4j_jdk17-jedis'`
Expected now: matches are listed. That is the state this task removes.

- [ ] **Step 2: Create the module, move the files, change the poms**

- [ ] **Step 3: Verify**

Run the dependency-tree command from Step 1 again.
Expected: no matches.
Run: `mvn -pl common/util,common/cache,common/cache-redis,dao install -DskipTests && mvn -pl application compile`
Expected: BUILD SUCCESS.
Run: `mvn -pl application dependency:tree -Dscope=runtime -Dincludes=org.thingsboard.mqtt-broker.common:cache-redis`
Expected: `cache-redis:jar:<version>:runtime`.
Run: `mvn -pl common/cache-redis test`
Expected: all tests pass. `TBRedisTlsConnectionTest` needs Docker.
Run: `mvn -pl application test -Dtest='RateLimitServiceImplTest,ThroughputQuotaServiceImplTest,TbmqSystemInfoServiceTest'`
Expected: all tests pass. The dao suites (Docker) run on TeamCity.

As shipped in PR 4, after review:
- `cache-redis` also needs `junit-vintage-engine` (test scope). `RateLimitRedisCacheServiceImplTest` and `RateLimitBucketConfigurationInjectionTest` are JUnit 4, and without the vintage engine surefire skips them silently (no report). The `TBRedis*` tests that came from `common/cache` are JUnit 5. Check the per-class counts in `target/surefire-reports` whenever tests change modules.
- `mvn -pl application test -Dtest=RedisCacheDefaultConfigurationTest` (no Docker; Lettuce is mocked) is the only non-integration test that builds `RedisServiceRegistryStore`'s real wiring, so run it too.
- A new module changes the root and `common` poms: install them once with `-N` (and `common/dao-api` if `~/.m2` is older) so `-pl dao` and `-pl application` resolve the new artifact from `~/.m2`.
- `RedisServiceRegistryStore.entries()` returns an unmodifiable map, and `String.valueOf` turns a null hash value into `"null"` (2.4.x showed `UNKNOWN` for it in `getServiceInfos`; only a hand-written empty value produces it).

- [ ] **Step 4: Commit, then ask the user to push and open PR 4**

The PR description lists every moved class.

```bash
git add -A common dao application pom.xml
git commit -m "build(cache): move the Valkey implementation into common/cache-redis"
```

## PR 5: Edition foundation

### Task 8: `TbmqEdition` and the marker resolver

**Files:**
- Create: `common/util/src/main/java/org/thingsboard/mqtt/broker/common/util/edition/TbmqEdition.java`, `common/util/src/main/java/org/thingsboard/mqtt/broker/common/util/edition/TbmqEditionResolver.java`
- Modify: `common/util/pom.xml` (test dependency `spring-boot-starter-test`)
- Test: `common/util/src/test/java/org/thingsboard/mqtt/broker/common/util/edition/TbmqEditionResolverTest.java`
- Test helper: `common/util/src/test/java/org/thingsboard/mqtt/broker/common/util/edition/EditionMarkers.java`. It provides `static ClassLoader markers(Path dir, ClassLoader parent, String... contents)`, which creates `dir/<i>/META-INF/tbmq-edition` for each content and returns a `URLClassLoader` over those directories. Task 9 reuses it.

**Interfaces:**
- Produces:
```java
public enum TbmqEdition {
    CLUSTER("cluster"), LITE("lite");
    public String getMarkerValue();
    public static TbmqEdition fromMarkerValue(String value); // IllegalStateException on anything else
}
public final class TbmqEditionResolver {
    public static final String MARKER_RESOURCE = "META-INF/tbmq-edition";
    public static TbmqEdition resolve(ClassLoader classLoader);
}
```
- How `resolve` works:
  - It reads every `MARKER_RESOURCE` from `classLoader.getResources`, using the first line of each that is neither blank nor a `#` comment, trimmed.
  - With no marker it throws `IllegalStateException`. The message names `META-INF/tbmq-edition` and says the image lacks its infrastructure modules.
  - With two different values it throws `IllegalStateException`, listing each value with its URL.
  - Duplicate markers with the same value are fine.
  - Cache the result per class loader in a `ConcurrentReferenceHashMap`, because the condition calls this for every gated bean.

- [ ] **Step 1: Write the failing tests**

The resolver tests pass `parent = null`, so that only the fake markers are visible.

```java
assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "cluster"))).isEqualTo(TbmqEdition.CLUSTER);
assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "lite\n"))).isEqualTo(TbmqEdition.LITE);
assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "# header\ncluster"))).isEqualTo(TbmqEdition.CLUSTER);
assertThat(TbmqEditionResolver.resolve(markers(tmp, null, "cluster", "cluster"))).isEqualTo(TbmqEdition.CLUSTER);
assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null))).isInstanceOf(IllegalStateException.class).hasMessageContaining("META-INF/tbmq-edition");
assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null, "cluster", "lite"))).hasMessageContaining("cluster").hasMessageContaining("lite");
assertThatThrownBy(() -> TbmqEditionResolver.resolve(markers(tmp, null, "edge"))).hasMessageContaining("edge");
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/util -am test -Dtest=TbmqEditionResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run them and see them pass**

Run the same command as Step 2.
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(util): resolve the TBMQ edition from a classpath marker"
```

### Task 9: The edition condition and the two annotations

**Files:**
- Create in `common/util/src/main/java/org/thingsboard/mqtt/broker/common/util/edition/`: `TbmqEditionCondition.java`, `ConditionalOnTbmqEdition.java`, `ClusterComponent.java`, `StandaloneComponent.java`
- Modify: `common/util/pom.xml` (add `spring-context`, compile scope)
- Test: `common/util/src/test/java/org/thingsboard/mqtt/broker/common/util/edition/TbmqEditionConditionTest.java`

**Interfaces:**
- Consumes: `TbmqEditionResolver.resolve(ClassLoader)` (Task 8).
- Produces:
```java
@Retention(RUNTIME) @Target({TYPE, METHOD}) @Documented @Conditional(TbmqEditionCondition.class)
public @interface ConditionalOnTbmqEdition { TbmqEdition value(); }

@Retention(RUNTIME) @Target({TYPE, METHOD}) @Documented @ConditionalOnTbmqEdition(TbmqEdition.CLUSTER)
public @interface ClusterComponent {}

@Retention(RUNTIME) @Target({TYPE, METHOD}) @Documented @ConditionalOnTbmqEdition(TbmqEdition.LITE)
public @interface StandaloneComponent {}

public class TbmqEditionCondition implements Condition {
    // matches when the edition resolved from context.getClassLoader() (fallback ClassUtils.getDefaultClassLoader())
    // equals the value of the merged @ConditionalOnTbmqEdition
}
```
- Like `@TbmqComponent`, these are conditions only. Beans still carry `@Service`, `@Component` or `@Configuration`.

- [ ] **Step 1: Write the failing tests**

Use `EditionMarkers.markers` from Task 8 with the test class loader as parent, so Spring can load the fixture classes. `common/util` itself has no marker on its classpath.

```java
@Component @ClusterComponent static class ClusterBean {}
@Component @StandaloneComponent static class StandaloneBean {}

@Test void clusterMarkerKeepsOnlyClusterBeans() {
    var ctx = new AnnotationConfigApplicationContext();
    ctx.setClassLoader(EditionMarkers.markers(tmp, getClass().getClassLoader(), "cluster"));
    ctx.register(ClusterBean.class, StandaloneBean.class);
    ctx.refresh();
    assertThat(ctx.getBeansOfType(ClusterBean.class)).hasSize(1);
    assertThat(ctx.getBeansOfType(StandaloneBean.class)).isEmpty();
}
// liteMarkerKeepsOnlyStandaloneBeans: the mirror image
// noMarkerFailsContextStart: refresh() throws, root cause IllegalStateException mentioning "META-INF/tbmq-edition"
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/util -am test -Dtest=TbmqEditionConditionTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run them and see them pass**

Run the same command as Step 2.
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(util): add the edition condition and @ClusterComponent/@StandaloneComponent"
```

### Task 10: The cluster marker and the `TbmqEdition` bean

**Files:**
- Create: `common/queue-kafka/src/main/resources/META-INF/tbmq-edition` (one line: `cluster`)
- Modify: the root `pom.xml` license-maven-plugin `<excludes>` (add `<exclude>**/META-INF/tbmq-edition</exclude>`)
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/config/TbmqEditionConfiguration.java`
- Test: `common/queue-kafka/src/test/java/org/thingsboard/mqtt/broker/queue/kafka/ClusterEditionMarkerTest.java`, `application/src/test/java/org/thingsboard/mqtt/broker/config/TbmqEditionConfigurationTest.java`

**Interfaces:**
- Consumes: `TbmqEditionResolver`, `TbmqEdition` (Task 8).
- Produces:
```java
@Configuration @Slf4j
public class TbmqEditionConfiguration implements BeanClassLoaderAware {
    @Bean
    public TbmqEdition tbmqEdition(Environment environment);
}
```
- `tbmqEdition` resolves the edition with the bean class loader. It throws `IllegalStateException("TBMQ Lite runs only with service.type=tbmq, got " + type)` when the edition is `LITE` and `environment.getProperty("service.type", "tbmq")` is not `tbmq`. It logs `TBMQ edition: {}` with the marker value at INFO.
- It lives in `application`, not `common/util`, so the dao and cache test contexts, which have no marker, keep starting. The install application does not scan `config`, and nothing it loads needs the bean.

- [ ] **Step 1: Write the failing tests**

```java
// ClusterEditionMarkerTest (queue-kafka's own test classpath carries exactly its marker)
assertThat(TbmqEditionResolver.resolve(getClass().getClassLoader())).isEqualTo(TbmqEdition.CLUSTER);

// TbmqEditionConfigurationTest. application's test classpath cannot see common/util's test helper, so copy the
// few lines of EditionMarkers.markers into a private method here, with parent null: the real cluster marker is on this classpath.
config.setBeanClassLoader(markers(tmp, "cluster"));
assertThat(config.tbmqEdition(new MockEnvironment())).isEqualTo(TbmqEdition.CLUSTER);
config.setBeanClassLoader(markers(tmp, "lite"));
assertThat(config.tbmqEdition(new MockEnvironment())).isEqualTo(TbmqEdition.LITE);
assertThatThrownBy(() -> config.tbmqEdition(new MockEnvironment().withProperty("service.type", "tbmq-integration-executor")))
    .isInstanceOf(IllegalStateException.class).hasMessage("TBMQ Lite runs only with service.type=tbmq, got tbmq-integration-executor");
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl common/util,common/queue-kafka install -DskipTests && mvn -pl common/queue-kafka test -Dtest=ClusterEditionMarkerTest`
Expected: failure: "No TBMQ edition marker".

- [ ] **Step 3: Add the marker, the license exclude and the configuration**

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -pl common/queue-kafka install && mvn -pl common/queue-kafka license:check && mvn -pl application test -Dtest=TbmqEditionConfigurationTest`
Expected: all tests pass, and `license:check` passes.

- [ ] **Step 5: Commit, then ask the user to push and open PR 5**

```bash
git commit -am "feat(edition): mark queue-kafka as the cluster edition and expose the TbmqEdition bean"
```

## PR 6: Spring bootstrap

### Task 11: Auto-configuration on the Boot entry points

**Files:**
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/ThingsboardMqttBrokerApplication.java` and `application/src/main/java/org/thingsboard/mqtt/broker/ThingsboardMqttBrokerInstallApplication.java`. Each gets `@EnableAutoConfiguration(excludeName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")`.
- Modify: `dao/src/main/java/org/thingsboard/mqtt/broker/dao/JpaDaoConfig.java` (remove `@EnableAutoConfiguration`; keep `@ComponentScan`, `@EnableJpaRepositories`, `@EntityScan` and `@EnableTransactionManagement`)
- Modify: `dao/src/test/java/org/thingsboard/mqtt/broker/dao/service/AbstractServiceTest.java` (add the same `@EnableAutoConfiguration`). Then run `grep -rln 'JpaDaoConfig\|@ComponentScan' --include=*.java */src/test` and give any other test context that relied on `JpaDaoConfig` for auto-configuration the same annotation.
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/SpringBootEntryPointsTest.java`

**Interfaces:** none new. Spring Boot defers auto-configuration imports, so declaring them on the entry points processes them in the same order as before.

- [ ] **Step 1: Write the failing test**

```java
@ParameterizedTest @ValueSource(classes = {ThingsboardMqttBrokerApplication.class, ThingsboardMqttBrokerInstallApplication.class})
void entryPointEnablesAutoConfigurationWithoutRedis(Class<?> entryPoint) {
    EnableAutoConfiguration ann = AnnotatedElementUtils.findMergedAnnotation(entryPoint, EnableAutoConfiguration.class);
    assertThat(ann).isNotNull();
    assertThat(ann.excludeName()).contains("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
}
@Test void jpaDaoConfigNoLongerEnablesAutoConfiguration() {
    assertThat(AnnotatedElementUtils.hasAnnotation(JpaDaoConfig.class, EnableAutoConfiguration.class)).isFalse();
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=SpringBootEntryPointsTest`
Expected: failure.

- [ ] **Step 3: Move the annotation**

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -pl dao install -DskipTests && mvn -pl application test -Dtest=SpringBootEntryPointsTest`
Expected: all tests pass. The dao suites and the full application suite run on TeamCity; Task 22 checks install and upgrade on real images.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(config): enable Spring Boot auto-configuration on the entry points, not in JpaDaoConfig"
```

### Task 12: Status checker and error handler without Hikari or Hibernate types

**Files:**
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/config/ApplicationStatusChecker.java`. Replace `@Autowired HikariDataSource` with a constructor-injected `ObjectProvider<javax.sql.DataSource>`.
  - In `@PostConstruct`, when a `DataSource` exists, open a connection and require `isValid(5)`. Any `SQLException` or an invalid connection throws `RuntimeException("Couldn't start database connection.", cause)`, the same message as today.
  - When no `DataSource` exists (Lite from Phase 3), it checks nothing; Phase 3 adds the store check.
  - The class does not move to `dao`. The dao test contexts scan everything, and `HikariDataSource.isRunning()` is `false` until the lazily created pool has handed out a connection, so a moved checker could fail there depending on bean order.
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/exception/ThingsboardErrorResponseHandler.java`. Remove the import at line 25 and the `instanceof ConstraintViolationException` branch in `handleDatabaseException`. The branch is unreachable: `databaseException` is always a Spring `DataAccessException` (see `BaseController.handleException` and `handle`), and Hibernate's exception is never one.
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/config/ApplicationStatusCheckerTest.java`, `application/src/test/java/org/thingsboard/mqtt/broker/exception/ThingsboardErrorResponseHandlerDatabaseErrorTest.java`

**Interfaces:**
- Produces: `public ApplicationStatusChecker(ObjectProvider<DataSource> dataSource)`, with `@PostConstruct public void init()`.

- [ ] **Step 0: Write the failing status-checker test**

```java
DataSource ds = mock(DataSource.class); Connection c = mock(Connection.class);
when(ds.getConnection()).thenReturn(c); when(c.isValid(5)).thenReturn(true);
assertThatCode(() -> new ApplicationStatusChecker(providerOf(ds)).init()).doesNotThrowAnyException();
when(c.isValid(5)).thenReturn(false);
assertThatThrownBy(() -> new ApplicationStatusChecker(providerOf(ds)).init()).hasMessage("Couldn't start database connection.");
when(ds.getConnection()).thenThrow(new SQLException("down"));
assertThatThrownBy(() -> new ApplicationStatusChecker(providerOf(ds)).init()).hasMessage("Couldn't start database connection.").hasRootCauseMessage("down");
assertThatCode(() -> new ApplicationStatusChecker(providerOf(null)).init()).doesNotThrowAnyException();
// providerOf: a StaticListableBeanFactory holding ds (or nothing) -> getBeanProvider(DataSource.class)
```

Run: `mvn -pl application test -Dtest=ApplicationStatusCheckerTest`
Expected: compilation failure, because the constructor does not exist yet.

- [ ] **Step 1: Write a characterization test for the error handler, which passes on today's code**

```java
@Test void constraintViolationInsideDataAccessExceptionIsA500DatabaseError() throws Exception {
    var cause = new org.hibernate.exception.ConstraintViolationException("dup", new SQLException("dup"), "uk");
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handle(new DataIntegrityViolationException("dup", cause), response);
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentAsString()).contains("\"message\":\"Database error\"");
}
@Test void wrappedThingsboardDatabaseExceptionIsA500DatabaseError() { /* same, via new ThingsboardException(dataAccessException, ThingsboardErrorCode.DATABASE) */ }
```

- [ ] **Step 2: Run it on the unchanged code**

Run: `mvn -pl application test -Dtest=ThingsboardErrorResponseHandlerDatabaseErrorTest`
Expected: all tests pass, which pins today's behaviour.

- [ ] **Step 3: Remove the branch and rewrite the checker**

- [ ] **Step 4: Run both tests again and grep**

Run: `mvn -pl application test -Dtest='ApplicationStatusCheckerTest,ThingsboardErrorResponseHandlerDatabaseErrorTest'`
Expected: all tests pass.
Run: `grep -rn -E 'import (org\.hibernate|jakarta\.persistence|com\.zaxxer)' application/src/main`
Expected: no output.

- [ ] **Step 5: Commit, then ask the user to push and open PR 6**

```bash
git commit -am "refactor(config): keep the datasource check and Hibernate types out of application"
```

## PR 7: Cluster seams and APPLICATION stand-ins

Disconnects need nothing in Phase 1: `DisconnectClientCommandService` is already the seam, and Phase 2 adds a local implementation.

### Task 13: The CONNECT reply path as an interface

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/actors/client/service/session/ClientSessionEventResponseSender.java`
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/actors/client/service/session/QueueClientSessionEventResponseSender.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/actors/client/service/session/SessionClusterManagerImpl.java`. Move the producer field, its creation in `init()`, the sender executor and `response-sender-threads`, `sendConnectResponse`, `createResponseHeaders`, and the producer and executor shutdown in `destroy()` into the new class.
- Modify: `application/src/test/java/org/thingsboard/mqtt/broker/actors/client/service/session/SessionClusterManagerImplTest.java` (mock the sender instead of the queue factory)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/actors/client/service/session/QueueClientSessionEventResponseSenderTest.java`

**Interfaces:**
- Produces:
```java
public interface ClientSessionEventResponseSender {
    void send(String clientId, ConnectionRequestInfo requestInfo, ClientSessionEventResponseProto response);
}
@Service @ClusterComponent @RequiredArgsConstructor @Slf4j
public class QueueClientSessionEventResponseSender implements ClientSessionEventResponseSender {
    // ClientSessionEventQueueFactory, ServiceInfoProvider; @Value("${queue.client-session-event-response.response-sender-threads}") int responseSenderThreads;
    @PostConstruct public void init();
    @PreDestroy public void destroy();
}
```
- `SessionClusterManagerImpl` keeps `@Value("${queue.client-session-event-response.max-request-timeout}")` and all of its session logic.

- [ ] **Step 1: Write the failing test**

```java
when(factory.createEventResponseProducer("node-1")).thenReturn(producer);
ReflectionTestUtils.setField(sender, "responseSenderThreads", 1);
sender.init();
UUID requestId = UUID.randomUUID();
sender.send("client-1", new ConnectionRequestInfo(requestId, 0L, "tbmq.client.session.event.response.node-2"), response);
verify(producer, timeout(2000)).send(eq("tbmq.client.session.event.response.node-2"), isNull(),
    argThat(m -> m.getKey().equals("client-1") && m.getValue() == response
        && Arrays.equals(m.getHeaders().get(BrokerConstants.REQUEST_ID_HEADER), BytesUtil.uuidToBytes(requestId))), any());
sender.destroy();
verify(producer).stop();
```

In `SessionClusterManagerImplTest`, the CONNECT scenarios now `verify(responseSender).send(eq(clientId), eq(requestInfo), argThat(...))` with the response assertions they make today.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl application test -Dtest='QueueClientSessionEventResponseSenderTest,SessionClusterManagerImplTest'`
Expected: compilation failure.

- [ ] **Step 3: Extract the class**

- [ ] **Step 4: Run them and see them pass**

Run the same command as Step 2.
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(session): send CONNECT responses through ClientSessionEventResponseSender"
```

### Task 14: One place that applies internode notifications locally

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/service/notification/InternodeNotificationsApplier.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/notification/InternodeNotificationsServiceImpl.java` (`applyLocally` delegates; the class becomes `@ClusterComponent`)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/notification/InternodeNotificationsConsumerImpl.java` (`processInternodeNotification` delegates; the class becomes `@ClusterComponent`)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/service/notification/InternodeNotificationsApplierTest.java`; adapt `InternodeNotificationsServiceImplTest` and `InternodeNotificationsConsumerImplTest`

**Interfaces:**
- Produces:
```java
@Component @RequiredArgsConstructor @Slf4j
public class InternodeNotificationsApplier {
    // MqttAuthProviderNotificationManager, ClientSessionStatsCleanupProcessor, AuthorizationRoutingService, IntegrationLifecycleEventTypeCache
    public void apply(String serviceId, InternodeNotificationProto notificationProto); // the four branches the two classes duplicate today, same trace logs
}
```
- Phase 2's local `InternodeNotificationsService` calls only `apply`.

- [ ] **Step 1: Write the failing test**

There is one test per branch. Each builds a proto with exactly one field set and verifies the single matching handler call, plus `verifyNoMoreInteractions` on the other three. A proto with no field set causes no calls.

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=InternodeNotificationsApplierTest`
Expected: compilation failure.

- [ ] **Step 3: Extract the applier and delegate from both classes**

- [ ] **Step 4: Run the tests**

Run: `mvn -pl application test -Dtest='InternodeNotificationsApplierTest,InternodeNotificationsServiceImplTest,InternodeNotificationsConsumerImplTest'`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(notification): apply internode notifications locally through one component"
```

### Task 15: APPLICATION stand-ins

**Files:**
- Modify: add `@ClusterComponent` to the 12 beans under `application/src/main/java/org/thingsboard/mqtt/broker/service/mqtt/persistence/application/`:
  - `ApplicationMsgQueuePublisherImpl`, `ApplicationPersistenceProcessorImpl`;
  - `delivery/AppMsgBufferedDeliveryStrategy`, `delivery/AppMsgFlushedDeliveryStrategy`;
  - `processing/ApplicationAckStrategyConfiguration`, `processing/ApplicationMsgAcknowledgeStrategyFactory`, `processing/ApplicationPersistedMsgCtxServiceImpl`, `processing/ApplicationSubmitStrategyFactory`;
  - `topic/ApplicationRemovedEventProcessorImpl`, `topic/ApplicationRemovedEventServiceImpl`, `topic/ApplicationTopicServiceImpl`;
  - `util/ApplicationClientHelperServiceImpl`.
- Create in the same packages as their interfaces: `NoOpApplicationPersistenceProcessor`, `NoOpApplicationMsgQueuePublisher`, `topic/NoOpApplicationRemovedEventService`, `topic/NoOpApplicationTopicService`.
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/service/mqtt/persistence/application/ApplicationStandInsTest.java`, `application/src/test/java/org/thingsboard/mqtt/broker/service/mqtt/persistence/application/ApplicationPersistenceEditionTest.java`

**Interfaces:**
- Consumes: `@ClusterComponent` and `@StandaloneComponent` (Task 9).
- Produces four `@Service @StandaloneComponent` beans, which are the only APPLICATION types the shared code injects (code check §4). Creating APPLICATION resources fails loudly; deleting them succeeds; nothing is ever acknowledged and dropped.
  - `NoOpApplicationPersistenceProcessor`: every method is a no-op.
  - `NoOpApplicationRemovedEventService`: no-op.
  - `NoOpApplicationTopicService`: `createTopic` and `createSharedTopic` throw `UnsupportedOperationException("APPLICATION clients are not supported in TBMQ Lite")`; `deleteTopic` calls `callback.onSuccess()`; `deleteSharedTopic` is a no-op.
  - `NoOpApplicationMsgQueuePublisher`: both methods call `callback.onFailure(new UnsupportedOperationException("APPLICATION clients are not supported in TBMQ Lite"))`.

- [ ] **Step 1: Write the failing tests**

```java
assertThatThrownBy(() -> new NoOpApplicationTopicService().createTopic("c"))
    .isInstanceOf(UnsupportedOperationException.class).hasMessage("APPLICATION clients are not supported in TBMQ Lite");
BasicCallback basic = mock(BasicCallback.class);
new NoOpApplicationTopicService().deleteTopic("c", basic);
verify(basic).onSuccess();
PublishMsgCallback publish = mock(PublishMsgCallback.class);
new NoOpApplicationMsgQueuePublisher().sendMsg("c", queueMsg, publish);
verify(publish).onFailure(any(UnsupportedOperationException.class));
verify(publish, never()).onSuccess();
assertThatCode(() -> new NoOpApplicationPersistenceProcessor().processPubAck("c", 1)).doesNotThrowAnyException();
```

`ApplicationPersistenceEditionTest` reads the class files with Spring's `PathMatchingResourcePatternResolver` (`classpath*:org/thingsboard/mqtt/broker/service/mqtt/persistence/application/**/*.class`) and a `CachingMetadataReaderFactory`, so no condition is evaluated. Every class with meta-annotation `@Component` must carry `@ClusterComponent`, except those whose simple name starts with `NoOp`, which must carry `@StandaloneComponent`. Assert that exactly 12 cluster classes and 4 stand-ins are found.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -pl application test -Dtest='ApplicationStandInsTest,ApplicationPersistenceEditionTest'`
Expected: failure.

- [ ] **Step 3: Annotate the 12 beans and add the 4 stand-ins**

- [ ] **Step 4: Run them and see them pass**

Run the same command as Step 2.
Expected: all tests pass.

- [ ] **Step 5: Commit, then ask the user to push and open PR 7**

```bash
git commit -am "feat(application): mark APPLICATION persistence cluster-only and add standalone stand-ins"
```

## PR 8: `AppController` split, capabilities, UI guard

### Task 16: Split `AppController`

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/controller/ClusterAppController.java`. Its annotations are `@RestController @RequestMapping("/api/app") @ClusterComponent @Tag(name = "app-controller", description = "App Controller")` (`io.swagger.v3.oas.annotations.tags.Tag`), so the OpenAPI tag stays the same. It takes `ApplicationRemovedEventProcessor`, `TbQueueAdmin` and `TbAppService` by constructor, and gets the eight endpoints `DELETE /remove-topics`, `GET /cluster-info`, `GET /cluster-info/v2`, `GET /kafka-topics`, `GET /kafka-topics/v2`, `GET /consumer-groups`, `GET /consumer-groups/v2` and `DELETE /consumer-group`. Their method names, parameters, `@PreAuthorize` and `@Operation(hidden = true)` stay byte-for-byte the same.
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/controller/AppController.java`. It keeps `GET /config`, `GET /listener/port`, `GET /brokers`, `GET /service/info` and `DELETE /service/info`, with `BrokerHomePageConfig`, `SystemInfoService` and `TbAppService`.
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/controller/BaseController.java` (remove the `TbQueueAdmin tbQueueAdmin` field)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/service/entity/app/DefaultTbAppService.java` (inject `ObjectProvider<TbQueueAdmin>`; `deleteKafkaConsumerGroup` calls `getObject()`; `deleteServiceInfo` is unchanged)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/controller/AppControllerSplitTest.java`

**Interfaces:**
- Produces: `ClusterAppController`. `AppController` and `DefaultTbAppService` no longer need a `TbQueueAdmin` bean at startup.

- [ ] **Step 1: Write the failing test**

```java
private static Set<String> mappings(Class<?> c) { /* for each @RequestMapping-derived method: http method + " /api/app" + path (+ params) */ }

@Test void splitKeepsEveryEndpoint() {
    assertThat(Sets.union(mappings(AppController.class), mappings(ClusterAppController.class))).containsExactlyInAnyOrder(
        "DELETE /api/app/remove-topics", "GET /api/app/cluster-info", "GET /api/app/cluster-info/v2",
        "GET /api/app/kafka-topics", "GET /api/app/kafka-topics/v2", "GET /api/app/consumer-groups",
        "GET /api/app/consumer-groups/v2", "DELETE /api/app/consumer-group", "GET /api/app/config",
        "GET /api/app/listener/port", "GET /api/app/brokers", "GET /api/app/service/info", "DELETE /api/app/service/info");
}
@Test void sharedControllerHasNoClusterDependencies() {
    assertThat(Arrays.stream(AppController.class.getDeclaredConstructors()).flatMap(c -> Arrays.stream(c.getParameterTypes())))
        .doesNotContain(TbQueueAdmin.class, ApplicationRemovedEventProcessor.class);
    assertThat(AnnotatedElementUtils.hasAnnotation(AppController.class, ClusterComponent.class)).isFalse();
    assertThat(AnnotatedElementUtils.hasAnnotation(ClusterAppController.class, ClusterComponent.class)).isTrue();
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=AppControllerSplitTest`
Expected: compilation failure.

- [ ] **Step 3: Split the controller, trim `BaseController`, adapt `DefaultTbAppService`**

- [ ] **Step 4: Run it, then check the OpenAPI spec**

Run: `mvn -pl application test -Dtest=AppControllerSplitTest`
Expected: all tests pass.
Run, where Docker and the test PostgreSQL are available: `mvn test -pl application -Dtest=OpenApiSpecTest`
Expected: no drift. If it reports drift, regenerate with `-Dtbmq.openapi.update=true`, check that the `api/openapi.json` diff is only ordering, and commit it. Otherwise TeamCity runs the drift check.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(api): split the cluster-only endpoints out of AppController"
```

### Task 17: `edition` and `capabilities` in `/api/app/config`

**Files:**
- Create: `application/src/main/java/org/thingsboard/mqtt/broker/dto/EditionCapabilitiesDto.java`
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/dto/HomePageConfigDto.java` (add `private String edition;` and `private EditionCapabilitiesDto capabilities;`)
- Modify: `application/src/main/java/org/thingsboard/mqtt/broker/config/BrokerHomePageConfig.java` (inject `TbmqEdition`; in `getConfig()`, set `edition(tbmqEdition.getMarkerValue())` and `capabilities(EditionCapabilitiesDto.of(tbmqEdition))`)
- Modify: `api/openapi.json` (regenerated: the two new properties and the new schema)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/dto/EditionCapabilitiesDtoTest.java`

**Interfaces:**
- Consumes: the `TbmqEdition` bean (Task 10).
- Produces:
```java
@Data @Builder
public class EditionCapabilitiesDto {
    private boolean applicationClients; private boolean kafka; private boolean cluster;
    private boolean integrations; private boolean nodeDrain; private boolean bridge;
    public static EditionCapabilitiesDto of(TbmqEdition edition); // CLUSTER: all true except bridge; LITE: all false
}
// JSON: "edition": "cluster", "capabilities": {"applicationClients":true,"kafka":true,"cluster":true,"integrations":true,"nodeDrain":true,"bridge":false}
```

- [ ] **Step 1: Write the failing test**

```java
assertThat(EditionCapabilitiesDto.of(TbmqEdition.CLUSTER)).isEqualTo(EditionCapabilitiesDto.builder()
    .applicationClients(true).kafka(true).cluster(true).integrations(true).nodeDrain(true).bridge(false).build());
assertThat(EditionCapabilitiesDto.of(TbmqEdition.LITE)).isEqualTo(EditionCapabilitiesDto.builder().build());
assertThat(JacksonUtil.toString(EditionCapabilitiesDto.of(TbmqEdition.CLUSTER)))
    .isEqualTo("{\"applicationClients\":true,\"kafka\":true,\"cluster\":true,\"integrations\":true,\"nodeDrain\":true,\"bridge\":false}");
```

Extend the existing `/api/app/config` controller test, wherever `getBrokerConfig` is covered (find it with `grep -rn 'api/app/config' application/src/test`), with `edition == "cluster"` and `capabilities.kafka == true`. If no such test exists, add one to `AppControllerSplitTest` that builds `BrokerHomePageConfig` with mocks.

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=EditionCapabilitiesDtoTest`
Expected: compilation failure.

- [ ] **Step 3: Implement, then regenerate the OpenAPI spec**

Regenerate with `mvn test -pl application -Dtest=OpenApiSpecTest -Dtbmq.openapi.update=true`, or take the generated file from the failing TeamCity run. The diff must be only the two properties and the `EditionCapabilitiesDto` schema.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -pl application test -Dtest=EditionCapabilitiesDtoTest`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/openapi.json application/src
git commit -m "feat(api): report the edition and its capabilities in /api/app/config"
```

### Task 18: Capability-aware menu and route guard in the UI

**Files:**
- Create: `ui-ngx/src/app/core/utils/capability.utils.ts`, `ui-ngx/src/app/core/utils/capability.utils.spec.ts`, `ui-ngx/vitest.config.ts` (environment `node`, include `src/**/*.spec.ts`)
- Modify: `ui-ngx/src/app/shared/models/config.model.ts` (add `edition` and `capabilities` to `BrokerConfig`)
- Modify: `ui-ngx/src/app/core/services/menu.models.ts` (`MenuSection.capability?: Capability`)
- Modify: `ui-ngx/src/app/core/services/menu.service.ts`. Inject `ConfigService`, tag sections, and filter in `buildSysAdminMenu`. The tags are `integrations` on `MenuId.integrations`, `applicationClients` on `MenuId.shared_subscriptions_application`, and `kafka` on `MenuId.kafka_management`.
- Modify the route `data`, adding `capability`:
  - `ui-ngx/src/app/modules/home/pages/kafka-management/kafka-management-routing.module.ts` (the `kafka` route): `kafka`;
  - `ui-ngx/src/app/modules/home/pages/shared-subscription-applications/shared-subscriptions-routing.module.ts` (the `applications` route): `applicationClients`;
  - `ui-ngx/src/app/modules/home/pages/integration/integration-routing.module.ts` (the `integrations` route): `integrations`.
- Modify: `ui-ngx/src/app/core/guards/auth.guard.ts` (after the authority check, deny by redirecting to `/home`)
- Modify: `ui-ngx/src/app/core/http/config.service.ts` (`getBrokerConfigPageData` uses `configTableEntries`)
- Modify: `ui-ngx/package.json` (devDependency `vitest@^3.2.4`; script `"test:unit": "vitest run"`) and `yarn.lock`
- Modify: `ui-ngx/pom.xml` (a `yarn test:unit` frontend-maven-plugin execution in the `yarn-build` profile, before `yarn build`)

**Interfaces:**
- Consumes: the `/api/app/config` fields from Task 17.
- Produces:
```ts
export type TbmqEdition = 'cluster' | 'lite';
export interface EditionCapabilities { applicationClients: boolean; kafka: boolean; cluster: boolean; integrations: boolean; nodeDrain: boolean; bridge: boolean; }
export type Capability = keyof EditionCapabilities;
// capability.utils.ts: only type imports (import type), so Vitest needs no Angular or path aliases
export function isCapabilityEnabled(capabilities: Partial<EditionCapabilities> | null | undefined, capability?: Capability): boolean; // no capability or no capabilities object -> true; else value !== false
export function filterSectionsByCapability(sections: MenuSection[], capabilities: Partial<EditionCapabilities> | null | undefined): MenuSection[]; // recursive over pages, drops a toggle left with no pages
export function requiredCapabilities(routeData: Array<{ capability?: Capability } | undefined>): Capability[];
export function configTableEntries(config: BrokerConfig): Array<[string, unknown]>; // drops 'edition' and 'capabilities'
```
- The guard collects `data.capability` from every snapshot from `state.root` down to the last child, because child routes do not inherit `data` from component routes. Missing capabilities count as enabled, which is the cluster default. A missing config therefore cannot cause a redirect loop, and the backend still answers 404 for absent controllers.

- [ ] **Step 1: Write the failing tests**

```ts
it('allows when nothing is required or config is not loaded', () => {
  expect(isCapabilityEnabled({ kafka: false }, undefined)).toBe(true);
  expect(isCapabilityEnabled(undefined, 'kafka')).toBe(true);
  expect(isCapabilityEnabled(null, 'kafka')).toBe(true);
});
it('denies a disabled capability', () => expect(isCapabilityEnabled({ kafka: false }, 'kafka')).toBe(false));
it('drops disabled sections and pages, keeps the rest', () => {
  const out = filterSectionsByCapability([home, { ...kafkaToggle, capability: 'kafka' }, { ...sharedToggle, pages: [manage, { ...apps, capability: 'applicationClients' }] }],
                                         { kafka: false, applicationClients: false });
  expect(out.map(s => s.id)).toEqual(['home', 'shared_subscriptions']);
  expect(out[1].pages.map(p => p.id)).toEqual(['shared_subscriptions_management']);
});
it('collects capabilities from every route level', () =>
  expect(requiredCapabilities([undefined, { capability: 'kafka' }, {}])).toEqual(['kafka']));
it('keeps edition and capabilities out of the config table', () =>
  expect(configTableEntries({ tcpPort: 1883, edition: 'cluster', capabilities: { kafka: true } } as any).map(([k]) => k)).toEqual(['tcpPort']));
```

- [ ] **Step 2: Run them and see them fail**

Run: `cd ui-ngx && yarn add -D vitest@^3.2.4 && yarn test:unit`
Expected: failure, because `capability.utils` is missing.

- [ ] **Step 3: Implement the utils, models, menu, routes, guard, config table and Maven execution**

- [ ] **Step 4: Run them, lint, and check manually**

Run: `cd ui-ngx && yarn test:unit && yarn lint`
Expected: the tests pass and lint is clean.
Manual check, with the backend running and the UI served by `yarn start`:
1. All menu entries are visible as before.
2. Temporarily patch `EditionCapabilitiesDto.of` to return all `false`. Kafka, integrations and application shared subscriptions disappear from the menu, and opening `/kafka/topics` or `/integrations` directly, or reloading on them, lands once on `/home`.
3. The home config table has no `edition` or `capabilities` row.
4. Revert the patch.

- [ ] **Step 5: Commit, then ask the user to push and open PR 8**

```bash
git add ui-ngx
git commit -m "feat(ui): hide pages by edition capability in the menu and the route guard"
```

## PR 9: Edition-neutral integration-test base

### Task 19: Containers move out of `AbstractPubSubIntegrationTest`

**Files:**
- Create: `application/src/test/java/org/thingsboard/mqtt/broker/testsupport/EditionTestInfrastructure.java`
- Create: `application/src/test/java/org/thingsboard/mqtt/broker/testsupport/cluster/ClusterEditionTestInfrastructure.java`
- Create: `application/src/test/resources/META-INF/services/org.thingsboard.mqtt.broker.testsupport.EditionTestInfrastructure` (one line: `org.thingsboard.mqtt.broker.testsupport.cluster.ClusterEditionTestInfrastructure`)
- Modify: `application/src/test/java/org/thingsboard/mqtt/broker/AbstractPubSubIntegrationTest.java`. Remove the `kafka` and `valkey` class rules, the `ExternalResource`, `ReplaceKafkaPropertiesBeanPostProcessor`, `KafkaTestContainersConfiguration`, the `@Import`, and the Kafka and Testcontainers imports. Add the single class rule below.
- Modify: `application/src/test/java/org/thingsboard/mqtt/broker/service/testing/integration/TotalThroughputQuotaRedisOutageIntegrationTestCase.java` (`valkey` becomes `ClusterEditionTestInfrastructure.valkey()`)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/testsupport/EditionTestInfrastructureTest.java`

**Interfaces:**
- Produces:
```java
public interface EditionTestInfrastructure {
    TestRule classRule();
    /** Exactly one provider must be on the test classpath (java.util.ServiceLoader); fails with the list otherwise. */
    static TestRule loadClassRule();
}
// in AbstractPubSubIntegrationTest:
@ClassRule public static final TestRule editionInfrastructure = EditionTestInfrastructure.loadClassRule();

public class ClusterEditionTestInfrastructure implements EditionTestInfrastructure {
    public static GenericContainer<?> valkey();
    // classRule(): RuleChain.outerRule(KAFKA).around(VALKEY).around(ExternalResource) where before() sets system properties
    //   queue.kafka.bootstrap.servers = KAFKA.getBootstrapServers(), redis.connection.type = standalone,
    //   redis.standalone.host / redis.standalone.port from VALKEY, redis.password = password; after() clears all five.
    // Containers are as today: confluentinc/cp-kafka:7.9.2 and valkey/valkey:8.0 with --requirepass password,
    // started and stopped per test class.
}
```
- The bean post-processor goes away because `TbKafkaConsumerSettings`, `TbKafkaProducerSettings` and `TbKafkaAdminSettings` already read `@Value("${queue.kafka.bootstrap.servers}")`, and system properties override the YAML.
- Phase 2 must keep `testsupport/cluster/**` and the services file out of `application`'s test-jar.

- [ ] **Step 1: Write the failing test**

```java
@Test void exactlyTheClusterInfrastructureIsOnTheApplicationTestClasspath() {
    assertThat(ServiceLoader.load(EditionTestInfrastructure.class).stream().map(ServiceLoader.Provider::type))
        .containsExactly(ClusterEditionTestInfrastructure.class);
    assertThat(EditionTestInfrastructure.loadClassRule()).isNotNull();
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -pl application test -Dtest=EditionTestInfrastructureTest`
Expected: compilation failure.

- [ ] **Step 3: Implement and refactor the base**

- [ ] **Step 4: Run it, grep, and run integration tests where possible**

Run: `mvn -pl application test -Dtest=EditionTestInfrastructureTest`
Expected: all tests pass.
Run: `grep -n -E 'kafka|Kafka|valkey|GenericContainer' application/src/test/java/org/thingsboard/mqtt/broker/AbstractPubSubIntegrationTest.java`
Expected: no output.
With Docker and the test PostgreSQL: `mvn -pl application test -Dtest='MqttClientCredentialsControllerTest,Mqtt5AuthIntegrationTestCase,TotalThroughputQuotaRedisOutageIntegrationTestCase'`
Expected: all tests pass. Otherwise TeamCity runs the full suite.

- [ ] **Step 5: Commit, then ask the user to push and open PR 9**

```bash
git commit -am "test(infra): make the integration-test base edition-neutral"
```

## PR 10: Enforcement

### Task 20: ArchUnit rules

**Files:**
- Modify: the root `pom.xml` (property `archunit.version` = `1.4.1`, and `com.tngtech.archunit:archunit` in `<dependencyManagement>`); `application/pom.xml` (`archunit`, test scope)
- Test: `application/src/test/java/org/thingsboard/mqtt/broker/architecture/EditionArchitectureTest.java`, `application/src/test/java/org/thingsboard/mqtt/broker/architecture/fixture/KafkaImportingFixture.java` (imports `org.apache.kafka.common.TopicPartition`, from the runtime-scope test classpath)

**Interfaces:**
- Consumes: one anchor class per module, used to import that module's classes by location (directory or jar):

| Module | Anchor class |
|---|---|
| `application` | `ThingsboardMqttBrokerApplication` |
| `dao` | `JpaDaoConfig` |
| `common/queue` | `TbQueueProducer` |
| `common/queue-kafka` | `TbKafkaAdmin` |
| `common/cache` | `TbCacheOps` |
| `common/cache-redis` | `TBRedisCacheConfiguration` |
| `common/util` | `JacksonUtil` |
| `cluster-integration-api` | `IntegrationTopicServiceImpl` |

- Rules: each set of modules must not depend on the listed package roots.

| Modules | Forbidden package roots |
|---|---|
| `application` (spec §4.6, plus `spring.data.redis`, `bucket4j.redis` and Hikari) | `org.apache.kafka`, `redis.clients`, `io.lettuce`, `org.springframework.data.redis`, `io.github.bucket4j.redis`, `jakarta.persistence`, `org.hibernate`, `com.zaxxer.hikari`, `org.rocksdb` |
| `dao` (the JPA part starts in Phase 3) | `org.apache.kafka`, `redis.clients`, `io.lettuce`, `org.springframework.data.redis`, `io.github.bucket4j.redis`, `org.rocksdb` |
| `common/queue`, `common/cache`, `common/util`, `cluster-integration-api` | the Kafka, Redis and RocksDB roots above |
| `queue-kafka` | the Redis roots and `org.rocksdb` |
| `cache-redis` | `org.apache.kafka` and `org.rocksdb` |

- [ ] **Step 1: Write the rules, including a check that they bite**

```java
@Test void applicationDependsOnNoInfrastructure() { APPLICATION_RULE.check(classesOf(ThingsboardMqttBrokerApplication.class)); }
// ... one @Test per row in the table above
@Test void ruleCatchesAKafkaImport() {
    assertThatThrownBy(() -> APPLICATION_RULE.check(new ClassFileImporter().importClasses(KafkaImportingFixture.class)))
        .isInstanceOf(AssertionError.class).hasMessageContaining("org.apache.kafka");
}
```

- [ ] **Step 2: Run them**

Run: `mvn -pl application test -Dtest=EditionArchitectureTest`
Expected: all tests pass, because Tasks 1 to 12 removed every violation. A failure here names the offending class: fix the class, not the rule.

- [ ] **Step 3: Commit**

```bash
git commit -am "test(arch): enforce edition-neutral modules with ArchUnit"
```

### Task 21: Maven Enforcer rules and the `msa/tbmq-lite` skeleton

**Files:**
- Modify: the root `pom.xml` `<pluginManagement>` (`maven-enforcer-plugin` 3.5.0)
- Modify: `application/pom.xml`. Add execution `ban-lite-modules` at phase `validate`, with `bannedDependencies` excludes `org.rocksdb:rocksdbjni` and `org.thingsboard.mqtt-broker:dao-rocksdb`, and the message "application packages the cluster edition; dao-rocksdb and rocksdbjni belong to the tbmq-lite build".
- Create: `msa/tbmq-lite/pom.xml`:
  - parent `msa`, groupId `org.thingsboard.mqtt-broker.msa`, artifactId `tbmq-lite`, packaging `pom`, name `TBMQ Lite`;
  - dependency `org.thingsboard.mqtt-broker:application` with exclusions `org.thingsboard.mqtt-broker.common:queue-kafka` and `org.thingsboard.mqtt-broker.common:cache-redis`;
  - execution `ban-cluster-infrastructure` at `validate`, with `bannedDependencies` excludes `org.apache.kafka:kafka-clients`, `redis.clients:jedis`, `io.lettuce:lettuce-core`, `org.springframework.data:spring-data-redis`, `com.bucket4j:bucket4j_jdk17-jedis` and `com.bucket4j:bucket4j_jdk17-redis-common`;
  - a pom comment saying `dao-rocksdb` joins in Phase 2, the image in Phase 4, and `org.hibernate.orm:hibernate-core` plus `org.postgresql:postgresql` join the ban list in Phase 3.
- Modify: `msa/pom.xml` (add `<module>tbmq-lite</module>`)

**Interfaces:**
- Produces: the `tbmq-lite` build, which holds no code yet. PE deletes this module when it merges Phase 1 (Task 24).

- [ ] **Step 1: Prove the Lite rule bites**

Temporarily delete the `queue-kafka` exclusion, then run `mvn -pl application install -DskipTests && mvn -pl msa/tbmq-lite validate`.
Expected: BUILD FAILURE naming `org.apache.kafka:kafka-clients`.

- [ ] **Step 2: Restore the exclusion and validate both builds**

Run: `mvn -pl msa/tbmq-lite validate && mvn -pl application validate`
Expected: BUILD SUCCESS for both.

- [ ] **Step 3: Commit, then ask the user to push and open PR 10**

```bash
git add -A msa pom.xml application/pom.xml
git commit -m "build(lite): add the tbmq-lite module and the two-way Enforcer bans"
```

## Exit

### Task 22: Cluster image smoke test, fresh install and upgrade

**Files:** none. This is a verification task; record the results in the release notes draft.

- [ ] **Step 1: Get the images**

You need `thingsboard/tbmq:<phase-1 head>` from TeamCity. Building it locally means `mvn install -DskipTests -Ddockerfile.skip=false -pl msa/tbmq -am`, which is a full build, so ask the user first.

- [ ] **Step 2: Fresh install**

Point `msa/tbmq/configs/docker-compose.yml` at the new tag and start it. Then:
1. The log shows `TBMQ edition: cluster`, and the UI login works.
2. `GET /api/app/config` returns `"edition":"cluster"` and the capabilities from Task 17.
3. Create Basic and SCRAM credentials. A Basic client publishes and subscribes, and an MQTT 5 SCRAM client authenticates.

- [ ] **Step 3: Upgrade from 2.4.1**

1. On `thingsboard/tbmq:2.4.1`, create Basic and SCRAM credentials and a persistent DEVICE session with QoS 1 messages queued while it is offline. Then stop.
2. Switch to the new tag and run `msa/tbmq/configs/tbmq-upgrade.sh`.
3. Expected: the broker starts, both credentials still authenticate (the SCRAM one was created by 2.4.1), and the queued messages arrive.

- [ ] **Step 4: If anything fails, open a fix task against the PR that caused it before Task 24**

### Task 23: Performance check against 2.4.1

**Files:** none (the perf-test repo is `/home/dlandiak/projects/tb-mqtt-perf-tests`).

- [ ] **Step 1: Run the baseline and the new build**

Run `simple_use_case.json`, `general_use_case.json`, `fan_in_use_case.json` and `fan_out_use_case.json` (cap fan-out at 30 minutes) against `thingsboard/tbmq:2.4.1` and against the Phase 1 head. Use the same hardware and the compose file from Task 22, and keep the client-side throughput and p99 latency.

- [ ] **Step 2: Compare**

Expected: throughput within 5% and p99 latency within 10% of 2.4.1 in every scenario. Report anything outside that to the user before release.

### Task 24: Release, PE merge, demo merge

- [ ] **Step 1: Confirm the version with the user**

Ask whether `develop/2.x` is on `2.5.0-SNAPSHOT` (D11: Phase 1 ships in 2.5.0). Bumping it is the release owner's call.

- [ ] **Step 2: Merge CE `main` into PE in `/home/dlandiak/projects/tbmq-pe`**

1. Delete `msa/tbmq-lite` and its `<module>` entry.
2. Port the registry change: PE's `TbmqSystemInfoService` keeps its typed-field logic, but calls `ServiceRegistryStore.put/get/entries/delete` instead of `RedisTemplate`. Found in the PR 4 review: PE's `TbmqSystemInfoServiceTest` still calls the removed `setServiceRegistryKey`; PE's `readServiceRegistry` keeps null values while the store returns `"null"`, so check `ServiceInfoIds.parseRegistryField` with `"null"`; and if PE moves more JUnit 4 tests into its `cache-redis`, they need the `junit-vintage-engine` CE's `cache-redis` declares.
3. Adapt PE's executor to `TbTopicPartition` and `TbQueueRebalanceListener`: `DefaultClusterIntegrationService`, plus any of its 7 extra integration types that use the changed queue APIs. The kafkasource and redis integrations' own Kafka and Redis clients are untouched.
3a. Move PE's own Kafka classes from PE `common/queue` to `common/queue-kafka` with `git mv`. This was found in the PR 3 review and checked on PE `origin/main` 52f62469c. Otherwise PE's `queue` stops compiling, because it no longer has `kafka-clients`. The classes are:
    - 8 settings under `queue/kafka/settings/**`: `DroppedMsgKafkaSettings`, `IntegrationSourceKafkaSettings`, and the `KafkaSource`, `MongoDb`, `PostgreSql`, `RabbitMq`, `Redis` and `TimescaleDb` `IntegrationDownlinkKafkaSettings`;
    - 8 factories under `queue/provider/**`: `KafkaDroppedMsgQueueFactory`, `KafkaIntegrationSourceQueueFactory`, `KafkaSourceIntegrationDownlinkQueueFactoryImpl` and the five `Kafka{MongoDb,PostgreSql,RabbitMq,Redis,TimescaleDb}IntegrationDownlinkQueueFactory`;
    - the tests `IntegrationSourceKafkaSettingsTest` and `KafkaDroppedMsgQueueFactoryTest`.

    Keep the neutral `KafkaSourceIntegrationDownlinkQueueFactory` interface in `queue`, as CE keeps `KafkaIntegrationDownlinkQueueFactory`. Git flags only the `queue/kafka/**` files as rename conflicts; the `provider/**` files stay in `queue` silently. Any PE module that got `kafka-clients` through `queue` now needs `queue-kafka` or its own dependency.
4. Keep PE's audit logging in `DefaultTbAppService` when applying the `ObjectProvider<TbQueueAdmin>` change.
5. Check that PE `application` and `dao` pass the Task 20 rules.
6. Let TeamCity run the PE suite.

- [ ] **Step 3: Merge PE into `tbmq-demo` the usual way**

- [ ] **Step 4: Release notes for 2.5.0**

Include:
- SCRAM now runs without kafka-clients, and stored credentials keep working;
- the new `edition` and `capabilities` fields in `/api/app/config`;
- the changed error text of the hidden `DELETE /api/app/consumer-group` endpoint;
- a list of the moved classes for #370, #385, #400, #401 and #404, which rebase onto Phase 1 (spec §10).
