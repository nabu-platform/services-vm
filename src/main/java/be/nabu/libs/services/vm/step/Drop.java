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

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

@XmlType(propOrder = { "path" })
public class Drop extends BaseStep {

	private String path;
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		setVariable(context.getServiceInstance().getPipeline(), path, null);
	}

	@Override
	public void refresh() {
		// do nothing
	}

	@XmlAttribute
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		messages.addAll(validateQuery(serviceContext, path));
		return messages;
	}

	
}
