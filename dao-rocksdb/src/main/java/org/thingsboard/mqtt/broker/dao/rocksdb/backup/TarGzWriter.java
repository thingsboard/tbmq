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
package org.thingsboard.mqtt.broker.dao.rocksdb.backup;

import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Streams a gzip-compressed POSIX ustar archive of regular files to an output stream, through a fixed buffer, so an
 * archive of any size never sits in memory. Each entry is a regular file with mode 0644 and owner 0:0.
 * <p>
 * An entry name of up to 100 bytes (UTF-8) goes into the name field; a longer one is split at a {@code /} into the
 * prefix field (up to 155 bytes) and the name field, and one that cannot be split so is refused. A size up to
 * 8 GiB - 1 is written in octal; a larger one in the base-256 form that GNU tar and bsdtar read.
 * <p>
 * {@link #finish()} completes the archive. {@link #close()} without it leaves the gzip stream without its trailer, so a
 * reader fails on the part written rather than taking it for a whole archive. Neither closes the output stream.
 */
public final class TarGzWriter implements Closeable {

    static final int BLOCK = 512;
    static final long MAX_OCTAL_SIZE = 077777777777L;

    private static final int NAME_LENGTH = 100;
    private static final int PREFIX_LENGTH = 155;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int FILE_MODE = 0644;

    private static final int NAME_OFFSET = 0;
    private static final int MODE_OFFSET = 100;
    private static final int UID_OFFSET = 108;
    private static final int GID_OFFSET = 116;
    private static final int SIZE_OFFSET = 124;
    private static final int SIZE_LENGTH = 12;
    private static final int MTIME_OFFSET = 136;
    private static final int CHECKSUM_OFFSET = 148;
    private static final int CHECKSUM_LENGTH = 8;
    private static final int TYPE_OFFSET = 156;
    private static final int MAGIC_OFFSET = 257;
    private static final int PREFIX_OFFSET = 345;
    private static final byte REGULAR_FILE = '0';
    private static final byte[] MAGIC = "ustar\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VERSION = "00".getBytes(StandardCharsets.US_ASCII);

    private final Gate gate;
    private final GZIPOutputStream gzip;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private boolean finished;
    private boolean closed;

    /**
     * Starts the archive: writes the gzip header to {@code out}.
     */
    public TarGzWriter(OutputStream out) throws IOException {
        this.gate = new Gate(out);
        this.gzip = new GZIPOutputStream(gate, BUFFER_SIZE);
    }

    /**
     * Adds the regular file {@code file} as {@code name}, with its size and modification time.
     *
     * @throws IllegalArgumentException when {@code file} is not a regular file or {@code name} does not fit ustar
     * @throws IOException              when {@code file} cannot be read, changes its size while it is added, or the
     *                                  output stream fails
     */
    public void addFile(String name, Path file) throws IOException {
        ensureWritable();
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IllegalArgumentException("Cannot add " + file + " to the archive: not a regular file");
        }
        long size = attributes.size();
        gzip.write(header(name, size, attributes.lastModifiedTime().to(TimeUnit.SECONDS)));
        long copied = 0;
        try (InputStream in = Files.newInputStream(file)) {
            while (copied < size) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, size - copied));
                if (read < 0) {
                    throw new IOException("The file " + file + " shrank while it was archived: " + copied + " of " + size + " bytes");
                }
                gzip.write(buffer, 0, read);
                copied += read;
            }
            if (in.read() >= 0) {
                throw new IOException("The file " + file + " grew while it was archived beyond its " + size + " bytes");
            }
        }
        pad(size);
    }

    /**
     * Adds {@code content} as the regular file {@code name}, modified now.
     */
    public void addBytes(String name, byte[] content) throws IOException {
        ensureWritable();
        gzip.write(header(name, content.length, Instant.now().getEpochSecond()));
        gzip.write(content);
        pad(content.length);
    }

    /**
     * Writes the end of the archive and the gzip trailer, and flushes the output stream.
     */
    public void finish() throws IOException {
        ensureWritable();
        gzip.write(new byte[2 * BLOCK]);
        gzip.finish();
        gzip.flush();
        finished = true;
    }

    /**
     * Releases the compressor. Unless {@link #finish()} completed the archive, nothing more reaches the output stream.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (!finished) {
            gate.shut();
        }
        gzip.close();
    }

    private void ensureWritable() {
        if (finished || closed) {
            throw new IllegalStateException("The archive is " + (closed ? "closed" : "finished"));
        }
    }

    private void pad(long size) throws IOException {
        int remainder = (int) (size % BLOCK);
        if (remainder != 0) {
            gzip.write(new byte[BLOCK - remainder]);
        }
    }

    /**
     * The ustar header block of a regular file.
     */
    static byte[] header(String name, long size, long mtimeEpochSecond) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative size " + size + " of the archive entry " + name);
        }
        byte[] header = new byte[BLOCK];
        putName(header, name);
        putOctal(header, MODE_OFFSET, 8, FILE_MODE);
        putOctal(header, UID_OFFSET, 8, 0);
        putOctal(header, GID_OFFSET, 8, 0);
        if (size <= MAX_OCTAL_SIZE) {
            putOctal(header, SIZE_OFFSET, SIZE_LENGTH, size);
        } else {
            // base-256: the high bit of the first byte marks it, the rest is the size big-endian
            header[SIZE_OFFSET] = (byte) 0x80;
            long value = size;
            for (int i = SIZE_OFFSET + SIZE_LENGTH - 1; i > SIZE_OFFSET; i--) {
                header[i] = (byte) value;
                value >>>= 8;
            }
        }
        putOctal(header, MTIME_OFFSET, 12, Math.max(mtimeEpochSecond, 0));
        header[TYPE_OFFSET] = REGULAR_FILE;
        System.arraycopy(MAGIC, 0, header, MAGIC_OFFSET, MAGIC.length);
        System.arraycopy(VERSION, 0, header, MAGIC_OFFSET + MAGIC.length, VERSION.length);
        // the checksum is computed with its own field as spaces, then written as 6 octal digits, NUL and space
        for (int i = CHECKSUM_OFFSET; i < CHECKSUM_OFFSET + CHECKSUM_LENGTH; i++) {
            header[i] = ' ';
        }
        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        putOctal(header, CHECKSUM_OFFSET, 7, checksum);
        return header;
    }

    private static void putName(byte[] header, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("An archive entry needs a name");
        }
        if (bytes.length <= NAME_LENGTH) {
            System.arraycopy(bytes, 0, header, NAME_OFFSET, bytes.length);
            return;
        }
        // the longest prefix that fits, so the rest is as short as possible
        for (int slash = Math.min(bytes.length - 1, PREFIX_LENGTH); slash > 0; slash--) {
            if (bytes[slash] == '/') {
                int rest = bytes.length - slash - 1;
                if (rest == 0 || rest > NAME_LENGTH) {
                    break;
                }
                System.arraycopy(bytes, 0, header, PREFIX_OFFSET, slash);
                System.arraycopy(bytes, slash + 1, header, NAME_OFFSET, rest);
                return;
            }
        }
        throw new IllegalArgumentException("The archive entry name " + name + " is too long for a ustar archive: "
                + bytes.length + " bytes that do not split at a '/' into at most " + PREFIX_LENGTH + " and "
                + NAME_LENGTH + " bytes");
    }

    /**
     * {@code value} as {@code length - 1} zero-padded octal digits and a NUL.
     */
    private static void putOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        if (octal.length() > length - 1) {
            throw new IllegalArgumentException("The value " + value + " does not fit a " + length + "-byte ustar field");
        }
        int padding = length - 1 - octal.length();
        for (int i = 0; i < padding; i++) {
            header[offset + i] = '0';
        }
        for (int i = 0; i < octal.length(); i++) {
            header[offset + padding + i] = (byte) octal.charAt(i);
        }
        header[offset + length - 1] = 0;
    }

    /**
     * Passes writes through to the output stream until it is shut, and drops them afterwards; never closes the
     * output stream.
     */
    private static final class Gate extends FilterOutputStream {

        private boolean shut;

        Gate(OutputStream out) {
            super(out);
        }

        void shut() {
            shut = true;
        }

        @Override
        public void write(int b) throws IOException {
            if (!shut) {
                out.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (!shut) {
                out.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            if (!shut) {
                out.flush();
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }

    }

}
