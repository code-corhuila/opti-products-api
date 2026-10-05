package co.edu.corhuila.opti.products.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases.FrameFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.ErrorKind;
import co.edu.corhuila.opti.products.domain.model.FieldError;
import co.edu.corhuila.opti.products.domain.model.Frame;
import co.edu.corhuila.opti.products.domain.model.MovementType;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;
import co.edu.corhuila.opti.products.domain.model.StockMovement;
import co.edu.corhuila.opti.products.testsupport.Fixtures;
import co.edu.corhuila.opti.products.testsupport.TestClock;

class FrameServiceTest {

    private static final String KEY = "register-0001";

    private TestClock clock;
    private FrameUseCases service;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        service = Fixtures.service(clock);
    }

    @Test
    void registersAnActiveFrameWithItsStock() {
        var result = service.register(Fixtures.validFrame(), KEY);

        assertThat(result.created()).isTrue();
        assertThat(result.value().stock()).isEqualTo(8);
        assertThat(result.value().salePriceCents()).isEqualTo(52_000_000L);
        assertThat(service.get(result.value().id())).isEqualTo(result.value());
    }

    @Test
    void repeatingTheKeyReturnsTheSameFrame() {
        var first = service.register(Fixtures.validFrame(), KEY);
        var second = service.register(Fixtures.validFrame(), KEY);

        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(service.search(new FrameFilter(null, null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateSkuWithADifferentKey() {
        service.register(Fixtures.validFrame(), KEY);

        assertThatThrownBy(() -> service.register(Fixtures.validFrame(), "register-0002"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var invalid = new Frame.RegisterData("x", " ", "", null, null, null, -5L, 100L, -1, null, null, null);

        assertThatThrownBy(() -> service.register(invalid, "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                            "Idempotency-Key", "sku", "brand", "model", "costCents", "stock", "minStock");
                });
    }

    @Test
    void salePriceBelowCostIsRejected() {
        var data = new Frame.RegisterData("RB5228-2000", "Ray-Ban", "RB5228", null, null, null,
                15_000_000L, 12_000_000L, 1, 0, null, null);

        assertThatThrownBy(() -> service.register(data, KEY))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).containsExactly("salePriceCents"));
    }

    @Test
    void minStockCanBeChangedAndDrivesTheLowStockListing() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        assertThat(service.search(new FrameFilter(null, true, null, null), PageQuery.first(20)).total()).isZero();
        Frame updated = service.updateMinStock(id, 8);

        assertThat(updated.lowStock()).isTrue();
        assertThat(service.search(new FrameFilter(null, true, null, null), PageQuery.first(20)).total()).isEqualTo(1);
        assertThatThrownBy(() -> service.updateMinStock(id, -1)).isInstanceOf(DomainException.class);
    }

    @Test
    void reservingTakesStockAndSnapshotsThePrice() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        var reservation = service.reserve(id, 2, "saga-1", "reserve-0001").value();

        assertThat(service.get(id).stock()).isEqualTo(6);
        assertThat(reservation.unitPriceCents()).isEqualTo(52_000_000L);
        assertThat(reservation.sku()).isEqualTo("RB5228-2000");
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(service.movements(id, PageQuery.first(20)).data()).extracting(StockMovement::type)
                .containsExactly(MovementType.EXIT);
    }

    @Test
    void reservingWithTheSameKeyTakesStockOnlyOnce() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        var first = service.reserve(id, 2, "saga-1", "reserve-0001");
        var replay = service.reserve(id, 2, "saga-1", "reserve-0001");

        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.value().id());
        assertThat(service.get(id).stock()).isEqualTo(6);
    }

    @Test
    void reservingMoreThanTheStockIsABusinessRuleViolation() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        assertThatThrownBy(() -> service.reserve(id, 9, "saga-1", "reserve-0001"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION);
                    assertThat(e.getMessage()).contains("insufficient stock");
                });
        assertThat(service.get(id).stock()).isEqualTo(8);
    }

    @Test
    void reserveValidatesQuantityAndReference() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        assertThatThrownBy(() -> service.reserve(id, 0, " ", "x"))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("Idempotency-Key", "quantity", "reference"));
    }

    @Test
    void reserveOfAnUnknownFrameIsNotFound() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), 1, "saga-1", "reserve-0001"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void releasingGivesTheStockBackOnlyOnce() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();
        UUID reservationId = service.reserve(id, 3, "saga-1", "reserve-0001").value().id();

        var released = service.release(reservationId);
        var again = service.release(reservationId);

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(again.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(service.get(id).stock()).isEqualTo(8);
        assertThat(service.movements(id, PageQuery.first(20)).data()).extracting(StockMovement::type)
                .containsExactlyInAnyOrder(MovementType.EXIT, MovementType.RETURN);
    }

    @Test
    void releasingAnUnknownReservationIsNotFound() {
        assertThatThrownBy(() -> service.release(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void stockEntryAddsUnitsOnceEvenIfRetried() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        var first = service.addStock(id, 5, "supplier purchase", "entry-0001");
        var retry = service.addStock(id, 5, "supplier purchase", "entry-0001");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(service.get(id).stock()).isEqualTo(13);
        assertThatThrownBy(() -> service.addStock(id, 0, null, "entry-0002")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.addStock(id, 2_000_000, null, "entry-0003"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void listsNewestFirstAndFiltersByText() {
        for (int i = 0; i < 3; i++) {
            service.register(Fixtures.frame("SKU-00" + i), "register-000" + i);
            clock.advance(Duration.ofMinutes(1));
        }

        var page = service.search(new FrameFilter(null, null, null, null), new PageQuery(1, 2));

        assertThat(page.data()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.data().get(0).sku()).isEqualTo("SKU-002");
        assertThat(service.search(new FrameFilter("sku-001", null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void filtersByBrand() {
        service.register(Fixtures.frame("SKU-RB-01"), "register-rb-01");
        service.register(new Frame.RegisterData("SKU-OK-01", "Oakley", "Holbrook", "Black", "Plastic", "Unisex",
                20_000_000L, 40_000_000L, 5, 1, "Main display", "Luxottica Colombia"), "register-ok-01");

        assertThat(service.search(new FrameFilter(null, null, null, "Oakley"), PageQuery.first(20)).total())
                .isEqualTo(1);
        assertThat(service.brands()).containsExactly("Oakley", "Ray-Ban");
    }

    @Test
    void summarizesTheInventory() {
        service.register(Fixtures.frame("SKU-NORMAL"), "register-normal"); // stock 8, minStock 2: normal
        service.register(new Frame.RegisterData("SKU-LOW", "Ray-Ban", "RB3025", "Gold", "Metal", "Unisex",
                10_000_000L, 20_000_000L, 1, 2, "Main display", "Luxottica Colombia"), "register-low"); // low stock
        service.register(new Frame.RegisterData("SKU-OUT", "Ray-Ban", "RB2140", "Black", "Acetate", "Unisex",
                15_000_000L, 30_000_000L, 0, 3, "Main display", "Luxottica Colombia"), "register-out"); // out of stock

        var summary = service.summary();

        assertThat(summary.totalReferences()).isEqualTo(3);
        assertThat(summary.lowStockCount()).isEqualTo(1);
        assertThat(summary.outOfStockCount()).isEqualTo(1);
        // SKU-NORMAL: 52_000_000 * 8 + SKU-LOW: 20_000_000 * 1 + SKU-OUT: 30_000_000 * 0 (ACTIVE, stock drives it to zero)
        assertThat(summary.totalValueCents()).isEqualTo(52_000_000L * 8 + 20_000_000L);
        assertThat(summary.recentCount30d()).isEqualTo(3);
    }

    @Test
    void storesTheUploadedPhotoAndPointsTheFrameToIt() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        Frame updated = service.uploadImage(id, "image/png", new byte[]{1, 2, 3});

        assertThat(updated.imageUrl()).isEqualTo("/media/frames/" + id + ".png");
        assertThat(service.get(id).imageUrl()).isEqualTo("/media/frames/" + id + ".png");
    }

    @Test
    void rejectsAnImageOfTheWrongType() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        assertThatThrownBy(() -> service.uploadImage(id, "application/pdf", new byte[]{1}))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).kind())
                .isEqualTo(ErrorKind.VALIDATION);
    }

    @Test
    void rejectsAnImageLargerThanTwoMegabytes() {
        UUID id = service.register(Fixtures.validFrame(), KEY).value().id();

        assertThatThrownBy(() -> service.uploadImage(id, "image/jpeg", new byte[2 * 1024 * 1024 + 1]))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void failsWithNotFoundWhenTheFrameDoesNotExist() {
        assertThatThrownBy(() -> service.uploadImage(UUID.randomUUID(), "image/jpeg", new byte[]{1}))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).kind())
                .isEqualTo(ErrorKind.NOT_FOUND);
    }
}
