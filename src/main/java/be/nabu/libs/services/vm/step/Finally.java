/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.services.vm.step;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;

public class Finally extends BaseStepGroup implements LimitedStepGroup {

	public Finally() {
		
	}
	public Finally(Step...steps) {
		super(steps);
	}

	@Override
	public void execute(VMContext context) throws ServiceException {
		for (Step child : getChildren()) {
			if (child.isDisabled()) {
				continue;
			}
			executeIfLabel(child, context);
			if (isAborted()) {
				break;
			}
		}
	}

	@XmlTransient
	@Override
	public Set<Class<? extends Step>> getAllowedSteps() {
		Set<Class<? extends Step>> allowed = new HashSet<Class<? extends Step>>();
		allowed.add(Switch.class);
		allowed.add(For.class);
		allowed.add(Map.class);
		allowed.add(Throw.class);
		allowed.add(Sequence.class);
		allowed.add(Break.class);
		return allowed;
	}
	
	@Override
	public void refresh() {
		// do nothing
	}
}
