
from decimal import Decimal, ROUND_HALF_UP

QUESTION_SET_VERSION = "v2.1"

# 各軸：(第一方向, 第二方向, 対応設問, 第一方向が右側の設問)
STYLE_AXES = {
    "GE": ("G", "E", ("Q01", "Q05", "Q09"), {"Q05"}),
    "IX": ("I", "X", ("Q02", "Q06", "Q10"), {"Q06"}),
    "PF": ("P", "F", ("Q03", "Q07", "Q11"), {"Q07"}),
    "SD": ("S", "D", ("Q04", "Q08", "Q12"), {"Q08"}),
}

READINESS_GROUPS = {
    "V": ("Q13", "Q14", "Q15"),
    "O": ("Q16", "Q17", "Q18"),
    "R": ("Q19", "Q20"),
}

QUESTION_IDS = {f"Q{i:02d}" for i in range(1, 21)}


def round_half_up(value):
    """小数点以下0.5を切り上げて整数にする"""
    return int(
        Decimal(value).quantize(
            Decimal("1"),
            rounding=ROUND_HALF_UP,
        )
    )


def validate_answers(answers):
    """20問すべてが1〜5の整数で回答されているか確認"""

    if not isinstance(answers, dict):
        raise ValueError("answers must be an object")

    if set(answers.keys()) != QUESTION_IDS:
        raise ValueError("Exactly Q01 through Q20 are required")

    for question_id, answer in answers.items():
        if type(answer) is not int or not 1 <= answer <= 5:
            raise ValueError(
                f"{question_id} must be an integer from 1 to 5"
            )


def calculate_style(answers):
    """Q01〜Q12から4軸のスコアと16タイプを算出"""

    axis_totals = {}
    axis_scores = {}
    direction_codes = {}
    boundary_flags = {
        "exactTie": [],
        "nearBoundary": [],
    }

    for axis, (first, second, questions, reversed_questions) in STYLE_AXES.items():

        total = 0

        for question_id in questions:
            answer = answers[question_id]

            # 第一方向が左側なら反転、右側ならそのまま
            corrected = (
                answer
                if question_id in reversed_questions
                else 6 - answer
            )

            total += corrected

        # Tは3〜15
        axis_totals[axis] = total

        # 第一方向のスコア：0〜100
        score = round_half_up(
            Decimal(total - 3) * 100 / 12
        )
        axis_scores[axis] = score

        # T=9は第一方向を選ぶ（v2.1確定仕様）
        direction_codes[axis] = first if total >= 9 else second

        if total == 9:
            boundary_flags["exactTie"].append(axis)
        elif total in (8, 10):
            boundary_flags["nearBoundary"].append(axis)

    type_code = "".join(direction_codes.values())

    return {
        "typeCode": type_code,
        "styleAxisRawTotals": axis_totals,
        "styleAxisScores": axis_scores,
        "styleDirectionCodes": direction_codes,
        "boundaryFlags": boundary_flags,
    }


def calculate_readiness(answers):
    """Q13〜Q20からV・O・Rの3指標を算出"""

    scores = {}

    for indicator, questions in READINESS_GROUPS.items():
        total = sum(answers[q] for q in questions)
        count = len(questions)

        score = round_half_up(
            Decimal(total - count) * 100 / (4 * count)
        )

        scores[indicator] = score

    return scores


def score_diagnosis(answers):
    """診断全体の採点入口"""

    validate_answers(answers)

    style = calculate_style(answers)
    readiness = calculate_readiness(answers)

    return {
        "questionSetVersion": QUESTION_SET_VERSION,
        **style,
        "readinessScores": readiness,
    }
