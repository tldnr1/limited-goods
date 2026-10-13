package io.github.tldnr1.limitedgoods.stock.domain;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends Repository<ItemStock, Long> {

	/**
	 * 남은 수량이 충분할 때만 차감한다. 0을 돌려주면 품절이다. 커밋할 때까지 그 품목 행을 잠근다.
	 */
	@Modifying
	@Query(value = """
			update item_stock
			set remaining = remaining - :quantity
			where item_id = :itemId
				and remaining >= :quantity
			""", nativeQuery = true)
	int take(@Param("itemId") long itemId, @Param("quantity") int quantity);

	@Modifying
	@Query(value = """
			update item_stock
			set remaining = remaining + :quantity
			where item_id = :itemId
			""", nativeQuery = true)
	int giveBack(@Param("itemId") long itemId, @Param("quantity") int quantity);

}
