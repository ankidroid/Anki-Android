/**
 * Copyright (C) 2009, 2010 SC 4ViewSoft SRL
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.achartengine;

import org.achartengine.chart.BarChart;
import org.achartengine.chart.BarChart.Type;
import org.achartengine.chart.BubbleChart;
import org.achartengine.chart.CombinedXYChart;
import org.achartengine.chart.CubicLineChart;
import org.achartengine.chart.DialChart;
import org.achartengine.chart.DoughnutChart;
import org.achartengine.chart.LineChart;
import org.achartengine.chart.PieChart;
import org.achartengine.chart.RangeBarChart;
import org.achartengine.chart.ScatterChart;
import org.achartengine.chart.TimeChart;
import org.achartengine.chart.XYChart;
import org.achartengine.model.CategorySeries;
import org.achartengine.model.MultipleCategorySeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.DialRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;

import android.content.Context;
import android.content.Intent;

/**
 * Utility methods for creating chart views or intents.
 */
public class ChartFactory {
  /** The key for the chart data. */
  public static final String CHART = "chart";

  /** The key for the chart graphical activity title. */
  public static final String TITLE = "title";

  private ChartFactory() {
    // empty for now
  }

  /**
   * Creates a line chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a line chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getLineChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer) {
    checkParameters(dataset, renderer);
    XYChart chart = new LineChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a cubic line chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a line chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getCubeLineChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, float smoothness) {
    checkParameters(dataset, renderer);
    XYChart chart = new CubicLineChart(dataset, renderer, smoothness);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a scatter chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a scatter chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getScatterChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer) {
    checkParameters(dataset, renderer);
    XYChart chart = new ScatterChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a bubble chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a scatter chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getBubbleChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer) {
    checkParameters(dataset, renderer);
    XYChart chart = new BubbleChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a time chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param format the date format pattern to be used for displaying the X axis
   *          date labels. If null, a default appropriate format will be used.
   * @return a time chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getTimeChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, String format) {
    checkParameters(dataset, renderer);
    TimeChart chart = new TimeChart(dataset, renderer);
    chart.setDateFormat(format);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a bar chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param type the bar chart type
   * @return a bar chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getBarChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, Type type) {
    checkParameters(dataset, renderer);
    XYChart chart = new BarChart(dataset, renderer, type);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a range bar chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param type the range bar chart type
   * @return a bar chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final GraphicalView getRangeBarChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, Type type) {
    checkParameters(dataset, renderer);
    XYChart chart = new RangeBarChart(dataset, renderer, type);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a combined XY chart view.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param types the chart types (cannot be null)
   * @return a combined XY chart graphical view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if a dataset number of items is different than the number of
   *           series renderers or number of chart types
   */
  public static final GraphicalView getCombinedXYChartView(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, String[] types) {
    if (dataset == null || renderer == null || types == null
        || dataset.getSeriesCount() != types.length) {
      throw new IllegalArgumentException(
          "Dataset, renderer and types should be not null and the datasets series count should be equal to the types length");
    }
    checkParameters(dataset, renderer);
    CombinedXYChart chart = new CombinedXYChart(dataset, renderer, types);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a pie chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @return a pie chart view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final GraphicalView getPieChartView(Context context, CategorySeries dataset,
      DefaultRenderer renderer) {
    checkParameters(dataset, renderer);
    PieChart chart = new PieChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a dial chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the dial renderer (cannot be null)
   * @return a pie chart view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final GraphicalView getDialChartView(Context context, CategorySeries dataset,
      DialRenderer renderer) {
    checkParameters(dataset, renderer);
    DialChart chart = new DialChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * Creates a doughnut chart intent that can be used to start the graphical
   * view activity.
   * 
   * @param context the context
   * @param dataset the multiple category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @return a pie chart view
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final GraphicalView getDoughnutChartView(Context context,
      MultipleCategorySeries dataset, DefaultRenderer renderer) {
    checkParameters(dataset, renderer);
    DoughnutChart chart = new DoughnutChart(dataset, renderer);
    return new GraphicalView(context, chart);
  }

  /**
   * 
   * Creates a line chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a line chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getLineChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer) {
    return getLineChartIntent(context, dataset, renderer, "");
  }

  /**
   * 
   * Creates a cubic line chart intent that can be used to start the graphical
   * view activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a line chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getCubicLineChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, float smoothness) {
    return getCubicLineChartIntent(context, dataset, renderer, smoothness, "");
  }

  /**
   * Creates a scatter chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a scatter chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getScatterChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer) {
    return getScatterChartIntent(context, dataset, renderer, "");
  }

  /**
   * Creates a bubble chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @return a scatter chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getBubbleChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer) {
    return getBubbleChartIntent(context, dataset, renderer, "");
  }

  /**
   * Creates a time chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param format the date format pattern to be used for displaying the X axis
   *          date labels. If null, a default appropriate format will be used.
   * @return a time chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getTimeChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, String format) {
    return getTimeChartIntent(context, dataset, renderer, format, "");
  }

  /**
   * Creates a bar chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param type the bar chart type
   * @return a bar chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getBarChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, Type type) {
    return getBarChartIntent(context, dataset, renderer, type, "");
  }

  /**
   * Creates a line chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title. If this is null,
   *          then the title bar will be hidden. If a blank title is passed in,
   *          then the title bar will be the default. Pass in any other string
   *          to set a custom title.
   * @return a line chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getLineChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    XYChart chart = new LineChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a line chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title. If this is null,
   *          then the title bar will be hidden. If a blank title is passed in,
   *          then the title bar will be the default. Pass in any other string
   *          to set a custom title.
   * @return a line chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getCubicLineChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, float smoothness,
      String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    XYChart chart = new CubicLineChart(dataset, renderer, smoothness);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a scatter chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a scatter chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getScatterChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    XYChart chart = new ScatterChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a bubble chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a scatter chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getBubbleChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    XYChart chart = new BubbleChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a time chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param format the date format pattern to be used for displaying the X axis
   *          date labels. If null, a default appropriate format will be used
   * @param activityTitle the graphical chart activity title
   * @return a time chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getTimeChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, String format, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    TimeChart chart = new TimeChart(dataset, renderer);
    chart.setDateFormat(format);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a bar chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param type the bar chart type
   * @param activityTitle the graphical chart activity title
   * @return a bar chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getBarChartIntent(Context context, XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer, Type type, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    BarChart chart = new BarChart(dataset, renderer, type);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a range bar chart intent that can be used to start the graphical
   * view activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param type the range bar chart type
   * @param activityTitle the graphical chart activity title
   * @return a range bar chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  public static final Intent getRangeBarChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, Type type,
      String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    RangeBarChart chart = new RangeBarChart(dataset, renderer, type);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a combined XY chart intent that can be used to start the graphical
   * view activity.
   * 
   * @param context the context
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @param types the chart types (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a combined XY chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if a dataset number of items is different than the number of
   *           series renderers or number of chart types
   */
  public static final Intent getCombinedXYChartIntent(Context context,
      XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer, String[] types,
      String activityTitle) {
    if (dataset == null || renderer == null || types == null
        || dataset.getSeriesCount() != types.length) {
      throw new IllegalArgumentException(
          "Datasets, renderers and types should be not null and the datasets series count should be equal to the types length");
    }
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    CombinedXYChart chart = new CombinedXYChart(dataset, renderer, types);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a pie chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a pie chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final Intent getPieChartIntent(Context context, CategorySeries dataset,
      DefaultRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    PieChart chart = new PieChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a doughnut chart intent that can be used to start the graphical
   * view activity.
   * 
   * @param context the context
   * @param dataset the multiple category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a pie chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final Intent getDoughnutChartIntent(Context context,
      MultipleCategorySeries dataset, DefaultRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    DoughnutChart chart = new DoughnutChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Creates a dial chart intent that can be used to start the graphical view
   * activity.
   * 
   * @param context the context
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the dial renderer (cannot be null)
   * @param activityTitle the graphical chart activity title
   * @return a dial chart intent
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  public static final Intent getDialChartIntent(Context context, CategorySeries dataset,
      DialRenderer renderer, String activityTitle) {
    checkParameters(dataset, renderer);
    Intent intent = new Intent(context, GraphicalActivity.class);
    DialChart chart = new DialChart(dataset, renderer);
    intent.putExtra(CHART, chart);
    intent.putExtra(TITLE, activityTitle);
    return intent;
  }

  /**
   * Checks the validity of the dataset and renderer parameters.
   * 
   * @param dataset the multiple series dataset (cannot be null)
   * @param renderer the multiple series renderer (cannot be null)
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset and the renderer don't include the same number of
   *           series
   */
  private static void checkParameters(XYMultipleSeriesDataset dataset,
      XYMultipleSeriesRenderer renderer) {
    if (dataset == null || renderer == null
        || dataset.getSeriesCount() != renderer.getSeriesRendererCount()) {
      throw new IllegalArgumentException(
          "Dataset and renderer should be not null and should have the same number of series");
    }
  }

  /**
   * Checks the validity of the dataset and renderer parameters.
   * 
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  private static void checkParameters(CategorySeries dataset, DefaultRenderer renderer) {
    if (dataset == null || renderer == null
        || dataset.getItemCount() != renderer.getSeriesRendererCount()) {
      throw new IllegalArgumentException(
          "Dataset and renderer should be not null and the dataset number of items should be equal to the number of series renderers");
    }
  }

  /**
   * Checks the validity of the dataset and renderer parameters.
   * 
   * @param dataset the category series dataset (cannot be null)
   * @param renderer the series renderer (cannot be null)
   * @throws IllegalArgumentException if dataset is null or renderer is null or
   *           if the dataset number of items is different than the number of
   *           series renderers
   */
  private static void checkParameters(MultipleCategorySeries dataset, DefaultRenderer renderer) {
    if (dataset == null || renderer == null
        || !checkMultipleSeriesItems(dataset, renderer.getSeriesRendererCount())) {
      throw new IllegalArgumentException(
          "Titles and values should be not null and the dataset number of items should be equal to the number of series renderers");
    }
  }

  private static boolean checkMultipleSeriesItems(MultipleCategorySeries dataset, int value) {
    int count = dataset.getCategoriesCount();
    boolean equal = true;
    for (int k = 0; k < count && equal; k++) {
      equal = dataset.getValues(k).length == dataset.getTitles(k).length;
    }
    return equal;
  }

}
