/*
 * Copyright 2006-2008 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peakpicking.twostep.massdetection.exactmass;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Vector;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.desktop.Desktop;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peakpicking.twostep.massdetection.MassDetector;
import net.sf.mzmine.modules.peakpicking.twostep.massdetection.MzPeak;
import net.sf.mzmine.modules.peakpicking.twostep.peakmodel.PeakModel;
import net.sf.mzmine.util.Range;

public class ExactMassDetector implements MassDetector {

	// parameter values
	private float noiseLevel, basePeakIntensity;
	private int resolution;
	private boolean cleanLateral;
	private String peakModelname;

	private DataPoint scanDataPoints[];
	private Vector<MzPeak> mzPeaks;
	
	private PeakModel peakModel, peakModelofBiggestPeak;

	// Desktop
	private Desktop desktop = MZmineCore.getDesktop();


	public ExactMassDetector(ExactMassDetectorParameters parameters) {
		noiseLevel = (Float) parameters
				.getParameterValue(ExactMassDetectorParameters.noiseLevel);
		resolution = (Integer) parameters
				.getParameterValue(ExactMassDetectorParameters.resolution);
		cleanLateral = (Boolean) parameters
				.getParameterValue(ExactMassDetectorParameters.cleanLateral);
		peakModelname = (String) parameters
				.getParameterValue(ExactMassDetectorParameters.peakModel);
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.sf.mzmine.modules.peakpicking.twostep.massdetection.MassDetector#getMassValues(net.sf.mzmine.data.Scan)
	 */
	public MzPeak[] getMassValues(Scan scan) {
		scanDataPoints = scan.getDataPoints();
		int startCurrentPeak = 0, endCurrentPeak = 0;
		mzPeaks = new Vector<MzPeak>();
		int length = scanDataPoints.length - 1;
		Vector<Integer> localMinimum = new Vector<Integer>();
		Vector<Integer> localMaximum = new Vector<Integer>();
		boolean top = true;
		basePeakIntensity = scan.getBasePeak().getIntensity();

		float[] intensityValues = new float[scanDataPoints.length];
		for (int i = 0; i < scanDataPoints.length; i++) {
			intensityValues[i] = scanDataPoints[i].getIntensity();
		}

		for (int ind = 1; ind <= length; ind++) {

			while ((ind < length) && (intensityValues[ind] == 0)) {
				ind++;
			}
			if (ind >= length) {
				break;
			}

			// While peak is on
			startCurrentPeak = ind - 1;
			while ((ind < length - 1) && (intensityValues[ind] > 0)) {
				// Check for all local maximum and minimum in this peak
				if (top) {
					if ((intensityValues[ind - 1] < intensityValues[ind])
							&& (intensityValues[ind] > intensityValues[ind + 1])) {
						localMaximum.add(ind);
						top = false;
					}
				} else {
					if ((intensityValues[ind - 1] > intensityValues[ind])
							&& (intensityValues[ind] < intensityValues[ind + 1])) {
						localMinimum.add(ind);
						top = true;
					}
				}
				ind++;
			}
			endCurrentPeak = ind;

			calculateExactMass(scan, localMaximum, localMinimum, startCurrentPeak,
					endCurrentPeak);

			top = true;
			localMaximum.clear();
			localMinimum.clear();
		}
		if (cleanLateral){
			removeLateralPeaks(); 
		}
		return mzPeaks.toArray(new MzPeak[0]);
	}

	/**
	 * 
	 * This function calculates the mass (m/z) giving weight to each data point
	 * of the peak using all local maximum and minimum. Also applies a filter
	 * for peaks with intensity below of noise level parameter.
	 * 
	 * @param localMaximum
	 * @param localMinimum
	 * @param start
	 * @param end
	 * @return
	 */
	private void calculateExactMass(Scan scan, Vector<Integer> localMaximum,
			Vector<Integer> localMinimum, int start, int end) {

		Vector<DataPoint> rangeDataPoints = new Vector<DataPoint>();

		float sumMz = 0;
		float sumIntensities = 0;

		if ((localMinimum.isEmpty()) && (localMaximum.size() == 1)) {
			// Filter of noise level
			float intensity = scanDataPoints[localMaximum.firstElement()]
					.getIntensity();
			if (intensity > noiseLevel) {
				for (int i = start; i < end; i++) {
					sumMz += scanDataPoints[i].getMZ()
							* scanDataPoints[i].getIntensity();
					sumIntensities += scanDataPoints[i].getIntensity();
					rangeDataPoints.add(scanDataPoints[i]);
				}
				float exactMz = sumMz / sumIntensities;
				mzPeaks.add(new MzPeak(scan, new SimpleDataPoint(exactMz, intensity), rangeDataPoints
						.toArray(new DataPoint[0])));
				rangeDataPoints.clear();
			}
			return;
		} else {
			Iterator<Integer> maximum = localMaximum.iterator();
			Iterator<Integer> minimum = localMinimum.iterator();
			if (!localMinimum.isEmpty()) {
				int tempStart = start;
				int tempEnd = minimum.next();
				while (maximum.hasNext()) {
					int index = maximum.next();
					sumMz = 0;
					sumIntensities = 0;
					// Filter of noise level
					float intensity = scanDataPoints[index].getIntensity();
					if (intensity > noiseLevel) {
						for (int i = tempStart; i <= tempEnd; i++) {
							sumMz += scanDataPoints[i].getMZ()
									* scanDataPoints[i].getIntensity();
							sumIntensities += scanDataPoints[i].getIntensity();
							rangeDataPoints.add(scanDataPoints[i]);
						}
						float exactMz = sumMz / sumIntensities;
						mzPeaks.add(new MzPeak(scan, new SimpleDataPoint(exactMz, intensity),
								rangeDataPoints.toArray(new DataPoint[0])));
						rangeDataPoints.clear();
					}
					tempStart = tempEnd;
					if (minimum.hasNext())
						tempEnd = minimum.next();
					else
						tempEnd = end;

				}
			}
			return;
		}
	}

	/**
	 * 
	 * This function calculates the base peak width with a fixed mass resolution
	 * (percentageResolution). After eliminates the encountered lateral peaks in
	 * this range, with a height value less than defined height percentage of
	 * central peak (percentageHeight).
	 * 
	 * @param mzPeaks
	 * @param percentageHeight
	 * @param percentageResolution
	 */
	private void removeLateralPeaks() { 
		
		Constructor peakModelConstruct;
		Class peakModelClass;
		int peakModelindex = 0;
		for (String model: ExactMassDetectorParameters.peakModelNames){
			if (model.equals(peakModelname))
				break;
			peakModelindex++;
		}
		
		String peakModelClassName = ExactMassDetectorParameters.peakModelClasses[peakModelindex];

		MzPeak[] arrayMzPeak = mzPeaks.toArray(new MzPeak[0]);
		for (MzPeak currentMzPeak : arrayMzPeak) {
			
			if (currentMzPeak.getIntensity() < noiseLevel)
				continue;

			try {
				peakModelClass = Class.forName(peakModelClassName);
				peakModelConstruct = peakModelClass
						.getConstructors()[0];
				peakModel = (PeakModel) peakModelConstruct.newInstance(currentMzPeak.getMZ(), 
						currentMzPeak.getIntensity(), resolution);
				peakModelofBiggestPeak = (PeakModel) peakModelConstruct.newInstance(currentMzPeak.getMZ(), 
						basePeakIntensity, resolution);
			} catch (Exception e) {
				desktop
						.displayErrorMessage("Error trying to make an instance of peak model "
								+ peakModelClassName);
				return;
			}

			Range rangePeak = peakModel.getBasePeakWidth();
			Range rangeNoise = peakModelofBiggestPeak.getBasePeakWidth();

			Iterator<MzPeak> anotherIteratorMzPeak = mzPeaks.iterator();
			while (anotherIteratorMzPeak.hasNext()) {
				MzPeak comparedMzPeak = anotherIteratorMzPeak.next();

				if ((comparedMzPeak.getMZ() < rangeNoise.getMin()) && (comparedMzPeak.getIntensity() < noiseLevel)){
					anotherIteratorMzPeak.remove();
				}
						
				if ((comparedMzPeak.getMZ() >= rangePeak.getMin())
						&& (comparedMzPeak.getMZ() <= rangePeak.getMax())
						&& (comparedMzPeak.getIntensity() < peakModel.getIntensity(comparedMzPeak.getMZ()))) {
					anotherIteratorMzPeak.remove();
				}
				if (comparedMzPeak.getMZ() > rangePeak.getMax())
					break;
			}
		}

	}
}
