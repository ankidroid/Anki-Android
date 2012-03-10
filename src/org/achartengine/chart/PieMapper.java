package org.achartengine.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.achartengine.model.Point;
import org.achartengine.model.SeriesSelection;

/**
 * PieChart Segment Selection Management.
 */
public class PieMapper implements Serializable {

  private List<PieSegment> mPieSegmentList = new ArrayList<PieSegment>();

  private int mPieChartRadius;

  private int mCenterX, mCenterY;

  /**
   * Set PieChart location on screen.
   * 
   * @param pieRadius
   * @param centerX
   * @param centerY
   */
  public void setDimensions(int pieRadius, int centerX, int centerY) {
    mPieChartRadius = pieRadius;
    mCenterX = centerX;
    mCenterY = centerY;
  }

  /**
   * If we have all PieChart Config then there is no point in reloading it
   * 
   * @param datasetSize
   * @return true if cfg for each segment is present
   */
  public boolean areAllSegmentPresent(int datasetSize) {
    return mPieSegmentList.size() == datasetSize;
  }
  
  /**
   * Add configuration for a PieChart Segment
   * 
   * @param dataIndex
   * @param value
   * @param startAngle
   * @param angle
   */
  public void addPieSegment(int dataIndex, float value, float startAngle, float angle) {
    mPieSegmentList.add(new PieSegment(dataIndex, value, startAngle, angle));
  }
  
  /**
   * Clears the pie segments list.
   */
  public void clearPieSegments() {
    mPieSegmentList.clear();
  }

  /**
   * Fetches angle relative to pie chart center point where 3 O'Clock is 0 and
   * 12 O'Clock is 270degrees
   * 
   * @param screenPoint
   * @return angle in degress from 0-360.
   */
  public double getAngle(Point screenPoint) {
    double dx = screenPoint.getX() - mCenterX;
    // Minus to correct for coord re-mapping
    double dy = -(screenPoint.getY() - mCenterY);

    double inRads = Math.atan2(dy, dx);

    // We need to map to coord system when 0 degree is at 3 O'clock, 270 at 12
    // O'clock
    if (inRads < 0)
      inRads = Math.abs(inRads);
    else
      inRads = 2 * Math.PI - inRads;

    return Math.toDegrees(inRads);
  }

  /**
   * Checks if Point falls within PieChart
   * 
   * @param screenPoint
   * @return true if in PieChart
   */
  public boolean isOnPieChart(Point screenPoint) {
    // Using a bit of Pythagoras
    // inside circle if (x-center_x)**2 + (y-center_y)**2 <= radius**2:

    double sqValue = (Math.pow(mCenterX - screenPoint.getX(), 2) + Math.pow(
        mCenterY - screenPoint.getY(), 2));

    double radiusSquared = mPieChartRadius * mPieChartRadius;
    boolean isOnPieChart = sqValue <= radiusSquared;
    return isOnPieChart;
  }

  /**
   * Fetches the SeriesSelection for the PieSegment selected.
   * 
   * @param screenPoint - the user tap location
   * @return null if screen point is not in PieChart or its config if it is
   */
  public SeriesSelection getSeriesAndPointForScreenCoordinate(Point screenPoint) {
    if (isOnPieChart(screenPoint)) {
      double angleFromPieCenter = getAngle(screenPoint);

      for (PieSegment pieSeg : mPieSegmentList) {
        if (pieSeg.isInSegment(angleFromPieCenter)) {
          return new SeriesSelection(0, pieSeg.getDataIndex(), pieSeg.getValue(),
              pieSeg.getValue());
        }
      }
    }
    return null;
  }
}
