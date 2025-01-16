package guru.sfg.beer.order.service.services.listeners;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.beer.order.service.services.BeerOrderManagerImpl;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationResultListener {

	private final BeerOrderManager beerOrderManager;

	@JmsListener(destination = JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE)
	public void listen(AllocateOrderResult result) {
		BeerOrderDto beerOrderDto = result.getBeerOrderDto();

		if (!result.getAllocationError() && !result.getPendingInventory()) {
			beerOrderManager.beerOrderAllocationPassed(beerOrderDto);
		} else if (!result.getAllocationError() && result.getPendingInventory()) {
			beerOrderManager.beerOrderAllocationPendingInventory(beerOrderDto);
		} else if (result.getAllocationError()) {
			beerOrderManager.beerOrderAllocationFailed(beerOrderDto);
		}
	}
}
