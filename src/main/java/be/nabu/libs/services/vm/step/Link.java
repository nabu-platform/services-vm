package be.nabu.libs.services.vm.step;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class Link extends BaseStep {

	private String from, to;

	/**
	 * If this is set, the "from" is interpreted as a fixed value instead of a query
	 */
	private boolean isFixedValue = false;
	
	/**
	 * If this is set, we only assign it if the target is null
	 */
	private boolean isOptional = false;
	
	public Link(String from, String to) {
		setFrom(from);
		setTo(to);
	}

	public Link() {
		
	}
	
	/**
	 * Links elements within a single context
	 */
	@Override
	public void execute(VMContext context) throws ServiceException {
		execute(context.getServiceInstance().getCurrentPipeline(), context.getServiceInstance().getCurrentPipeline());
	}
	
	/**
	 * Links element from the source pipeline to the target pipeline
	 */
	public void execute(ComplexContent source, ComplexContent target) throws ServiceException {
		// if it's optional, first check whether the "to" is null
		if (isOptional) {
			Object currentValue = getVariable(target, to);
			if (currentValue != null) {
				return;
			}
		}
		Object value;
		if (isFixedValue) {
			if (from.startsWith("=")) {
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
		setVariable(
			target,
			to, 
			value
		);
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
	public boolean isOptional() {
		return isOptional;
	}

	public void setOptional(boolean isOptional) {
		this.isOptional = isOptional;
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
