package com.ichi2.libanki.template;

import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public class MathJax {
    // MathJax opening delimiters
    private static final String[] sMathJaxOpenings = {"\\(", "\\["};

    // MathJax closing delimiters
    private static final String[] sMathJaxClosings = {"\\)", "\\]"};


    public static boolean textContainsMathjax(@NonNull String txt) {
        // Do you have the first opening and then the first closing,
        // or the second opening and the second closing...?

        //This assumes that the openings and closings are the same length.

        String opening;
        String closing;
        for (int i = 0; i < sMathJaxOpenings.length; i++) {
            opening = sMathJaxOpenings[i];
            closing = sMathJaxClosings[i];

            //What if there are more than one thing?
            //Let's look for the first opening, and the last closing, and if they're in the right order,
            //we are good.

            int first_opening_index = txt.indexOf(opening);
            int last_closing_index = txt.lastIndexOf(closing);

            if (first_opening_index != -1
                    && last_closing_index != -1
                    && first_opening_index < last_closing_index)
            {
                return true;
            }
        }
        return false;
    }
}
