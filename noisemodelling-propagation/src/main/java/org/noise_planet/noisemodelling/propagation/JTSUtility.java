/**
 * NoiseMap is a scientific computation plugin for OrbisGIS developed in order to
 * evaluate the noise impact on urban mobility plans. This model is
 * based on the French standard method NMPB2008. It includes traffic-to-noise
 * sources evaluation and sound propagation processing.
 *
 * This version is developed at French IRSTV Institute and at IFSTTAR
 * (http://www.ifsttar.fr/) as part of the Eval-PDU project, funded by the
 * French Agence Nationale de la Recherche (ANR) under contract ANR-08-VILL-0005-01.
 *
 * Noisemap is distributed under GPL 3 license. Its reference contact is Judicaël
 * Picaut <judicael.picaut@ifsttar.fr>. It is maintained by Nicolas Fortin
 * as part of the "Atelier SIG" team of the IRSTV Institute <http://www.irstv.fr/>.
 *
 * Copyright (C) 2011 IFSTTAR
 * Copyright (C) 2011-2012 IRSTV (FR CNRS 2488)
 *
 * Noisemap is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Noisemap is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Noisemap. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.noise_planet.noisemodelling.propagation;

import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Nicolas Fortin
 */
public class JTSUtility {
    /**
     * Utility class
     **/
    private JTSUtility() {}

    /**
     * Create a linear interpolation of the provided control points.
     * @param points Array of points
     * @param delta Distance between interval points
     * @return Interpolated points
     */
    public static Coordinate[] splitMultiPointsInRegularPoints(
            Coordinate[] points, double delta) {
        GeometryFactory gf = new GeometryFactory();
        if (points.length == 0) {
            return null;
        }
        if (points.length == 1) {
            return points;
        }
        // If the distance between the first and the last point is smaller than
        // delta, then return the avg point
        LineString Line = gf.createLineString(points);
        Double length = Line.getLength();
        if (length < delta) {
            if (points.length == 2) {
                return new Coordinate[] { new Coordinate(
                        (points[1].x + points[0].x) / 2.,
                        (points[1].y + points[0].y) / 2.,
                        (points[1].y + points[0].y) / 2.) };
            } else {
                return Line.getInteriorPoint().getCoordinates();
            }
        }
        // We can't set an absolute value, but we can provide a way to compute
        // a delta value that will build a regular set of points with the
        // constraint of a maximum delta value.
        Double ModifiedDelta = length / Math.ceil(length / delta);
        // The line length is greater than Delta, then we will create a set of
        // points where each point lies at the distance of the modified delta
        int nbpts = (int) Math.ceil(length / delta);
        Coordinate[] deltaPoints = new Coordinate[nbpts];
        int pts = 0;
        int cursorPts = 1; // This is the navigation cursor inside the points
        // array
        Coordinate refpt = points[0];
        Double distToNextPt = ModifiedDelta;
        while (pts < nbpts) {
            Coordinate nextbldpts;
            Double distToNextControlPt = refpt.distance(points[cursorPts]);
            while (distToNextControlPt < distToNextPt
                    && points.length != cursorPts + 1) {
                // The distance with the next control point is not sufficient to
                // build a new point
                distToNextPt -= distToNextControlPt;
                refpt = points[cursorPts];
                cursorPts++;
                distToNextControlPt = refpt.distance(points[cursorPts]);
            }
            if (distToNextControlPt >= distToNextPt) {
                // AB Normalized vector
                Coordinate B = points[cursorPts];
                Double ABlen = B.distance(refpt);
                Coordinate AB = new Coordinate((B.x - refpt.x) / ABlen, (B.y - refpt.y)
                        / ABlen, (B.z - refpt.z) / ABlen);
                // Compute intermediate P vector
                Coordinate P = new Coordinate(refpt.x + AB.x * distToNextPt, refpt.y
                        + AB.y * distToNextPt, refpt.z + AB.z * distToNextPt);
                nextbldpts = P;
            } else {
                nextbldpts = points[cursorPts];
            }
            refpt = nextbldpts;
            deltaPoints[pts] = nextbldpts;
            pts++;
            distToNextPt = ModifiedDelta;
        }
        return deltaPoints;
    }

    /**
     * Compute a and b linear function of the line p1 p2
     * @param p1 p1
     * @param p2 p2 with p2.x != p1.x
     * @return [a,b] linear function parameters.
     */
    public static double[] getLinearFunction(Coordinate p1, Coordinate p2) {
        if(Double.compare(p1.x, p2.x) == 0) {
            throw new IllegalArgumentException("X value must be different to compute linear function parameters");
        }
        double a = (p2.y - p1.y) / (p2.x - p1.x);
        double b = (p2.x * p1.y - p1.x * p2.y) / (p2.x - p1.x);
        return new double[]{a, b};
    }

    /**
     * NFS 31-133 P.69 Annex E
     * @param xzList Line coordinates in the same plan of the line formed by the first and the last point.
     *                           X must be incremental.
     * @return [a,b] Linear function parameters produced by least square regression of provided points.
     */
    public static double[] getLinearRegressionPolyline(List<Coordinate> xzList) {
        // Linear regression
        SimpleRegression simpleRegression = new SimpleRegression();
        for(Coordinate p : xzList) {
            simpleRegression.addData(p.x, p.y);
        }
        return new double[] {simpleRegression.getSlope(), simpleRegression.getIntercept()};
    }

    /**
     * Project point on y=ax+b plane
     * @param a a plane parameter
     * @param b b plane parameter
     * @param point Point to project
     * @return Projected point
     */
    public static Coordinate makeProjectedPoint(double a, double b, Coordinate point) {
        LineSegment plane = new LineSegment(new Coordinate(point.x - 1,
                a*(point.x - 1)+b),new Coordinate(point.x + 1,a*(point.x + 1)+b));
        return plane.project(point);
    }

    /**
     * Make image (like line y=ax+b would be a mirror) of point
     * @param a a linear parameter
     * @param b b linear parameter
     * @param point Point to project
     * @return Mirrored point position
     */
    public static Coordinate makePointImage(double a, double b, Coordinate point) {
        LineSegment pointPlane = new LineSegment(point, makeProjectedPoint(a,b, point));
        return pointPlane.pointAlong(2);
    }

    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D with new x' computed by algorithm and y' is original height of point.
     * Attention this function can just be used when the points in the same plane.
     * {@link "http://en.wikipedia.org/wiki/Rotation_matrix"}
     * @param  listPoints X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static List<Coordinate> getNewCoordinateSystem(List<Coordinate> listPoints) {
        List<Coordinate> newCoord = new ArrayList<>(listPoints.size());
        //get angle by ray source-receiver with the X-axis.
        double angle = new LineSegment(listPoints.get(0), listPoints.get(listPoints.size() - 1)).angle();
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        for (Coordinate listPoint : listPoints) {
            double newX = (listPoint.x - listPoints.get(0).x) * cos +
                    (listPoint.y - listPoints.get(0).y) * sin;
            newCoord.add(new Coordinate(newX, listPoint.z));
        }
        return newCoord;
    }

    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D with new x' computed by algorithm and y' is original height of point.
     * Attention this function can just be used when the points in the same plane.
     * {@link "http://en.wikipedia.org/wiki/Rotation_matrix"}
     * @param  listPoints X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static List<Coordinate> getOldCoordinateSystemList(List<Coordinate> listPoints, double angle) {
        List<Coordinate> newCoord = new ArrayList<>(listPoints.size());
        //get angle by ray source-receiver with the X-axis.
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        for (Coordinate listPoint : listPoints) {
            double newX = (listPoint.x - listPoints.get(listPoints.size()-1).x) *-cos;
            double newY = (listPoint.x - listPoints.get(listPoints.size()-1).x) *sin;

            newCoord.add(new Coordinate(newX,newY, listPoint.y));
        }
        return newCoord;
    }


    /**
     * ChangeCoordinateSystem, use original coordinate in 3D to change into a new markland in 2D with new x' computed by algorithm and y' is original height of point.
     * Attention this function can just be used when the points in the same plane.
     * {@link "http://en.wikipedia.org/wiki/Rotation_matrix"}
     * @param  Point X Y Z points, all should be on the same plane as first and last points.
     * @return X Z projected points
     */
    public static Coordinate getOldCoordinateSystem(Coordinate Point, double angle) {
        //get angle by ray source-receiver with the X-axis.
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        return new Coordinate( (Point.x ) *cos, (Point.x ) *sin, Point.y);
    }

    /**
     * @param coordinates Coordinates
     * @return Parts of the clock-wise ConvexHull where x value are increasing from the minimum X value
     */
    public static List<Coordinate> getXAscendingHullPoints(Coordinate[] coordinates) {
        ConvexHull convexHull = new ConvexHull(coordinates, new GeometryFactory());
        Geometry hullGeom = convexHull.getConvexHull();
        Coordinate[] hull = hullGeom.getCoordinates();
        if(Orientation.isCCW(hull)) {
            Coordinate temp;
            for (int i = 0; i < hull.length / 2; i++) {
                temp = hull[i];
                hull[i] = hull[hull.length - 1 - i];
                hull[hull.length - 1 - i] = temp;
            }
        }
        // Find minimal x index
        int index = -1;
        double minX = Double.MAX_VALUE;
        for(int i=0; i < hull.length; i++) {
            if(hull[i].x < minX) {
                index = i;
                minX = hull[i].x;
            }
        }
        List<Coordinate> offsetHull = new ArrayList<>(hull.length);
        double lastX = Double.NEGATIVE_INFINITY;
        for(int i = index; i < hull.length; i++) {
            if(hull[i].x > lastX) {
                offsetHull.add(hull[i]);
                lastX = hull[i].x;
            } else {
                break;
            }
        }
        for(int i = 0; i < index; i++) {
            if(hull[i].x > lastX) {
                offsetHull.add(hull[i]);
                lastX = hull[i].x;
            } else {
                break;
            }
        }
        return offsetHull;
    }

}