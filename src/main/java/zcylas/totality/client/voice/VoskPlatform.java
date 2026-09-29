package zcylas.totality.client.voice;

import java.util.Locale;

/**
 * Platforms the bundled Vosk 0.3.45 natives are shipped and supported for. The published jar also
 * carries an Intel macOS library, but macOS is not a Totality target yet, and there are no ARM
 * builds at all — anything else fails gracefully with UNSUPPORTED_PLATFORM before native code is
 * touched.
 */
final class VoskPlatform {

    private VoskPlatform() {}

    static boolean isSupported() {
        return isSupported(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static boolean isSupported(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        return x64 && (os.startsWith("linux") || os.startsWith("windows"));
    }

    static String describe() {
        return System.getProperty("os.name") + " / " + System.getProperty("os.arch");
    }
}
