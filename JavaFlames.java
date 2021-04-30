import com.sun.net.httpserver.HttpServer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;

import java.awt.Desktop;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaFlames {

    private static final int HTTP_PORT = 8090;
    private static final String PATH_TO_DATA = "data";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            exit(1, "expected jfr input file as argument");
        }
        var jfrFile = Paths.get(args[0]);
        if (!Files.exists(jfrFile)) {
            exit(2, jfrFile + " not found.");
        }
        startHttpServer(jfrFile);
        Desktop.getDesktop().browse(URI.create("http://localhost:%d?baseLineInput=%s".formatted(HTTP_PORT, PATH_TO_DATA)));
    }

    private static void exit(int code, String message) {
        System.err.println(message);
        System.exit(code);
    }

    private static void startHttpServer(Path jfrFile) throws IOException {
        var httpServer = HttpServer.create(new InetSocketAddress("localhost", HTTP_PORT), 0);
        httpServer.createContext("/", exchange -> {
            final Path htmlPage = Paths.get("flamegraph.html");
            exchange.sendResponseHeaders(200, Files.size(htmlPage));
            try (var responseBody = exchange.getResponseBody(); var fis = new FileInputStream(htmlPage.toFile())) {
                fis.transferTo(responseBody);
            }
        });
        httpServer.createContext("/" + PATH_TO_DATA, exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try(var responseBody = exchange.getResponseBody()){
                produceFlameGraphLog(jfrFile).forEach(io(line -> responseBody.write(line.getBytes(StandardCharsets.UTF_8))));
            }
            System.exit(0);
        });
        httpServer.start();
    }

    public static Stream<String> produceFlameGraphLog(final Path jfrRecording) throws IOException {
        var recordingFile = new RecordingFile(jfrRecording);
        return extractEvents(recordingFile)
                .filter(it -> "jdk.ExecutionSample".equalsIgnoreCase(it.getEventType().getName()))
                .map(event -> collapseFrames(event.getStackTrace().getFrames()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().map(e -> "%s %d\n".formatted(e.getKey(), e.getValue()))
                .onClose(io(recordingFile::close));
    }

    private static String collapseFrames(List<RecordedFrame> frames) {
        var methodNames = new ArrayDeque<String>(frames.size());
        for (var frame : frames) {
            final RecordedMethod method = frame.getMethod();
            methodNames.addFirst("%s::%s".formatted(method.getType().getName(), method.getName()));
        }
        return String.join(";", methodNames);
    }

    private static Stream<RecordedEvent> extractEvents(RecordingFile recordingFile) {
        return Stream.generate(() -> {
            if (!recordingFile.hasMoreEvents()) {
                return null;
            }
            try {
                return recordingFile.readEvent();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }).takeWhile(Objects::nonNull);
    }

    @FunctionalInterface
    interface IORunnable {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface IOConsumer<T> {
        void apply(T input) throws IOException;
    }

    private static <T> Consumer<T> io(IOConsumer<T> consumer) {
        return t -> {
            try {
                consumer.apply(t);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static Runnable io(IORunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
