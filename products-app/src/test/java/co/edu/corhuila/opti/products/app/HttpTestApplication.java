package co.edu.corhuila.opti.products.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.products.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.products.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.products.application.port.in.AccessoryUseCases;
import co.edu.corhuila.opti.products.application.port.in.FrameUseCases;
import co.edu.corhuila.opti.products.application.port.in.LensUseCases;
import co.edu.corhuila.opti.products.application.port.in.LiquidUseCases;
import co.edu.corhuila.opti.products.testsupport.Fixtures;
import co.edu.corhuila.opti.products.testsupport.TestClock;

/**
 * Boots only the HTTP adapter over the in-memory fakes: no database, same filters, same
 * error handling, same controllers as production.
 */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.products.adapter.in.http",
        exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
class HttpTestApplication {

    @Bean
    TestClock clock() {
        return TestClock.at(Fixtures.START);
    }

    @Bean
    Rs256Verifier verifier(ObjectMapper json, TestClock clock) {
        return new Rs256Verifier(TestTokens.publicKeyPem(), json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    FrameUseCases useCases(TestClock clock) {
        return Fixtures.service(clock);
    }

    @Bean
    LensUseCases lensUseCases(TestClock clock) {
        return Fixtures.lensService(clock);
    }

    @Bean
    AccessoryUseCases accessoryUseCases(TestClock clock) {
        return Fixtures.accessoryService(clock);
    }

    @Bean
    LiquidUseCases liquidUseCases(TestClock clock) {
        return Fixtures.liquidService(clock);
    }
}
