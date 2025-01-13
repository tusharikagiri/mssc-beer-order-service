package guru.sfg.beer.order.service.services;

import java.util.Optional;
import java.util.UUID;

import guru.springframework.msscbeerservice.web.model.BeerDto;

public interface BeerService {

	Optional<BeerDto> getBeerById(UUID beerId); 
	
	Optional<BeerDto> getBeerByUpc(String upc);
}
