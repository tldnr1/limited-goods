package io.github.tldnr1.limitedgoods.event.domain;

import java.util.List;

import org.springframework.data.repository.Repository;

public interface ItemRepository extends Repository<Item, Long> {

	List<Item> findByEventId(Long eventId);

}
