package co.edu.corhuila.opti.products.testsupport;

import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.usecase.FrameService;
import co.edu.corhuila.opti.products.domain.model.Frame;

/** Ready-made valid inputs and a fully wired service over the fakes. */
public final class Fixtures {

    public static final String START = "2026-09-29T15:00:00Z";

    private Fixtures() {
    }

    public static FrameUseCases service(TestClock clock) {
        return new FrameService(new InMemoryFrameRepository(), new InMemoryReservationRepository(),
                new InMemoryStockMovementRepository(), new InMemoryIdempotencyStore(), new SequentialIds(),
                new DirectUnitOfWork(), clock);
    }

    public static Frame.RegisterData validFrame() {
        return frame("RB5228-2000");
    }

    public static Frame.RegisterData frame(String sku) {
        return new Frame.RegisterData(sku, "Ray-Ban", "RB5228", "Matte black", "Acetate", "Unisex",
                31_000_000L, 52_000_000L, 8, 2, "Main display", "Luxottica Colombia");
    }
}
