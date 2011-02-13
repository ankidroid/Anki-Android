package com.ichi2.chartdroid;

import android.content.ContentResolver;
import android.provider.BaseColumns;

public final class ColumnSchema {

        public static final int X_AXIS_INDEX = 0;
        public static final int Y_AXIS_INDEX = 1;

        public static final String DATASET_ASPECT_PARAMETER = "aspect";
        
        public static final String AXIS_COLUMN_PREFIX = "AXIS_";

        public static class Aspect {
                
                public static final String DATASET_ASPECT_DATA = "data";
                public static final String DATASET_ASPECT_AXES = "axes";
                public static final String DATASET_ASPECT_SERIES = "series";
                
                public enum DatsetAspect {
                        DATA(DATASET_ASPECT_DATA), AXES(DATASET_ASPECT_AXES), SERIES(DATASET_ASPECT_SERIES);
                        String name;
                        DatsetAspect(String name) {
                                this.name = name;
                        }
                }
                
                public static class Data {
                        
                        public static final String COLUMN_AXIS_INDEX = "COLUMN_AXIS_INDEX";
                        public static final String COLUMN_SERIES_INDEX = "COLUMN_SERIES_INDEX";
                        public static final String COLUMN_DATUM_VALUE = "COLUMN_DATUM_VALUE";
                        public static final String COLUMN_DATUM_LABEL = "COLUMN_DATUM_LABEL";
                }
                
                public static class Axes {

                        public enum AxisExpressionMethod {
                                HORIZONTAL_AXIS, VERTICAL_AXIS, MARKER_SIZE, MARKER_HUE;
                        }

                        public static final String COLUMN_AXIS_LABEL = "COLUMN_AXIS_LABEL";
                        public static final String COLUMN_AXIS_MIN = "COLUMN_AXIS_MIN";
                        public static final String COLUMN_AXIS_MAX = "COLUMN_AXIS_MAX";
                        public static final String COLUMN_AXIS_EXPRESSION = "COLUMN_AXIS_EXPRESSION";
                }
                
                public static class Series {
                        
                        public static final String COLUMN_SERIES_LABEL = "COLUMN_SERIES_LABEL";
                        public static final String COLUMN_SERIES_COLOR = "COLUMN_SERIES_COLOR";
                        public static final String COLUMN_SERIES_MARKER = "COLUMN_SERIES_MARKER";
                        public static final String COLUMN_SERIES_LINE_STYLE = "COLUMN_SERIES_LINE_STYLE";
                        public static final String COLUMN_SERIES_LINE_THICKNESS = "COLUMN_SERIES_LINE_THICKNESS";
                        public static final String COLUMN_SERIES_AXIS_SELECT = "COLUMN_SERIES_AXIS_SELECT";
                }
        }


        public static final class PlotData implements BaseColumns {

                public static final String VND_TYPE_DECLARATION = "vnd.com.googlecode.chartdroid.graphable";

                // ==== CONTENT TYPES ====

                public static final String CONTENT_TYPE_PLOT_DATA = ContentResolver.CURSOR_DIR_BASE_TYPE + '/' + VND_TYPE_DECLARATION;
                public static final String CONTENT_TYPE_ITEM_PLOT_DATA = ContentResolver.CURSOR_ITEM_BASE_TYPE + '/' + VND_TYPE_DECLARATION;
        }


        public static final class EventData implements BaseColumns {

                public static final String VND_TYPE_DECLARATION = "vnd.com.googlecode.chartdroid.timeline";

                // ==== CONTENT TYPES ====

                public static final String CONTENT_TYPE_PLOT_DATA = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + VND_TYPE_DECLARATION;
                public static final String CONTENT_TYPE_ITEM_PLOT_DATA = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + VND_TYPE_DECLARATION;
        }
}

