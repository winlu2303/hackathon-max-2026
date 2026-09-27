#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
import sys

from pathlib import Path
from typing import Any

import yaml

from jsonschema import Draft202012Validator, FormatChecker

VARIABLE_RE = re.compile(
    r"\$\{([A-Za-z_][A-Za-z0-9_.-]*)\}"
)

PATH_PARAM_RE = re.compile(
    r"\{([A-Za-z_][A-Za-z0-9_.-]*)\}"
)


def load_structured(path: Path) -> Any:

    text = path.read_text(encoding="utf-8")

    if path.suffix.lower() == ".json":
        return json.loads(text)

    return yaml.safe_load(text)


def format_path(parts: list[Any]) -> str:

    if not parts:
        return "$"

    out = "$"
    
    for part in parts:

        if isinstance(part, int):
            out += f"[{part}]"

        else:
            out += f".{part}"

    return out


def collect_strings(value: Any):

    if isinstance(value, str):
        yield value

    elif isinstance(value, dict):
        for item in value.values():
            yield from collect_strings(item)

    elif isinstance(value, list):
        for item in value:
            yield from collect_strings(item)


def semantic_checks(
    doc: dict[str, Any]
) -> tuple[list[str], list[str]]:

    errors: list[str] = []

    warnings: list[str] = []

    checks = doc.get("checks") or []

    cleanup = doc.get("cleanup") or []

    ids = [
        c.get("id")
        for c in checks
        if isinstance(c, dict)
    ]

    duplicates = sorted({
        x
        for x in ids
        if x and ids.count(x) > 1
    })

    if duplicates:
        errors.append(
            "Повторяющиеся значения контрольных идентификаторов: "
            + ", ".join(duplicates)
        )

    id_set = set(x for x in ids if x)

    produced_variables: set[str] = set()

    for index, check in enumerate(checks):

        if not isinstance(check, dict):
            continue

        cid = check.get("id", f"#{index}")

        for dep in check.get("dependsOn") or []:

            if dep not in id_set:
                errors.append(
                    f"Проверка {cid} зависит от неизвестного "
                    f"идентификатора проверки {dep}"
                )

            if dep == cid:
                errors.append(
                    f"Проверка {cid} не может зависеть сама от себя"
                )

        path = check.get("path", "")
        path_params = (
            (check.get("request") or {}).get("path") or {}
        )

        placeholders = set(PATH_PARAM_RE.findall(path))

        supplied = set(path_params.keys())

        missing = placeholders - supplied

        extra = supplied - placeholders

        if missing:
            errors.append(
                f"Проверка {cid} не содержит значения "
                f"path-параметров для: "
                f"{', '.join(sorted(missing))}"
            )

        if extra:
            warnings.append(
                f"Проверка {cid} содержит неиспользуемые "
                f"path-параметры: "
                f"{', '.join(sorted(extra))}"
            )

        used_variables: set[str] = set()

        for text in collect_strings(
            check.get("request") or {}
        ):
            used_variables.update(
                VARIABLE_RE.findall(text)
            )

        unknown = used_variables - produced_variables

        if unknown:
            warnings.append(
                f"Проверка {cid} использует переменные, "
                f"не извлечённые предыдущими проверками: "
                f"{', '.join(sorted(unknown))}. "
                "Требуется дополнительная проверка экспертом."
            )
            
        for variable, expression in (
            check.get("extract") or {}
        ).items():

            if not expression.startswith("$"):
                errors.append(
                    f"Проверка {cid} извлекает переменную "
                    f"{variable}, однако выражение должно "
                    f"быть JSONPath и начинаться с $"
                )

            produced_variables.add(variable)

        if (
            check.get("method")
            in {"POST", "PUT", "PATCH", "DELETE"}
            and "repeatable" not in check
        ):
            warnings.append(
                f"Проверка {cid} должна явно устанавливать "
                f"поле repeatable в true или false"
            )

        headers = (
            (check.get("request") or {}).get("headers")
            or {}
        )

        auth_value = (
            headers.get("Authorization")
            or headers.get("authorization")
        )

        if (
            isinstance(auth_value, str)
            and "${" not in auth_value
        ):
            warnings.append(
                f"Проверка {cid} содержит буквальное значение "
                f"заголовка Authorization. "
                "Не храните реальные учётные данные "
                "в DATA-API.yaml."
            )

    for index, step in enumerate(cleanup):

        if not isinstance(step, dict):
            continue

        sid = step.get(
            "id",
            f"cleanup#{index}"
        )

        path = step.get("path", "")
        path_params = (
            (step.get("request") or {}).get("path")
            or {}
        )

        placeholders = set(
            PATH_PARAM_RE.findall(path)
        )

        supplied = set(path_params.keys())

        missing = placeholders - supplied

        if missing:
            errors.append(
                f"При очистке {sid} отсутствуют значения "
                f"path-параметров для: "
                f"{', '.join(sorted(missing))}"
            )

    return errors, warnings


def cross_check_openapi(
    doc: dict[str, Any],
    data_api_path: Path,
    explicit_openapi: str | None
) -> tuple[list[str], list[str]]:

    errors: list[str] = []
    warnings: list[str] = []

    openapi_ref = (
        explicit_openapi
        or ((doc.get("api") or {}).get("openapi"))
    )

    if not openapi_ref:
        return errors, [
            "Перекрёстная проверка OpenAPI пропущена, "
            "поскольку файл OpenAPI не был указан"
        ]

    openapi_path = Path(openapi_ref)

    if not openapi_path.is_absolute():
        openapi_path = (
            data_api_path.parent / openapi_path
        ).resolve()

    if not openapi_path.exists():
        return [
            f"Файл OpenAPI не найден: {openapi_path}"
        ], warnings

    try:
        spec = load_structured(openapi_path)

    except Exception as exc:
        return [
            f"Не удаётся прочитать файл OpenAPI "
            f"{openapi_path}: {exc}"
        ], warnings

    if not isinstance(spec, dict):
        return [
            "Документ OpenAPI должен быть объектом"
        ], warnings

    version = str(
        spec.get("openapi", "")
    )

    if not (
        version.startswith("3.0.")
        or version.startswith("3.1.")
    ):
        errors.append(
            f"Версия OpenAPI должна быть 3.0.x "
            f"или 3.1.x, обнаружено "
            f"{version or '<missing>'}"
        )

    paths = spec.get("paths") or {}

    for check in (doc.get("checks") or []):

        path = check.get("path")

        method = str(
            check.get("method", "")
        ).lower()

        if path not in paths:
            errors.append(
                f"OpenAPI не содержит пути {path}, "
                f"используемого при проверке "
                f"{check.get('id')}"
            )
            continue

        if method not in (paths.get(path) or {}):
            errors.append(
                f"OpenAPI не содержит "
                f"{method.upper()} {path}, "
                f"используемого при проверке "
                f"{check.get('id')}"
            )

    for step in (doc.get("cleanup") or []):

        path = step.get("path")

        method = str(
            step.get("method", "")
        ).lower()

        if path not in paths:
            warnings.append(
                f"OpenAPI не содержит пути очистки {path}"
            )
            continue

        if method not in (paths.get(path) or {}):
            warnings.append(
                f"OpenAPI не содержит метода очистки "
                f"{method.upper()} {path}"
            )

    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Проверка DATA-API.yaml "
            "на соответствие DATA-API 1.0"
        )
    )

    parser.add_argument(
        "file",
        help="Путь к файлу DATA-API.yaml"
    )

    parser.add_argument(
        "--schema",
        default=None,
        help="Путь к DATA-API.schema.json"
    )

    parser.add_argument(
        "--openapi",
        default=None,
        help=(
            "Необязательный явный путь "
            "к openapi.yaml или openapi.json"
        )
    )

    args = parser.parse_args()
    data_api_path = Path(
        args.file
    ).resolve()
    schema_path = (
        Path(args.schema).resolve()
        if args.schema
        else Path(__file__).with_name(
            "DATA-API.schema.json"
        )
    )

    if not data_api_path.exists():
        print(
            f"ОШИБКА файл DATA-API не найден: "
            f"{data_api_path}"
        )
        return 2

    if not schema_path.exists():
        print(
            f"ОШИБКА схема данных не найдена: "
            f"{schema_path}"
        )
        return 2

    try:
        doc = load_structured(
            data_api_path
        )

    except Exception as exc:
        print(
            f"ОШИБКА невозможно прочитать файл "
            f"DATA-API {data_api_path.name}: {exc}"
        )
        return 2

    try:
        schema = load_structured(
            schema_path
        )

    except Exception as exc:
        print(
            f"ОШИБКА не удаётся проанализировать "
            f"схему: {exc}"
        )
        return 2

    if not isinstance(doc, dict):
        print(
            "ОШИБКА файл DATA-API "
            "должен быть YAML-объектом"
        )
        return 1

    validator = Draft202012Validator(
        schema,
        format_checker=FormatChecker()
    )

    schema_errors = sorted(
        validator.iter_errors(doc),
        key=lambda e: list(e.absolute_path)
    )

    errors: list[str] = []
    warnings: list[str] = []

    for error in schema_errors:
        errors.append(
            f"{format_path(list(error.absolute_path))}: "
            f"{error.message}"
        )

    semantic_errors, semantic_warnings = (
        semantic_checks(doc)
    )

    errors.extend(
        semantic_errors
    )

    warnings.extend(
        semantic_warnings
    )

    openapi_errors, openapi_warnings = (
        cross_check_openapi(
            doc,
            data_api_path,
            args.openapi
        )
    )

    errors.extend(
        openapi_errors
    )

    warnings.extend(
        openapi_warnings
    )

    if warnings:
        print("ВНИМАНИЕ")

        for warning in warnings:
            print(
                f"  - {warning}"
            )

    if errors:
        print("НЕДЕЙСТВИТЕЛЬНЫЙ")

        for error in errors:
            print(
                f"  - {error}"
            )

        return 1

    print(
        "ПОДТВЕРЖДЕНИЕ файл DATA-API.yaml "
        "соответствует версии схемы 1.0"
    )

    return 0

if __name__ == "__main__":
    raise SystemExit(main())