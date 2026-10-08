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
package org.thingsboard.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local TCP server that counts the connections it accepts and never speaks MQTT: it either closes each one at once,
 * before any CONNACK, or holds it open in silence, reading what the client sends to count the connections the client
 * wrote to and those it closed.
 */
final class LocalTcpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final boolean holdConnections;
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger writtenTo = new AtomicInteger();
    private final AtomicInteger closedByClient = new AtomicInteger();
    private final List<Socket> held = new CopyOnWriteArrayList<>();

    private LocalTcpServer(boolean holdConnections) throws IOException {
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.holdConnections = holdConnections;
        Thread acceptor = new Thread(this::acceptLoop, "local-tcp-server-" + serverSocket.getLocalPort());
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Holds every connection open and never writes to it: a server that never answers CONNECT. */
    static LocalTcpServer silent() throws IOException {
        return new LocalTcpServer(true);
    }

    /** Closes every connection as soon as it is accepted: a channel that closes before its CONNACK. */
    static LocalTcpServer closingEachConnection() throws IOException {
        return new LocalTcpServer(false);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    int accepted() {
        return accepted.get();
    }

    /** The held connections the client sent at least one byte on - its CONNECT, for an MQTT client. */
    int writtenTo() {
        return writtenTo.get();
    }

    /** The held connections the client closed. */
    int closedByClient() {
        return closedByClient.get();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                accepted.incrementAndGet();
                if (holdConnections) {
                    held.add(socket);
                    Thread reader = new Thread(() -> readUntilClosed(socket), "local-tcp-server-reader-" + socket.getPort());
                    reader.setDaemon(true);
                    reader.start();
                } else {
                    socket.close();
                }
            } catch (IOException e) {
                return; // the server socket was closed
            }
        }
    }

    private void readUntilClosed(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[256];
            boolean written = false;
            while (in.read(buffer) != -1) {
                if (!written) {
                    written = true;
                    writtenTo.incrementAndGet();
                }
            }
            closedByClient.incrementAndGet();
        } catch (IOException e) {
            if (!socket.isClosed()) {
                closedByClient.incrementAndGet(); // the client reset the connection
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Socket socket : held) {
            socket.close();
        }
    }
}
