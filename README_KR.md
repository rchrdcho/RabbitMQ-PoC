# RabbitMQ 기반 이벤트형 MSA (PoC)

## Exchange 및 Queue 전략

### 1. 서비스별 전용 Exchange + 서비스 전용 라우팅 prefix

- 무엇: 각 마이크로서비스가 자신의 exchange(예: serviceA.events, serviceB.events)에 발행하고, 일관된 prefix(serviceA.*)를 사용합니다. 해당 서비스를 소비하는 쪽이 그
  exchange에 바인딩합니다.
- 장점: 서비스 단위 격리가 강하며(정책/권한 분리), 이름이 예측 가능하고, 서비스 제거가 쉽습니다. 오작동 시 영향 범위가 줄어듭니다.
- 단점: 관리해야 할 exchange/binding이 늘어나며, 교차 서비스 라우팅 시 추가 binding 또는 퍼블리셔가 여러 exchange를 알아야 합니다.

### 2. 중앙 공유 Exchange + 네임스페이스 라우팅 키

- 무엇: 모든 서비스가 하나의 exchange(예: app.events)를 사용하고, 라우팅 키를 네임스페이스(serviceA.*, serviceB.*)로 구분합니다. 소비자는 자신의 네임스페이스 패턴으로 공유
  exchange에 바인딩합니다.
- 장점: 운영이 가장 단순(하나의 exchange)하며 소비자를 추가하기 쉽고 broker 객체가 최소화됩니다.
- 단점: 격리가 약해 서비스별 정책/권한, DLX/TTL 튜닝이 어려워집니다. 규칙이 흐트러지면 라우팅 키 충돌과 소음이 생길 수 있습니다.

### 3. 서비스별 Exchange + Exchange 간(“브리지”) 바인딩

- 무엇: 각 서비스가 자신의 exchange를 가지되, exchange끼리(또는 허브 exchange로) 바인딩하여 퍼블리셔가 모든 대상 서비스를 알 필요 없이 메시지를 팬아웃할 수 있습니다.
- 장점: 서비스 격리를 유지하면서도 교차 서비스 팬아웃을 지원합니다. 퍼블리셔는 단순히 자신의 exchange에만 발행하고, 라우팅 로직은 바인딩으로 중앙 관리할 수 있습니다.
- 단점: 토폴로지가 복잡해져 이해가 어려울 수 있으며, 브리지 오구성으로 루프나 예상치 못한 팬아웃이 생길 수 있습니다. 바인딩 관리/디버깅 운영 비용이 늘어납니다.

## 메시징 신뢰성 패턴

### RabbitMQ 내장 신뢰성 레버

- 내구성 토폴로지 + 지속성 메시지: exchange/queue를 durable로 선언하고 `deliveryMode=PERSISTENT`를 사용합니다.
- 퍼블리셔 confirm/return: `mandatory=true`를 유지하고 return을 처리하며 NACK/return 이상 동작을 알립니다.
- Dead-letter 및 Alternate Exchange: 실패/미라우팅 메시지를 DLQ/AE로 보내 조용한 드롭을 방지합니다.
- 재시도는 지연/TTL 라우팅(핫 재큐 금지): 큐별 TTL 또는 지연 exchange를 사용해 백오프를 구현합니다.
- Quorum Queue / HA 정책: 가용성과 복구 특성을 높이기 위해 quorum queue를 우선 고려합니다.
- 가시성 훅: 헬스체크 및 `messageId`/`traceId`를 포함한 publish/consume/ack/nack 로그, 그리고 publish/consume/DLQ 메트릭을 활성화합니다.

### 애플리케이션/인프라 신뢰성 레버(브로커 기본 외)

- 멱등 소비자: `messageId` 기준으로 중복 제거해 최소 1회 전달을 안전하게 만듭니다.
- 아웃박스 패턴: DB 트랜잭션에 이벤트를 함께 저장 후 브로커로 릴레이하여 “데이터 저장됐지만 메시지는 미발행” 상태를 없앱니다.
- 스키마/버전 검증: `schema-version`을 포함하고 검증하여 호환되지 않는 payload를 보류하거나 거절합니다.
- 운영 위생: DLQ/return을 모니터링하고, 클러스터 크기/디스크/네트워크를 튜닝하며 백업/업그레이드를 안전히 수행합니다.

### 아웃박스 패턴(RabbitMQ + 서비스별 exchange/prefix)

- 장점: 동일 DB 트랜잭션에 이벤트를 저장해 “데이터 저장됐지만 메시지는 미발행” 상황을 막습니다. outbox PK/UUID를 `messageId`로 재사용해 추적/멱등성을 확보하며, outbox에서
  리플레이/모니터링이 가능합니다.
- 비용: 추가 테이블 저장/유지보수(파티셔닝/TTL), 폴링/CDC로 인한 지연, 폴링으로 인한 DB 부하, CDC/배치/정리 등 운영 오버헤드가 있습니다. 릴레이(outbox→broker)와 소비자 모두에서 멱등
  처리가 필요합니다.
- RabbitMQ 연동: 라우팅 키, 헤더, payload를 outbox에 함께 저장해 릴레이가 실제 전송 형태로 내보내도록 합니다. 릴레이 실패의 백오프/재시도 정책을 정의하고, DLQ와 outbox 상태를 함께
  관리해 “sent/consumed” 가시성을 만듭니다. 시스템이 단순하고 지연에 민감하다면, outbox를 도입하기 전에 직접 발행 + `messageId` 로깅/메트릭부터 시작하는 것도 충분할 수 있습니다.
- 의사결정 포인트:
    - 폴링/CDC로 인한 추가 지연이 허용되는가?
    - DB가 outbox 쓰기/읽기와 인덱싱 부하를 감당할 여력이 있는가?
    - CDC/릴레이/정리를 운영할 인력이 있는가?
    - 다운스트림 소비자가 `messageId` 기반 멱등 처리를 준비했는가?
    - 규제/추적 요구사항이 추가 설비를 정당화할 만큼 엄격한가?

## 이 PoC 실행 및 테스트 방법

- RabbitMQ 시작(Docker): `cd docker && docker compose up -d` (관리 UI: http://localhost:15672, guest/guest).
- 프로필로 앱 실행:
    - Service A만: `./gradlew bootRun --args='--spring.profiles.active=service-a'`
    - Service B만: `./gradlew bootRun --args='--spring.profiles.active=service-b'`
    - Both(A+B): `./gradlew bootRun --args='--spring.profiles.active=both'`
    - Cross(B 이벤트를 A와 B가 소비): `./gradlew bootRun --args='--spring.profiles.active=cross'`
- 발행(Service A):
    - 커스텀 JSON:
      `curl -X POST http://localhost:8080/api/service-a/events -H "Content-Type: application/json" -d '{"eventType":"order.created","payload":{"orderId":"ORD-123","customerId":"C-1","totalAmount":12000,"currency":"KRW","items":[{"sku":"SKU-1","qty":1,"price":12000}]}}'`
    - 간단 트리거(HEAD, 랜덤 payload):
      `curl -I http://localhost:8080/api/service-a/events`
- 발행(Service B):
    - 커스텀 JSON:
      `curl -X POST http://localhost:8080/api/service-b/events -H "Content-Type: application/json" -d '{"eventType":"user.created","payload":{"userId":"USER-1","email":"user1@example.com","name":"Sample User","status":"ACTIVE"}}'`
    - 간단 트리거(HEAD, 랜덤 payload):
      `curl -I http://localhost:8080/api/service-b/events`
- IDE HTTP 클라이언트: `requests.http`에 Service A/B용 POST/HEAD 요청이 준비돼 있습니다.

### 메시지 헤더 예시

```json
{
  "messageId": "c3f9f7f4-5d2c-4d91-a18e-56e2b7a90b8e",
  "correlationId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
  "type": "order.created",
  "appId": "serviceA",
  "timestamp": "2025-02-12T09:15:27.123Z",
  "contentType": "application/json",
  "contentEncoding": "utf-8",
  "deliveryMode": "PERSISTENT",
  "headers": {
    "traceId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
    "schema-version": "1.0"
  }
}
```

### 메시지 바디 예시

```json
{
  "eventType": "order.created",
  "occurredAt": "2025-02-12T09:15:27.123Z",
  "publishedAt": "2025-02-12T09:15:27.123Z",
  "payload": {
    "orderId": "ORD-12345",
    "customerId": "C-999",
    "totalAmount": 12000,
    "currency": "KRW",
    "items": [
      {
        "sku": "SKU-1",
        "qty": 1,
        "price": 8000
      },
      {
        "sku": "SKU-2",
        "qty": 2,
        "price": 2000
      }
    ]
  },
  "metadata": {
    "sourceService": "serviceA",
    "traceId": "4b2c1d9c-7b1b-4b9c-9c7f-123456789abc",
    "messageId": "c3f9f7f4-5d2c-4d91-a18e-56e2b7a90b8e",
    "schemaVersion": "1.0",
    "priority": "normal",
    "sensitivity": "internal"
  }
}
```

---

## AI Agent와 상호작용하기

이 프로젝트에는 개발자가 RabbitMQ를 학습할 수 있도록 돕는 **Technical Guide** 역할의 AI 에이전트가 포함되어 있습니다. 프로젝트의 코드, 아키텍처, 그리고 그 안에 구현된 RabbitMQ
개념에 대해 에이전트에게 질문할 수 있습니다.

에이전트의 주된 역할은 프로젝트 코드를 분석하고 동작 방식을 설명하는 것입니다. 메시징 패턴, MSA 연동, 이 PoC의 구체적 구현에 대해 이해를 도울 수 있습니다.

### AI Agent 사용 방법

1. 프로젝트 루트 디렉터리에서 다음 명령을 실행해 에이전트와 상호작용합니다.
2. 호출 후 `AGENTS.md` 파일을 먼저 읽도록 지시하면, 에이전트가 해당 내용을 기준으로 답변을 시작합니다.
