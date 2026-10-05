package co.edu.corhuila.opti.products.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.products.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.products.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.products.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcFrameRepository;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcLensRepository;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcLensReservationRepository;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcStockMovementRepository;
import co.edu.corhuila.opti.products.adapter.out.persistence.JdbcUnitOfWork;
import co.edu.corhuila.opti.products.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.opti.products.adapter.out.storage.FileSystemFrameImageStorage;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.out.FrameImageStorage;
import co.edu.corhuila.opti.products.application.port.out.FrameRepository;
import co.edu.corhuila.opti.products.application.port.out.IdGenerator;
import co.edu.corhuila.opti.products.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.products.application.port.out.LensRepository;
import co.edu.corhuila.opti.products.application.port.out.ReservationRepository;
import co.edu.corhuila.opti.products.application.port.out.StockMovementRepository;
import co.edu.corhuila.opti.products.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.products.application.usecase.FrameService;
import co.edu.corhuila.opti.products.application.usecase.LensService;

/**
 * Composition root: the only place that knows every concrete type. The numeric limits (server
 * timeouts, pool size, statement timeout, graceful shutdown) are declared with their value in
 * {@code application.yml}, next to this class.
 */
@Configuration
class ProductsConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Rs256Verifier tokenVerifier(ObjectMapper json, Clock clock,
                                @Value("${jwt.public-key:}") String publicKey,
                                @Value("${jwt.public-key-file:}") String publicKeyFile) throws IOException {
        String pem = publicKey.isBlank() && !publicKeyFile.isBlank()
                ? Files.readString(Path.of(publicKeyFile)) : publicKey;
        if (pem.isBlank()) {
            throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE (identity service public key)");
        }
        return new Rs256Verifier(pem, json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcClient jdbc) {
        return new IdempotencyKeys(jdbc);
    }

    @Bean
    UnitOfWork unitOfWork(TransactionTemplate transaction) {
        return new JdbcUnitOfWork(transaction);
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidGenerator();
    }

    @Bean
    FrameRepository frameRepository(JdbcClient jdbc) {
        return new JdbcFrameRepository(jdbc);
    }

    @Bean
    ReservationRepository reservationRepository(JdbcClient jdbc) {
        return new JdbcReservationRepository(jdbc);
    }

    @Bean
    StockMovementRepository stockMovementRepository(JdbcClient jdbc) {
        return new JdbcStockMovementRepository(jdbc);
    }

    @Bean
    FrameImageStorage frameImageStorage(@Value("${app.frame-images.dir:/data/frame-images}") String dir) {
        return new FileSystemFrameImageStorage(Path.of(dir));
    }

    @Bean
    FrameUseCases frameUseCases(FrameRepository frames, ReservationRepository reservations,
                                StockMovementRepository movements, IdempotencyStore keys, IdGenerator ids,
                                UnitOfWork unitOfWork, Clock clock, FrameImageStorage images) {
        return new FrameService(frames, reservations, movements, keys, ids, unitOfWork, clock, images);
    }

    @Bean
    LensRepository lensRepository(JdbcClient jdbc) {
        return new JdbcLensRepository(jdbc);
    }

    /**
     * Lens reservations reuse the frame-independent {@link ReservationRepository} port and the
     * shared {@code Reservation} domain class, but are backed by their own {@code lens_reservation}
     * table (see {@link JdbcLensReservationRepository}) instead of sharing {@code stock_reservation}
     * with frames.
     */
    @Bean
    ReservationRepository lensReservationRepository(JdbcClient jdbc) {
        return new JdbcLensReservationRepository(jdbc);
    }

    @Bean
    LensUseCases lensUseCases(LensRepository lenses, ReservationRepository lensReservationRepository,
                              IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        return new LensService(lenses, lensReservationRepository, keys, ids, unitOfWork, clock);
    }
}
