package net.djvk.fireflyPlaidConnector2

import net.djvk.fireflyPlaidConnector2.smoke.SmokeTestCommand
import net.djvk.fireflyPlaidConnector2.sync.Runner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import kotlin.system.exitProcess

@Profile("!test")
@ConfigurationPropertiesScan(basePackages = ["net.djvk.fireflyPlaidConnector2.config.properties"])
@SpringBootApplication
class FireflyPlaidConnector2Application(
    private val runner: Runner,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun appReady() {
        /**
         * We're doing this here rather than in a bean init function or @PostConstruct to avoid
         *  things being launched automatically, thus making them easier to test
         */
        runner.run()
    }
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "smoke-test") {
        exitProcess(SmokeTestCommand.run(args.drop(1).toTypedArray()))
    }
    runApplication<FireflyPlaidConnector2Application>(*args)
}
