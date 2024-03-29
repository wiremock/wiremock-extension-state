import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.extension.Parameters
import org.junit.jupiter.api.Test
import org.wiremock.extensions.state.extensions.Dsl.deleteContext
import org.wiremock.extensions.state.extensions.Dsl.recordContext
import org.wiremock.extensions.state.functionality.AbstractTestBase

class KotlinDslTest : AbstractTestBase() {
    @Test
    fun `delete state`() {
        wm.stubFor(
            get(urlPathMatching("/state/[^/]+"))
                .willReturn(
                    WireMock.ok()
                        .withHeader("content-type", "application/json")
                        .withBody("{}")
                )
                .withServeEventListener(deleteContext("something").list().last().build())
                .withServeEventListener(recordContext("something").list().addLast(Parameters()).build())
        )
    }
}