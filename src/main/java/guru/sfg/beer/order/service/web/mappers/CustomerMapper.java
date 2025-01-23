package guru.sfg.beer.order.service.web.mappers;

import org.mapstruct.Mapper;

import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.brewery.model.CustomerDto;
import org.mapstruct.Mapping;

@Mapper(uses = {DateMapper.class})
public interface CustomerMapper {

	CustomerDto customerToCustomerDto(Customer customer);
	
	@Mapping(target = "beerOrders", ignore = true)
	Customer customerDtoToCustomer(CustomerDto customerDto);
}
