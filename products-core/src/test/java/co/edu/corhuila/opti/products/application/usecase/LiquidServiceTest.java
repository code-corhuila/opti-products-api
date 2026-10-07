package co.edu.corhuila.opti.products.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases;
import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases.LiquidFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.ErrorKind;
import co.edu.corhuila.opti.products.domain.model.FieldError;
import co.edu.corhuila.opti.products.domain.model.Liquid;
import co.edu.corhuila.opti.products.domain.model.ReservationStatus;
import co.edu.corhuila.opti.products.testsupport.Fixtures;
import co.edu.corhuila.opti.products.testsupport.TestClock;

class LiquidServiceTest {

    private static final String KEY = "register-0001";

    private TestClock clock;
    private LiquidUseCases service;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        service = Fixtures.liquidService(clock);
    }

    @Test
    void registersAnActiveLiquidWithItsStock() {
        var result = service.register(Fixtures.validLiquid(), KEY);

        assertThat(result.created()).isTrue();
        assertThat(result.value().stock()).isEqualTo(40);
        assertThat(result.value().salePriceCents()).isEqualTo(800_000L);
        assertThat(service.get(result.value().id())).isEqualTo(result.value());
    }

    @Test
    void repeatingTheKeyReturnsTheSameLiquid() {
        var first = service.register(Fixtures.validLiquid(), KEY);
        var second = service.register(Fixtures.validLiquid(), KEY);

        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(service.search(new LiquidFilter(null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateSkuWithADifferentKey() {
        service.register(Fixtures.validLiquid(), KEY);

        assertThatThrownBy(() -> service.register(Fixtures.validLiquid(), "register-0002"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var invalid = new Liquid.RegisterData("x", " ", 99_999, -5L, 100L, -1, null);

        assertThatThrownBy(() -> service.register(invalid, "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                            "Idempotency-Key", "sku", "brand", "volumeMl", "costCents", "stock", "minStock");
                });
    }

    @Test
    void salePriceBelowCostIsRejected() {
        var data = new Liquid.RegisterData("LIQ-LOW", "Opti", 120, 15_000_00L, 12_000_00L, 1, 0);

        assertThatThrownBy(() -> service.register(data, KEY))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).containsExactly("salePriceCents"));
    }

    @Test
    void reservingTakesStockAndSnapshotsThePrice() {
        UUID id = service.register(Fixtures.validLiquid(), KEY).value().id();

        var reservation = service.reserve(id, 2, "saga-1", "reserve-0001").value();

        assertThat(service.get(id).stock()).isEqualTo(38);
        assertThat(reservation.unitPriceCents()).isEqualTo(800_000L);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void reservingMoreThanTheStockIsABusinessRuleViolation() {
        UUID id = service.register(Fixtures.validLiquid(), KEY).value().id();

        assertThatThrownBy(() -> service.reserve(id, 99, "saga-1", "reserve-0001"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
        assertThat(service.get(id).stock()).isEqualTo(40);
    }

    @Test
    void releasingGivesTheStockBackOnlyOnce() {
        UUID id = service.register(Fixtures.validLiquid(), KEY).value().id();
        UUID reservationId = service.reserve(id, 3, "saga-1", "reserve-0001").value().id();

        var released = service.release(reservationId);
        var again = service.release(reservationId);

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(again.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(service.get(id).stock()).isEqualTo(40);
    }

    @Test
    void releasingAnUnknownReservationIsNotFound() {
        assertThatThrownBy(() -> service.release(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void stockEntryAddsUnitsOnceEvenIfRetried() {
        UUID id = service.register(Fixtures.validLiquid(), KEY).value().id();

        var first = service.addStock(id, 5, "entry-0001");
        var retry = service.addStock(id, 5, "entry-0001");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(service.get(id).stock()).isEqualTo(45);
    }

    @Test
    void listsNewestFirstAndFiltersByText() {
        for (int i = 0; i < 3; i++) {
            service.register(Fixtures.liquid("LIQ-00" + i), "register-000" + i);
            clock.advance(Duration.ofMinutes(1));
        }

        var page = service.search(new LiquidFilter(null, null, null), new PageQuery(1, 2));

        assertThat(page.data()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.data().get(0).sku()).isEqualTo("LIQ-002");
    }
}
