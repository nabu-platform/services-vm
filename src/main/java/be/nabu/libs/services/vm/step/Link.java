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
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

/**
 * @2025-06-04: I updated some primitive booleans to boolean objects
 * The primary reason for this is for them not to have a default value so they don't show in the XML, this has a few advantages:
 * - smaller XML if not set
 * - resaving an existing service with an added primitive boolean would suddenly create a massive change set where nothing actually changed functionally
 * - backwards compatible with older versions that don't have the boolean
 */
@XmlType(propOrder = {"from", "to", "mask", "optional", "patch", "fixedValue", "sourceNotNull"})
public class Link extends BaseStep {

	// the to can ONLY be null if we map to the full input of a service
	private String from, to;

	/**
	 * If this is set, the "from" is interpreted as a fixed value instead of a query
	 */
	private boolean isFixedValue = false;
	
	/**
	 * If this is set, we only assign it if the target is null
	 */
	private Boolean optional;
	
	/**
	 * If this is set, we only assign it IF the source has a non null value
	 */
	private Boolean sourceNotNull;
	
	/**
	 * If this is set, we only assign it if the source has an explicit value (!= undefined)
	 */
	private Boolean patch;
	
	/**
	 * Whether or not to mask the content we set, allowing for non-type equivalent sets
	 */
	private Boolean mask;
	
	public Link(String from, String to) {
		setFrom(from);
		setTo(to);
	}

	public Link() {
		
	}
	
	public void executeRootMap(ComplexContent source, ComplexContent target) throws ServiceException {
		Object value = getFromValue(source);
		// we want this value to be the full input
		// however, we can't overwrite the actual target reference
		// so instead, we clean out the target (remove all data) and map all the children from the source
		// this has a similar effect
		for (Element<?> child : TypeUtils.getAllChildren(target.getType())) {
			Object childValue = value == null ? null : ((ComplexContent) value).get(child.getName());
			if (childValue != null && childValue instanceof ComplexContent && mask != null && mask && target.getType().get(child.getName()) instanceof ComplexType) {
				childValue = new MaskedContent((ComplexContent) childValue, (ComplexType) target.getType().get(child.getName()));
			}
			target.set(child.getName(), childValue);
		}
	}
	
	/**
	 * Links elements within a single context
	 */
	@Override
	public void execute(VMContext context) throws ServiceException {
		execute(context.getServiceInstance().getPipeline(), context.getServiceInstance().getPipeline());
	}
	
	/**
	 * Links element from the source pipeline to the target pipeline
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void execute(ComplexContent source, ComplexContent target) throws ServiceException {
		if (to == null) {
			throw new ServiceException("VM-10", "Missing link to variable");
		}
		// if it's optional, first check whether the "to" is null
		if (optional != null && optional) {
			Object currentValue = getVariable(target, to);
			if (currentValue != null) {
				return;
			}
		}
		if (patch != null && patch) {
			// if the from is undefined, don't do anything!
			if (isFromUndefined(source)) {
				return;
			}
		}
		Object value = getFromValue(source);
		// if we only want to map non-null values, don't proceed if it is null
		if (sourceNotNull != null && sourceNotNull && value == null) {
			return;
		}
		if (mask != null && mask && value != null) {
			try {
				TypeOperation operation = getOperation(to);
				Type returnType = operation.getReturnType(target.getType());
				if (!(returnType instanceof ComplexType)) {
					throw new ServiceException("VM-7", "Can only mask complex types");
				}
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
				// @2024-05-02 a map should be handled like complex content...
				// @2024-10-04 it was still not being handled correctly because of the wrong map type......... not sure what the original usecase was because it was evidently not solved
				if (collectionHandler != null && !(value instanceof java.util.Map)) {
					Collection indexes = collectionHandler.getIndexes(value);
					Object newCollection = collectionHandler.create(value.getClass(), indexes.size());
					for (Object index : indexes) {
						Object single = collectionHandler.get(value, index);
						if (!(single instanceof ComplexContent)) {
							ComplexContent wrapped = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(single);
							if (wrapped == null) {
								throw new ServiceException("VM-8", "Can not convert the original to complex content for type masking");
							}
							single = wrapped;
						}
						collectionHandler.set(newCollection, index, new MaskedContent((ComplexContent) single, (ComplexType) returnType));
					}
					value = newCollection;
				}
				else {
					if (!(value instanceof ComplexContent)) {
						ComplexContent wrapped = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(value);
						if (wrapped == null) {
							throw new ServiceException("VM-8", "Can not convert the original to complex content for type masking");
						}
						value = wrapped;
					}
					value = new MaskedContent((ComplexContent) value, (ComplexType) returnType);
				}
			}
			catch (ParseException e) {
				e.printStackTrace();
			}
		}
		setVariable(
			target,
			to, 
			value
		);
	}

	private Object getFromValue(ComplexContent source) throws ServiceException {
		Object value;
		if (isFixedValue) {
			// you can also pass in the explicit string "=" which will not be evaluated
			if (from.startsWith("=") && !from.equals("=")) {
				value = getVariable(source, from.substring(1));
			}
			// escape equals sign
			else if (from.startsWith("\\=")) {
				value = from.substring(1);
			}
			else {
				value = from;
			}
		}
		else {
			value = getVariable(source, from);
		}
		return value;
	}
	
	private boolean isFromUndefined(ComplexContent source) throws ServiceException {
		// can't be undefined if it's a fixed value
		if (isFixedValue) {
			return false;
		}
		else {
			Object variable = getVariable(source, from + " == undefined");
			return variable instanceof Boolean && ((Boolean) variable) == true;
		}
	}
	
	@XmlAttribute
	public boolean isFixedValue() {
		return isFixedValue;
	}

	public void setFixedValue(boolean isFixedValue) {
		this.isFixedValue = isFixedValue;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}
	
	@XmlAttribute
	public Boolean getOptional() {
		return optional;
	}
	public void setOptional(Boolean optional) {
		this.optional = optional;
	}

	@XmlAttribute
	public Boolean getPatch() {
		return patch;
	}
	public void setPatch(Boolean patch) {
		this.patch = patch;
	}

	@XmlAttribute
	public Boolean getMask() {
		return mask;
	}

	public void setMask(Boolean mask) {
		this.mask = mask;
	}

	@XmlAttribute
	public Boolean getSourceNotNull() {
		return sourceNotNull;
	}
	public void setSourceNotNull(Boolean sourceNotNull) {
		this.sourceNotNull = sourceNotNull;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		if (from == null) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "No from is defined")));
		}
		else if (from.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, from.substring(1)));
		}
		// a fixed value without leading "=" is just a string, don't validate
		else if (!isFixedValue()) {
			messages.addAll(validateQuery(serviceContext, from));
		}
		if (to == null) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "No to is defined")));
		}
		else {
			messages.addAll(validateQuery(serviceContext, to));
		}
		return messages;
	}
	
	@Override
	public void refresh() {
		// do nothing
	}
}
