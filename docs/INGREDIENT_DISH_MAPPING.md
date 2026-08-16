# 원재료–요리 매핑

## 목적

KDRI 부족 영양소 계산으로 정렬한 원재료를 사용자가 실제로 활용할 수 있는 요리 후보로 변환한다. 실제 추천 결과 스냅샷인 `recommendation_dishes`와 재사용 가능한 원본 후보를 분리한다.

## 저장 구조

- `dish_templates`: 재사용 가능한 요리명, 구성 원재료, 알레르기 코드와 선택적 음식 카탈로그 연결을 저장한다.
- `ingredient_dish_mappings`: 추천 원재료 Food와 요리 템플릿을 우선순위와 함께 연결한다.
- `recommendation_dishes`: 추천 생성 시점에 선택된 결과를 보관하는 기존 스냅샷 테이블이다.

요리 템플릿의 `food_id`는 기존 음식 카탈로그에서 동일한 요리를 찾았을 때만 사용한다. 일치 항목이 없으면 `NULL`을 유지한다.

## 선택 규칙

1. 부족 영양소 기준으로 정렬된 원재료 후보 전체에서 활성 매핑을 조회한다.
2. 매핑 우선순위, 정규화된 요리명, 요리 ID 순으로 결과를 고정한다.
3. 알레르기 또는 기피 구성 원재료가 있는 요리를 제외한다.
4. 정규화된 요리명이 같은 후보를 한 건만 남긴다.
5. 안전한 요리가 2개 미만인 원재료는 건너뛰고 다음 원재료를 평가한다.
6. 원재료 하나당 최대 3개 요리, 전체 최대 3개 원재료를 반환한다.

초기 식품 카탈로그의 추천 후보가 672건이므로 `RecommendationPlanningService`는 최대 1,000건의 정렬 결과를 매핑 단계에 전달한다. 이는 일부 상위 원재료에 초기 매핑이 없어도 다음 후보를 찾기 위한 범위이며 반환 개수는 늘리지 않는다.

## 안전 정책

- 요리 템플릿은 구성 원재료 목록과 표준 알레르기 코드 목록을 반드시 가진다.
- 구성 원재료가 비어 있거나 알레르기 코드 목록이 `NULL`이면 안전성을 평가하지 않고 제외한다.
- 사용자 또는 요리 템플릿에 알 수 없는 알레르기 코드가 있으면 해당 요리를 제외한다.
- 사용자 기피 음식과 구성 원재료는 정규화한 뒤 완전 일치로 비교한다.
- 알레르기 코드의 영어·한글 별칭은 같은 키워드 그룹으로 비교한다.

이 정책은 등록된 템플릿 구성에만 적용된다. 실제 조리 과정에서 재료를 추가하면 안전성이 달라질 수 있으므로, 이후 AI 단계는 DB 후보에 없는 재료를 임의로 추가해서는 안 된다.

## 초기 seed

`db/data/ingredient_dish_mapping_seed.sql`은 13개 대표 원재료와 요리 39건을 제공한다. 고정 UUID와 upsert를 사용하므로 반복 실행할 수 있다.

적재 순서:

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u bodybuddy -p bodybuddy_db \
  < db/migrations/V20260816_01__ingredient_dish_mapping.sql

mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u bodybuddy -p bodybuddy_db \
  < db/data/ingredient_dish_mapping_seed.sql
```

검증:

```sql
SELECT COUNT(*) FROM dish_templates WHERE active = TRUE;
SELECT COUNT(DISTINCT ingredient_food_id) FROM ingredient_dish_mappings WHERE active = TRUE;
SELECT ingredient_food_id, COUNT(*) AS dish_count
FROM ingredient_dish_mappings
WHERE active = TRUE
GROUP BY ingredient_food_id
HAVING COUNT(*) < 2;
```

초기 seed에 없는 원재료는 추천 오류가 아니라 매핑 미지원 후보로 취급한다. 운영 적용 전 실제 사용자 추천 결과를 바탕으로 매핑 범위를 확장한다.
