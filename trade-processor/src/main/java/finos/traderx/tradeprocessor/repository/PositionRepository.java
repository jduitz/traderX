package finos.traderx.tradeprocessor.repository;

import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.PositionID;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, PositionID> {
  List<Position> findByAccountId(Integer id);
  Position findByAccountIdAndSecurity(Integer id, String security);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Position p where p.accountId = :accountId and p.security = :security")
  Optional<Position> findForUpdate(
      @Param("accountId") Integer accountId,
      @Param("security") String security);
}
