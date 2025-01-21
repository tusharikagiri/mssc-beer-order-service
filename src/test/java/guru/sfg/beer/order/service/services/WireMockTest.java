package guru.sfg.beer.order.service.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest
@EnableWireMock
public class WireMockTest {

	@InjectWireMock
	  WireMockServer wireMockServer;

		/*
		 * @BeforeEach void setUp() { // Configure and start WireMock with Jetty
		 * wireMockServer = new
		 * WireMockServer(WireMockConfiguration.wireMockConfig().port(8080));
		 * wireMockServer.start(); }
		 * 
		 * @AfterEach void tearDown() { if (wireMockServer != null) {
		 * wireMockServer.stop(); } }
		 */

    @Test
    void testMockServer() {
        // Stubbing
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/customers/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"id\": \"123\", \"name\": \"John Doe\"}")
                        .withHeader("Content-Type", "application/json")));

        // Make a request using RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject("http://localhost:8080/api/v1/customers/123", String.class);

        // Assertions
        assert response.contains("John Doe");
    }
}
