package com.ichi2.anki.lint.utils;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Severity;

import static com.android.tools.lint.detector.api.Category.create;

/**
 * Hold some constants applicable to all lint issues.
 */
public class Constants {

    /**
     * A special {@link Category} which groups the Lint issues related to the usage of the new SystemTime class as a
     * sub category for {@link Category#CORRECTNESS}.
     */
    public static final Category ANKI_TIME_CATEGORY = create(Category.CORRECTNESS, "AnkiTime", 10);

    /**
     * The priority for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    public static final int ANKI_TIME_PRIORITY = 10;

    /**
     * The severity for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    public static final Severity ANKI_TIME_SEVERITY = Severity.FATAL;

    /**
     * The priority for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    public static final int ANKI_CROWDIN_PRIORITY = 10;

    /**
     * A special {@link Category} which groups the Lint issues related to the usage of CrowdIn as a
     * sub category for {@link Category#CORRECTNESS}.
     */
    public static final Category ANKI_CROWDIN_CATEGORY = create(Category.CORRECTNESS, "AnkiCrowdIn", ANKI_CROWDIN_PRIORITY);


    /**
     * The severity for the Lint issues used by all rules related to CrowdIn restrictions.
     */
    public static final Severity ANKI_CROWDIN_SEVERITY = Severity.FATAL;

}
