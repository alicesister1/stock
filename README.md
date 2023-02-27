# Stock

재고 시스템으로 알아보는 동시성 이슈 해결 방법

- Spring Web
- Spring Data JPA
- MySQL (run as docker container)

## Synchronized 문제점

서버가 2대 이상일 때 동일한 문제 발생

> 인스턴스 단위로 thread-safe 가 보장되고, 서버가 여러대면 여러개의 인스턴스가 있는 것과 동일하기 때문 