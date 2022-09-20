package net.djvk.fireflyPlaidConnector2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan(basePackages = ["net.djvk.fireflyPlaidConnector2.config.properties"])
@SpringBootApplication
class FireflyPlaidConnector2Application

fun main(args: Array<String>) {
    runApplication<FireflyPlaidConnector2Application>(*args)
}
