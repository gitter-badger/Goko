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
package org.goko.core.gcode.bean.commands;

import org.goko.core.common.exception.GkException;
import org.goko.core.gcode.bean.BoundingTuple6b;
import org.goko.core.gcode.bean.IGCodeCommandVisitor;


/**
 * Defines a linear motion command
 *
 * @author PsyKo
 *
 */
public class LinearMotionCommand extends MotionCommand {

	/**
	 * Constructor
	 */
	public LinearMotionCommand() {
		super(EnumGCodeCommandMotionType.LINEAR);
	}

	@Override
	public BoundingTuple6b getBounds() {
		return super.getBounds();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.bean.GCodeCommand#accept(org.goko.core.gcode.bean.IGCodeCommandVisitor)
	 */
	@Override
	public void accept(IGCodeCommandVisitor visitor) throws GkException {
		visitor.visit(this);
	}
}
