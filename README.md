# BodyBuddy-BE

## 🛠 기술 스택
- Language: Java
- Framework: Spring Boot
- Database: MySQL

## 실행 환경

로컬 실행은 기본 프로필을 사용하며 Hibernate가 개발 DB 스키마를 갱신합니다.
배포 환경에서는 반드시 `prod` 프로필과 DB 환경변수를 지정합니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<host>:3306/bodybuddy_db?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

`prod` 프로필은 `ddl-auto=validate`, `show-sql=false`로 실행되므로 애플리케이션이 운영 DB 구조를 임의로 변경하지 않습니다.

### OpenAI 음식 인식

기본값은 외부 API를 호출하지 않는 `fake` provider입니다. 실제 음식 인식을 사용하려면 다음 환경변수를 설정합니다.

```text
FOOD_RECOGNITION_PROVIDER=openai
OPENAI_API_KEY=<OpenAI API key>
```

필요하면 모델과 요청 설정을 변경할 수 있습니다.

```text
OPENAI_FOOD_RECOGNITION_MODEL=gpt-5-mini-2025-08-07
OPENAI_IMAGE_DETAIL=auto
OPENAI_CONNECT_TIMEOUT=5s
OPENAI_READ_TIMEOUT=45s
```

API 키는 저장소 파일에 작성하거나 커밋하지 않습니다.

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
