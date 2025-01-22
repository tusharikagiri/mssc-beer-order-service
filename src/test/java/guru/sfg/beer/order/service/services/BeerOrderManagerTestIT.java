package guru.sfg.beer.order.service.services;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.events.AllocationFailureEvent;
import guru.sfg.brewery.model.events.DeallocateOrderRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest
//Add the property from where the service URL is being read.
@EnableWireMock({ @ConfigureWireMock(baseUrlProperties = "sfg.brewery.beer-service-host") })
 
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
	JmsTemplate jmsTemplate;

	Customer testCustomer;

	UUID beerId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		testCustomer = Customer.builder().customerName("Tusharika").build();
		customerRepository.save(testCustomer);
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
	void testNewToAllocated() {

		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
		log.info("URL = " + wireMockServer.baseUrl());
		log.info("PORT = " + wireMockServer.port());
		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		wireMockServer.verify(1, getRequestedFor(urlEqualTo(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")));

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertNotNull(savedBeerOrder);
			assertEquals(BeerOrderStatusEnum.ALLOCATED, foundBeerOrder.getOrderStatus());
		});
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
	
	@Test
	void testValidationPendingToCancel() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("dont-validate");
		
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.VALIDATION_PENDING, foundBeerOrder.getOrderStatus());
		});
		
		beerOrderManager.cancelBeerOrder(savedBeerOrder.getId());
		
		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.CANCELLED, foundBeerOrder.getOrderStatus());
		});
	}
	
	@Test
	void testAllocationPendingToCancel() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("dont-allocate");
		
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.ALLOCATE_PENDING, foundBeerOrder.getOrderStatus());
		});
		
		beerOrderManager.cancelBeerOrder(savedBeerOrder.getId());
		
		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.CANCELLED, foundBeerOrder.getOrderStatus());
		});
	}

	@Test
	void testAllocatedToCancel() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
					.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		BeerOrder beerOrder = createBeerOrder();
		
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.ALLOCATED, foundBeerOrder.getOrderStatus());
		});
		
		beerOrderManager.cancelBeerOrder(savedBeerOrder.getId());
		
		await().untilAsserted(() -> {
			BeerOrder foundBeerOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			assertEquals(BeerOrderStatusEnum.CANCELLED, foundBeerOrder.getOrderStatus());
		});
		
		DeallocateOrderRequest cancelEvent = (DeallocateOrderRequest) jmsTemplate.receiveAndConvert(JmsConfig.DEALLOCATE_ORDER_QUEUE);
		assertNotNull(cancelEvent);
		assertThat(cancelEvent.getBeerOrderDto().getId()).isEqualTo(savedBeerOrder.getId());
	}
	
	@Test
	void testNewToPickedUp() {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

        try {
			wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345")
			        .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        BeerOrder beerOrder = createBeerOrder();

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
        });

        beerOrderManager.beerOrderPickerUp(savedBeerOrder.getId());

        await().untilAsserted(() -> {
            BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.PICKED_UP, foundOrder.getOrderStatus());
        });

        BeerOrder pickedUpOrder = beerOrderRepository.findById(savedBeerOrder.getId()).get();

        assertEquals(BeerOrderStatusEnum.PICKED_UP, pickedUpOrder.getOrderStatus());
	}

}
