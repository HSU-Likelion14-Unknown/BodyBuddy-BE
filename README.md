# BodyBuddy-BE

## 🛠 기술 스택
- Language: Java
- Framework: Spring Boot
- Database: MySQL

## 실행 환경

### 로컬 실행

필수 환경은 Java 21과 MySQL 8입니다. MySQL 서버를 먼저 실행한 뒤 IntelliJ의
`Run/Debug Configurations > Environment variables`에 최소한 다음 값을 설정합니다.

```text
DB_PASSWORD=<local MySQL password>
```

기본 접속 정보와 다른 경우 아래 값도 함께 설정합니다.

```text
DB_URL=jdbc:mysql://localhost:3306/bodybuddy_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
```

기본 프로필에서는 `createDatabaseIfNotExist=true`와 `ddl-auto=update`가 적용됩니다.
MySQL 계정에 데이터베이스 생성 권한이 있으면 `bodybuddy_db`가 없을 때 생성되고,
애플리케이션 시작 시 엔티티에 필요한 테이블과 컬럼이 개발 DB에 자동 반영됩니다.

명령줄에서 실행할 때는 다음 명령을 사용합니다. (인텔리제이 구성편집에 환경변수를 저장할 경우 bootRun으로 실행하면 안 됩니다. 실행 버튼 (또는 shift + f10)으로 실행)

```text
./gradlew bootRun
```

Windows에서는 `./gradlew` 대신 `./gradlew.bat`을 사용합니다.

### 배포 환경

배포 환경에서는 반드시 `prod` 프로필과 DB 환경변수를 지정합니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<host>:3306/bodybuddy_db?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

`prod` 프로필은 `ddl-auto=validate`, `show-sql=false`로 실행되므로 애플리케이션이 운영 DB 구조를 임의로 변경하지 않습니다.

### DB 스키마 적용 정책

| 환경 | Hibernate 설정 | 적용 방법 |
|---|---|---|
| 로컬 개발 | `ddl-auto=update` | 애플리케이션 시작 시 테이블과 컬럼 자동 반영 |
| 테스트 | 테스트별 `none` 또는 `create-drop` | H2 격리 DB 사용 |
| 운영 (`prod`) | `ddl-auto=validate` | 배포 전에 SQL을 명시적으로 적용하고 시작 시 검증 |

신규 DB는 `db/schema.sql`을 사용합니다. 이미 운영 중인 DB는 필요한
`db/migrations/*.sql` 파일을 백업 후 적용해야 합니다. 현재 프로젝트에는 Flyway나
Liquibase 자동 실행기가 연결되어 있지 않으므로 이 SQL 파일들은 자동 실행되지 않습니다.
향후 Flyway를 도입하려면 전체 초기 스키마용 baseline을 만들고, 현재 중복된
`V20260817_02` 마이그레이션 버전을 먼저 고유한 번호로 정리해야 합니다.

`ddl-auto=update`는 로컬 개발 편의를 위한 설정입니다. 기존 컬럼 삭제·이름 변경,
데이터 보정, CHECK 제약조건까지 안전하게 처리하는 배포 도구는 아니므로 운영에서
`update`로 변경하지 않습니다.

이번 저신뢰도·비음식 인식 실패 기능은 기존 `ai_analysis_runs`의
`normalized_response`, `error_code`, `error_message` 컬럼을 사용하므로 별도의 DB
마이그레이션이 필요하지 않습니다. DB 미등록 음식 영양 추정 기능을 아직 반영하지 않은
기존 DB에는 `db/migrations/V20260817_02__meal_item_nutrition_estimates.sql`을 적용해야
합니다. 신규 DB용 `db/schema.sql`에는 해당 컬럼이 이미 포함되어 있습니다.

### OpenAI 음식 인식

기본값은 외부 API를 호출하지 않는 `fake` provider입니다. 실제 음식 인식을 사용하려면 다음 환경변수를 설정합니다.

```text
FOOD_RECOGNITION_PROVIDER=openai
OPENAI_API_KEY=<OpenAI API key>
FOOD_RECOGNITION_MIN_CONFIDENCE=0.60
```

`FOOD_RECOGNITION_MIN_CONFIDENCE`는 음식 후보 중 하나라도 검토 화면으로 보낼 최소
신뢰도입니다. 모든 후보가 이 값보다 낮거나 음식이 아닌 입력으로 판정되면 식사 상태가
`FAILED`가 됩니다. 값은 `0.0` 이상 `1.0` 이하로 설정합니다.

필요하면 모델과 요청 설정을 변경할 수 있습니다.

```text
OPENAI_FOOD_RECOGNITION_MODEL=gpt-5-mini-2025-08-07
OPENAI_IMAGE_DETAIL=auto
OPENAI_CONNECT_TIMEOUT=5s
OPENAI_READ_TIMEOUT=45s
```

DB에 매칭되지 않은 음식의 영양성분도 OpenAI로 추정하려면 아래 provider를 사용합니다.
미설정 시 `FOOD_RECOGNITION_PROVIDER` 값을 따르므로 음식 인식이 `openai`이면 함께 활성화됩니다.

```text
FOOD_NUTRITION_ESTIMATION_PROVIDER=openai
OPENAI_FOOD_NUTRITION_MODEL=gpt-5-mini-2025-08-07
```

다음 식사 원재료 추천은 한 번에 정확히 2개를 반환합니다. DB 후보는 대표 부족 영양소의
하루 권장량을 기본 20% 이상 채우는 경우만 사용하며, 2개를 채우지 못하면 OpenAI 후보를
생성한 뒤 같은 충족률·알레르기·비선호 검증을 다시 적용합니다. 추천 새로고침은 이전에
노출된 모든 원재료 이름을 누적 제외합니다.

```text
RECOMMENDATION_INGREDIENT_COUNT=2
RECOMMENDATION_MIN_TARGET_COVERAGE_PERCENT=20.0
RECOMMENDATION_AI_FALLBACK_PROVIDER=openai
OPENAI_INGREDIENT_RECOMMENDATION_MODEL=gpt-5-mini-2025-08-07
```

`RECOMMENDATION_AI_FALLBACK_PROVIDER`를 생략하면 `FOOD_RECOGNITION_PROVIDER` 값을
따릅니다. DB와 OpenAI 후보를 합쳐도 안전한 원재료 2개를 확보하지 못하면 생성 시
`NO_CANDIDATE`, 새로고침 시 `RECOMMENDATION_REFRESH_EXHAUSTED` 오류를 반환하며 기존
추천은 유지합니다.

API 키는 저장소 파일에 작성하거나 커밋하지 않습니다.

## 프론트엔드 연동 가이드

`POST /api/v1/auth/anonymous`를 제외한 API에는 발급받은 접근키를
`Authorization: Bearer <access-key>` 헤더로 전달합니다. 식사 생성과 재분석처럼
`Idempotency-Key`를 요구하는 요청은 한 번의 사용자 동작마다 새 UUID를 사용하고,
네트워크 재시도일 때만 같은 값을 재사용합니다.

음식 인식 API는 비동기 흐름입니다.

1. `POST /api/v1/meals/images` 또는 `POST /api/v1/meals/text`를 호출합니다.
2. `202 Accepted` 응답의 `mealId`로 `GET /api/v1/meals/{mealId}`를 조회합니다.
3. `ANALYZING` 또는 `REANALYZING`이면 잠시 후 다시 조회합니다.
4. `REVIEW_REQUIRED`이면 `GET /api/v1/meals/{mealId}/recognition-candidates`에서 후보를
   가져와 수정·삭제·확정 화면을 표시합니다.
5. `FAILED`이면 `recognitionFailure`를 사용해 인식 실패 화면을 표시합니다.

실패 응답 예시는 다음과 같습니다. `FAILED` 상태에서는 이전 성공 결과가 존재할 수
있으므로 화면 분기는 `recognizedItems`가 아니라 `status`와 `recognitionFailure`를
기준으로 해야 합니다.

```json
{
  "mealId": "meal-id",
  "status": "FAILED",
  "recognitionFailure": {
    "reason": "LOW_CONFIDENCE",
    "message": "음식 인식 결과의 신뢰도가 너무 낮습니다."
  }
}
```

`recognitionFailure.reason`은 다음 값을 사용합니다.

| 값 | 의미 | 권장 화면 처리 |
|---|---|---|
| `NO_FOOD` | 음식이 아니거나 식별 가능한 음식이 없음 | 실패 화면 |
| `LOW_CONFIDENCE` | 모든 후보가 최소 신뢰도 미만 | 실패 화면 |
| `AI_UNAVAILABLE` | OpenAI 지연·장애 등 일시적 오류 | 실패 화면 및 재시도 안내 |
| `INVALID_RESPONSE` | AI 응답 형식이 계약과 다름 | 실패 화면 |
| `UNKNOWN` | 분류되지 않은 실패 | 공통 실패 화면 |

후보 중 하나라도 `FOOD_RECOGNITION_MIN_CONFIDENCE` 이상이면 식사 전체가
`REVIEW_REQUIRED`가 됩니다. 이 경우 기준 미만 후보도 삭제하지 않고 함께 반환하므로
사용자가 후보 화면에서 수정하거나 제거할 수 있어야 합니다.

Figma 버튼의 API 연결은 다음과 같습니다.

| 버튼 | API |
|---|---|
| 다시 찍으러 가기 | 새 이미지로 `POST /api/v1/meals/images` |
| 메뉴 직접 입력하기 | `POST /api/v1/meals/text` |
| 같은 사진 다시 분석 | `POST /api/v1/meals/{mealId}/recognition/retry` |

확정된 음식의 `nutritionStatus`는 `CALCULATED`, `ESTIMATED`, `UNKNOWN` 중 하나입니다.
`ESTIMATED`이면 `nutritionBasis=AI_ESTIMATE`이며, `nutritionProvider`,
`nutritionModel`, `nutritionPromptVersion`, `nutritionConfidence`를 함께 확인할 수 있습니다.
프론트에서는 DB 계산값과 AI 추정값을 같은 값처럼 표시하지 말고 출처를 구분해야 합니다.

## 백엔드 작업 가이드

- 실제 OpenAI 연동 시 IntelliJ 또는 배포 환경에 `FOOD_RECOGNITION_PROVIDER=openai`와
  `OPENAI_API_KEY`를 설정합니다.
- 최소 신뢰도는 `FOOD_RECOGNITION_MIN_CONFIDENCE`로 조정하며 허용 범위는 `0.0~1.0`입니다.
- DB에 매칭되지 않은 음식의 AI 영양 추정값은 `foods`나 `food_nutritions`에 정식 음식
  데이터로 저장하지 않습니다. 사용자가 확정한 `meal_items`의 영양 스냅샷으로만 저장합니다.
- 저신뢰도와 `NO_FOOD` 실패 결과는 `ai_analysis_runs`에 분석 스냅샷과 실패 코드로
  저장합니다. 원본 AI 예외 문구는 API 응답으로 직접 노출하지 않습니다.
- 엔티티 변경으로 DB 구조가 바뀌면 `db/schema.sql`과 기존 DB용
  `db/migrations/*.sql`을 함께 수정합니다. 로컬 자동 갱신만 확인하고 운영 SQL을 생략하면
  `prod` 시작 시 스키마 검증에 실패합니다.
- 기능 변경 시 `openapi/bodybuddy-openapi.yaml`과 관련 테스트를 함께 갱신합니다.

## API 명세 변경 사항

현재 계약 파일은 `openapi/bodybuddy-openapi.yaml`입니다. 음식 인식 및 영양 처리와 관련해
다음 내용이 반영되어 있습니다.

- `GET /api/v1/meals/{mealId}`에 `recognitionFailure`가 추가되었습니다.
- 식사 상세의 인식 결과 필드명은 `candidates`가 아니라 `recognizedItems`입니다.
- 수정 가능한 후보는 별도 API인
  `GET /api/v1/meals/{mealId}/recognition-candidates`의 `candidates`로 반환됩니다.
- `RecognitionFailureReason`에 `NO_FOOD`, `LOW_CONFIDENCE`, `AI_UNAVAILABLE`,
  `INVALID_RESPONSE`, `UNKNOWN`이 정의되어 있습니다.
- `MealItem`에 AI 영양 추정 상태와 provider/model/prompt/confidence 메타데이터가
  정의되어 있습니다.

OpenAPI 파일은 별도 Swagger UI를 제공하는 런타임 설정이 아니라 프론트엔드와 백엔드가
공유하는 계약 파일입니다. 이번 음식 인식·영양 기능 범위는 현재 구현과 맞췄습니다.

다만 전체 컨트롤러를 대조하면 아래 기존 차이가 남아 있습니다. 해당 도메인을 연동할
때는 현재 컨트롤러를 확인해야 하며, 별도의 OpenAPI 전체 동기화 작업이 필요합니다.

- 사용자 구현은 `PATCH /users/me`, `PATCH /users/me/profile-image`,
  `DELETE /users/me`를 제공하지만 현재 명세에는 일부가 없거나 예전
  `/users/me/preferences` 경로로 작성되어 있습니다.
- 캘린더 구현은 `/calendar/days/{date}`, `/calendar/months/{month}`를 사용하지만
  명세에는 예전 경로 형식이 남아 있습니다.
- 식사 완료 `POST /meals/{mealId}/complete`가 현재 명세에 없습니다.
- 방 목록·나가기·커버·반응·피드 API가 현재 명세에 아직 포함되어 있지 않습니다.
- 사용자·캘린더·방 API 구현은 공통 `SuccessResponse` 봉투를 사용하지만 기존 명세의
  일부 응답은 내부 DTO만 직접 반환하는 형태로 작성되어 있습니다.

API가 변경되면 컨트롤러와 DTO만 수정하지 말고 이 파일과 프론트엔드 타입을 함께
갱신해야 합니다.

## 🌿 브랜치 네이밍 규칙

브랜치 명: `(태그/#이슈번호)`

예시: `feat/#2`, `fix/#3`

## 📝 커밋 컨벤션

| 타입      | 설명                                         |
|-----------|----------------------------------------------|
| `feat`    | 새로운 기능 추가 또는 기존 기능 개선          |
| `fix`     | 버그 수정                                     |
| `refactor`| 코드 리팩토링 (기능 변화 없이 구조 개선)      |
| `docs`    | 문서 작업 (README 등)                         |
| `test`    | 테스트 코드 추가 또는 수정                    |
| `chore`   | 패키지 매니저 수정, 그 외 기타 수정 (예: .gitignore) |
| `comment` | 주석 수정, 추가                               |
| `merge`   | 브랜치 병합                                   |
| `rename`  | 파일 또는 폴더명 수정                         |
| `remove`  | 파일 삭제                                     |
| `init`    | 프로젝트 초기 세팅                            |

## 🔀 PR 규칙

1. 작업 브랜치에서 작업 후 PR 생성
2. PR 제목: `type: 작업 내용 요약`
3. Assignee에는 본인을 지정
4. 리뷰어 1인 이상 승인 후 머지
