package net.ccbluex.liquidbounce.integration.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MirroredPytorchTest {
    @TempDir Path temporary;

    @Test void checksumMismatchRejectsCorruptedArtifact() throws Exception {
        Path file = Files.writeString(temporary.resolve("artifact"), "abc");
        MirroredPytorch.verifyChecksum(file, "a9993e364706816aba3e25717850c26c9cd0d89d");
        Files.writeString(file, "corrupted");
        assertThrows(IOException.class, () -> MirroredPytorch.verifyChecksum(file, "a9993e364706816aba3e25717850c26c9cd0d89d"));
    }

    @Test void extractsOnlyTheMatchingCpuJniAndFlattensNativePaths() throws Exception {
        Path archive = temporary.resolve("natives.jar");
        String library = System.mapLibraryName("djl_torch");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            entry(zip, "jnilib/win-x86_64/cpu/" + library, "correct");
            entry(zip, "jnilib/win-x86_64/cu128/" + library, "wrong GPU");
            entry(zip, "jnilib/linux-x86_64/cpu/" + library, "wrong platform");
            entry(zip, "../../escape.dll", "flattened");
            entry(zip, "metadata.properties", "ignored");
        }
        Path jni = Files.createDirectory(temporary.resolve("jni"));
        MirroredPytorch.extract(archive, jni, "version-" + library, "win-x86_64");
        assertEquals("correct", Files.readString(jni.resolve("version-" + library)));
        try (var files = Files.list(jni)) { assertEquals(1, files.count()); }
        Path nativeFiles = Files.createDirectory(temporary.resolve("native"));
        MirroredPytorch.extract(archive, nativeFiles, null, "win-x86_64");
        assertEquals("flattened", Files.readString(nativeFiles.resolve("escape.dll")));
        assertFalse(Files.exists(temporary.resolve("escape.dll")));
        assertFalse(Files.exists(nativeFiles.resolve("metadata.properties")));
    }

    private void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
