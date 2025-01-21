package guru.sfg.beer.order.service.services;

import java.util.UUID;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.brewery.model.BeerOrderDto;

public interface BeerOrderManager {
	
	BeerOrder newBeerOrder(BeerOrder beerOrder);

	void processValidationResult(UUID beerOrderId, Boolean isValid);
	
	void beerOrderAllocationPassed(BeerOrderDto beerOrderDto);
	
	void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto);
	
	void beerOrderAllocationFailed(BeerOrderDto beerOrderDto);

	void beerOrderPickerUp(UUID beerOrderId);
	
	void cancelBeerOrder(UUID beerOrderId);

}
