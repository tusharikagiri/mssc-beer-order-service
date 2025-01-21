package guru.sfg.beer.order.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.Destination;

@Configuration
public class JmsConfig {
	
	public static final String VALIDATE_ORDER_QUEUE = "validate-order";
	public static final String VALIDATE_ORDER_RESPONSE_QUEUE = "validate-order-response";
	public static final String ALLOCATE_ORDER_REQUEST_QUEUE = "allocate-order-request";
	public static final String ALLOCATE_ORDER_RESULT_QUEUE = "allocate-order-result";
	public static final String ALLOCATION_FAILURE_QUEUE = "allocation-failure";
	public static final String DEALLOCATE_ORDER_QUEUE = "deallocate-order";
	
	@Bean
	public MessageConverter messageConverter(ObjectMapper objectMapper) {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();		
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		converter.setObjectMapper(objectMapper);
		return converter;
	}
}
