package net.ccbluex.liquidbounce.integration.task;

import ai.djl.util.Platform;
import net.ccbluex.liquidbounce.api.core.HttpClient;
import net.ccbluex.liquidbounce.integration.task.type.Task;
import okhttp3.Request;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Installs matching Maven native artifacts into DJL's normal cache, without changing engine versions. */
public final class MirroredPytorch {
    private static final String MIRROR = "https://maven.aliyun.com/repository/central/ai/djl/pytorch/";

    private MirroredPytorch() { }

    public static void prepare(Path engines, Task task) {
        Path temporary = null;
        try {
            Platform platform = Platform.detectPlatform("pytorch");
            String version = platform.getVersion();
            String classifier = platform.getClassifier();
            String api = platform.getApiVersion();
            Path destination = engines.resolve("pytorch").resolve(version + "-cpu-" + classifier);
            String nativeName = System.mapLibraryName("torch");
            String jniName = api + "-" + System.mapLibraryName("djl_torch");
            if (Files.isRegularFile(destination.resolve(nativeName)) && Files.isRegularFile(destination.resolve(jniName))) {
                task.setStatus("本地 AI 引擎缓存完整，无需下载");
                InitializationNetwork.report(task.getStatus());
                return;
            }
            Files.createDirectories(engines);
            temporary = Files.createTempDirectory(engines, "mirror-");
            Path extracted = Files.createDirectory(temporary.resolve("extracted"));
            fetch("pytorch-native-cpu/" + version + "/pytorch-native-cpu-" + version + "-" + classifier + ".jar",
                temporary.resolve("native.jar"), extracted, null, task);
            fetch("pytorch-jni/" + version + "-" + api + "/pytorch-jni-" + version + "-" + api + ".jar",
                temporary.resolve("jni.jar"), extracted, jniName, task);
            if (!Files.isRegularFile(extracted.resolve(nativeName)) || !Files.isRegularFile(extracted.resolve(jniName))) {
                throw new IOException("Mirror artifact has no matching native libraries");
            }
            task.setStatus("镜像校验完成，安装 AI 引擎缓存");
            Files.createDirectories(destination);
            // Install the sentinel torch library last, so interrupted installation is not treated as complete.
            try (var files = Files.list(extracted)) {
                for (Path file : files.sorted(Comparator.comparing(path -> path.getFileName().toString().equals(nativeName))).toList()) {
                    Files.move(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            InitializationNetwork.report("镜像 AI 引擎安装完成");
        } catch (Exception error) {
            task.setStatus("镜像不可用，回退 DJL 官方源：" + error.getClass().getSimpleName());
            InitializationNetwork.report(task.getStatus());
        } finally {
            if (temporary != null) {
                try (var paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    InitializationNetwork.report("镜像临时文件清理失败，下次可重试");
                }
            }
        }
    }

    private static void fetch(String artifact, Path jar, Path extracted, String jniName, Task task) throws Exception {
        String url = MIRROR + artifact;
        task.setStatus("从阿里云 Maven 镜像下载匹配版本的 AI 库");
        var download = task.getOrCreateFileTask(url);
        try (var response = HttpClient.getClient().newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Mirror HTTP " + response.code());
            try (var input = response.body().byteStream(); var output = Files.newOutputStream(jar)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                int length;
                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                    download.update(total += length, response.body().contentLength());
                }
            }
        } finally {
            download.setCompleted(true);
        }
        task.setStatus("校验镜像 SHA-1 并解压本地库");
        String expected;
        try (var response = HttpClient.getClient().newCall(new Request.Builder().url(url + ".sha1").build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Mirror checksum unavailable");
            expected = response.body().string().trim().split("\\s+")[0];
        }
        verifyChecksum(jar, expected);
        extract(jar, extracted, jniName, Platform.detectPlatform("pytorch").getClassifier());
    }

    static void verifyChecksum(Path jar, String expected) throws Exception {
        var digest = MessageDigest.getInstance("SHA-1");
        try (var input = Files.newInputStream(jar)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected)) throw new IOException("Checksum mismatch");
    }

    static void extract(Path jar, Path extracted, String jniName, String classifier) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(entry -> !entry.isDirectory()).toList()) {
                String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                if (!(name.endsWith(".dll") || name.endsWith(".so") || name.contains(".so.") || name.endsWith(".dylib"))) continue;
                if (jniName != null && !entry.getName().equals("jnilib/" + classifier
                    + "/cpu/" + System.mapLibraryName("djl_torch"))) continue;
                try (var input = zip.getInputStream(entry)) {
                    Files.copy(input, extracted.resolve(jniName == null ? name : jniName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
