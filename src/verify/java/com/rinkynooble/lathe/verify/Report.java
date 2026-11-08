package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.Lathe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects check results and writes them to a file the test script reads. */
final class Report {
    private final List<String> lines = new ArrayList<>();
    private int passed;
    private int failed;

    void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            lines.add("FAIL " + what);
        }
    }

    void info(String line) {
        lines.add("INFO " + line);
    }

    int failed() {
        return failed;
    }

    void write(Path file, String title) {
        List<String> out = new ArrayList<>();
        out.add(title + ": " + passed + " passed, " + failed + " failed");
        out.addAll(lines);
        out.add(failed == 0 ? "RESULT PASS" : "RESULT FAIL");
        try {
            Files.write(file, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Lathe.LOG.error("Lathe verify: couldn't write {}", file, e);
        }
        out.forEach(line -> Lathe.LOG.info("[verify] {}", line));
    }
}
