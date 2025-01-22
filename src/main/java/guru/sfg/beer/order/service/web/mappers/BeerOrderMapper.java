/*
 *  Copyright 2019 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package guru.sfg.beer.order.service.web.mappers;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.brewery.model.BeerOrderDto;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(uses = {DateMapper.class, BeerOrderLineMapper.class})
public interface BeerOrderMapper {

	@Mapping(source = "customer.id", target = "customerId", qualifiedByName = "uuidToString")
    BeerOrderDto beerOrderToDto(BeerOrder beerOrder);

	@Mapping(source = "customerId", target = "customer.id", qualifiedByName = "stringToUuid")
    BeerOrder dtoToBeerOrder(BeerOrderDto dto);
	
	@Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("stringToUuid")
    default UUID stringToUuid(String id) {
        return id != null ? UUID.fromString(id) : null;
    }
}
