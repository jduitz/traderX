package finos.traderx.ordermatcher.repository;

import finos.traderx.ordermatcher.model.OrderRecord;
import finos.traderx.ordermatcher.model.OrderSide;
import finos.traderx.ordermatcher.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderRecord, String> {
    List<OrderRecord> findAllByOrderByUpdatedAtDesc();

    long countByStatusIn(Collection<OrderStatus> statuses);

    long countByStatusInAndRemainingQuantityGreaterThan(Collection<OrderStatus> statuses, Integer remainingQuantity);

    long countByStatusInAndSide(Collection<OrderStatus> statuses, OrderSide side);

    long countByStatus(OrderStatus status);

    @Query("select o.orderId from OrderRecord o")
    List<String> findAllOrderIds();

    List<OrderRecord> findAllByPendingTradeIdIsNotNullOrderByUpdatedAtAsc();

    @Query("""
        select coalesce(sum(o.remainingQuantity), 0)
        from OrderRecord o
        where o.accountId = :accountId
          and o.security = :security
          and o.side = finos.traderx.ordermatcher.model.OrderSide.Sell
          and o.status in (finos.traderx.ordermatcher.model.OrderStatus.NEW,
                           finos.traderx.ordermatcher.model.OrderStatus.PARTIALLY_FILLED)
        """)
    Long sumOpenTreasurySellReservations(
        @Param("accountId") Integer accountId,
        @Param("security") String security);
}
