
import unittest

from api.diagnosis_service import (
    score_diagnosis,
    calculate_style,
)
from api.diagnosis_questions import (
    TYPE_MAP,
    get_question_set,
)


def all_answers(value=3):
    return {
        f"Q{i:02d}": value
        for i in range(1, 21)
    }


class DiagnosisServiceTests(unittest.TestCase):

    def test_question_definitions(self):
        data = get_question_set()
        questions = data["questions"]

        self.assertEqual(data["questionSetVersion"], "v2.1")
        self.assertEqual(len(questions), 20)
        self.assertEqual(len(TYPE_MAP), 16)
        self.assertEqual(
            [q["id"] for q in questions],
            [f"Q{i:02d}" for i in range(1, 21)],
        )

    def test_all_neutral(self):
        result = score_diagnosis(all_answers(3))

        self.assertEqual(result["typeCode"], "GIPS")
        self.assertEqual(
            result["styleAxisScores"],
            {"GE": 50, "IX": 50, "PF": 50, "SD": 50},
        )
        self.assertEqual(
            result["boundaryFlags"]["exactTie"],
            ["GE", "IX", "PF", "SD"],
        )
        self.assertEqual(
            result["readinessScores"],
            {"V": 50, "O": 50, "R": 50},
        )

    def test_first_direction_maximum(self):
        answers = all_answers()

        for question_id in (
            "Q01", "Q02", "Q03", "Q04",
            "Q09", "Q10", "Q11", "Q12",
        ):
            answers[question_id] = 1

        for question_id in ("Q05", "Q06", "Q07", "Q08"):
            answers[question_id] = 5

        result = score_diagnosis(answers)

        self.assertEqual(result["typeCode"], "GIPS")
        self.assertTrue(
            all(v == 100 for v in result["styleAxisScores"].values())
        )

    def test_second_direction_maximum(self):
        answers = all_answers()

        for question_id in (
            "Q01", "Q02", "Q03", "Q04",
            "Q09", "Q10", "Q11", "Q12",
        ):
            answers[question_id] = 5

        for question_id in ("Q05", "Q06", "Q07", "Q08"):
            answers[question_id] = 1

        result = score_diagnosis(answers)

        self.assertEqual(result["typeCode"], "EXFD")
        self.assertTrue(
            all(v == 0 for v in result["styleAxisScores"].values())
        )

    def test_ge_boundary_values(self):
        # G/E軸のT=8,9,10を確認
        cases = [
            ((2, 3, 5), 8, 42, "E", "nearBoundary"),
            ((2, 3, 4), 9, 50, "G", "exactTie"),
            ((2, 4, 4), 10, 58, "G", "nearBoundary"),
        ]

        for values, total, score, direction, flag in cases:
            with self.subTest(total=total):
                answers = all_answers()
                answers["Q01"], answers["Q05"], answers["Q09"] = values

                result = calculate_style(answers)

                self.assertEqual(
                    result["styleAxisRawTotals"]["GE"], total
                )
                self.assertEqual(
                    result["styleAxisScores"]["GE"], score
                )
                self.assertEqual(
                    result["styleDirectionCodes"]["GE"], direction
                )
                self.assertIn(
                    "GE", result["boundaryFlags"][flag]
                )

    def test_readiness_minimum_and_maximum(self):
        for value, expected in [(1, 0), (5, 100)]:
            with self.subTest(value=value):
                result = score_diagnosis(all_answers(value))
                self.assertEqual(
                    result["readinessScores"],
                    {"V": expected, "O": expected, "R": expected},
                )

    def test_readiness_half_up(self):
        answers = all_answers()

        answers["Q13"] = 4
        answers["Q14"] = 3
        answers["Q15"] = 5
        answers["Q19"] = 3
        answers["Q20"] = 4

        result = score_diagnosis(answers)

        self.assertEqual(result["readinessScores"]["V"], 75)
        self.assertEqual(result["readinessScores"]["R"], 63)

    def test_missing_answer_rejected(self):
        answers = all_answers()
        del answers["Q20"]

        with self.assertRaises(ValueError):
            score_diagnosis(answers)

    def test_invalid_answer_rejected(self):
        for invalid in (0, 6, "3", 3.5, True):
            with self.subTest(invalid=invalid):
                answers = all_answers()
                answers["Q01"] = invalid

                with self.assertRaises(ValueError):
                    score_diagnosis(answers)

    def test_extra_answer_rejected(self):
        answers = all_answers()
        answers["Q21"] = 3

        with self.assertRaises(ValueError):
            score_diagnosis(answers)


if __name__ == "__main__":
    unittest.main()
