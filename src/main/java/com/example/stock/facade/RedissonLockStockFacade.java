package com.example.stock.facade;

import com.example.stock.service.StockService;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class RedissonLockStockFacade {

  private final RedissonClient redissonClient;
  private final StockService stockService;

  public RedissonLockStockFacade(RedissonClient redissonClient, StockService stockService) {
    this.redissonClient = redissonClient;
    this.stockService = stockService;
  }

  public void decrease(Long id, Long quantity) {
    RLock lock = redissonClient.getLock(id.toString());

    try {
      boolean available = lock.tryLock(5, 1, TimeUnit.SECONDS);

      if (!available) {
        System.out.println("Lock is already set");
        return;
      }

      stockService.decrease(id, quantity);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }
  }
}
