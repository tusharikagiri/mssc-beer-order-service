package guru.sfg.beer.order.service.services;

import java.util.List;

import org.springframework.stereotype.Service;

import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService {
	
	private final CustomerRepository customerRepository;
	
	@Override
	public List<Customer> listCustomers() {
		
		return customerRepository.findAll();
	}

}
