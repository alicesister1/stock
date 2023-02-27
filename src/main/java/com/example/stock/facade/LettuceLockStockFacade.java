package com.example.stock.facade;

import com.example.stock.repository.RedisRepository;
import com.example.stock.service.StockService;
import org.springframework.stereotype.Component;

@Component
public class LettuceLockStockFacade {

  private final RedisRepository redisRepository;
  private final StockService stockService;

  public LettuceLockStockFacade(RedisRepository redisRepository, StockService stockService) {
    this.redisRepository = redisRepository;
    this.stockService = stockService;
  }

  public void decrease(Long id, Long quantity) throws InterruptedException {
    while (!redisRepository.lock(id)) {
      Thread.sleep(100);
    }

    try {
      stockService.decrease(id, quantity);
    } finally {
      redisRepository.unlock(id);
    }
  }
}
