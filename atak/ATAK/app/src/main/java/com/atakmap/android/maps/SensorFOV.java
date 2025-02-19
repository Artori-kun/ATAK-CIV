
package com.atakmap.android.maps;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SensorFOV extends Shape implements MapItem.OnGroupChangedListener,
        MapItem.OnVisibleChangedListener {
    private final static String TAG = "SensorFOV";

    private Arrow arrow;

    private GeoPointMetaData _point = GeoPointMetaData
            .wrap(GeoPoint.ZERO_POINT);
    private float _azimuth = 0f;
    private float _fov = 45f;
    private float _extent = 100f;
    private float _rangeLines = 100f;
    private boolean _labels = false;
    private String labelL = null;
    private String labelR = null;

    private float _red = 1f;
    private float _green = 1f;
    private float _blue = 1f;
    private float _alpha = 0.3f;

    private final ConcurrentLinkedQueue<OnMetricsChangedListener> _onChanged = new ConcurrentLinkedQueue<>();

    public SensorFOV(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
        setLabels(false);
        arrow = new Arrow(uid + "_arrow");
        arrow.setMetaBoolean("nevercot", true);
        arrow.setMetaBoolean("addToObjList", false);
        arrow.setClickable(false);
        arrow.setStrokeColor(Color.argb(20, 211, 211, 211));
        arrow.setStrokeWeight(1f);
        arrow.setText("");
        arrow.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
        addOnGroupChangedListener(this);
        addOnVisibleChangedListener(this);
    }

    public SensorFOV(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        group.addItem(arrow);
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        group.removeItem(arrow);
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        arrow.setVisible(item.getVisible());
        onMetricsChanged();
    }

    @Override
    public GeoPointMetaData getCenter() {
        return _point;
    }

    @Override
    public GeoPoint[] getPoints() {
        if (_point == null)
            return new GeoPoint[0];

        return new GeoPoint[] {
                _point.get(),
                DistanceCalculations.metersFromAtBearing(_point.get(), _extent,
                        _azimuth - (_fov / 2.0f)),
                DistanceCalculations.metersFromAtBearing(_point.get(), _extent,
                        _azimuth + (_fov / 2.0f))
        };

    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return GeoPointMetaData.wrap(getPoints());
    }

    /**
     * When setting the metrics that define the sensor, please pay attention to the
     * units of the various items:
     * @param azimuth  the azimuth is in Degrees True
     * @param fov field of view in angular degrees
     * @param extent in meters.
     */
    public void setMetrics(float azimuth, float fov, float extent) {
        this.setMetrics(azimuth, fov, extent, false, 100);
    }

    /**
     * When setting the metrics that define the sensor, please pay attention to the 
     * units of the various items:
     * @param azimuth  the azimuth is in Degrees True
     * @param fov field of view in angular degrees
     * @param extent in meters.
     * @param bLabels display FoV edges/angles as labels
     * @param rangeLines spacing for range lines in meters
     */
    public void setMetrics(float azimuth, float fov, float extent,
            boolean bLabels, float rangeLines) {
        Log.d(TAG, "setMetrics: " + bLabels + ", rangeLines: " + rangeLines);

        _azimuth = azimuth;
        _fov = fov;
        _extent = extent;
        _rangeLines = rangeLines;
        setLabels(bLabels);
        arrow.setPoint1(_point);
        arrow.setPoint2(GeoPointMetaData
                .wrap(DistanceCalculations.metersFromAtBearing(_point.get(),
                        _extent,
                        _azimuth)));

        onMetricsChanged();
        this.onPointsChanged();
    }

    private void setLabels(boolean bLabels) {
        _labels = bLabels;

        if (_labels) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(MapView._mapView.getContext());
            NorthReference northReference = NorthReference
                    .findFromValue(Integer.parseInt(prefs
                            .getString("rab_north_ref_pref",
                                    String.valueOf(NorthReference.MAGNETIC
                                            .getValue()))));

            Angle bearingUnits = Angle
                    .findFromValue(Integer.parseInt(prefs.getString(
                            "rab_brg_units_pref",
                            String.valueOf(Angle.DEGREE.getValue()))));

            //set left label
            double l0 = (_azimuth - _fov / 2);
            //            if (_northReference == NorthReference.GRID) {
            //                double gridConvergence = ATAKUtilities
            //                        .computeGridConvergence(_point1.get(), _point2.get());
            //                labelL = AngleUtilities.format(AngleUtilities.wrapDeg(
            //                        _bearing - gridConvergence), _bearingUnits) + "G";
            //            } else
            if (northReference == NorthReference.MAGNETIC) {
                double bearingMag = ATAKUtilities.convertFromTrueToMagnetic(
                        _point.get(), l0);
                labelL = AngleUtilities.format(bearingMag, bearingUnits) + "M";
            } else {
                labelL = AngleUtilities.format(l0, bearingUnits) + "T";
            }

            //set right lable
            l0 = (_azimuth + _fov / 2);
            //            if (_northReference == NorthReference.GRID) {
            //                double gridConvergence = ATAKUtilities
            //                        .computeGridConvergence(_point1.get(), _point2.get());
            //                labelR = AngleUtilities.format(AngleUtilities.wrapDeg(
            //                        _bearing - gridConvergence), _bearingUnits) + "G";
            //            } else
            if (northReference == NorthReference.MAGNETIC) {
                double bearingMag = ATAKUtilities.convertFromTrueToMagnetic(
                        _point.get(), l0);
                labelR = AngleUtilities.format(bearingMag, bearingUnits) + "M";
            } else {
                labelR = AngleUtilities.format(l0, bearingUnits) + "T";
            }
        } else {
            labelL = null;
            labelR = null;
        }
        onMetricsChanged();
    }

    public void setAlpha(float alpha) {
        _alpha = MathUtils.clamp(alpha, 0, 1);
        onFillColorChanged();
    }

    /**
     * set the percentage of each primary color 
     * @param r - the decimal value of the percentage of red (between 0-1)
     * @param g - the decimal value of the percentage of green (between 0-1)
     * @param b - the decimal value of the percentage of blue (between 0-1)
     */
    public void setColor(float r, float g, float b) {
        _red = MathUtils.clamp(r, 0, 1);
        _green = MathUtils.clamp(g, 0, 1);
        _blue = MathUtils.clamp(b, 0, 1);
        onFillColorChanged();
    }

    @Override
    public void setFillColor(int fillColor) {
        _red = Color.red(fillColor) / 255f;
        _green = Color.green(fillColor) / 255f;
        _blue = Color.blue(fillColor) / 255f;
        _alpha = Color.alpha(fillColor) / 255f;
        onFillColorChanged();
    }

    @Override
    public int getFillColor() {
        return Color.argb((int) (255 * _alpha),
                (int) (255 * _red),
                (int) (255 * _green),
                (int) (255 * _blue));
    }

    public void addOnMetricsChangedListener(OnMetricsChangedListener l) {
        _onChanged.add(l);
    }

    public void removeOnMetricsChangedListener(OnMetricsChangedListener l) {
        _onChanged.remove(l);
    }

    protected void onMetricsChanged() {
        for (OnMetricsChangedListener l : _onChanged) {
            l.onMetricsChanged(this);
        }
    }

    public interface OnMetricsChangedListener {
        void onMetricsChanged(SensorFOV fov);
    }

    public float getAzimuth() {
        return _azimuth;
    }

    public float getFOV() {
        return _fov;
    }

    public float getExtent() {
        return _extent;
    }

    public float getRangeLines() {
        return _rangeLines;
    }

    public boolean isShowLabels() {
        return _labels;
    }

    private boolean isDrawLabels() {
        return _labels && !FileSystemUtils.isEmpty(labelL)
                && !FileSystemUtils.isEmpty(labelR);
    }

    public String getLabelL() {
        return labelL;
    }

    public String getLabelR() {
        return labelR;
    }

    public GeoPointMetaData getPoint() {
        return _point;
    }

    /**
     * @return Return an array of values representing the color and alpha of the FOV view.
     * Each element is the decimal representation of the percentage of that value.
     * The order of the values in the array is as follows {red, green, blue, alpha}
     */
    public float[] getColor() {
        return new float[] {
                _red, _green, _blue, _alpha
        };
    }

    /**
     * set the color of the field of view cone.
     * @param color the color in ARGB format.
     */
    @Override
    public void setColor(int color) {
        float a = ((0xFF000000 & color) >> 16) / 256f;
        float r = ((0x00FF0000 & color) >> 16) / 256f;
        float g = ((0x0000FF00 & color) >> 8) / 256f;
        float b = ((0x000000FF & color)) / 256f;
        setAlpha(a);
        setColor(r, g, b);
    }

    public void setPoint(GeoPointMetaData point) {
        if (point == null) {
            point = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
        }
        _point = point;
        arrow.setPoint1(_point);
        arrow.setPoint2(GeoPointMetaData
                .wrap(DistanceCalculations.metersFromAtBearing(_point.get(),
                        _extent,
                        _azimuth)));
        onPointsChanged();
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        List<GeoPoint> points = new ArrayList<>();
        if (_point != null) {
            // Include center and cone edges
            points.addAll(Arrays.asList(getPoints()));
            // Include arrow tip
            points.add(DistanceCalculations.metersFromAtBearing(
                    _point.get(), _extent, _azimuth));
            // Include max bounds covered by the FOV
            double minDeg = _azimuth - (_fov / 2), maxDeg = _azimuth
                    + (_fov / 2);
            for (int deg = 0; deg < 360; deg += 90) {
                if (angleWithin(deg, minDeg, maxDeg))
                    points.add(DistanceCalculations.metersFromAtBearing(
                            _point.get(), _extent, deg));
            }
        }
        GeoPoint[] p = points.toArray(new GeoPoint[0]);
        if (bounds != null) {
            bounds.set(p);
            return bounds;
        } else {
            return GeoBounds.createFromPoints(p);
        }
    }

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Store forward returns for all points
        Bundle data = new Bundle();
        if (_point != null && _point.get().isValid()) {
            PointF top = capture.forward(DistanceCalculations
                    .metersFromAtBearing(_point.get(), _extent, 0));
            PointF right = capture.forward(DistanceCalculations
                    .metersFromAtBearing(_point.get(), _extent, 90));
            PointF bottom = capture.forward(DistanceCalculations
                    .metersFromAtBearing(_point.get(), _extent, 180));
            PointF left = capture.forward(DistanceCalculations
                    .metersFromAtBearing(_point.get(), _extent, 270));
            RectF oval = new RectF(left.x, top.y, right.x, bottom.y);
            data.putParcelable("oval", oval);
            Bundle arrowBundle = arrow.preDrawCanvas(capture);
            if (arrowBundle != null)
                data.putSerializable("arrow", arrowBundle
                        .getSerializable("points"));

            if (isDrawLabels()) {
                // Include left label
                PointF p0 = capture.forward(_point.get());
                GeoPoint gp1 = DistanceCalculations
                        .metersFromAtBearing(_point.get(), _extent,
                                _azimuth - _fov / 2);

                PointF p1 = capture.forward(gp1);
                MapView mv = MapView.getMapView();
                GeoPointMetaData c = GeoPointMetaData
                        .wrap(GeoCalculations.midPointCartesian(_point.get(),
                                gp1, mv != null
                                        && mv.isContinuousScrollEnabled()));

                float deg = CanvasHelper.angleTo(p0, p1) + 90;
                PointA pa = new PointA(capture.forward(c.get()), deg);
                data.putParcelable("labelPointL", pa);

                // Include right label
                PointF rp0 = capture.forward(_point.get());
                GeoPoint rgp1 = DistanceCalculations
                        .metersFromAtBearing(_point.get(), _extent,
                                _azimuth + _fov / 2);

                PointF rp1 = capture.forward(rgp1);
                GeoPointMetaData rc = GeoPointMetaData
                        .wrap(GeoCalculations.midPointCartesian(_point.get(),
                                rgp1, mv != null
                                        && mv.isContinuousScrollEnabled()));

                float rdeg = CanvasHelper.angleTo(rp0, rp1) + 90;
                PointA rpa = new PointA(capture.forward(rc.get()), rdeg);
                data.putParcelable("labelPointR", rpa);
            }
        }

        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        RectF oval = data.getParcelable("oval");
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        if (oval != null) {
            RectF ovalFull = new RectF(dr * oval.left, dr * oval.top,
                    dr * oval.right, dr * oval.bottom);

            // Draw cone fill
            int fill = getFillColor();
            if (Color.alpha(fill) > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fill);
                can.drawArc(ovalFull, _azimuth - 90 - _fov / 2, _fov, true,
                        paint);
            }

            // Draw cone stroke
            int stroke = getStrokeColor();
            if (Color.alpha(stroke) > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(stroke);
                paint.setStrokeWidth((float) (getStrokeWeight()
                        * cap.getLineWeight()));
                can.drawArc(ovalFull, _azimuth - 90 - _fov / 2, _fov, true,
                        paint);
            }
        }
        PointF[] p = (PointF[]) data.getSerializable("arrow");
        if (p != null) {
            // Draw arrow
            int col = arrow.getStrokeColor();
            float arrowWidth = (float) (getStrokeWeight() + 2f)
                    * cap.getLineWeight();
            paint.setStyle(Paint.Style.STROKE);
            path.moveTo(dr * p[0].x, dr * p[0].y);
            for (int i = 1; i < p.length; i++) {
                if (p[i] == null)
                    continue;
                path.lineTo(dr * p[i].x, dr * p[i].y);
            }
            // Draw black outline first
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(arrowWidth + dr * cap.getLineWeight());
            can.drawPath(path, paint);

            // Draw arrow itself
            paint.setColor(Color.rgb(Color.red(col),
                    Color.green(col), Color.blue(col)));
            paint.setStrokeWidth(arrowWidth);
            can.drawPath(path, paint);
            path.reset();
        }

        if (isDrawLabels()) {
            // Draw label L
            PointA labelPoint = data.getParcelable("labelPointL");
            if (labelPoint != null) { //TODO  && cap.shouldDrawLabel(label, points))
                cap.drawLabel(labelL, labelPoint);
            }

            // Draw label R
            PointA labelPointR = data.getParcelable("labelPointR");
            if (labelPointR != null) { //TODO  && cap.shouldDrawLabel(label, points))
                cap.drawLabel(labelR, labelPointR);
            }

            //TODO draw range lines
        }
    }

    public static boolean angleWithin(double deg, double minDeg,
            double maxDeg) {
        minDeg = CanvasHelper.deg360(minDeg);
        maxDeg = CanvasHelper.deg360(maxDeg);
        deg = CanvasHelper.deg360(deg);
        if (maxDeg < minDeg) {
            if (deg < maxDeg)
                deg += 360;
            maxDeg += 360;
        }
        return deg >= minDeg && deg <= maxDeg;
    }
}
