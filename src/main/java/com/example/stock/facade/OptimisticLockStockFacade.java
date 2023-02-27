package com.example.stock.facade;

import com.example.stock.service.OptimisticLockStockService;
import org.springframework.stereotype.Service;

@Service
public class OptimisticLockStockFacade {

  private final OptimisticLockStockService optimisticLockStockService;

  public OptimisticLockStockFacade(OptimisticLockStockService optimisticLockStockService) {
    this.optimisticLockStockService = optimisticLockStockService;
  }

  public void decrease(Long id, Long quantity) throws InterruptedException {
    while (true) {
      try {
        optimisticLockStockService.decrease(id, quantity);

        break;
      } catch (Exception e) {
        System.out.println("[Facade]" + e.getLocalizedMessage());
        Thread.sleep(50);
      }
    }
  }
}
