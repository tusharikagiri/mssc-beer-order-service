package guru.sfg.beer.order.service.sm;

import java.util.Optional;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManagerImpl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class BeerOrderStatusChangeInterceptor extends StateMachineInterceptorAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {

	private final BeerOrderRepository beerOrderRepository;
	
	@Transactional
	@Override
	public void preStateChange(State<BeerOrderStatusEnum, BeerOrderEventEnum> state,
			Message<BeerOrderEventEnum> message, Transition<BeerOrderStatusEnum, BeerOrderEventEnum> transition,
			StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine) {
		
		Optional.ofNullable(message)
		.flatMap(msg -> Optional.ofNullable((String)msg.getHeaders().getOrDefault(BeerOrderManagerImpl.ORDER_ID_HEADER, "")))
		.ifPresent(orderId -> {
			Optional<BeerOrder> beerOrderRepoOptional = beerOrderRepository.findById(UUID.fromString(orderId));
			beerOrderRepoOptional.ifPresent(beerOrder -> {
				beerOrder.setOrderStatus(state.getId());
				beerOrderRepository.saveAndFlush(beerOrder);
				System.out.println("Beer Order Saved Status : " + state.getId() + "for order id: " + orderId);
			});
		});
		
	}
}
