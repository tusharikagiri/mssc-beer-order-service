package guru.sfg.beer.order.service.services.TestComponents;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderAllocationListener {
	
	private final JmsTemplate jmsTemplate;

	@JmsListener(destination = JmsConfig.ALLOCATE_ORDER_REQUEST_QUEUE)
	public void listen(Message<AllocateOrderRequest> msg) {
		
		AllocateOrderRequest request = msg.getPayload();
		
		boolean pendingInventory = false;		
		boolean allocationError = false;
		boolean sendResponse = true;
		
		String customerRef = request.getBeerOrderDto().getCustomerRef();
		
		if (customerRef != null) {
			if (customerRef.equals("partial-allocation")) {
				pendingInventory = true;
			} else if (customerRef.equals("fail-allocation")) {
				allocationError = true;
			} else if (customerRef.equals("dont-allocate")) {
				sendResponse = false;
			}
		}

		boolean finalPending = pendingInventory;
		request.getBeerOrderDto().getBeerOrderLines().forEach(beerOrderLineDto -> {
			if (finalPending) {
				beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity() - 1);
			} else {
				beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
			}
		});
		
		if(sendResponse) {
			jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE, AllocateOrderResult.builder()
					.beerOrderDto(request.getBeerOrderDto())
					.allocationError(allocationError)
					.pendingInventory(pendingInventory)
					.build());
		}	
		
	}
}
