
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.view.View;

import java.text.Normalizer;

/** Implementation of {@link Compat} for SDK level 9 */
@TargetApi(9)
public class CompatV9 extends CompatV8 implements Compat {


    /*
     *  Return the input string in its Unicode Canonical Decomposed form. For example umlauts such as ü are
     *  decomposed into u plus the dots as an extra character. This decomposed form is how Apples’s HFS+ stores file
     *  names. Decomposing media file names ensures these media will sync correctly.
     *
     * @param txt Text to be normalized
     * @return The input text in its canonical decomposed form.
    */
    @Override
    public String nfdNormalized(String txt) {
        if (!Normalizer.isNormalized(txt, Normalizer.Form.NFD)) {
            return Normalizer.normalize(txt, Normalizer.Form.NFD);
        }
        return txt;
    }


    /*
     *  Return the input string in the Unicode normalized form. This helps with text comparisons, for example a ü
     *  stored as u plus the dots but typed as a single character compare as the same.
     *
     * @param txt Text to be normalized
     * @return The input text in its NFC normalized form form.
    */
    @Override
    public String nfcNormalized(String txt) {
        if (!Normalizer.isNormalized(txt, Normalizer.Form.NFC)) {
            return Normalizer.normalize(txt, Normalizer.Form.NFC);
        }
        return txt;
    }


    @Override
    public void setOverScrollModeNever(View v) {
        v.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

}
