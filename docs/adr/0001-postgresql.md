# ADR-0001. DB로 PostgreSQL을 쓴다

- 상태: 채택

## 맥락

수량 확보(목표 A)는 DB 1개에서 동시 요청의 정합성을 지킨다. 조건부 갱신, 행 잠금, 잠긴 행을 건너뛰는 조회(`SKIP LOCKED`)가 필요하다.

## 결정

PostgreSQL을 쓴다. 두 후보 모두 위 요구를 만족하며, 기술 우열이 아니라 선호로 골랐다.

## 고르지 않은 대안

- MySQL(InnoDB): 위 요구를 만족한다.

## 결과

- 기본 격리 수준은 READ COMMITTED이고, `FOR UPDATE`는 가져온 행만 잠그며 간격 잠금이 없다. 1인 한도처럼 합계를 세고 새 행을 넣는 검사는 동시 삽입을 막는 방법을 스키마 설계에서 따로 정한다.
- 재처리 대상은 `FOR UPDATE SKIP LOCKED`로 나눠 가져온다([ADR-0002](0002-db-polling-scheduler.md)).
