package controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Screenshots and demos sent with the clan war form. They are stored under the
 * name the browser sent, made safe, and never replace a file already there: a
 * name in use gets a counter, since another clan war may refer to that file.
 */
final class ClanwarUploads {
    /** Leaves room for the counter within AdminUploadController.UPLOAD_NAME's 128 characters. */
    private static final int MAX_NAME_LENGTH = 120;
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern LEADING = Pattern.compile("^[^A-Za-z0-9]+");

    private ClanwarUploads() { }

    static boolean isScreenshot(String name) {
        return AdminUploadController.UPLOAD_NAME.matcher(name).matches()
                && HomeController.hasAllowedImageExtension(Path.of(name));
    }

    /**
     * The browser's file name with anything outside letters, digits, ".", "_" and "-"
     * replaced, or null when the result is no name the target accepts.
     */
    static String safeName(String original, Predicate<String> accepted) {
        if (original == null) return null;
        // Some browsers send the whole client-side path.
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = LEADING.matcher(UNSAFE.matcher(name.trim()).replaceAll("_")).replaceFirst("");
        int dot = name.lastIndexOf('.');
        if (name.length() > MAX_NAME_LENGTH && dot > 0) {
            String extension = name.substring(dot);
            name = name.substring(0, Math.max(1, MAX_NAME_LENGTH - extension.length())) + extension;
        }
        return !name.isEmpty() && accepted.test(name) ? name : null;
    }

    /** The name itself while it is free in the folder, otherwise name-1.ext, name-2.ext and so on. */
    static String freeName(Path folder, String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String candidate = name;
        for (int counter = 1; Files.exists(folder.resolve(candidate), LinkOption.NOFOLLOW_LINKS); counter++) {
            candidate = base + "-" + counter + extension;
        }
        return candidate;
    }

    /**
     * Copies an upload into the folder under a free name and returns that name.
     * Written next to its final place first, so a download never sees half a file.
     */
    static String store(Path source, Path folder, String name) throws IOException {
        Files.createDirectories(folder);
        String file = freeName(folder, name);
        Path partial = folder.resolve("." + file + ".partial");
        Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
        Files.move(partial, folder.resolve(file), StandardCopyOption.ATOMIC_MOVE);
        return file;
    }
}
