import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Profile {
    private static final String NOW = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
    private static final Path LOG_FILE_NAME = Paths.get("log" + NOW + ".jfr").toAbsolutePath();
    private static final Path CONFIG_FILE_NAME = Paths.get("config.jfc").toAbsolutePath();
    private static final String PROFILING_NAME = "temp";
    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(30);

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception throwable) {
            throwable.printStackTrace();
            exit(99, "Encountered exception: " + throwable.getMessage());
        }
    }

    private static void run(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            exit(1, "expected pid to profile");
        }

        final int pid = findProcessPid(args[0]);

        var duration = args.length == 2 ?
                Duration.ofSeconds(Integer.parseInt(args[1])) :
                DEFAULT_DURATION;

        run("jcmd %d JFR.start name=%s settings=%s".formatted(pid, PROFILING_NAME, CONFIG_FILE_NAME));
        println("Started profiling process %d for %d seconds".formatted(pid, duration.getSeconds()));
        final LocalTime deadline = LocalTime.now().plus(duration);

        while (LocalTime.now().isBefore(deadline)) {
            TimeUnit.SECONDS.sleep(1);
            System.out.print(".");
        }

        println("Stopping profiling");
        run("jcmd %d JFR.dump name=%s filename=%s".formatted(pid, PROFILING_NAME, LOG_FILE_NAME));
        run("jcmd %d JFR.stop name=%s".formatted(pid, PROFILING_NAME));

        println("Starting JavaFlames");
        run("java JavaFlames.java %s".formatted(LOG_FILE_NAME));

        Files.deleteIfExists(LOG_FILE_NAME);
    }

    private static int findProcessPid(String javaProcess) throws IOException {
        if(javaProcess.matches("[0-9]+")){
            return Integer.parseInt(javaProcess);
        }

        final Process jps = new ProcessBuilder("jps").start();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(jps.getInputStream()))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.endsWith(javaProcess)) {
                    return Integer.parseInt(line.split(" ")[0]);
                }
            }
        }

        throw new IllegalStateException("Failed to find pid for process: " + javaProcess);
    }

    private static void println(String s) {
        System.out.println(s);
    }

    private static void run(String command) throws IOException, InterruptedException {
        println("> " + command);
        final int exitCode = new ProcessBuilder(command.split(" "))
                .inheritIO()
                .start()
                .waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("Command \"%s\" exited with code %d".formatted(command, exitCode));
        }
    }

    private static void exit(int code, String message) {
        println(message);
        usage();
        System.exit(code);
    }

    private static void usage() {
        println("Usage: java Profile.java <pid> [<duration in seconds>]");
    }
}
