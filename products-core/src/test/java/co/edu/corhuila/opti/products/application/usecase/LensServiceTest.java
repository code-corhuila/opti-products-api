package co.edu.corhuila.opti.products.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.in.LensUseCases.LensFilter;
import co.edu.corhuila.opti.products.application.port.in.PageQuery;
import co.edu.corhuila.opti.products.domain.model.DomainException;
import co.edu.corhuila.opti.products.domain.model.ErrorKind;
import co.edu.corhuila.opti.products.domain.model.FieldError;
import co.edu.corhuila.opti.products.domain.model.Lens;
import co.edu.corhuila.opti.products.testsupport.Fixtures;
import co.edu.corhuila.opti.products.testsupport.TestClock;

class LensServiceTest {

    private static final String KEY = "register-0001";

    private TestClock clock;
    private LensUseCases service;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        service = Fixtures.lensService(clock);
    }

    @Test
    void registersAnActiveLensWithItsStock() {
        var result = service.register(Fixtures.validLens(), KEY);

        assertThat(result.created()).isTrue();
        assertThat(result.value().stock()).isEqualTo(20);
        assertThat(result.value().salePriceCents()).isEqualTo(15_000_000L);
        assertThat(service.get(result.value().id())).isEqualTo(result.value());
    }

    @Test
    void repeatingTheKeyReturnsTheSameLens() {
        var first = service.register(Fixtures.validLens(), KEY);
        var second = service.register(Fixtures.validLens(), KEY);

        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(service.search(new LensFilter(null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateSkuWithADifferentKey() {
        service.register(Fixtures.validLens(), KEY);

        assertThatThrownBy(() -> service.register(Fixtures.validLens(), "register-0002"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var invalid = new Lens.RegisterData("x", " ", "UNKNOWN", null, null, 999, -5L, 100L, -1, null);

        assertThatThrownBy(() -> service.register(invalid, "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                            "Idempotency-Key", "sku", "brand", "lensType", "refractiveIndexX100", "costCents",
                            "stock", "minStock");
                });
    }

    @Test
    void salePriceBelowCostIsRejected() {
        var data = new Lens.RegisterData("LNS-MONO-150", "Essilor", "MONOFOCAL", null, null, null,
                15_000_000L, 12_000_000L, 1, 0);

        assertThatThrownBy(() -> service.register(data, KEY))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).containsExactly("salePriceCents"));
    }

    @Test
    void minStockCanBeChangedAndDrivesTheLowStockListing() {
        UUID id = service.register(Fixtures.validLens(), KEY).value().id();

        assertThat(service.search(new LensFilter(null, true, null), PageQuery.first(20)).total()).isZero();
        Lens updated = service.updateMinStock(id, 25);

        assertThat(updated.lowStock()).isTrue();
        assertThat(service.search(new LensFilter(null, true, null), PageQuery.first(20)).total()).isEqualTo(1);
        assertThatThrownBy(() -> service.updateMinStock(id, -1)).isInstanceOf(DomainException.class);
    }

    @Test
    void stockEntryAddsUnitsOnceEvenIfRetried() {
        UUID id = service.register(Fixtures.validLens(), KEY).value().id();

        var first = service.addStock(id, 5, "entry-0001");
        var retry = service.addStock(id, 5, "entry-0001");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(service.get(id).stock()).isEqualTo(25);
        assertThatThrownBy(() -> service.addStock(id, 0, "entry-0002")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.addStock(id, 2_000_000, "entry-0003")).isInstanceOf(DomainException.class);
    }

    @Test
    void listsNewestFirstAndFiltersByText() {
        for (int i = 0; i < 3; i++) {
            service.register(Fixtures.lens("LNS-00" + i), "register-000" + i);
            clock.advance(Duration.ofMinutes(1));
        }

        var page = service.search(new LensFilter(null, null, null), new PageQuery(1, 2));

        assertThat(page.data()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.data().get(0).sku()).isEqualTo("LNS-002");
        assertThat(service.search(new LensFilter("lns-001", null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }
}
