# Stock

재고 시스템으로 알아보는 동시성 이슈 해결 방법

- Spring Web 3.0.2
- Spring Data JPA 3.0.2
- MySQL 8.0.32 (run as docker container)

## Synchronized 문제점 (Application Level)

서버가 2대 이상일 때 동일한 문제 발생

> 인스턴스 단위로 thread-safe 가 보장되고, 서버가 여러대면 여러개의 인스턴스가 있는 것과 동일하기 때문

## Lock 활용 (Database Level)

### Pessimistic Lock

트랜잭션 충돌이 발생하는 것을 가정하고 우선 락을 걸어 해결

```sql
select stock0_.id         as id1_0_,
       stock0_.product_id as product_2_0_,
       stock0_.quantity   as quantity3_0_
from stock stock0_
where stock0_.id = ? for
update
```

### Optimistic Lock

명시적인 락 보다 버전 정보를 이용하여 버전 충돌 시 재시도 로직 직접 구현하여 해결

```sql 
update stock
set product_id=?,
    quantity=?,
    version=?
where id = ?
  and version = ?
-- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [1] as [BIGINT] - [1]
-- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [2] as [BIGINT] - [47]
-- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [3] as [BIGINT] - [53]
-- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [4] as [BIGINT] - [1]
-- [pool-1-thread-3] o.h.type.descriptor.sql.BasicBinder      : binding parameter [5] as [BIGINT] - [52]
    Batch
update returned unexpected row count
from
update [0];
actual row count: 0;
expected: 1;
statement executed: update stock set product_id=?, quantity=?, version=? where id=? and version=?;
nested exception is org.hibernate.StaleStateException
  ```

### Named Lock

- 이름을 가진 metadata lock으로 주로 분산 Lock을 구현하는 데 사용된다.
- Pessimistic Lock은 column, record 단위의 Lock인 반면 Named Lock은 metadata 단위의 lock이다.
- Pessimistic Lock에 비해 Timeout을 손쉽게 구현할 수 있다.
- 데이터 삽입 시 정합성 맞추는 데 사용할 수 있다.
- 트랜잭션 종료 시 락이 자동으로 해제되지 않아 별도 명령어로 **직접 해제**하거나 **선점시간이 끝나야 해제**되는 것에 주의해야 한다.
- 여기서는 편의를 위해 jpa nativeQuery를 사용하지만 실무에서는 jdbc를 사용하는 등 datasource 를 분리하여 사용하는 것이 추천된다.
- 만약 같은 datasource 사용 시 connection pool이 부족해지는 현상으로 인해 다른 서비스에 영향을 끼칠 수 있다.

```sql
Hibernate:
select get_lock(?, 3000)
           Hibernate: select s1_0.id, s1_0.product_id,
       s1_0.quantity,
       s1_0.version
from stock s1_0
where s1_0.id = ? Hibernate:
update stock
set product_id=?,
    quantity=?,
    version=?
where id = ?
  and version = ? Hibernate:
select release_lock(?)
```