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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.FeaturedExecutionContext;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.structure.StructureInstance;
import be.nabu.libs.validator.api.Validation;

@XmlType(propOrder = {"children"})
abstract public class BaseStepGroup extends BaseStep implements StepGroup {

	private List<Step> children = new ArrayList<Step>();
	private java.util.Map<String, Structure> childFeatures = new HashMap<String, Structure>();
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public BaseStepGroup(VMService definition, Step...steps) {
		super(definition);
		getChildren().addAll(Arrays.asList(steps));
		for (Step step : steps)
			step.setParent(this);
	}
	public BaseStepGroup(Step...steps) {
		this(null, steps);
	}

	@XmlElement(name = "steps")
	@Override
	public List<Step> getChildren() {
		return children;
	}
	public void setChildren(List<Step> children) {
		if (children != null) {
			for (Step child : children) {
				child.setParent(this);
			}
		}
		this.children = children;
	}
	/**
	 * By default most step groups do not add their own definitions to the pipeline
	 * Override this to extend with your own parameters 
	 */
	@Override
	public ComplexType getPipeline(ServiceContext serviceContext) {
		return getParent() != null ? getParent().getPipeline(serviceContext) : getServiceDefinition().getPipeline();
	}
	
	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);		
		for (Step child : getChildren()) {
			messages.addAll(addContext(child.validate(serviceContext)));
		}
		return messages;
	}
	
	public static List<String> getFeatures(String features) {
		List<String> result = new ArrayList<String>();
		try {
			List<QueryPart> parse = QueryParser.getInstance().parse(features);
			for (QueryPart part : parse) {
				if (part.getType() == Type.VARIABLE) {
					result.add(part.getToken().getContent());
				}
			}
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return result;
	}
	
	protected boolean isOkForFeatures(Step child, VMContext context) throws ServiceException {
		Boolean execute = true;
		if (child.getFeatures() != null && !child.getFeatures().trim().isEmpty() && context.getExecutionContext() instanceof FeaturedExecutionContext) {
			Structure structure = childFeatures.get(child.getId());
			if (structure == null) {
				structure = new Structure();
				for (String single : getFeatures(child.getFeatures())) {
					structure.add(new SimpleElementImpl<Boolean>(single, SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), structure));
				}
				if (!childFeatures.containsKey(child.getId())) {
					synchronized(childFeatures) {
						if (!childFeatures.containsKey(child.getId())) {
							childFeatures.put(child.getId(), structure);
						}
					}
				}
			}
			List<String> enabled = ((FeaturedExecutionContext) context.getExecutionContext()).getEnabledFeatures();
			StructureInstance instance = childFeatures.get(child.getId()).newInstance();
			for (Element<?> feature : instance.getType()) {
				instance.set(feature.getName(), enabled.contains(feature.getName()));
			}
			execute = (Boolean) getVariable(instance, child.getFeatures());
		}
		return execute;
	}
	
	protected boolean executeIfLabel(Step child, VMContext context) throws ServiceException {
		Boolean execute = isOkForFeatures(child, context);
		if (execute && child.getLabel() != null) {
			Object variable = getVariable(context.getServiceInstance().getPipeline(), child.getLabel());
			execute = variable != null ? ConverterFactory.getInstance().getConverter().convert(variable, Boolean.class) : false;
			// if we can not convert the variable to a boolean, we execute if it is not null, we checked for null in the above so always true at this point
			if (execute == null) {
				execute = true;
			}
		}
		if (execute) {
			execute(child, context);
		}
		return execute;
	}
	
	protected void emitDescription(Step child, VMContext context) {
		try {
			if (child.getDescription() != null) {
				Object descriptionValue;
				if (child.getDescription().startsWith("=")) {
					descriptionValue = getVariable(context.getServiceInstance().getPipeline(), child.getDescription().substring(1));
				}
				else {
					descriptionValue = child.getDescription();
				}
				if (descriptionValue != null) {
					ServiceRuntime.getRuntime().getRuntimeTracker().describe(descriptionValue);
				}
			}
		}
		// we don't want this suppressing actual exceptions
		catch (Exception e) {
			logger.warn("Could not output child description: " + child.getDescription(), e);
		}
	}
	
	protected void execute(Step child, VMContext context) throws ServiceException {
		try {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().before(child);
			}
			child.execute(context);
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				emitDescription(child, context);
				ServiceRuntime.getRuntime().getRuntimeTracker().after(child);
			}
		}
		catch (Exception e) {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				emitDescription(child, context);	// questionable? you likely need data gathered in this step.... unless it's a throw of course!
				ServiceRuntime.getRuntime().getRuntimeTracker().error(child, e);
			}
			if (e instanceof ServiceException) {
				throw (ServiceException) e;
			}
			else {
				throw new ServiceException("VM-6", child.getClass().getSimpleName() + ": " + child.getId(), e);
			}
		}
	}
	
	protected boolean isAborted() {
		return ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().isAborted();
	}
}
