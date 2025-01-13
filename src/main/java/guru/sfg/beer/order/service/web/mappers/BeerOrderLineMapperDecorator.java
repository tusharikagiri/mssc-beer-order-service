package guru.sfg.beer.order.service.web.mappers;

import java.util.Optional;

import org.mapstruct.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;

import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.services.BeerService;
import guru.sfg.beer.order.service.web.model.BeerOrderLineDto;
import guru.springframework.msscbeerservice.web.model.BeerDto;

public class BeerOrderLineMapperDecorator implements BeerOrderLineMapper {
	
	private BeerService beerService;
    private BeerOrderLineMapper beerOrderLineMapper;
    
    @Autowired
    public void setBeerService(BeerService beerService) {
        this.beerService = beerService;
    }

    @Autowired
    public void setBeerOrderLineMapper(BeerOrderLineMapper beerOrderLineMapper) {
        this.beerOrderLineMapper = beerOrderLineMapper;
    }

	@Override
	public BeerOrderLineDto beerOrderLineToDto(BeerOrderLine line) {
		BeerOrderLineDto orderLineDto = beerOrderLineMapper.beerOrderLineToDto(line);
        Optional<BeerDto> beerDtoOptional = beerService.getBeerByUpc(line.getUpc());

        beerDtoOptional.ifPresent(beerDto -> {
            orderLineDto.setBeerName(beerDto.getBeerName());
            orderLineDto.setBeerStyle(beerDto.getBeerStyle().name());
            orderLineDto.setPrice(beerDto.getPrice());
            orderLineDto.setBeerId(beerDto.getId());
        });

        return orderLineDto;
	}

	@Override
	public BeerOrderLine dtoToBeerOrderLine(BeerOrderLineDto dto) {
		// TODO Auto-generated method stub
		return null;
	}

}
