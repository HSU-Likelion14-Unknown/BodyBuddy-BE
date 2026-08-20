# 국가표준식품성분 DB 10.4 적재

## 목적

운영 서버는 Excel 파일을 직접 읽지 않는다. 개발 환경에서 원본 Excel을 한 번 정제해 생성한 SQL seed를 MySQL에 적재한다.

- 원본: `식품성분표(10개정판).xlsx`
- 사용 시트: `국가표준식품성분 Database 10.4`
- 코드 연결: `부록1)식품코드 연계표`
- 원본 영양 기준: 가식부 100g
- 서비스 영양 기준: 원본 100g 값을 1인분으로 간주해 `reference_amount=1`, `reference_unit='인분'`으로 적재
- 원천 식별자: `(data_source, source_food_code)`
- 음식 UUID: 원천 식품코드로 만든 UUID v5이므로 재생성해도 동일하다.

## 정제 원칙

1. 10.0~10.3 시트는 과거 스냅샷이므로 합치지 않는다.
2. 10.4 식품 변형 3,366개를 모두 보존한다.
3. 음식명 첫 토큰을 원재료 기본명으로 사용하되 영양 값은 서로 합산하거나 무조건 평균내지 않는다.
4. 원재료별 대표 행은 `기본명과 완전히 같은 행 → 일반 생것 → 일반 부위 생것 → 생것 변형 중 영양 중앙값에 가장 가까운 실제 행 → 최소 가공 변형` 순으로 한 건만 선택한다. 중앙값 자체를 새 영양 값으로 저장하지 않고 실제 원본 행을 선택한다.
5. `Tr`은 수치 계산에서 0으로 두되 `trace_nutrients`에 기록한다.
6. 괄호 값은 수치로 적재하되 `qualified_nutrients`에 기록한다.
7. `-`와 빈 셀은 0이 아니라 `NULL`로 유지한다.

## 개발 환경에서 seed 재생성

```powershell
& 'C:\Users\prett\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  'scripts\food_catalog\build_food_catalog.py' `
  --source 'C:\Users\prett\Downloads\식품성분표(10개정판).xlsx' `
  --output-dir 'db\data'
```

다른 Python 환경에서는 다음 패키지가 필요하다.

```bash
python -m pip install -r scripts/food_catalog/requirements.txt
```

## 기존 MySQL에 적재

반드시 운영 DB 백업 후 실행한다. 비밀번호는 명령행 인자로 넣지 않고 MySQL 프롬프트에서 입력한다.

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u bodybuddy -p bodybuddy_db \
  < db/migrations/V20260815_01__expand_food_catalog.sql

mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u bodybuddy -p bodybuddy_db \
  < db/data/food_catalog_10_4_seed.sql

mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u bodybuddy -p bodybuddy_db \
  < db/migrations/V20260820_01__standardize_food_nutrition_serving_unit.sql
```

seed는 동일한 원천 코드에 대해 upsert하므로 반복 실행할 수 있다. 마이그레이션은 한 번만 실행한다.

## 적재 검증

```sql
SELECT COUNT(*) AS catalog_count
FROM foods
WHERE data_source = 'KFCT' AND source_version = '10.4' AND active = TRUE;

SELECT COUNT(*) AS recommendation_candidate_count
FROM foods
WHERE data_source = 'KFCT'
  AND source_version = '10.4'
  AND is_recommendation_candidate = TRUE
  AND active = TRUE;

SELECT COUNT(*) AS nutrition_count
FROM food_nutritions fn
JOIN foods f ON f.food_id = fn.food_id
WHERE f.data_source = 'KFCT' AND f.source_version = '10.4';

SELECT f.ingredient_name, f.canonical_name, f.representative_method,
       fn.reference_amount, fn.reference_unit,
       fn.protein_g, fn.calcium_mg, fn.iron_mg
FROM foods f
JOIN food_nutritions fn ON fn.food_id = f.food_id
WHERE f.is_recommendation_candidate = TRUE
  AND f.ingredient_name IN ('시금치', '두부', '고등어', '닭고기');
```

## 가비아 단일 서버 배치

`루트 50GB + 데이터 50GB`를 선택할 수 있다면 MySQL 데이터, 업로드 이미지, DB 백업을 데이터 스토리지에 두는 구성이 관리하기 쉽다. 데이터 스토리지의 독립 보존·스냅샷 정책은 가비아 계약 조건을 확인한 뒤 확정한다.

예시 배치:

```text
/opt/bodybuddy/app          Spring Boot JAR, 배포 스크립트
/data/bodybuddy/mysql       MySQL 데이터
/data/bodybuddy/uploads     식사 이미지
/data/bodybuddy/backups     암호화된 DB 백업
```

현재 3,366개 식품 seed는 매우 작으므로 4GB 서버에서 메모리 부담이 거의 없다. Excel/Python/pandas는 운영 서버에 설치하지 않고 seed SQL만 배포한다.
