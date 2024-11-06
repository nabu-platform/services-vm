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

package be.nabu.libs.services.vm.api;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

public interface Step {
	public void execute(VMContext context) throws ServiceException;
	
	@XmlAttribute
	public String getId();
	public void setId(String id);
	
	@XmlTransient
	public StepGroup getParent();
	public void setParent(StepGroup parent);

	@XmlAttribute
	public String getComment();
	public void setComment(String comment);
	
	@XmlAttribute
	public String getName();
	public void setName(String name);
	
	@XmlTransient
	public VMService getServiceDefinition();
	
	public List<Validation<?>> validate(ServiceContext context);
	
	@XmlAttribute
	public String getLabel();
	public void setLabel(String label);
	
	@XmlAttribute
	public boolean isDisabled();
	public void setDisabled(boolean isDisabled);
	
	@XmlAttribute
	public String getFeatures();
	public void setFeatures(String features);
	
	@XmlAttribute
	public String getDescription();
	public void setDescription(String description);
	
	@XmlAttribute
	public Integer getLineNumber();
	public void setLineNumber(Integer lineNumber);
	
	/**
	 * Refresh this step
	 */
	public void refresh();
}
