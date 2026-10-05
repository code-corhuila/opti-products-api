package co.edu.corhuila.opti.products.testsupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import co.edu.corhuila.opti.products.application.port.out.FrameImageStorage;

/** Fake of the frame photo store, used to test the core without touching the filesystem. */
public class InMemoryFrameImageStorage implements FrameImageStorage {

    private final Map<UUID, byte[]> byFrame = new LinkedHashMap<>();

    @Override
    public String store(UUID frameId, String extension, byte[] content) {
        byFrame.put(frameId, content);
        return "/media/frames/" + frameId + "." + extension;
    }

    public byte[] contentOf(UUID frameId) {
        return byFrame.get(frameId);
    }
}
