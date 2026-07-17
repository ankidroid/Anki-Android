# complete code
from string_caser import StringCaser

class AnkiDroidJsAPI:
    def __init__(self):
        self.strings = ['hello world', 'this is a test', 'another string']

    def convert_strings(self, target_case: str):
        """
        Converts the API's strings to the target case.

        Args:
            target_case (str): The target case to convert to. Can be 'title' or 'sentence'.
        """
        self.strings = StringCaser.convert_strings(self.strings, target_case)

# Example usage:
api = AnkiDroidJsAPI()
api.convert_strings('title')
print(api.strings)  # Output: ['Hello World', 'This Is A Test', 'Another String']
api.convert_strings('sentence')
print(api.strings)  # Output: ['Hello world', 'This is a test', 'Another string']