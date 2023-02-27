# Stock

재고 시스템으로 알아보는 동시성 이슈 해결 방법

- Spring Web
- Spring Data JPA
- MySQL (run as docker container)

## Synchronized 문제점 (Application Level)

서버가 2대 이상일 때 동일한 문제 발생

> 인스턴스 단위로 thread-safe 가 보장되고, 서버가 여러대면 여러개의 인스턴스가 있는 것과 동일하기 때문

## Lock 활용 (Database Level)

- Pessimistic Lock
  - 트랜잭션 충돌이 발생하는 것을 가정하고 우선 락을 걸어 해결
    ```sql
    select stock0_.id         as id1_0_,
           stock0_.product_id as product_2_0_,
           stock0_.quantity   as quantity3_0_
    from stock stock0_
    where stock0_.id = ? for
    update
    ```

- Optimistic Lock
  - 명시적인 락 보다 버전 정보를 이용하여 버전 충돌 시 재시도 로직 직접 구현하여 해결
    ```sql 
    update stock set product_id=?, quantity=?, version=? where id=? and version=?
    -- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [1] as [BIGINT] - [1]
    -- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [2] as [BIGINT] - [47]
    -- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [3] as [BIGINT] - [53]
    -- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [4] as [BIGINT] - [1]
    -- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [5] as [BIGINT] - [52]
    Batch update returned unexpected row count from update [0]; actual row count: 0; expected: 1; statement executed: update stock set product_id=?, quantity=?, version=? where id=? and version=?; nested exception is org.hibernate.StaleStateException
    ```

- Named Lock