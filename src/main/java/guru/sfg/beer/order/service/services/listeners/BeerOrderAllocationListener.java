package guru.sfg.beer.order.service.services.listeners;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {
	
	private final JmsTemplate jmsTemplate;

	@JmsListener(destination = JmsConfig.ALLOCATE_ORDER_REQUEST_QUEUE)
	public void listen(Message<AllocateOrderRequest> msg) {

		AllocateOrderRequest request = msg.getPayload();

		request.getBeerOrderDto().getBeerOrderLines().forEach(beerOrderLineDto -> {
			beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
		});

		jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE, AllocateOrderResult.builder()
				.beerOrderDto(request.getBeerOrderDto()).allocationError(false).pendingInventory(false).build());
	}
}
