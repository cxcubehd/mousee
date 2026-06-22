package dev.chrones.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

public final class MacosRawMouseNative {
    private static final String RESOURCE_PATH = "/natives/macos/libmousee_macos.dylib";
    private static volatile boolean loaded;
    private static volatile Throwable loadFailure;

    private MacosRawMouseNative() {}

    public static boolean load(final Logger logger) {
        if (loaded) {
            return true;
        }

        if (loadFailure != null) {
            return false;
        }

        synchronized (MacosRawMouseNative.class) {
            if (loaded) {
                return true;
            }

            try (InputStream input = MacosRawMouseNative.class.getResourceAsStream(RESOURCE_PATH)) {
                if (input == null) {
                    throw new IOException("Missing native resource " + RESOURCE_PATH);
                }

                Path directory = Files.createTempDirectory("mousee-native-");
                Path library = directory.resolve("libmousee_macos.dylib");
                Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
                library.toFile().deleteOnExit();
                directory.toFile().deleteOnExit();
                System.load(library.toAbsolutePath().toString());
                loaded = true;
                return true;
            } catch (Throwable throwable) {
                loadFailure = throwable;
                logger.warn("Mousee could not load its macOS raw mouse native library", throwable);
                return false;
            }
        }
    }

    public static Throwable loadFailure() {
        return loadFailure;
    }

    public static native boolean init(boolean diagnostics);

    public static native boolean isSupported();

    public static native boolean hasMouse();

    public static native void setRelativeMode(boolean enabled);

    public static native int poll(double[] output);

    public static native void setDiagnosticLogging(boolean enabled);

    public static native String diagnosticSummary();

    public static native void shutdown();
}
