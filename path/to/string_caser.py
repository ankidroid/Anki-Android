# complete code
import re
from typing import List

class StringCaser:
    @staticmethod
    def to_title_case(input_string: str) -> str:
        """
        Converts a string to title case.

        Args:
            input_string (str): The input string to convert.

        Returns:
            str: The converted string in title case.
        """
        return input_string.title()

    @staticmethod
    def to_sentence_case(input_string: str) -> str:
        """
        Converts a string to sentence case.

        Args:
            input_string (str): The input string to convert.

        Returns:
            str: The converted string in sentence case.
        """
        return input_string.capitalize()

    @staticmethod
    def convert_strings(strings: List[str], target_case: str) -> List[str]:
        """
        Converts a list of strings to the target case.

        Args:
            strings (List[str]): The list of strings to convert.
            target_case (str): The target case to convert to. Can be 'title' or 'sentence'.

        Returns:
            List[str]: The converted list of strings.
        """
        if target_case == 'title':
            return [StringCaser.to_title_case(s) for s in strings]
        elif target_case == 'sentence':
            return [StringCaser.to_sentence_case(s) for s in strings]
        else:
            raise ValueError("Invalid target case. Must be 'title' or 'sentence'.")

# Example usage:
strings = ['hello world', 'this is a test', 'another string']
print(StringCaser.convert_strings(strings, 'title'))  # Output: ['Hello World', 'This Is A Test', 'Another String']
print(StringCaser.convert_strings(strings, 'sentence'))  # Output: ['Hello world', 'This is a test', 'Another string']