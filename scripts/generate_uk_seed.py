#!/usr/bin/env python3

import argparse
import re
from pathlib import Path
from openpyxl import load_workbook

BATCH_SIZE = 1000  

def sql_str(v):
    if v is None:
        return "NULL"
    s = str(v).strip()
    if not s:
        return "NULL"
    s = re.sub(r"<br\s*/?>", " | ", s, flags=re.IGNORECASE)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("\u00a0", " ")
    s = s.replace("░", "")
    s = re.sub(r"\s+", " ", s).strip()
    if not s:
        return "NULL"
    if not re.search(r"[A-Za-zА-Яа-я0-9]", s):
        return "NULL"
    s = s.replace("'", "''")
    return f"'{s}'"


def pick_first(value, sep=r"[,;]"):
    if value is None:
        return None
    s = str(value).split(sep)[0].strip()
    return s or None


def normalize_phone(value):
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    s = re.sub(r"\s+", " ", s)
    s = s.replace(";", " | ")
    s = re.sub(r"\s*\|\s*", " | ", s)
    return s.strip(" |").strip()


def normalize_website(value):
    v = pick_first(value)
    if not v:
        return None
    v = v.strip()
    if not re.match(r"https?://", v, re.IGNORECASE):
        v = "https://" + v
    return v


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--sheet", default=None, help="Имя листа; по умолчанию все")
    args = ap.parse_args()

    wb = load_workbook(args.xlsx, read_only=True, data_only=True)
    sheets = [args.sheet] if args.sheet else wb.sheetnames

    rows = []

    if "Веб-сайты" in wb.sheetnames:
        ws = wb["Веб-сайты"]
        it = ws.iter_rows(values_only=True)
        header = next(it)  
        for r in it:
            # r[0] Сайты, r[1] Название, r[2] Описание, r[3] Мобильные,
            # r[4] Городские, r[5] Email, r[6] Регион, r[7] Населённый пункт,
            # r[8] Telegram, r[9] WhatsApp, r[10] ВКонтакте, r[11] ОК,
            # r[12] Instagram, r[13] ИНН, r[14] ОГРН ЮЛ, r[15] ОГРН ИП,
            # r[16] CMS, r[17] Даты парсинга
            name = r[1]
            if not name or not str(name).strip():
                continue
            phones = " | ".join(
                x for x in [normalize_phone(r[3]), normalize_phone(r[4])] if x
            ) or None
            rows.append({
                "name": name,
                "description": r[2],
                "phone": phones,
                "email": pick_first(r[5]),
                "region": r[6],
                "city": r[7],
                "telegram": pick_first(r[8]),
                "whatsapp": pick_first(r[9]),
                "vk": pick_first(r[10]),
                "ok": pick_first(r[11]),
                "instagram": pick_first(r[12]),
                "inn": r[13],
                "ogrn": r[14],
                "website": normalize_website(r[0]),
                "max_link": None,
                "category": None,
                "address": None,
                "branches": None,
                "working_hours": None,
            })

    if "Справочник" in wb.sheetnames:
        ws = wb["Справочник"]
        it = ws.iter_rows(values_only=True)
        header = next(it)
        # r[0] Названия, r[1] Категории, r[2] Регионы, r[3] Населённые пункты,
        # r[4] Полные адреса, r[5] Мобильные, r[6] Городские, r[7] Email,
        # r[8] Telegram, r[9] WhatsApp, r[10] ВКонтакте, r[11] Одноклассники,
        # r[12] Сайты, r[13] Max, r[14] Филиалов, r[15] Индексы, r[16] Дата
        for r in it:
            name = r[0]
            if not name or not str(name).strip():
                continue
            phones = " | ".join(
                x for x in [normalize_phone(r[5]), normalize_phone(r[6])] if x
            ) or None
            rows.append({
                "name": name,
                "category": r[1],
                "region": r[2],
                "city": r[3],
                "address": r[4],
                "phone": phones,
                "email": pick_first(r[7]),
                "telegram": pick_first(r[8]),
                "whatsapp": pick_first(r[9]),
                "vk": pick_first(r[10]),
                "ok": pick_first(r[11]),
                "website": normalize_website(r[12]),
                "max_link": pick_first(r[13]),
                "branches": r[14],
                "working_hours": None,
                "description": None,
                "inn": None,
                "ogrn": None,
                "instagram": None,
            })

    print(f"Всего строк для вставки: {len(rows)}")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)

    cols = ("name", "category", "description", "region", "city", "address",
            "phone", "email", "website", "telegram", "whatsapp", "vk", "ok",
            "instagram", "max_link", "inn", "ogrn", "branches", "working_hours")

    tuples = []
    for row in rows:
        vals = []
        for c in cols:
            v = row.get(c)
            if c == "branches":
                try:
                    vals.append(str(int(v)) if v is not None else "NULL")
                except (TypeError, ValueError):
                    vals.append("NULL")
            else:
                vals.append(sql_str(v))
        tuples.append("(" + ", ".join(vals) + ")")

    total_batches = (len(tuples) + BATCH_SIZE - 1) // BATCH_SIZE

    with out.open("w", encoding="utf-8") as f:
        f.write("-- Автогенерировано scripts/generate_uk_seed.py\n")
        f.write("-- Источник: Управляющие компании. Пример.xlsx\n\n")
        f.write("DELETE FROM management_companies;\n\n")

        for i in range(0, len(tuples), BATCH_SIZE):
            batch = tuples[i:i + BATCH_SIZE]
            f.write("INSERT INTO management_companies\n    ("
                    + ", ".join(cols) + ")\nVALUES\n")
            f.write(",\n".join(batch) + ";\n\n")

    print(f"Батчей: {total_batches} по {BATCH_SIZE} строк")
    print(f"Записано: {out}")


if __name__ == "__main__":
    main()