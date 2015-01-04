/*******************************************************************************
 * 	This file is part of Goko.
 *
 *   Goko is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Goko is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Goko.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.goko.viewer.jogl.model;

import javax.inject.Inject;

import org.goko.common.bindings.AbstractController;
import org.goko.core.common.exception.GkException;
import org.goko.core.gcode.bean.IGCodeProvider;
import org.goko.core.log.GkLog;
import org.goko.viewer.jogl.camera.OrthographicCamera;
import org.goko.viewer.jogl.camera.PerspectiveCamera;
import org.goko.viewer.jogl.service.IJoglViewerService;
import org.goko.viewer.jogl.utils.render.CoordinateSystemRenderer;
import org.goko.viewer.jogl.utils.render.GridRenderer;

/**
 * GCode 3D Viewer controller
 * @author PsyKo
 *
 */
public class GCodeViewer3DController extends AbstractController<GCodeViewer3DModel> {
	private static final GkLog LOG = GkLog.getLogger(GCodeViewer3DController.class);
	@Inject
	private IJoglViewerService viewerService;

	public GCodeViewer3DController(GCodeViewer3DModel binding) {
		super(binding);
	}

	@Override
	public void initialize() throws GkException {
		//controllerService.addListener(this);
	}

//	@EventListener(MachineValueUpdateEvent.class)
//	public void onMachineValueUpdate(MachineValueUpdateEvent updateEvent) throws GkException{
//		getDataModel().setCurrentPosition(controllerService.getPosition());
//	}

	public void setPerspectiveCamera() throws GkException{
		viewerService.setActiveCamera(PerspectiveCamera.ID);
	}
	public void setOrthographicCamera() throws GkException{
		viewerService.setActiveCamera(OrthographicCamera.ID);
	}

	public void setGCodeFile(IGCodeProvider provider) throws GkException {
		viewerService.renderGCode(provider);
	}

	public void setLockCameraOnTool(boolean lockOnTool) throws GkException {
		viewerService.setLockCameraOnTool(lockOnTool);
	}

	public void setShowGrid(boolean showGrid) throws GkException {
		viewerService.setRendererEnabled(GridRenderer.ID, showGrid);
	}

	public void setRenderEnabled(boolean enabled){
		viewerService.setEnabled(enabled);
	}

	public void setShowCoordinateSystem(boolean selection) throws GkException {
		viewerService.setRendererEnabled(CoordinateSystemRenderer.ID, selection);
	}
}
