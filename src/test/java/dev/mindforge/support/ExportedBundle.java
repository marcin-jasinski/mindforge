package dev.mindforge.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.mindforge.infrastructure.export.BundleExporter;

/** Test oracle: exports a knowledge base and unzips it to bundle-relative path -> content. */
public final class ExportedBundle {

    private ExportedBundle() {}

    public static Map<String, String> files(BundleExporter exporter, UUID kbId, String name) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(kbId, name, out);
            Map<String, String> files = new LinkedHashMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    files.put(entry.getName().substring(entry.getName().indexOf('/') + 1),
                        new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
