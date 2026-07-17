# complete code
from string_caser import StringCaser

class AnkiDroidApp:
    def __init__(self):
        self.strings = ['hello world', 'this is a test', 'another string']

    def convert_strings(self, target_case: str):
        """
        Converts the app's strings to the target case.

        Args:
            target_case (str): The target case to convert to. Can be 'title' or 'sentence'.
        """
        self.strings = StringCaser.convert_strings(self.strings, target_case)

# Example usage:
app = AnkiDroidApp()
app.convert_strings('title')
print(app.strings)  # Output: ['Hello World', 'This Is A Test', 'Another String']
app.convert_strings('sentence')
print(app.strings)  # Output: ['Hello world', 'This is a test', 'Another string']