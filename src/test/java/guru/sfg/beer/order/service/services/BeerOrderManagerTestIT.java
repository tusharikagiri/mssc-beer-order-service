package guru.sfg.beer.order.service.services;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.events.AllocationFailureEvent;

@ActiveProfiles("test")
//Do not need the SpringBootTest annotation for this test. WireMock will handle the server configuration and lifecycle.
@SpringBootTest
@EnableWireMock({
	  @ConfigureWireMock(
		baseUrlProperties = "http://localhost:",
				portProperties = "8083")
})
public class BeerOrderManagerTestIT {

	@InjectWireMock
	  WireMockServer wireMockServer;

	@Autowired
	BeerOrderManager beerOrderManager;

	@Autowired
	BeerOrderRepository beerOrderRepository;

	@Autowired
	CustomerRepository customerRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	BeerOrderMapper beerOrderMapper;
	
	@Autowired
	JmsTemplate jmsTemplate;

	Customer testCustomer;

	UUID beerId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		testCustomer = Customer.builder().customerName("Tusharika").build();
		customerRepository.save(testCustomer);
		// Configure and start WireMock with Jetty
		//wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8083));
		//wireMockServer.start();
	}

	@AfterEach
	void tearDown() {
		/*
		 * if (wireMockServer != null) { wireMockServer.stop(); }
		 */
	}

	public BeerOrder createBeerOrder() {
		BeerOrder beerOrder = BeerOrder.builder().customer(testCustomer).build();

		Set<BeerOrderLine> lines = new HashSet<>();
		lines.add(BeerOrderLine.builder().beerId(beerId).orderQuantity(1).beerOrder(beerOrder).upc("12345").build());

		beerOrder.setBeerOrderLines(lines);

		return beerOrder;
	}

	@Test
	void testWorking() {

		UUID customerId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();

		// You can use the DSL directly from the extension field
		wireMockServer.stubFor(get("/api/v1/customers/" + customerId + "/orders/" + orderId).willReturn(ok()));

		wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v1/customers/" + customerId + "/orders/" + orderId)));

	}

	@Test
	public void helloWorld() {

		stubFor(get(urlEqualTo("/helloworld")).willReturn(
				aResponse().withHeader("Content-Type", "text/plain").withStatus(200).withBody("Hello world!")));
	}

	@Test
	void testNewToAllocated() {

		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);
		
		wireMockServer.verify(1, getRequestedFor(urlEqualTo(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")));

		/*
		 * await().untilAsserted(() -> { BeerOrder foundBeerOrder =
		 * beerOrderRepository.findById(beerOrder.getId()).get();
		 * 
		 * assertEquals(BeerOrderStatusEnum.ALLOCATED, foundBeerOrder.getOrderStatus());
		 * });
		 * 
		 * savedBeerOrder = beerOrderRepository.findById(savedBeerOrder.getId()).get();
		 * 
		 * 
		 * 
		 * assertNotNull(savedBeerOrder); assertEquals(BeerOrderStatusEnum.ALLOCATED,
		 * savedBeerOrder.getOrderStatus());
		 */
	}
	
	@Test
	void testFailedValidation() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
		
		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("fail-validation");

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.VAILDATION_EXCEPTION, foundBeerOrder.getOrderStatus());
		});		
	}
	
	@Test
	void testAllocationFailure() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("fail-allocation");
		
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.AlLOCATION_EXCEPTION, foundBeerOrder.getOrderStatus());
		});
		
		AllocationFailureEvent failureEvent = (AllocationFailureEvent) jmsTemplate.receiveAndConvert(JmsConfig.ALLOCATION_FAILURE_QUEUE);
		assertNotNull(failureEvent);
		assertThat(failureEvent.getOrderId()).isEqualTo(savedBeerOrder.getId());
	}
	
	@Test
	void testPartialAllocation() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("partial-allocation");

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.PENDING_INVENTORY, foundBeerOrder.getOrderStatus());
		});
	}

}
