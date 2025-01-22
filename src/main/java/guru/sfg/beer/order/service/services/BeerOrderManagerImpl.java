package guru.sfg.beer.order.service.services;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
	
	private void awaitStatus(UUID beerOrderId, BeerOrderStatusEnum statusEnum) {
		AtomicBoolean found = new AtomicBoolean(false);
		AtomicInteger loopCount = new AtomicInteger(0);
		
		while(!found.get()) {
			
			if(loopCount.incrementAndGet() > 10) {
				found.set(true);
				log.debug("Loop retries exceeded");
			}
			
			beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
				if(beerOrder.getOrderStatus().equals(statusEnum)) {
					found.set(true);
					log.debug("Order Found");
				} else {
					log.debug("Order status not equal. Expected: " + statusEnum.name() + " Found: " + beerOrder.getOrderStatus().name());
				}
			}, () -> {
				log.error("Order not found with id :" + beerOrderId);
			});
			
			if(!found.get()) {
				log.debug("Sleeping for retry");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	@Transactional
	@Override
	public void processValidationResult(UUID beerOrderId, Boolean isValid) {
		log.info("processValidationResult for beerOrderId "+ beerOrderId + " isValid? " + isValid);
		Optional<BeerOrder> savedBeerOrder = beerOrderRepository.findById(beerOrderId);
		
		if(savedBeerOrder.isEmpty()) {
			log.error("Order not found with id :" + beerOrderId);
			return;
		}

		if (isValid) {
			sendBeerOrderEvent(savedBeerOrder.get(), BeerOrderEventEnum.VALIDATION_PASSED);
			
			//wait for status change
			awaitStatus(beerOrderId, BeerOrderStatusEnum.VALIDATED);
			
			savedBeerOrder = beerOrderRepository.findById(beerOrderId);
			
			sendBeerOrderEvent(savedBeerOrder.get(), BeerOrderEventEnum.ALLOCATE_ORDER);
		} else {
			sendBeerOrderEvent(savedBeerOrder.get(), BeerOrderEventEnum.VALIDATION_FAILED);
		}

	}

	@Override
	public void beerOrderAllocationPassed(BeerOrderDto beerOrderDto) {
		beerOrderRepository.findById(beerOrderDto.getId()).ifPresentOrElse(beerOrder -> {

			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_SUCCESS);
			awaitStatus(beerOrderDto.getId(), BeerOrderStatusEnum.ALLOCATED);
			updateAllocatedQty(beerOrderDto, beerOrder);
		}, () -> {
			log.error("Order not found with id :" + beerOrderDto.getId());
		});
	}

	private void updateAllocatedQty(BeerOrderDto beerOrderDto, BeerOrder beerOrder) {
		Optional<BeerOrder> allocatedOrder = beerOrderRepository.findById(beerOrderDto.getId());

		if (allocatedOrder.isEmpty()) {
			log.error("Order not found with id :" + beerOrderDto.getId());
			return;
		}

		allocatedOrder.get().getBeerOrderLines().forEach(beerOrderLine -> {
			beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
				if (beerOrderLine.getId().equals(beerOrderLineDto.getId())) {
					beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
				}
			});
		});

		beerOrderRepository.saveAndFlush(allocatedOrder.get());
	}

	@Override
	public void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto) {
		beerOrderRepository.findById(beerOrderDto.getId()).ifPresentOrElse(beerOrder -> {

			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
			awaitStatus(beerOrderDto.getId(), BeerOrderStatusEnum.PENDING_INVENTORY);
			updateAllocatedQty(beerOrderDto, beerOrder);
		}, () -> {
			log.error("Order not found with id :" + beerOrderDto.getId());
		});
	}

	@Override
	public void beerOrderAllocationFailed(BeerOrderDto beerOrderDto) {
		beerOrderRepository.findById(beerOrderDto.getId()).ifPresentOrElse(beerOrder -> {
			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_FAILED);
		}, () -> {
			log.error("Order not found with id :" + beerOrderDto.getId());
		});
		
	}

	@Override
	public void beerOrderPickerUp(UUID beerOrderId) {
		beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.BEERORDER_PICKED_UP);
		}, () ->{
			log.error("Order not found!" + beerOrderId.toString());
		});
		
	}

	@Override
	public void cancelBeerOrder(UUID beerOrderId) {		
		beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
			sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.CANCEL_ORDER);
		}, () -> log.error("Beer Order Id not found : " + beerOrderId.toString()));
	}

}
