import unittest

import deepseek_team


class TeamConsoleTests(unittest.TestCase):
    def test_trim_messages_keeps_newest_messages(self):
        messages = [{"role": "user", "content": str(i)} for i in range(50)]
        trimmed = deepseek_team.trim_messages(messages)
        self.assertEqual(len(trimmed), deepseek_team.MAX_HISTORY_MESSAGES)
        self.assertEqual(trimmed[0]["content"], "10")
        self.assertEqual(trimmed[-1]["content"], "49")

    def test_transcript_for_github_formats_roles(self):
        transcript = deepseek_team.transcript_for_github([
            {"role": "user", "content": "Проверь загрузчик"},
            {"role": "assistant", "content": "Начну с анализа."},
        ])
        self.assertIn("### USER", transcript)
        self.assertIn("Проверь загрузчик", transcript)
        self.assertIn("### ASSISTANT", transcript)

    def test_transcript_ignores_empty_content(self):
        transcript = deepseek_team.transcript_for_github([
            {"role": "user", "content": " "},
            {"role": "assistant", "content": "Готово"},
        ])
        self.assertNotIn("### USER", transcript)
        self.assertIn("Готово", transcript)


if __name__ == "__main__":
    unittest.main()
