package be.nabu.libs.services.vm.step;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

/**
 * Allows you to rethrow an existing exception or throw a new one
 * 
 * @author alex
 *
 */
@XmlType(propOrder = { "code", "message", "description" })
public class Throw extends BaseStep {
	
	public Throw() {
		// automatic creation
	}
	
	public Throw(String message) {
		this.message = message;
	}

	/**
	 * The message is meant to be a generic description of the problem, e.g. "You do not have enough credit."
	 * This message is evaluated against the pipeline if you start it with "="
	 * ="Can not find " + b/value + " in this"
	 */
	private String message;
	
	/**
	 * A reference code 
	 */
	private String code;
	
	/**
	 * The description is meant to be a more detailed description of this particular problem instance, e.g. "Your current balance is 30, but that costs 50."
	 * This description is evaluated against the pipeline if you start it with "="
	 * ="Can not find " + b/value + " in this"
	 */
	private String description;
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		Object messageValue = null;
		Object descriptionValue = null;
		Object codeValue = null;
		if (message != null) {
			if (message.startsWith("=")) {
				messageValue = getVariable(context.getServiceInstance().getPipeline(), message.substring(1));
			}
			else {
				messageValue = message;
			}
		}
		if (description != null) {
			if (description.startsWith("=")) {
				descriptionValue = getVariable(context.getServiceInstance().getPipeline(), description.substring(1));
			}
			else {
				descriptionValue = description;
			}
		}
		if (code != null) {
			if (code.startsWith("=")) {
				codeValue = getVariable(context.getServiceInstance().getPipeline(), code.substring(1));
			}
			else {
				codeValue = code;
			}
		}
		// if we have a service exception and we don't want to add a code to it, just rethrow
		if (messageValue instanceof ServiceException && codeValue == null) {
			throw ((ServiceException) messageValue);
		}
		// any other exception is wrapped
		else if (messageValue instanceof Exception) {
			throw new ServiceException(codeValue == null ? null : codeValue.toString(), (String) null, (Exception) messageValue);
		}
		else {
			ServiceException serviceException = new ServiceException(codeValue == null ? null : codeValue.toString(), messageValue == null ? "No message" : messageValue.toString());
			serviceException.setDescription(descriptionValue == null ? null : descriptionValue.toString());
			throw serviceException;
		}
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		if (message != null && message.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, message.substring(1)));
		}
		if (code != null && code.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, code.substring(1)));
		}
		return messages;
	}

	@XmlAttribute
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	@Override
	public void refresh() {
		// do nothing
	}

	@XmlAttribute
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	@XmlAttribute
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
}
