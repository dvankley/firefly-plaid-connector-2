package net.djvk.fireflyPlaidConnector2.lib

import net.djvk.fireflyPlaidConnector2.FireflyPlaidConnector2Application
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

/**
 * The only functional differences between this and the actual app are that this one
 *  doesn't start a runner on its own and runs with the "test" profile active
 */
@ConfigurationPropertiesScan(basePackages = ["net.djvk.fireflyPlaidConnector2.config.properties"])
@SpringBootApplication(scanBasePackageClasses = [FireflyPlaidConnector2Application::class])
class TestApplication