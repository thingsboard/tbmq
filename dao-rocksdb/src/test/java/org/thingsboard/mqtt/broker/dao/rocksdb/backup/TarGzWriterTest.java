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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TarGzWriterTest {

    private static final int MIB = 1 << 20;

    @TempDir
    Path dir;

    @Test
    void systemTarExtractsWhatWasWritten() throws IOException {
        SystemTar.assumeAvailable();
        Path empty = Files.write(dir.resolve("empty"), new byte[0]);
        byte[] large = new byte[MIB];
        new Random(42).nextBytes(large);
        Path big = Files.write(dir.resolve("big.sst"), large);
        byte[] manifest = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        Path archive = dir.resolve("a.tar.gz");

        try (OutputStream out = Files.newOutputStream(archive); TarGzWriter tar = new TarGzWriter(out)) {
            tar.addBytes("manifest.json", manifest);
            tar.addFile("db/empty", empty);
            tar.addFile("db/big.sst", big);
            tar.finish();
        }

        Path extracted = Files.createDirectory(dir.resolve("out"));
        SystemTar.extract(archive, extracted);
        assertThat(extracted.resolve("manifest.json")).hasBinaryContent(manifest);
        assertThat(extracted.resolve("db/empty")).exists().isEmptyFile();
        assertThat(extracted.resolve("db/big.sst")).hasBinaryContent(large);
    }

    @Test
    void aNameLongerThanTheNameFieldIsSplitIntoThePrefix() throws IOException {
        SystemTar.assumeAvailable();
        // 3 x 50-char directories and a 100-char file name: 253 bytes, over the 100 of the name field
        String name = "d".repeat(50) + "/" + "e".repeat(50) + "/" + "f".repeat(50) + "/" + "g".repeat(100);
        Path archive = dir.resolve("long.tar.gz");

        try (OutputStream out = Files.newOutputStream(archive); TarGzWriter tar = new TarGzWriter(out)) {
            tar.addBytes(name, bytes("long"));
            tar.finish();
        }

        Path extracted = Files.createDirectory(dir.resolve("out"));
        SystemTar.extract(archive, extracted);
        assertThat(extracted.resolve(name)).hasBinaryContent(bytes("long"));
    }

    @Test
    void aNameThatFitsNeitherTheNameNorThePrefixIsRefused() throws IOException {
        TarGzWriter tar = new TarGzWriter(new ByteArrayOutputStream());

        // a last segment over 100 bytes cannot go into the name field, whatever the prefix
        assertThatThrownBy(() -> tar.addBytes("db/" + "x".repeat(101), bytes("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
        // a directory part over 155 bytes cannot go into the prefix
        assertThatThrownBy(() -> tar.addBytes("p".repeat(156) + "/x", bytes("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
        // exactly 100 bytes without a directory fit the name field
        assertThat(TarGzWriter.header("n".repeat(100), 0, 0)).hasSize(TarGzWriter.BLOCK);
    }

    @Test
    void aSizeBeyondTheOctalFieldIsWrittenInBase256() throws IOException {
        long eightGibPlusOne = (8L << 30) + 1;

        byte[] header = TarGzWriter.header("db/huge.sst", eightGibPlusOne, 0);

        // the 12-byte size field at offset 124: the marker bit, then the size big-endian
        assertThat(header[124] & 0xff).isEqualTo(0x80);
        long size = 0;
        for (int i = 125; i < 136; i++) {
            size = (size << 8) | (header[i] & 0xff);
        }
        assertThat(size).isEqualTo(eightGibPlusOne);
        // 8 GiB - 1 is the largest size in octal
        assertThat(new String(TarGzWriter.header("x", TarGzWriter.MAX_OCTAL_SIZE, 0), 124, 12, StandardCharsets.US_ASCII))
                .isEqualTo("77777777777\0");

        // tar reads the size: list a header without its content, which tar reports as a truncated archive
        SystemTar.assumeAvailable();
        Path archive = dir.resolve("huge.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write(header);
        }
        assertThat(SystemTar.list(archive).output()).contains("db/huge.sst").contains(Long.toString(eightGibPlusOne));
    }

    @Test
    void anEntryKeepsTheModeAndTheNumericOwnerOfItsSourceFile() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"), "no unix file attributes");
        Path file = Files.write(dir.resolve("000008.sst"), bytes("sst"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (TarGzWriter tar = new TarGzWriter(out)) {
            tar.addFile("db/000008.sst", file);
            tar.addBytes("manifest.json", bytes("{}"));
            tar.finish();
        }

        byte[] archive;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            archive = in.readAllBytes();
        }
        assertThat(octal(archive, 100, 8)).as("mode").isEqualTo(0600);
        assertThat(octal(archive, 108, 8)).as("uid").isEqualTo(((Number) Files.getAttribute(file, "unix:uid")).longValue());
        assertThat(octal(archive, 116, 8)).as("gid").isEqualTo(((Number) Files.getAttribute(file, "unix:gid")).longValue());
        // empty owner and group names: tar -x as root takes the numeric ids
        assertThat(Arrays.copyOfRange(archive, 265, 265 + 64)).containsOnly(0);
        // an entry from bytes: 0644 and 0:0; it starts after the header and the padded 3 bytes of the file
        assertThat(octal(archive, 1024 + 100, 8)).isEqualTo(0644);
        assertThat(octal(archive, 1024 + 108, 8)).isZero();
    }

    @Test
    void aDirectoryEntryKeepsTheModeAndTheNumericOwnerOfItsSourceDirectory() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"), "no unix file attributes");
        Path source = Files.createDirectory(dir.resolve("checkpoint"));
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rwx------"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (TarGzWriter tar = new TarGzWriter(out)) {
            tar.addDirectory("db", source);
            tar.finish();
        }

        byte[] archive;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            archive = in.readAllBytes();
        }
        // a trailing slash, type '5', no content: the next block is the end of the archive
        assertThat(new String(archive, 0, 100, StandardCharsets.UTF_8).replace("\0", "")).isEqualTo("db/");
        assertThat(archive[156]).as("type").isEqualTo((byte) '5');
        assertThat(octal(archive, 124, 12)).as("size").isZero();
        assertThat(octal(archive, 100, 8)).as("mode").isEqualTo(0700);
        assertThat(octal(archive, 108, 8)).as("uid").isEqualTo(((Number) Files.getAttribute(source, "unix:uid")).longValue());
        assertThat(octal(archive, 116, 8)).as("gid").isEqualTo(((Number) Files.getAttribute(source, "unix:gid")).longValue());
        assertThat(Arrays.copyOfRange(archive, 265, 265 + 64)).as("owner and group names").containsOnly(0);
        assertThat(Arrays.copyOfRange(archive, TarGzWriter.BLOCK, 3 * TarGzWriter.BLOCK)).containsOnly(0);
    }

    @Test
    void systemTarCreatesTheDirectoryWithItsModeAndOwner() throws IOException {
        SystemTar.assumeAvailable();
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"), "no unix file attributes");
        Path source = Files.createDirectory(dir.resolve("checkpoint"));
        Path file = Files.write(source.resolve("CURRENT"), bytes("MANIFEST-000005\n"));
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rwx------"));
        Path archive = dir.resolve("dir.tar.gz");

        try (OutputStream out = Files.newOutputStream(archive); TarGzWriter tar = new TarGzWriter(out)) {
            tar.addDirectory("db/", source);
            tar.addFile("db/CURRENT", file);
            tar.finish();
        }

        Path extracted = Files.createDirectory(dir.resolve("out"));
        SystemTar.extract(archive, extracted);
        Path db = extracted.resolve("db");
        assertThat(db).isDirectory();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(db))).isEqualTo("rwx------");
        // as root tar restores the numeric owner; as another user it is that user, who also owns the source here
        assertThat(Files.getAttribute(db, "unix:uid")).isEqualTo(Files.getAttribute(source, "unix:uid"));
        assertThat(Files.getAttribute(db, "unix:gid")).isEqualTo(Files.getAttribute(source, "unix:gid"));
        assertThat(db.resolve("CURRENT")).hasBinaryContent(bytes("MANIFEST-000005\n"));
    }

    @Test
    void onlyDirectoriesAreAddedAsDirectories() throws IOException {
        Path file = Files.write(dir.resolve("file"), bytes("f"));
        TarGzWriter tar = new TarGzWriter(new ByteArrayOutputStream());

        assertThatThrownBy(() -> tar.addDirectory("db/", file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void anIdBeyondTheOctalFieldIsWrittenInBase256() {
        byte[] header = TarGzWriter.header("x", 0, 0, 0644, 3_000_000L, 7);

        assertThat(header[108] & 0xff).isEqualTo(0x80);
        assertThat(((header[113] & 0xff) << 16) | ((header[114] & 0xff) << 8) | (header[115] & 0xff)).isEqualTo(3_000_000);
        assertThat(octal(header, 116, 8)).isEqualTo(7);
    }

    @Test
    void anArchiveClosedWithoutFinishIsTruncated() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (TarGzWriter tar = new TarGzWriter(out)) {
            tar.addBytes("a", new byte[100_000]);
        }

        // the gzip trailer is missing, so a reader never takes the part for a whole archive
        assertThatThrownBy(() -> readAll(new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()))))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void theOutputStreamStaysOpen() throws IOException {
        boolean[] closed = new boolean[1];
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        try (TarGzWriter tar = new TarGzWriter(out)) {
            tar.addBytes("a", bytes("a"));
            tar.finish();
        }

        assertThat(closed[0]).isFalse();
    }

    @Test
    void onlyRegularFilesAreAdded() throws IOException {
        Path subdir = Files.createDirectory(dir.resolve("sub"));
        TarGzWriter tar = new TarGzWriter(new ByteArrayOutputStream());

        assertThatThrownBy(() -> tar.addFile("sub", subdir)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a regular file");
    }

    private static long octal(byte[] block, int offset, int length) {
        String digits = new String(block, offset, length, StandardCharsets.US_ASCII).replace("\0", "").trim();
        return Long.parseLong(digits, 8);
    }

    private static void readAll(InputStream in) throws IOException {
        try (in) {
            in.readAllBytes();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
