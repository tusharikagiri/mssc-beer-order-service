package guru.sfg.beer.order.service.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStatusChangeInterceptor;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.events.ValidateOrderResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class BeerOrderManagerImpl implements BeerOrderManager {

	public static final String ORDER_ID_HEADER = "ORDER_ID_HEADER";
	private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> factory;
	private final BeerOrderRepository beerOrderRepository;
	private final BeerOrderStatusChangeInterceptor beerOrderStatusChangeInterceptor;

	@Override
	public BeerOrder newBeerOrder(BeerOrder beerOrder) {
		beerOrder.setId(null);
		beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

		BeerOrder savedBeerOrder = beerOrderRepository.save(beerOrder);
		sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATE_ORDER);

		return savedBeerOrder;
	}

	private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum event) {
		StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);

		Message<BeerOrderEventEnum> msg = MessageBuilder.withPayload(event).setHeader(ORDER_ID_HEADER,
				beerOrder.getId().toString()).build();

		sm.sendEvent(msg);
	}

	private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
		StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = factory.getStateMachine(beerOrder.getId());

		sm.stop();

		sm.getStateMachineAccessor().doWithAllRegions(sma -> {
			sma.addStateMachineInterceptor(beerOrderStatusChangeInterceptor);
			sma.resetStateMachine(new DefaultStateMachineContext<BeerOrderStatusEnum, BeerOrderEventEnum>(
					beerOrder.getOrderStatus(), null, null, null));
		});
		sm.start();
		return sm;
	}

	@Transactional
	@Override
	public void processValidationResult(UUID beerOrderId, Boolean isValid) {
		BeerOrder savedBeerOrder = beerOrderRepository.findOneById(beerOrderId);

		if (isValid) {
			sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATION_PASSED);
			
			savedBeerOrder = beerOrderRepository.findOneById(beerOrderId);
			
			sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.ALLOCATE_ORDER);
		} else {
			sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATION_FAILED);
		}

	}

	@Override
	public void beerOrderAllocationPassed(BeerOrderDto beerOrderDto) {
		BeerOrder beerOrder = beerOrderRepository.findOneById(beerOrderDto.getId());
		sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_SUCCESS);
		
		updateAllocatedQty(beerOrderDto, beerOrder);
	}

	private void updateAllocatedQty(BeerOrderDto beerOrderDto, BeerOrder beerOrder) {
		BeerOrder allocatedOrder = beerOrderRepository.findOneById(beerOrderDto.getId());
		
		allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> {
			beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
				if(beerOrderLine.getId().equals(beerOrderLineDto.getId())) {
					beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
				}
			});
		});
		
		beerOrderRepository.saveAndFlush(allocatedOrder);
	}

	@Override
	public void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto) {
		BeerOrder beerOrder = beerOrderRepository.findOneById(beerOrderDto.getId());
		sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
		
		updateAllocatedQty(beerOrderDto, beerOrder);
	}

	@Override
	public void beerOrderAllocationFailed(BeerOrderDto beerOrderDto) {
		BeerOrder beerOrder = beerOrderRepository.findOneById(beerOrderDto.getId());
		sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_FAILED);
	}

	@Override
	public void beerOrderPickerUp(UUID beerOrderId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void cancelBeerOrder(UUID beerOrderId) {		
		beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.CANCEL_ORDER);
		}, () -> log.error("Beer Order Id not found : " + beerOrderId.toString()));
	}

}
