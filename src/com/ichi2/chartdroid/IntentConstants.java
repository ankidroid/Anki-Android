package com.ichi2.chartdroid;

public class IntentConstants {

        public static class Meta {
                
                public static class Axes {

                    /**
                     * Specify axis titles in an ArrayList&lt;String&gt;.
                     */
                        public static final String EXTRA_AXIS_TITLES = "com.googlecode.chartdroid.intent.extra.AXIS_TITLES";
                    
                        /**
                         * Format string for the String.format() method, applied to horizontal axis labels.
                         */
                        public static final String EXTRA_FORMAT_STRING_X = "com.googlecode.chartdroid.intent.extra.FORMAT_STRING_X";

                        /**
                         * Format string for the String.format() method, applied to vertical axis labels.
                         */
                        public static final String EXTRA_FORMAT_STRING_Y = "com.googlecode.chartdroid.intent.extra.FORMAT_STRING_Y";

                        /**
                         * Format string for the String.format() method, applied to secondary vertical axis labels.
                         */
                        public static final String EXTRA_FORMAT_STRING_Y_SECONDARY = "com.googlecode.chartdroid.intent.extra.FORMAT_STRING_Y_SECONDARY";
                        
                        /**
                         * Boolean to hide/show the horizontal axis. Default: shown.
                         */
                        public static final String EXTRA_AXIS_VISIBLE_X = "com.googlecode.chartdroid.intent.extra.AXIS_VISIBLE_X";

                        /**
                         * Boolean to hide/show the vertical axis. Default: shown.
                         */
                        public static final String EXTRA_AXIS_VISIBLE_Y = "com.googlecode.chartdroid.intent.extra.AXIS_VISIBLE_Y";
                    
                }
                
                public static class Series {

                        /**
                     * Boolean enabling automatic assignment of series colors evenly-spaced from the spectrum
                     */
                        public static final String EXTRA_RAINBOW_COLORS = "com.googlecode.chartdroid.intent.extra.RAINBOW_COLORS";

                        /**
                     * Specify series colors in an array of int values.
                     */
                        public static final String EXTRA_SERIES_COLORS = "com.googlecode.chartdroid.intent.extra.SERIES_COLORS";
                        
                    /**
                     * Specify series labels in an array of String values.
                     */
                        public static final String EXTRA_SERIES_LABELS = "com.googlecode.chartdroid.intent.extra.SERIES_LABELS";
                        
                    /**
                     * Specify series marker styles in an array of int values.
                     * Types:
                     * 0 = x
                     * 1 = circle
                     * 2 = triangle
                     * 3 = square
                     * 4 = diamond
                     * 5 = point
                     */
                        public static final String EXTRA_SERIES_MARKERS = "com.googlecode.chartdroid.intent.extra.SERIES_MARKERS";
                        
                        
                        
                    /**
                     * Specify series line styles in an array of int values.
                     * Types:
                     * 0 = none,
                     * 1 = dotted
                     * 2 = dashed
                     * 3 = solid
                     */
                        public static final String EXTRA_SERIES_LINE_STYLES = "com.googlecode.chartdroid.intent.extra.SERIES_LINE_STYLES";
                        
                        public enum LineStyle {
                                NONE, DOTTED, DASHED, SOLID
                        }
                        
                        
                    /**
                     * Specify series line thicknesses in an array of float values.
                     */
                        public static final String EXTRA_SERIES_LINE_THICKNESSES = "com.googlecode.chartdroid.intent.extra.SERIES_LINE_THICKNESSES";
                        
                        
                    /**
                     * Specify primary or secondary y-axis.
                     */
                        public static final String EXTRA_SERIES_AXIS_SELECTION = "com.googlecode.chartdroid.intent.extra.SERIES_AXIS_SELECTION";
                }
        }


        /**
         * Causes the plot to start in full-screen mode.
         */
        public static final String EXTRA_FULLSCREEN = "com.googlecode.chartdroid.intent.extra.FULLSCREEN";

        
    public static String INTENT_EXTRA_CALENDAR_SELECTION_ID = "INTENT_EXTRA_CALENDAR_SELECTION_ID";
    public static String INTENT_EXTRA_DATE = "INTENT_EXTRA_DATE";
    

    
    
        public static final String ACTION_PLOT = "com.googlecode.chartdroid.intent.action.PLOT";
        
        public static final String CATEGORY_XY_CHART = "com.googlecode.chartdroid.intent.category.XY_CHART";
        public static final String CATEGORY_PIE_CHART = "com.googlecode.chartdroid.intent.category.PIE_CHART";
        public static final String CATEGORY_CALENDAR = "com.googlecode.chartdroid.intent.category.CALENDAR";
        
        // Pie chart extras
        public static final String EXTRA_COLORS = "com.googlecode.chartdroid.intent.extra.COLORS";
        public static final String EXTRA_LABELS = "com.googlecode.chartdroid.intent.extra.LABELS";
        public static final String EXTRA_DATA = "com.googlecode.chartdroid.intent.extra.DATA";
        
        // Retained for compatibility...
        @Deprecated
        public static final String EXTRA_TITLE = "com.googlecode.chartdroid.intent.extra.TITLE";

        
        
        // Calendar extras
        public static final String EXTRA_EVENT_IDS = "com.googlecode.chartdroid.intent.extra.EVENT_IDS";
        public static final String EXTRA_EVENT_TIMESTAMPS = "com.googlecode.chartdroid.intent.extra.EVENT_TIMESTAMPS";
}

