package co.edu.corhuila.opti.products.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.application.usecase.FrameService;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.FrameStatus;
import co.edu.corhuila.opti.products.domain.model.MovementType;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;

/**
 * Runs the real use cases over a real PostgreSQL carrying the schema of opti-products-db.
 * {@code TEST_DATABASE_URL} example: {@code jdbc:postgresql://localhost:5432/products?user=x&password=y}.
 * Without it the test is skipped, not failed.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriesIntegrationTest {

    private static FrameUseCases service;
    private static JdbcFrameRepository frames;

    @BeforeAll
    static void connect() {
        var dataSource = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"));
        dataSource.setSchema("products");
        var jdbc = JdbcClient.create(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        frames = new JdbcFrameRepository(jdbc);
        service = new FrameService(frames, new JdbcReservationRepository(jdbc), new JdbcStockMovementRepository(jdbc),
                new IdempotencyKeys(jdbc), new UuidGenerator(), new JdbcUnitOfWork(transaction), Clock.systemUTC());
    }

    @Test
    void registersAndReadsBackAFrameWithMoneyInCents() {
        var created = service.register(frame("IT-" + UUID.randomUUID().toString().substring(0, 8), 5), key());

        Frame read = service.get(created.value().id());

        assertThat(read.costCents()).isEqualTo(31_000_000L);
        assertThat(read.salePriceCents()).isEqualTo(52_000_000L);
        assertThat(read.stock()).isEqualTo(5);
        assertThat(read.status()).isEqualTo(FrameStatus.ACTIVE);
        assertThat(read.createdAt()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void sameKeyStoresNothingNewAndDuplicateSkuRollsTheKeyBack() {
        String sku = "IT-" + UUID.randomUUID().toString().substring(0, 8);
        String key = key();
        var first = service.register(frame(sku, 1), key);
        var replay = service.register(frame(sku, 1), key);

        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.value().id());

        String lostKey = key();
        assertThatThrownBy(() -> service.register(frame(sku, 1), lostKey)).isInstanceOf(DomainException.class);
        assertThat(frames.existsBySku(sku)).isTrue();
        var retryLostKey = service.register(frame("IT-" + UUID.randomUUID().toString().substring(0, 8), 1), lostKey);
        assertThat(retryLostKey.created()).as("the key of the failed attempt was rolled back and is reusable").isTrue();
    }

    @Test
    void reserveAndReleaseMoveStockAndWriteTheLedgerAtomically() {
        UUID id = service.register(frame("IT-" + UUID.randomUUID().toString().substring(0, 8), 5), key()).value().id();

        var reservation = service.reserve(id, 2, "saga-it", key()).value();
        assertThat(service.get(id).stock()).isEqualTo(3);

        service.release(reservation.id());
        service.release(reservation.id());

        assertThat(service.get(id).stock()).isEqualTo(5);
        assertThat(service.getReservation(reservation.id()).status()).isEqualTo(ReservationStatus.RELEASED);
        var ledger = service.movements(id, PageQuery.first(10));
        assertThat(ledger.total()).isEqualTo(2);
        assertThat(ledger.data()).extracting(m -> m.type()).containsExactlyInAnyOrder(MovementType.EXIT, MovementType.RETURN);
    }

    @Test
    void insufficientStockRollsBackTheWholeReservation() {
        UUID id = service.register(frame("IT-" + UUID.randomUUID().toString().substring(0, 8), 1), key()).value().id();
        String key = key();

        assertThatThrownBy(() -> service.reserve(id, 2, "saga-it", key)).isInstanceOf(DomainException.class);

        assertThat(service.get(id).stock()).isEqualTo(1);
        assertThat(service.movements(id, PageQuery.first(10)).total()).isZero();
        assertThat(service.reserve(id, 1, "saga-it", key).created()).as("key not burned by the failure").isTrue();
    }

    @Test
    void concurrentReservationsNeverOversellAndTheSameKeyIsAppliedOnce() throws Exception {
        UUID id = service.register(frame("IT-" + UUID.randomUUID().toString().substring(0, 8), 3), key()).value().id();
        String sharedKey = key();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var sameKey = new java.util.ArrayList<Future<UUID>>();
            var different = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 4; i++) {
                sameKey.add(pool.submit(() -> {
                    start.await();
                    return service.reserve(id, 1, "same", sharedKey).value().id();
                }));
            }
            for (int i = 0; i < 4; i++) {
                String own = key();
                different.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.reserve(id, 1, "other", own);
                        return true;
                    } catch (DomainException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var f : sameKey) {
                ids.add(f.get(20, TimeUnit.SECONDS));
            }
            long succeeded = 0;
            for (var f : different) {
                succeeded += f.get(20, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(ids).as("the four calls with the same key produced one reservation").hasSize(1);
            assertThat(service.get(id).stock()).isGreaterThanOrEqualTo(0);
            assertThat(3 - service.get(id).stock()).isEqualTo(1 + succeeded);
            assertThat(1 + succeeded).isLessThanOrEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void searchFiltersLowStockAndEscapesWildcards() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        service.register(frame("IT-LOW-" + tag, 1), key());
        service.register(frame("IT-OK-" + tag, 9), key());

        assertThat(service.search(new FrameFilter("it-low-" + tag, true, null), PageQuery.first(10)).total()).isEqualTo(1);
        assertThat(service.search(new FrameFilter("it-ok-" + tag, true, null), PageQuery.first(10)).total()).isZero();
        assertThat(service.search(new FrameFilter("%" + tag, null, null), PageQuery.first(10)).total()).isZero();
    }

    private static Frame.RegisterData frame(String sku, int stock) {
        return new Frame.RegisterData(sku, "Ray-Ban", "RB5228", "Matte black", "Acetate", "Unisex", 31_000_000L,
                52_000_000L, stock, 2, "Main display", "Luxottica");
    }

    private static String key() {
        return "it-" + UUID.randomUUID();
    }
}
