/*
 *
 *   Goko
 *   Copyright (C) 2013  PsyKo
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.goko.viewer.jogl.utils.render.coordinate;

import java.util.HashMap;
import java.util.Map;

import javax.media.opengl.GL3;
import javax.vecmath.Color3f;

import org.goko.core.common.exception.GkException;
import org.goko.core.controller.ICoordinateSystemAdapter;
import org.goko.core.gcode.bean.Tuple6b;
import org.goko.core.gcode.bean.commands.EnumCoordinateSystem;
import org.goko.viewer.jogl.service.AbstractCoreJoglMultipleRenderer;
import org.goko.viewer.jogl.service.AbstractCoreJoglRenderer;

import com.jogamp.opengl.util.PMVMatrix;

/**
 * Draw the XYZ axis
 *
 * @author PsyKo
 *
 */
public class CoordinateSystemSetRenderer extends AbstractCoreJoglMultipleRenderer{
	public static final String ID = "org.goko.viewer.jogl.utils.render.CoordinateSystemRenderer";
	private ICoordinateSystemAdapter adapter;
	private Map<EnumCoordinateSystem, AbstractCoreJoglRenderer> coordinateSystemRenderer;

	public CoordinateSystemSetRenderer() {
		super();
		coordinateSystemRenderer = new HashMap<EnumCoordinateSystem, AbstractCoreJoglRenderer>();
	}

	/**
	 * (inheritDoc)
	 *
	 * @see org.goko.core.viewer.renderer.IViewer3DRenderer#getId()
	 */
	@Override
	public String getId() {
		return ID;
	}

	/** (inheritDoc)
	 * @see org.goko.viewer.jogl.service.AbstractCoreJoglMultipleRenderer#render(javax.media.opengl.GL3, com.jogamp.opengl.util.PMVMatrix)
	 */
	@Override
	public void render(GL3 gl, PMVMatrix modelViewMatrix) throws GkException {
		if(!isEnabled() || shouldDestroy()){
			return;
		}
		if(adapter != null){
			EnumCoordinateSystem activeCoordinateSystem = adapter.getCurrentCoordinateSystem();
			Tuple6b offsets 		= adapter.getCoordinateSystemOffset(activeCoordinateSystem);
			Tuple6b machineOrigin 	= new Tuple6b().setZero().subtract(new Tuple6b(offsets));

			for (EnumCoordinateSystem cs : adapter.getCoordinateSystem()) {
				AbstractCoreJoglRenderer renderer = getCoordinateSystemRenderer(cs);
				Tuple6b csOffset = adapter.getCoordinateSystemOffset(cs);
				if(csOffset != null){
					csOffset = csOffset.add(machineOrigin);

					renderer.getModelMatrix().glLoadIdentity();
					renderer.getModelMatrix().glTranslatef( (float)csOffset.getX().doubleValue(),
															(float)csOffset.getY().doubleValue(),
															(float)csOffset.getZ().doubleValue());
					renderer.getModelMatrix().update();
				}
			}
			super.render(gl, modelViewMatrix);
		}
	}


	/** (inheritDoc)
	 * @see org.goko.viewer.jogl.service.AbstractCoreJoglRenderer#performInitialize(javax.media.opengl.GL3)
	 */
	@Override
	protected void performInitialize(GL3 gl) throws GkException {
		//this.setShaderProgram(ShaderLoader.loadShader(gl, EnumGokoShaderProgram.GCODE_SHADER));

	}

	protected AbstractCoreJoglRenderer getCoordinateSystemRenderer(EnumCoordinateSystem cs){
		if(!coordinateSystemRenderer.containsKey(cs)){
			//ThreeAxisRenderer axisRenderer = new ThreeAxisRenderer(4, new Color3f(0.4f,0.4f,0.4f), new Color3f(0.4f,0.4f,0.4f), new Color3f(0.4f,0.4f,0.4f));
			CoordinateSystemRenderer axisRenderer = new CoordinateSystemRenderer(cs, 4, new Color3f(0.4f,0.4f,0.4f), new Color3f(0.4f,0.4f,0.4f), new Color3f(0.4f,0.4f,0.4f), new Color3f(1f,0.77f,0.04f));
			coordinateSystemRenderer.put(cs, axisRenderer);
			addRenderer(axisRenderer);
		}
		return coordinateSystemRenderer.get(cs);
	}

	/**
	 * @return the adapter
	 */
	public ICoordinateSystemAdapter getAdapter() {
		return adapter;
	}

	/**
	 * @param adapter the adapter to set
	 */
	public void setAdapter(ICoordinateSystemAdapter adapter) {
		this.adapter = adapter;
	}

	public void setCoordinateSystemEnabled(EnumCoordinateSystem cs, boolean enabled) {
		if(coordinateSystemRenderer.containsKey(cs)){
			coordinateSystemRenderer.get(cs).setEnabled(enabled);
		}
	}
}
