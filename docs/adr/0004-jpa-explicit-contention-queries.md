# ADR-0004. DB 접근은 JPA로 하고, 경합 경로는 조건부 갱신 쿼리로 쓴다

- 상태: 채택

## 맥락

수량 정합성(A)은 수량의 조건부 갱신, 1인 한도 검사, 상태 전이, 재처리 대상 조회(`SKIP LOCKED`) 같은 몇 개의 쿼리에서 지켜진다. 이 쿼리들은 어떤 기술을 쓰든 SQL이 드러나야 잠금과 결과를 분석할 수 있다. 나머지는 단순한 읽기와 쓰기다.

## 결정

Spring Data JPA를 쓰고 엔티티를 도메인 모델로 쓴다. 수량과 상태를 바꾸는 경합 경로는 조건부 갱신 쿼리로 쓰고 영향받은 행 수로 성공을 판단하며, 엔티티 변경 감지에 맡기지 않는다.

## 고르지 않은 대안

- JdbcClient만 사용: 모든 SQL이 드러나 분석이 가장 쉽지만, 단순한 읽기와 쓰기의 매핑을 직접 써야 한다.
- jOOQ, MyBatis: 의존성이 늘고, 위 두 방식보다 나은 점이 이 범위에서는 작다.

## 결과

- 경합 경로의 SQL은 쿼리 정의에 그대로 보인다.
- 조건부 갱신은 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서 갱신 뒤의 값을 쓰려면 컨텍스트를 비우거나 그 엔티티를 다시 읽어야 한다. 비우면 반영하지 않은 변경이 버려지므로([Spring Data JPA: Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html), [`EntityManager.clear()`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager)), 그 전에 미반영 변경이 없음을 트랜잭션 흐름에서 보장한다. 규칙은 [설계](../04-design.md#db-접근)에 있다.
