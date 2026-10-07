package co.edu.corhuila.opti.products.application.port.out;

import java.util.UUID;

/** Persists a frame's photo outside the database and hands back its public, relative URL. */
public interface FrameImageStorage {

    /**
     * Writes the content for the given frame, overwriting any previous photo of that frame.
     *
     * @param extension lower-case file extension without the dot ({@code jpg} or {@code png})
     * @return a public relative path (never the on-disk location), for example {@code /media/frames/<id>.jpg}
     */
    String store(UUID frameId, String extension, byte[] content);
}
