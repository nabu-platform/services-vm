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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.PipelineExtension;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.validator.api.Validation;

@XmlType(propOrder = { "variable", "suppressException", "types", "codes" })
public class Catch extends BaseStepGroup implements LimitedStepGroup {

	private List<String> codes;
	private List<Class<? extends Exception>> types;
	private String variable;
	private Boolean suppressException;
	
	private PipelineExtension pipeline;

	public Catch() {
		// automatic
	}
	public Catch(Step...steps) {
		super(steps);
	}
	
	public void setTypes(List<Class<? extends Exception>> types) {
		this.types = types;
	}
	
	public void addType(Class<? extends Exception> type) {
		getTypes().add(type);
	}
	
	public List<Class<? extends Exception>> getTypes() {
		if (types == null)
			types = new ArrayList<Class<? extends Exception>>();
		return types;
	}
	
	public List<String> getCodes() {
		return codes;
	}
	public void setCodes(List<String> codes) {
		this.codes = codes;
	}
	
	@XmlAttribute
	public String getVariable() {
		return variable;
	}

	public void setVariable(String variable) {
		this.pipeline = null;
		this.variable = variable;
	}
	
	@Override
	public ComplexType getPipeline(ServiceContext serviceContext) {
		// we need to inject the exception 
		if (pipeline == null && variable != null) {
			synchronized(this) {
				if (pipeline == null && variable != null) {
					PipelineExtension pipeline = new PipelineExtension();
					pipeline.setSuperType(getParent().getPipeline(serviceContext));
					pipeline.setName(getParent().getPipeline(serviceContext).getName());
					pipeline.add(new ComplexElementImpl(
						variable, 
						(ComplexType) BeanResolver.getInstance().resolve(getCommonParent()),
						pipeline
					), false);
					this.pipeline = pipeline;
				}
			}
		}
		return pipeline == null ? getParent().getPipeline(serviceContext) : pipeline;
	}
	
	@XmlTransient
	@SuppressWarnings("unchecked")
	public Class<? extends Exception> getCommonParent() {
		Class<? extends Exception> common = null;
		for (Class<? extends Exception> type : getTypes()) {
			if (common == null) {
				common = type;
			}
			// if they are still compatible, keep going
			else if (common.isAssignableFrom(type)) {
				continue;
			}
			// otherwise we need a common parent
			else {
				while (common.isAssignableFrom(Exception.class) && !common.isAssignableFrom(type))
					common = (Class<? extends Exception>) common.getSuperclass();
			}
		}
		return common == null || !Exception.class.isAssignableFrom(common) ? Exception.class : common;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		if (variable != null) {
			messages.addAll(checkNameInScope(serviceContext, variable));
		}
		messages.addAll(super.validate(serviceContext));
		return messages;
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
	public void execute(VMContext context) throws ServiceException {
		if (variable != null) {
			context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
			setVariable(context.getServiceInstance().getPipeline(), variable, context.getCaughtException());
		}
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

	@XmlAttribute
	public Boolean getSuppressException() {
		return suppressException;
	}
	public void setSuppressException(Boolean suppressException) {
		this.suppressException = suppressException;
	}
	@Override
	public void refresh() {
		pipeline = null;
	}
}
