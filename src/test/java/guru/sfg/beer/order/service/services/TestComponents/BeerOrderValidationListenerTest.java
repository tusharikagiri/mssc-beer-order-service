package guru.sfg.beer.order.service.services.TestComponents;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.ValidateOrderRequest;
import guru.sfg.brewery.model.events.ValidateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderValidationListenerTest {

	private final JmsTemplate jmsTemplate;

	@JmsListener(destination = JmsConfig.VALIDATE_ORDER_QUEUE)
	public void list(Message<ValidateOrderRequest> msg) {
		boolean isValid = true;
		boolean sendResponse = true;

		ValidateOrderRequest request = (ValidateOrderRequest) msg.getPayload();
		
		String customerRef = request.getBeerOrder().getCustomerRef();
		// condition to fail validation.
		if (customerRef != null) {
			if (customerRef.equals("fail-validation")) {
				isValid = false;
			} else if (customerRef.equals("dont-validate")) {
				sendResponse = false;
			}
		}
		
		
		if(sendResponse) {
			jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_RESPONSE_QUEUE,
				ValidateOrderResult.builder()
				.isValid(isValid)
				.beerOrderId(request.getBeerOrder().getId())
				.build());
		}

	}
}
