#!/usr/bin/env python3
"""Build a deterministic MySQL seed from 국가표준식품성분 Database 10.4.

The workbook is treated as an offline build input. Production never reads Excel.
All 10.4 variants are preserved, while one conservative representative per
ingredient base name is marked as a recommendation candidate.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import unicodedata
import uuid
from collections import defaultdict
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from statistics import median
from typing import Any, Iterable

import pandas as pd


SOURCE_NAME = "KFCT"
SOURCE_VERSION = "10.4"
MAIN_SHEET = "국가표준식품성분 Database 10.4"
CODE_SHEET = "부록1)식품코드 연계표"
FOOD_ID_NAMESPACE = uuid.UUID("9e7bd34a-2c69-5dd2-b7ad-3fc79b5042cb")

NUTRIENT_COLUMNS = {
    "calories_kcal": "에너지",
    "carbohydrate_g": "탄수화물",
    "protein_g": "단백질",
    "fat_g": "지방 ",
    "fiber_g": "총 \n식이섬유",
    "sodium_mg": "나트륨",
    "calcium_mg": "칼슘",
    "iron_mg": "철",
    "potassium_mg": "칼륨",
    "vitamin_a_mcg_rae": "비타민 A",
    "vitamin_c_mg": "비타민 C",
}

RECOMMENDABLE_GROUPS = {
    "곡류 및 그 제품",
    "감자류 및 전분류",
    "두류",
    "견과류 및 종실류",
    "채소류",
    "버섯류",
    "과일류",
    "육류 및 그 제품",
    "난류",
    "어패류 및 그 제품",
    "해조류",
    "우유 및 그 제품",
}

EXCLUDED_BASE_PATTERN = re.compile(
    r"(빵|과자|라면|떡|김치|젓갈|잼|주스|음료|술|맥주|소주|와인|"
    r"소스|드레싱|스프|죽|밥|국수|냉면|피자|햄버거|샌드위치|케이크|초콜릿|사탕)"
)

RAW_STATES = {"생것"}
FROZEN_STATES = {"냉동"}
DRIED_STATES = {"말린것", "동결건조", "반건조"}
SIMPLE_COOKED_STATES = {
    "삶은것",
    "데친것",
    "찐것",
    "구운것",
    "구운것(팬)",
    "구운것(오븐)",
}


@dataclass(frozen=True)
class ParsedNutrient:
    value: Decimal | None
    trace: bool = False
    qualified: bool = False


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args()


def text(value: Any) -> str:
    if pd.isna(value):
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def index_key(value: Any) -> str:
    if pd.isna(value):
        return ""
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    raw = str(value).strip()
    return raw[:-2] if raw.endswith(".0") and raw[:-2].isdigit() else raw


def normalize_catalog_name(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold().strip()
    value = re.sub(r"\s*,\s*", ",", value)
    return re.sub(r"\s+", " ", value)


def normalize_lookup(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold()
    return re.sub(r"[\s,./·ㆍ_()\[\]{}\-]+", "", value)


def parse_nutrient(value: Any) -> ParsedNutrient:
    if pd.isna(value):
        return ParsedNutrient(None)
    if isinstance(value, (int, float, Decimal)):
        return ParsedNutrient(Decimal(str(value)))

    raw = str(value).strip()
    if not raw or raw == "-":
        return ParsedNutrient(None)

    qualified = raw.startswith("(") and raw.endswith(")")
    inner = raw[1:-1].strip() if qualified else raw
    if inner.casefold() == "tr":
        return ParsedNutrient(Decimal("0"), trace=True, qualified=qualified)

    try:
        return ParsedNutrient(Decimal(inner.replace(",", "")), qualified=qualified)
    except InvalidOperation as exc:
        raise ValueError(f"Unsupported nutrient value: {raw!r}") from exc


def processing_state(tokens: list[str]) -> str:
    if len(tokens) < 2:
        return ""
    last = tokens[-1]
    known = RAW_STATES | FROZEN_STATES | DRIED_STATES | SIMPLE_COOKED_STATES
    if last in known or any(marker in last for marker in ("튀긴것", "통조림", "염장", "조미")):
        return last
    return ""


def recommendation_eligible(food_group: str, ingredient_name: str) -> bool:
    return (
        food_group in RECOMMENDABLE_GROUPS
        and not EXCLUDED_BASE_PATTERN.search(ingredient_name)
    )


def representative_score(row: dict[str, Any]) -> tuple[int, int, int, str]:
    tokens = row["name_tokens"]
    state = row["processing_state"]
    missing_priority_nutrients = sum(
        row[name] is None
        for name in ("protein_g", "fiber_g", "calcium_mg", "iron_mg", "potassium_mg")
    )

    if row["canonical_name"] == row["ingredient_name"]:
        tier = 0
    elif state in RAW_STATES and len(tokens) == 2:
        tier = 10
    elif state in RAW_STATES:
        tier = 20
    elif state in FROZEN_STATES:
        tier = 30
    elif state in DRIED_STATES:
        tier = 40
    elif state in SIMPLE_COOKED_STATES:
        tier = 50
    else:
        tier = 100

    return (tier, len(tokens), missing_priority_nutrients, row["source_food_code"])


def representative_method(row: dict[str, Any]) -> str:
    if row["canonical_name"] == row["ingredient_name"]:
        return "EXACT_BASE_NAME"
    if row["processing_state"] in RAW_STATES and len(row["name_tokens"]) == 2:
        return "GENERIC_RAW"
    if row["processing_state"] in RAW_STATES:
        return "SHORTEST_RAW_VARIANT"
    return "LOWEST_COMPLEXITY_VARIANT"


def nutrition_medoid(rows: list[dict[str, Any]]) -> dict[str, Any]:
    nutrients = (
        "protein_g",
        "fiber_g",
        "calcium_mg",
        "iron_mg",
        "potassium_mg",
        "vitamin_a_mcg_rae",
        "vitamin_c_mg",
    )
    centers: dict[str, Decimal] = {}
    scales: dict[str, Decimal] = {}
    for nutrient in nutrients:
        values = [row[nutrient] for row in rows if row[nutrient] is not None]
        if not values:
            continue
        centers[nutrient] = Decimal(str(median(values)))
        spread = max(values) - min(values)
        scales[nutrient] = spread if spread > 0 else Decimal("1")

    def distance(row: dict[str, Any]) -> tuple[Decimal, int, str]:
        total = Decimal("0")
        compared = 0
        for nutrient, center in centers.items():
            value = row[nutrient]
            if value is None:
                total += Decimal("2")
            else:
                total += abs(value - center) / scales[nutrient]
            compared += 1
        score = total / compared if compared else Decimal("999")
        return (score, len(row["name_tokens"]), row["source_food_code"])

    return min(rows, key=distance)


def choose_representative(rows: list[dict[str, Any]]) -> tuple[dict[str, Any], str]:
    exact = [row for row in rows if row["canonical_name"] == row["ingredient_name"]]
    if exact:
        return min(exact, key=representative_score), "EXACT_BASE_NAME"

    generic_raw = [
        row for row in rows
        if row["processing_state"] in RAW_STATES and len(row["name_tokens"]) == 2
    ]
    if generic_raw:
        return min(generic_raw, key=representative_score), "GENERIC_RAW"

    semantic_generic_raw = [
        row for row in rows
        if row["processing_state"] in RAW_STATES
        and len(row["name_tokens"]) <= 3
        and any(token in {"살코기", "전체"} for token in row["name_tokens"][1:-1])
    ]
    if semantic_generic_raw:
        return min(semantic_generic_raw, key=representative_score), "GENERIC_RAW_VARIANT"

    raw_variants = [row for row in rows if row["processing_state"] in RAW_STATES]
    if raw_variants:
        return nutrition_medoid(raw_variants), "NUTRITION_MEDOID_RAW"

    selected = min(rows, key=representative_score)
    return selected, representative_method(selected)


def stable_uuid(kind: str, key: str) -> str:
    return str(uuid.uuid5(FOOD_ID_NAMESPACE, f"{kind}:{SOURCE_NAME}:{SOURCE_VERSION}:{key}"))


def decimal_text(value: Decimal | None) -> str:
    if value is None:
        return ""
    if value == 0:
        return "0"
    return format(value.normalize(), "f")


def sql_string(value: str | None) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + value.replace("'", "''").replace("\x00", "") + "'"


def sql_decimal(value: Decimal | None) -> str:
    return "NULL" if value is None else decimal_text(value)


def chunks(rows: list[dict[str, Any]], size: int = 200) -> Iterable[list[dict[str, Any]]]:
    for start in range(0, len(rows), size):
        yield rows[start : start + size]


def workbook_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_rows(source: Path) -> list[dict[str, Any]]:
    main = pd.read_excel(source, sheet_name=MAIN_SHEET, header=1)
    main = main[main["식품명"].notna()].copy()

    codes = pd.read_excel(source, sheet_name=CODE_SHEET, header=3, usecols=[0, 1, 2])
    codes.columns = ["source_index", "source_food_code", "linked_food_name"]
    code_by_index = {
        index_key(row.source_index): text(row.source_food_code)
        for row in codes.itertuples(index=False)
        if index_key(row.source_index) and text(row.source_food_code) not in {"", "-"}
    }

    source_index_column = main.columns[0]
    rows: list[dict[str, Any]] = []
    missing_codes: list[str] = []

    for _, source_row in main.iterrows():
        source_index = index_key(source_row[source_index_column])
        source_food_code = code_by_index.get(source_index, "")
        if not source_food_code:
            missing_codes.append(source_index)
            continue

        canonical_name = text(source_row["식품명"])
        tokens = [part.strip() for part in canonical_name.split(",") if part.strip()]
        ingredient_name = tokens[0]
        trace_nutrients: list[str] = []
        qualified_nutrients: list[str] = []
        nutrients: dict[str, Decimal | None] = {}

        for target, workbook_column in NUTRIENT_COLUMNS.items():
            parsed = parse_nutrient(source_row[workbook_column])
            nutrients[target] = parsed.value
            if parsed.trace:
                trace_nutrients.append(target)
            if parsed.qualified:
                qualified_nutrients.append(target)

        missing_count = sum(value is None for value in nutrients.values())
        if qualified_nutrients:
            nutrition_quality = "QUALIFIED"
        elif missing_count:
            nutrition_quality = "PARTIAL"
        else:
            nutrition_quality = "MEASURED"

        row = {
            "food_id": stable_uuid("food", source_food_code),
            "source_food_code": source_food_code,
            "source_index": source_index,
            "canonical_name": canonical_name,
            "normalized_name": normalize_catalog_name(canonical_name),
            "ingredient_name": ingredient_name,
            "normalized_ingredient_name": normalize_lookup(ingredient_name),
            "food_group": text(source_row["식품군"]),
            "processing_state": processing_state(tokens),
            "source_name": text(source_row["출처"]),
            "source_version": SOURCE_VERSION,
            "food_type": "INGREDIENT",
            "active": True,
            "is_recommendation_candidate": False,
            "representative_method": "",
            "variant_count": 0,
            "nutrition_data_quality": nutrition_quality,
            "trace_nutrients": json.dumps(trace_nutrients, ensure_ascii=False, separators=(",", ":")),
            "qualified_nutrients": json.dumps(qualified_nutrients, ensure_ascii=False, separators=(",", ":")),
            "name_tokens": tokens,
            **nutrients,
        }
        rows.append(row)

    if missing_codes:
        raise ValueError(f"Missing source food codes for indexes: {missing_codes[:20]}")

    normalized_names: dict[str, list[str]] = defaultdict(list)
    for row in rows:
        normalized_names[row["normalized_name"]].append(row["source_food_code"])
    collisions = {key: value for key, value in normalized_names.items() if len(value) > 1}
    if collisions:
        raise ValueError(f"Normalized food name collisions: {list(collisions.items())[:10]}")

    variants_by_ingredient: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        variants_by_ingredient[row["normalized_ingredient_name"]].append(row)

    for variants in variants_by_ingredient.values():
        for row in variants:
            row["variant_count"] = len(variants)
        eligible = [
            row
            for row in variants
            if recommendation_eligible(row["food_group"], row["ingredient_name"])
        ]
        if not eligible:
            continue
        selected, method = choose_representative(eligible)
        selected["is_recommendation_candidate"] = True
        selected["representative_method"] = method

    rows.sort(key=lambda row: (int(row["source_index"]), row["source_food_code"]))
    return rows


def write_catalog_csv(rows: list[dict[str, Any]], path: Path) -> None:
    columns = [
        "food_id",
        "source_food_code",
        "source_index",
        "canonical_name",
        "normalized_name",
        "ingredient_name",
        "normalized_ingredient_name",
        "food_group",
        "processing_state",
        "source_name",
        "source_version",
        "food_type",
        "active",
        "is_recommendation_candidate",
        "representative_method",
        "variant_count",
        "nutrition_data_quality",
        "trace_nutrients",
        "qualified_nutrients",
        *NUTRIENT_COLUMNS.keys(),
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            output = dict(row)
            output["active"] = 1 if row["active"] else 0
            output["is_recommendation_candidate"] = 1 if row["is_recommendation_candidate"] else 0
            for nutrient in NUTRIENT_COLUMNS:
                output[nutrient] = decimal_text(row[nutrient])
            writer.writerow(output)


def write_alias_csv(rows: list[dict[str, Any]], path: Path) -> list[dict[str, str]]:
    aliases = []
    for row in rows:
        if not row["is_recommendation_candidate"]:
            continue
        aliases.append(
            {
                "food_alias_id": stable_uuid("alias", row["normalized_ingredient_name"]),
                "food_id": row["food_id"],
                "alias_name": row["ingredient_name"],
                "normalized_alias": row["normalized_ingredient_name"],
                "alias_type": "INGREDIENT_BASE",
            }
        )
    aliases.sort(key=lambda row: row["normalized_alias"])
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(aliases[0].keys()))
        writer.writeheader()
        writer.writerows(aliases)
    return aliases


def write_seed_sql(rows: list[dict[str, Any]], aliases: list[dict[str, str]], path: Path) -> None:
    lines = [
        "-- Generated by scripts/food_catalog/build_food_catalog.py",
        "-- Source: 국가표준식품성분 Database 10.4, per 100g edible portion",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "UPDATE foods",
        "SET active = FALSE, is_recommendation_candidate = FALSE, updated_at = CURRENT_TIMESTAMP(6)",
        "WHERE data_source = 'KFCT' AND source_version = '10.4';",
        "",
    ]

    food_columns = [
        "food_id", "canonical_name", "normalized_name", "active", "data_source",
        "source_food_code", "food_group", "food_type", "ingredient_name",
        "processing_state", "source_version", "source_name",
        "is_recommendation_candidate", "representative_method", "variant_count",
        "nutrition_data_quality", "trace_nutrients", "qualified_nutrients",
        "created_at", "updated_at",
    ]
    for batch in chunks(rows):
        lines.append(f"INSERT INTO foods ({', '.join(food_columns)}) VALUES")
        values = []
        for row in batch:
            values.append(
                "(" + ", ".join(
                    [
                        sql_string(row["food_id"]),
                        sql_string(row["canonical_name"]),
                        sql_string(row["normalized_name"]),
                        "TRUE",
                        sql_string(SOURCE_NAME),
                        sql_string(row["source_food_code"]),
                        sql_string(row["food_group"]),
                        sql_string(row["food_type"]),
                        sql_string(row["ingredient_name"]),
                        sql_string(row["processing_state"]),
                        sql_string(row["source_version"]),
                        sql_string(row["source_name"]),
                        "TRUE" if row["is_recommendation_candidate"] else "FALSE",
                        sql_string(row["representative_method"]),
                        str(row["variant_count"]),
                        sql_string(row["nutrition_data_quality"]),
                        sql_string(row["trace_nutrients"]),
                        sql_string(row["qualified_nutrients"]),
                        "CURRENT_TIMESTAMP(6)",
                        "CURRENT_TIMESTAMP(6)",
                    ]
                ) + ")"
            )
        lines.append(",\n".join(values))
        lines.append(
            "ON DUPLICATE KEY UPDATE "
            "canonical_name=VALUES(canonical_name), normalized_name=VALUES(normalized_name), "
            "active=TRUE, food_group=VALUES(food_group), food_type=VALUES(food_type), "
            "ingredient_name=VALUES(ingredient_name), processing_state=VALUES(processing_state), "
            "source_version=VALUES(source_version), source_name=VALUES(source_name), "
            "is_recommendation_candidate=VALUES(is_recommendation_candidate), "
            "representative_method=VALUES(representative_method), variant_count=VALUES(variant_count), "
            "nutrition_data_quality=VALUES(nutrition_data_quality), "
            "trace_nutrients=VALUES(trace_nutrients), qualified_nutrients=VALUES(qualified_nutrients), "
            "updated_at=CURRENT_TIMESTAMP(6);"
        )
        lines.append("")

    nutrition_columns = [
        "food_id", "reference_amount", "reference_unit", *NUTRIENT_COLUMNS.keys(), "updated_at"
    ]
    for batch in chunks(rows):
        lines.append(f"INSERT INTO food_nutritions ({', '.join(nutrition_columns)}) VALUES")
        values = []
        for row in batch:
            values.append(
                "(" + ", ".join(
                    [
                        sql_string(row["food_id"]),
                        "100.00",
                        "'g'",
                        *[sql_decimal(row[name]) for name in NUTRIENT_COLUMNS],
                        "CURRENT_TIMESTAMP(6)",
                    ]
                ) + ")"
            )
        lines.append(",\n".join(values))
        lines.append(
            "ON DUPLICATE KEY UPDATE reference_amount=VALUES(reference_amount), "
            "reference_unit=VALUES(reference_unit), "
            + ", ".join(f"{name}=VALUES({name})" for name in NUTRIENT_COLUMNS)
            + ", updated_at=CURRENT_TIMESTAMP(6);"
        )
        lines.append("")

    for batch in chunks(aliases):
        lines.append(
            "INSERT INTO food_aliases "
            "(food_alias_id, food_id, alias_name, normalized_alias, alias_type, created_at) VALUES"
        )
        lines.append(",\n".join(
            "(" + ", ".join([
                sql_string(row["food_alias_id"]),
                sql_string(row["food_id"]),
                sql_string(row["alias_name"]),
                sql_string(row["normalized_alias"]),
                sql_string(row["alias_type"]),
                "CURRENT_TIMESTAMP(6)",
            ]) + ")"
            for row in batch
        ))
        lines.append(
            "ON DUPLICATE KEY UPDATE food_id=VALUES(food_id), alias_name=VALUES(alias_name);"
        )
        lines.append("")

    lines.extend(["COMMIT;", ""])
    path.write_text("\n".join(lines), encoding="utf-8")


def write_summary(rows: list[dict[str, Any]], source: Path, catalog_path: Path, path: Path) -> None:
    candidates = [row for row in rows if row["is_recommendation_candidate"]]
    summary = {
        "source_file": source.name,
        "source_sha256": workbook_sha256(source),
        "source_sheet": MAIN_SHEET,
        "source_version": SOURCE_VERSION,
        "row_count": len(rows),
        "unique_food_ids": len({row["food_id"] for row in rows}),
        "unique_source_codes": len({row["source_food_code"] for row in rows}),
        "recommendation_candidate_count": len(candidates),
        "candidate_base_name_count": len({row["normalized_ingredient_name"] for row in candidates}),
        "catalog_sha256": workbook_sha256(catalog_path),
        "nutrition_quality_counts": {
            quality: sum(row["nutrition_data_quality"] == quality for row in rows)
            for quality in ("MEASURED", "QUALIFIED", "PARTIAL")
        },
        "representative_method_counts": {
            method: sum(row["representative_method"] == method for row in candidates)
            for method in sorted({row["representative_method"] for row in candidates})
        },
    }
    path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    source = args.source.resolve()
    output_dir = args.output_dir.resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    output_dir.mkdir(parents=True, exist_ok=True)

    rows = load_rows(source)
    catalog_path = output_dir / "food_catalog_10_4.csv"
    alias_path = output_dir / "food_aliases_10_4.csv"
    seed_path = output_dir / "food_catalog_10_4_seed.sql"
    summary_path = output_dir / "food_catalog_10_4_summary.json"

    write_catalog_csv(rows, catalog_path)
    aliases = write_alias_csv(rows, alias_path)
    write_seed_sql(rows, aliases, seed_path)
    write_summary(rows, source, catalog_path, summary_path)

    print(summary_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
