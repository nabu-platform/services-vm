package be.nabu.libs.services.vm.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.validator.api.Validation;

@XmlType(propOrder = {"children"})
abstract public class BaseStepGroup extends BaseStep implements StepGroup {

	private List<Step> children = new ArrayList<Step>();
	
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
	
	protected boolean executeIfLabel(Step child, VMContext context) throws ServiceException {
		Boolean execute = true;
		if (child.getLabel() != null) {
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
	
	protected void execute(Step child, VMContext context) throws ServiceException {
		try {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().before(child);
			}
			child.execute(context);
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().after(child);
			}
		}
		catch (Exception e) {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
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
