/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.lite.durability;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.testsupport.lite.SystemDataTestStep;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A Lite broker in a JVM of its own, started on the classpath the standalone image ships
 * ({@code target/lite-runtime-classpath.txt}, written by the build) with its output in {@code target/lite-broker-<n>.log}.
 * <p>
 * The ports are system properties, which win over the {@code LISTENER_*} variables a developer or a build agent may
 * have set: the MQTT port the caller chose, a free HTTP port, and no WebSocket, SSL or WSS listener. PostgreSQL is the
 * caller's {@link LiteTestDatabase}.
 * <p>
 * No broker outlives the tests: a shutdown hook kills the ones still running when the test JVM exits, and
 * {@link LiteBrokerLauncher} halts a broker whose test JVM was killed.
 */
@Slf4j
public final class LiteBrokerProcess {

    private static final String RUNTIME_CLASSPATH_FILE = "lite-runtime-classpath.txt";
    private static final String LAUNCHER_DIR = "lite-broker-launcher";
    // without it, the heap would default to a quarter of the build agent's memory
    private static final String MAX_HEAP = "1g";
    private static final String HOST = "127.0.0.1";
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(30);
    private static final long PORT_POLL_INTERVAL_MS = 200;
    private static final int PORT_CONNECT_TIMEOUT_MS = 1000;

    private static final AtomicInteger STARTS = new AtomicInteger();
    private static final Set<Process> RUNNING = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> RUNNING.forEach(Process::destroyForcibly), "lite-broker-reaper"));
    }

    private final Process process;
    private final int mqttPort;
    private final Path logFile;

    private LiteBrokerProcess(Process process, int mqttPort, Path logFile) {
        this.process = process;
        this.mqttPort = mqttPort;
        this.logFile = logFile;
    }

    /**
     * @param extraProperties further system properties of the broker, which win over the ones this method sets
     */
    public static LiteBrokerProcess start(Path storePath, LiteTestDatabase db, int mqttPort, Map<String, String> extraProperties) {
        Path targetDir = targetDir();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("store.path", storePath.toString());
        properties.put("spring.datasource.url", db.jdbcUrl());
        properties.put("spring.datasource.username", db.username());
        properties.put("spring.datasource.password", db.password());
        properties.put("spring.datasource.driverClassName", "org.postgresql.Driver");
        properties.put("listener.tcp.bind_port", String.valueOf(mqttPort));
        properties.put("server.port", String.valueOf(freePort()));
        properties.put("listener.ws.enabled", "false");
        properties.put("listener.ssl.enabled", "false");
        properties.put("listener.wss.enabled", "false");
        // no free-space watermark: the broker must not refuse writes because the build host's disk is nearly full
        properties.put("store.disk.min-free-percent", "0");
        properties.put("store.disk.min-free-bytes", "0");
        properties.putAll(extraProperties);

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + MAX_HEAP);
        command.add("-cp");
        command.add(runtimeClasspath(targetDir) + File.pathSeparator + launcherDir(targetDir));
        properties.forEach((key, value) -> command.add("-D" + key + "=" + value));
        command.add(LiteBrokerLauncher.class.getName());
        command.add("--spring.config.name=thingsboard-mqtt-broker");

        Path logFile = targetDir.resolve("lite-broker-" + STARTS.incrementAndGet() + ".log");
        Process process;
        try {
            // the standard input stays a pipe from this JVM, which the launcher watches
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start the broker", e);
        }
        RUNNING.add(process);
        log.info("Started a broker (pid {}) on MQTT port {} and store {}, output in {}", process.pid(), mqttPort, storePath, logFile);
        return new LiteBrokerProcess(process, mqttPort, logFile);
    }

    /**
     * Waits until the MQTT port accepts connections. The listener opens once the application is ready.
     */
    public void awaitMqttPort(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The broker exited with code " + process.exitValue()
                        + " before it opened the MQTT port; see " + logFile);
            }
            if (mqttPortOpen()) {
                return;
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException("The broker did not open the MQTT port " + mqttPort + " within " + timeout + "; see " + logFile);
            }
            try {
                Thread.sleep(PORT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the MQTT port", e);
            }
        }
    }

    /**
     * Kills the broker with {@code SIGKILL} on Linux ({@link Process#destroyForcibly()}) and waits until it is gone.
     */
    public void kill9() {
        process.destroyForcibly();
        if (!awaitExit()) {
            throw new IllegalStateException("The broker did not exit within " + EXIT_TIMEOUT + " of SIGKILL; see " + logFile);
        }
    }

    /**
     * Stops the broker with {@code SIGTERM}, which runs its shutdown, and waits until it is gone.
     */
    public void stopGracefully() {
        process.destroy();
        if (!awaitExit()) {
            process.destroyForcibly();
            awaitExit();
            throw new IllegalStateException("The broker did not stop within " + EXIT_TIMEOUT + " and was killed; see " + logFile);
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * @return the file the broker's standard output and error go to
     */
    public Path logFile() {
        return logFile;
    }

    /**
     * @return the URI of a broker on this port, for the clients
     */
    public static String mqttUri(int mqttPort) {
        return "tcp://" + HOST + ":" + mqttPort;
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to find a free port", e);
        }
    }

    /**
     * @return {@code false} when the broker is still running after {@link #EXIT_TIMEOUT}
     */
    private boolean awaitExit() {
        boolean exited;
        try {
            exited = process.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the broker to exit", e);
        }
        if (exited) {
            RUNNING.remove(process);
            try {
                // the launcher's pipe: nothing reads it any more
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // the process is gone, and the pipe with it
            }
        }
        return exited;
    }

    private boolean mqttPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, mqttPort), PORT_CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @return {@code lite-tests/target}, found from where the test classes were loaded, whatever the working directory
     */
    private static Path targetDir() {
        try {
            return Path.of(LiteBrokerProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the test classes", e);
        }
    }

    private static String runtimeClasspath(Path targetDir) {
        Path file = targetDir.resolve(RUNTIME_CLASSPATH_FILE);
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (NoSuchFileException e) {
            throw new IllegalStateException(file + " is missing: the lite-tests build writes it at generate-test-resources", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    /**
     * Copies the class file of the launcher, which must stay a single class without nested, anonymous or local classes,
     * and the class files of {@link SystemDataTestStep} and its nested classes, which give the broker, started without
     * the test classpath, the management data that the in-process Lite tests get from that step.
     *
     * @return a directory holding those classes and nothing else
     */
    private static Path launcherDir(Path targetDir) {
        Path dir = targetDir.resolve(LAUNCHER_DIR);
        copyClassFiles(LiteBrokerLauncher.class, false, dir);
        copyClassFiles(SystemDataTestStep.class, true, dir);
        return dir;
    }

    private static void copyClassFiles(Class<?> type, boolean withNestedClasses, Path dir) {
        String simpleName = type.getSimpleName();
        Path source;
        try {
            source = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .resolve(type.getPackageName().replace('.', '/'));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate " + type.getName(), e);
        }
        Path destination = dir.resolve(type.getPackageName().replace('.', '/'));
        try (Stream<Path> files = Files.list(source)) {
            Files.createDirectories(destination);
            List<Path> classFiles = files
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.equals(simpleName + ".class")
                                || withNestedClasses && name.startsWith(simpleName + "$") && name.endsWith(".class");
                    })
                    .toList();
            if (classFiles.isEmpty()) {
                throw new IllegalStateException(simpleName + ".class is not in " + source);
            }
            for (Path file : classFiles) {
                Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy " + simpleName + " to " + dir, e);
        }
    }

}
