# Stock

재고 시스템으로 알아보는 동시성 이슈 해결 방법

- Spring Web 3.0.2
- Spring Data JPA 3.0.2
- MySQL 8.0.32 (run as docker container)

### MySQL (docker) 환경 설정 및 실행

```sh
docker pull mysql
docker run -d -p 13306:3306 -e MYSQL_ROOT_PASSWORD=1234 --name mysql mysql
docker exec -it mysql bash

mysql -u root -p

mysql> create database stock_example;
mysql> use stock_example;
```

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
statement executed:
update stock
set product_id=?,
    quantity=?,
    version=?
where id = ?
  and version = ?;
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

## Redis Library 를 활용한 Distributed Lock 구현

### Lettuce 활용하는 방법

`SETNX`(set if not exist, 기존 키가 없는 경우 값을 입력하는 방식) 명령어를 활용하여 분산락 구현할 수 있다.
SETNX를 활용하는 방식은 spin lock 방식으로 retry 로직을 개발자가 직접 작성해야 한다.
Lettuce를 활용하여 Lock을 구현하는 방법은 MySQL의 NamedLock과 거의 비슷하지만, Redis를 사용하는 점과 세션관리에 신경쓰지 않아도 되는 점이
NamedLock과 다르다. Lettuce를 활용한 방법은 구현이 간단한 장점이 있지만, spin lock 방식으로 redis에 부하를 줄 수 있기 때문에 Lock 획득 시도
사이에 텀을 줘서 부하를 줄여야 한다.

> spin lock 이란 Lock을 획득하려는 스레드가 Lock을 사용할 수 있는지 지속적으로 확인하여 Lock을 획득하는 방식이다.

### Redis (docker) 환경설정 및 redis-cli 실행

build.gradle 파일에 `implementation 'org.springframework.boot:spring-boot-starter-data-redis'` 의존성 추가

```sh
docker pull redis
docker run --name myredis -d -p 6379:6379 redis
docker exec -it myredis redis-cli

127.0.0.1:6379> SETNX 1 lock
(integer) 1
127.0.0.1:6379> SETNX 1 lock
(integer) 0
127.0.0.1:6379> del 1
(integer) 1
```

### Redisson 활용하는 방법

pub-sub 방식으로 Lock 구현을 제공한다. 보통은 retry 로직을 직접 구현할 필요가 없다
Lettuce의 spin lock 방식과 다르게 매번 시도할 필요 없이 채널 subscribe 후 메세지를 받을 때 Lock 획득을 시도하므로 Redis의 부하를 줄일 수 있다.
하지만 Lettuce에 비해 구현이 조금은 복잡하고 별도의 라이브러리를 사용해야 한다.
Redisson은 Lock 관련 기능을 라이브러리에서 제공하므로 별도의 repository를 생성할 필요가 없다.

> pub-sub 방식이란 채널을 하나 만들고 Lock을 점유중인 스레드가 해제를 할 때 채널에 해제 메세지를 보내고, Lock을 획득하려고 대기중인 스레드는 채널을 구독하고
> 있다가 채널해제 메세지가 왔을 때 Lock을 획득하는 방식이다.

터미널 2개로 pub-sub 방식을 실습

```sh
# 터미널 A
docker exec -it myredis redis-cli

# subscribe (구독)
127.0.0.1:6379> SUBSCRIBE ch1
Reading messages... (press Ctrl-C to quit)
1) "subscribe"
2) "ch1"
3) (integer) 1
```

```sh
# 터미널 B
docker exec -it myredis redis-cli

# publish (발행)
127.0.0.1:6379> PUBLISH ch1 hi
(integer) 1
```

```sh
# 터미널 A
docker exec -it myredis redis-cli

# subscribe (구독)
127.0.0.1:6379> SUBSCRIBE ch1
Reading messages... (press Ctrl-C to quit)
1) "subscribe"
2) "ch1"
3) (integer) 1
1) "message"
2) "ch1"
3) "hi"
```

## 결론

기존 방식으로 처리가 가능하다면 그대로 유지하되, 트래픽이 점차 증가해 Database lock으로는 더이상 처리가 불가능할 정도라면 비용을 감수하고서라도 Redis를 사용할
필요가 있다.

## 실무에서 Lettuce vs Redisson

Lettuce

- 재시도가 필요하지 않은 경우 lettuce를 활용하여 lock을 구현한다.

Redisson

- 재시도가 필요한 경우 redisson 활용하는 방식을 혼용한다.

### 실무에서 MySQL vs Redis

MySQL

- 이미 MySQL을 사용하고 있다면 별도의 비용없이 사용 가능하다
- 어느정도 트래픽까지는 문제없이 활용이 가능하다
- Redis에 비해 성능이 좋지 않다.

Redis

- 활용중인 Redis가 없다면 별도의 구축 비용과 인프라 관리 비용이 추가로 발생한다.
- MySQL에 비해 성능이 좋아 더 많은 요청을 처리할 수 있다.

