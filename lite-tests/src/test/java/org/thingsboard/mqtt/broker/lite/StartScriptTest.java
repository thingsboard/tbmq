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
package org.thingsboard.mqtt.broker.lite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the shipped start script with {@code --print-flags} over temporary cgroup trees, so that the limits it reads and
 * the flags it chooses are checked without a container.
 */
public class StartScriptTest {

    private static final long GIB = 1024L * 1024 * 1024;
    private static final String BUNDLED_LOGBACK = "/usr/share/tbmq-lite/conf/logback.xml";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path script;
    private Path meminfo;
    private Path configDir;

    @Before
    public void setUp() throws IOException {
        String path = System.getProperty("tbmq.lite.start-script");
        assertNotNull("system property tbmq.lite.start-script (the start script's path) is not set", path);
        script = new File(path).toPath().toAbsolutePath().normalize();
        assertTrue("start script not found: " + script, Files.isRegularFile(script));
        meminfo = writeMeminfo(64 * GIB);
        configDir = tmp.newFolder("config").toPath();
    }

    @Test
    public void v2With2CpusAnd2GiBPicksG1() throws Exception {
        Path root = v2("2147483648", "200000 100000", "0-7");

        Output out = run(root, null);

        assertEquals(0, out.exit);
        assertTrue(out.flags.contains("-XX:+UseG1GC"));
        assertFalse(out.flags.contains("-XX:+UseSerialGC"));
        assertTrue(out.flags.contains("-XX:MaxDirectMemorySize=204m"));
        assertTrue(out.flags.contains("-XX:MaxRAMPercentage=45"));
        assertTrue(out.flags.contains("-XX:+ExitOnOutOfMemoryError"));
        assertTrue(out.flags.contains("-Djava.library.path=/usr/share/tbmq-lite/native"));
        assertTrue(out.flags.contains("--enable-native-access=ALL-UNNAMED"));
        assertEquals("TBMQ Lite: memory 2048 MiB, CPUs 2 (cgroup v2), collector G1, max direct memory 204 MiB, jemalloc off",
                out.logLine);
    }

    @Test
    public void v2With1Point5CpusRoundsUpToTwo() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "150000 100000", "0-7");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("CPUs 2 (cgroup v2)"));
        assertTrue(out.flags.contains("-XX:+UseG1GC"));
    }

    @Test
    public void v2With1CpuPicksSerial() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "100000 100000", "0-7");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("CPUs 1 (cgroup v2)"));
        assertTrue(out.logLine, out.logLine.contains("collector Serial"));
        assertTrue(out.flags.contains("-XX:+UseSerialGC"));
        assertFalse(out.flags.contains("-XX:+UseG1GC"));
    }

    @Test
    public void v2With1GiBPicksSerial() throws Exception {
        Path root = v2("1073741824", "max 100000", "0-3");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("memory 1024 MiB, CPUs 4 (cgroup v2), collector Serial"));
        assertTrue(out.flags.contains("-XX:+UseSerialGC"));
        assertTrue(out.flags.contains("-XX:MaxDirectMemorySize=102m"));
    }

    @Test
    public void unlimitedFallsBackToMeminfoAndCpuset() throws Exception {
        meminfo = writeMeminfo(8 * GIB);
        Path root = v2("max", "max 100000", "0-1,4");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("memory 8192 MiB, CPUs 3 (cgroup v2), collector G1"));
        assertTrue(out.flags.contains("-XX:+UseG1GC"));
        assertTrue(out.flags.contains("-XX:MaxDirectMemorySize=819m"));
    }

    @Test
    public void quotaBelowCpusetWins() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "200000 100000", "0-7");

        assertTrue(run(root, null).logLine.contains("CPUs 2 "));
    }

    @Test
    public void cpusetBelowQuotaWins() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "800000 100000", "0-1");

        assertTrue(run(root, null).logLine.contains("CPUs 2 "));
    }

    @Test
    public void aMissingCpusetFallsBackToNproc() throws Exception {
        Path root = tmp.newFolder("cg-nocpuset").toPath();
        write(root.resolve("cgroup.controllers"), "cpu memory");
        write(root.resolve("memory.max"), Long.toString(4 * GIB));
        write(root.resolve("cpu.max"), "max 100000");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("CPUs " + nproc() + " (cgroup v2)"));
    }

    @Test
    public void exactly2GiBIsNotBelow2GiB() throws Exception {
        Path root = v2("2147483648", "max 100000", "0-1");

        assertTrue(run(root, null).flags.contains("-XX:+UseG1GC"));
    }

    @Test
    public void smallMemoryKeepsTheDirectMemoryFloor() throws Exception {
        Path root = v2("536870912", "max 100000", "0-3");

        Output out = run(root, null);

        assertTrue(out.flags.contains("-XX:MaxDirectMemorySize=64m"));
        assertTrue(out.logLine, out.logLine.contains("max direct memory 64 MiB"));
        assertTrue(out.logLine, out.logLine.contains("memory 512 MiB"));
    }

    @Test
    public void v1IsRead() throws Exception {
        meminfo = writeMeminfo(8 * GIB);
        Path root = tmp.newFolder("cg-v1").toPath();
        write(root.resolve("memory/memory.limit_in_bytes"), "9223372036854771712");
        write(root.resolve("cpu/cpu.cfs_quota_us"), "-1");
        write(root.resolve("cpu/cpu.cfs_period_us"), "100000");
        write(root.resolve("cpuset/cpuset.effective_cpus"), "0-2");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("memory 8192 MiB, CPUs 3 (cgroup v1), collector G1"));
    }

    @Test
    public void v1LimitsAreRead() throws Exception {
        Path root = tmp.newFolder("cg-v1-limited").toPath();
        write(root.resolve("memory/memory.limit_in_bytes"), "1073741824");
        write(root.resolve("cpu/cpu.cfs_quota_us"), "150000");
        write(root.resolve("cpu/cpu.cfs_period_us"), "100000");
        write(root.resolve("cpuset/cpuset.effective_cpus"), "0-7");

        Output out = run(root, null);

        assertTrue(out.logLine, out.logLine.contains("memory 1024 MiB, CPUs 2 (cgroup v1), collector Serial"));
    }

    @Test
    public void aCollectorInJavaOptsWins() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, "-XX:+UseZGC");

        assertFalse(out.flags.contains("-XX:+UseG1GC"));
        assertFalse(out.flags.contains("-XX:+UseSerialGC"));
        assertTrue(out.logLine, out.logLine.contains("collector from JAVA_OPTS"));
        assertEquals("-XX:+UseZGC", out.flags.get(out.flags.size() - 1));
    }

    @Test
    public void optionsThatAreNotACollectorDoNotSuppressTheChoice() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, "-XX:+UseStringDeduplication -XX:+DisableExplicitGC");

        assertTrue(out.flags.contains("-XX:+UseG1GC"));
        assertTrue(out.logLine, out.logLine.contains("collector G1"));
        assertFalse(out.logLine, out.logLine.contains("from JAVA_OPTS"));
    }

    @Test
    public void aDisabledCollectorFlagDoesNotCount() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, "-XX:-UseSerialGC");

        assertTrue(out.flags.contains("-XX:+UseG1GC"));
        assertFalse(out.logLine, out.logLine.contains("from JAVA_OPTS"));
    }

    @Test
    public void javaOptsAreSplitIntoWordsAndAppendedLast() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, "-Xss512k   -Dfoo=bar");

        int size = out.flags.size();
        assertEquals("-Xss512k", out.flags.get(size - 2));
        assertEquals("-Dfoo=bar", out.flags.get(size - 1));
        assertTrue(out.flags.contains("-XX:+UseG1GC"));
    }

    @Test
    public void multiLineJavaOptsKeepEveryLine() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, "-Xss512k\n-XX:+UseZGC\n  -Dfoo=bar\n");

        int size = out.flags.size();
        assertEquals("-Xss512k", out.flags.get(size - 3));
        assertEquals("-XX:+UseZGC", out.flags.get(size - 2));
        assertEquals("-Dfoo=bar", out.flags.get(size - 1));
        assertFalse(out.flags.contains("-XX:+UseG1GC"));
        assertFalse(out.flags.contains("-XX:+UseSerialGC"));
        assertTrue(out.logLine, out.logLine.contains("collector from JAVA_OPTS"));
    }

    @Test
    public void aJemallocThatDoesNotExistIsSkipped() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, null, "/nonexistent/libjemalloc.so.2");

        assertEquals("LD_PRELOAD=", out.ldPreload);
        assertEquals("MALLOC_ARENA_MAX=2", out.arenaMax);
        assertTrue(out.logLine, out.logLine.endsWith("jemalloc off"));
        assertTrue(out.stderr, out.stderr.contains("WARN"));
    }

    @Test
    public void aJemallocThatFailsToLoadIsSkipped() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");
        // ld.so ignores a preload it cannot load, with exit status 0 and a message on stderr
        Path notALibrary = tmp.newFile("libjemalloc.so.2").toPath();
        write(notALibrary, "this is not an ELF object");

        Output out = run(root, null, notALibrary.toString());

        assertEquals("LD_PRELOAD=", out.ldPreload);
        assertEquals("MALLOC_ARENA_MAX=2", out.arenaMax);
        assertTrue(out.logLine, out.logLine.endsWith("jemalloc off"));
        assertTrue(out.stderr, out.stderr.contains("WARN"));
    }

    @Test
    public void aJemallocThatLoadsIsPreloaded() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");
        String loadable = aLibraryThatPreloadsCleanly();

        Output out = run(root, null, loadable);

        assertEquals("LD_PRELOAD=" + loadable, out.ldPreload);
        assertEquals("MALLOC_ARENA_MAX=", out.arenaMax);
        assertTrue(out.logLine, out.logLine.endsWith("jemalloc on"));
        assertFalse(out.stderr, out.stderr.contains("WARN"));
    }

    @Test
    public void aUserLogbackWins() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");
        Path userLogback = configDir.resolve("logback.xml");
        write(userLogback, "<configuration/>");

        Output out = run(root, null);

        assertTrue(out.flags.toString(), out.flags.contains("-Dlogging.config=" + userLogback));
    }

    @Test
    public void theBundledLogbackIsUsedWithoutAUserFile() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");

        Output out = run(root, null);

        assertTrue(out.flags.toString(), out.flags.contains("-Dlogging.config=" + BUNDLED_LOGBACK));
    }

    @Test
    public void withoutPrintFlagsItExecsJavaWithTheFlagsBeforeTheJar() throws Exception {
        Path root = v2(Long.toString(4 * GIB), "400000 100000", "0-7");
        Path bin = tmp.newFolder("bin").toPath();
        Path fakeJava = bin.resolve("java");
        write(fakeJava, "#!/bin/bash\nfor a in \"$@\"; do echo \"ARG:$a\"; done\n");
        assertTrue(fakeJava.toFile().setExecutable(true));

        ProcessResult result = start(root, "-Dfoo=bar", nonexistentJemalloc(), bin.toString(), false);

        List<String> args = new ArrayList<>();
        for (String line : result.stdout) {
            if (line.startsWith("ARG:")) {
                args.add(line.substring(4));
            }
        }
        int size = args.size();
        assertEquals("/usr/share/tbmq-lite/app/tbmq-lite.jar", args.get(size - 1));
        assertEquals("-jar", args.get(size - 2));
        assertEquals("-Dfoo=bar", args.get(size - 3));
        assertTrue(args.toString(), args.contains("-XX:+UseG1GC"));
    }

    private Output run(Path cgroupRoot, String javaOpts) throws Exception {
        return run(cgroupRoot, javaOpts, nonexistentJemalloc());
    }

    private Output run(Path cgroupRoot, String javaOpts, String jemalloc) throws Exception {
        ProcessResult result = start(cgroupRoot, javaOpts, jemalloc, null, true);
        List<String> lines = result.stdout;
        assertTrue("unexpected output: " + lines + " stderr: " + result.stderr, lines.size() >= 4);
        Output out = new Output();
        out.exit = result.exit;
        out.stderr = result.stderr;
        out.logLine = lines.get(0);
        out.flags = new ArrayList<>(lines.subList(1, lines.size() - 2));
        out.ldPreload = lines.get(lines.size() - 2);
        out.arenaMax = lines.get(lines.size() - 1);
        return out;
    }

    private ProcessResult start(Path cgroupRoot, String javaOpts, String jemalloc, String extraPath, boolean printFlags)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.toString());
        if (printFlags) {
            command.add("--print-flags");
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        env.remove("JAVA_OPTS");
        env.remove("LD_PRELOAD");
        env.remove("MALLOC_ARENA_MAX");
        env.put("TBMQ_CGROUP_ROOT", cgroupRoot.toString());
        env.put("TBMQ_MEMINFO", meminfo.toString());
        env.put("TBMQ_JEMALLOC", jemalloc);
        env.put("TBMQ_CONFIG_DIR", configDir.toString());
        if (javaOpts != null) {
            env.put("JAVA_OPTS", javaOpts);
        }
        if (extraPath != null) {
            env.put("PATH", extraPath + File.pathSeparator + env.get("PATH"));
        }
        Path stdoutFile = tmp.newFile().toPath();
        Path stderrFile = tmp.newFile().toPath();
        pb.redirectOutput(stdoutFile.toFile());
        pb.redirectError(stderrFile.toFile());
        Process process = pb.start();
        assertTrue("start script timed out", process.waitFor(30, TimeUnit.SECONDS));
        ProcessResult result = new ProcessResult();
        result.exit = process.exitValue();
        result.stdout = Files.readAllLines(stdoutFile, StandardCharsets.UTF_8);
        result.stderr = new String(Files.readAllBytes(stderrFile), StandardCharsets.UTF_8);
        return result;
    }

    private String nonexistentJemalloc() {
        return tmp.getRoot().toPath().resolve("no-such-jemalloc.so").toString();
    }

    /** A cgroup v2 tree as the kernel lays it out at the root of a container's cgroup namespace. */
    private Path v2(String memoryMax, String cpuMax, String cpuset) throws IOException {
        Path root = tmp.newFolder().toPath();
        write(root.resolve("cgroup.controllers"), "cpuset cpu io memory pids");
        write(root.resolve("memory.max"), memoryMax);
        write(root.resolve("cpu.max"), cpuMax);
        write(root.resolve("cpuset.cpus.effective"), cpuset);
        return root;
    }

    private Path writeMeminfo(long bytes) throws IOException {
        Path file = tmp.newFile().toPath();
        write(file, "MemTotal:       " + (bytes / 1024) + " kB\nMemFree:        1234 kB\nMemAvailable:   1234 kB");
        return file;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, (content + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String nproc() throws Exception {
        Process process = new ProcessBuilder("nproc").start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        process.waitFor();
        return out;
    }

    /** The C library: any glibc host has it, and preloading it into /bin/true succeeds without a message. */
    private static String aLibraryThatPreloadsCleanly() throws Exception {
        Process process = new ProcessBuilder("ldd", "/bin/true").redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        for (String line : out.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("libc.so.6")) {
                String[] parts = trimmed.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equals("=>")) {
                        return parts[i + 1];
                    }
                }
            }
        }
        throw new AssertionError("no libc found in: " + out);
    }

    private static class ProcessResult {
        int exit;
        List<String> stdout;
        String stderr;
    }

    private static class Output {
        int exit;
        String stderr;
        String logLine;
        List<String> flags;
        String ldPreload;
        String arenaMax;
    }

}
