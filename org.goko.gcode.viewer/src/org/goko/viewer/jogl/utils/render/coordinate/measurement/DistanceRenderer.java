/*
 *	This file is part of Goko.
 *
 *  Goko is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Goko is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Goko.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.goko.viewer.jogl.utils.render.coordinate.measurement;

import javax.media.opengl.GL3;
import javax.vecmath.Color4f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.SI;
import org.goko.core.common.measure.SIPrefix;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Quantity;
import org.goko.core.common.measure.quantity.type.NumberQuantity;
import org.goko.core.config.GokoPreference;
import org.goko.core.log.GkLog;
import org.goko.viewer.jogl.service.AbstractCoreJoglMultipleRenderer;
import org.goko.viewer.jogl.utils.render.basic.PolylineRenderer;
import org.goko.viewer.jogl.utils.render.text.TextRenderer;

/**
 * Simple rule display
 *
 * @author PsyKo
 *
 */
public class DistanceRenderer extends AbstractCoreJoglMultipleRenderer{
	static GkLog LOG = GkLog.getLogger(DistanceRenderer.class);
	private Point3d startPoint;
	private Point3d endPoint;
	private Vector3d normal;
	private Vector3d direction;
	private Quantity<Length> length;

	private PolylineRenderer lineRenderer;
	private ArrowRenderer startArrowRenderer;
	private ArrowRenderer endArrowRenderer;
	private TextRenderer textRenderer;

	public DistanceRenderer(Point3d startPoint, Point3d endPoint, Vector3d normal , int verticalAlignement) throws GkException {
		super();
		this.startPoint = new Point3d(startPoint);
		this.endPoint = new Point3d(endPoint);
		this.direction = new Vector3d();
		this.direction.sub(endPoint, startPoint);
		this.length = NumberQuantity.of(this.direction.length(), SIPrefix.MILLI(SI.METRE));
		this.direction.normalize();
		this.normal = new Vector3d(normal);
		normal.normalize();
		Vector3d baseDirection = new Vector3d();
		baseDirection.cross(direction, normal);

		lineRenderer = new PolylineRenderer(false, new Color4f(1,1,1,1), startPoint, endPoint);
		endArrowRenderer = new ArrowRenderer(endPoint, new Vector3d(direction.x,direction.y,direction.z), baseDirection, new Color4f(1,1,1,1));
		startArrowRenderer = new ArrowRenderer(startPoint, new Vector3d(-direction.x,-direction.y,-direction.z), baseDirection, new Color4f(1,1,1,1));

		String sLength = GokoPreference.getInstance().format(length);
		double dLength = length.doubleValue();
		textRenderer = new TextRenderer(sLength, 3, new Point3d(startPoint.x + direction.x * dLength/2,startPoint.y + direction.y * dLength/2,startPoint.z + direction.z * dLength/2), this.direction, new Vector3d(-baseDirection.x,-baseDirection.y,-baseDirection.z), TextRenderer.CENTER | verticalAlignement);
		addRenderer(lineRenderer);
		addRenderer(endArrowRenderer);
		addRenderer(startArrowRenderer);
		addRenderer(textRenderer);
	}

	/** (inheritDoc)
	 * @see org.goko.viewer.jogl.service.AbstractCoreJoglRenderer#performInitialize(javax.media.opengl.GL3)
	 */
	@Override
	protected void performInitialize(GL3 gl) throws GkException {

	}


}