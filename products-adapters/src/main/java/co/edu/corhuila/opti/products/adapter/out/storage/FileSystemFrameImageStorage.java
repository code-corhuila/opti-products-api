package co.edu.corhuila.opti.products.adapter.out.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.FrameImageStorage;

/**
 * Writes frame photos under a directory mounted as a volume (for example {@code /data/frame-images}),
 * one file per frame named by its id, overwriting any previous photo. The gateway serves that same
 * volume, read-only, under the public prefix returned here.
 */
public class FileSystemFrameImageStorage implements FrameImageStorage {

    private static final String PUBLIC_PREFIX = "/media/frames/";

    private final Path directory;

    public FileSystemFrameImageStorage(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create the frame images directory: " + directory, e);
        }
    }

    @Override
    public String store(UUID frameId, String extension, byte[] content) {
        Path target = directory.resolve(frameId + "." + extension);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the frame image: " + target, e);
        }
        return PUBLIC_PREFIX + frameId + "." + extension;
    }
}
