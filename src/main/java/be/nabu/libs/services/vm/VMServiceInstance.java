package be.nabu.libs.services.vm;

import java.io.Closeable;
import java.util.List;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.properties.ValidateProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.Validator;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class VMServiceInstance implements ServiceInstance {
	
	private VMService definition;
	
	private ComplexContent pipeline;
	
	public static final String RUNTIME_TRACKER = "runtimeTracker";
	
	public VMServiceInstance(VMService definition) {
		this.definition = definition;
	}
	
	/**
	 * The input must be of the type of service input
	 * This check is however delegated to the runtime service engine
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		if (ValueUtils.getValue(ValidateProperty.getInstance(), definition.getPipeline().get(Pipeline.INPUT).getProperties())) {
			Validator validator = definition.getServiceInterface().getInputDefinition().createValidator();
			List<? extends Validation<?>> validations = validator.validate(input);
			for (Validation<?> validation : validations) {
				if (validation.getSeverity() == Severity.CRITICAL || validation.getSeverity() == Severity.ERROR) {
					throw new ServiceException("VM-4", "The input is not valid: " + validations);
				}
			}
		}
		// create a pipeline for the service execution
		pipeline = getDefinition().getPipeline().newInstance();

		// set the input
		pipeline.set(Pipeline.INPUT, input);
		
		VMContext context = new VMContext(executionContext, this);
		try {
			// run the service
			getDefinition().getRoot().execute(context);
		}
		finally {
			for (Closeable entity : context.getManaged(Scope.SERVICE)) {
				try {
					System.out.println("Auto-closing service-managed entity: " + entity);
					entity.close();
				}
				// suppress
				catch (Exception e) {
					System.out.println("Can not close service-managed entity: " + e.getMessage());
				}
			}
		}
		ComplexContent output = (ComplexContent) pipeline.get(Pipeline.OUTPUT);
		if (output != null && ValueUtils.getValue(ValidateProperty.getInstance(), definition.getPipeline().get(Pipeline.OUTPUT).getProperties())) {
			Validator validator = definition.getServiceInterface().getOutputDefinition().createValidator();
			List<? extends Validation<?>> validations = validator.validate(output);
			for (Validation<?> validation : validations) {
				if (validation.getSeverity() == Severity.CRITICAL || validation.getSeverity() == Severity.ERROR) {
					throw new ServiceException("VM-5", "The output is not valid: " + validations);
				}
			}
		}		
		return output;
	}

	@Override
	public VMService getDefinition() {
		return definition;
	}
	
	public ComplexContent getCurrentPipeline() {
		return pipeline;
	}
	
	public void castPipeline(ComplexType to) {
		ComplexContent castPipeline = Structure.cast(getCurrentPipeline(), to);
		if (castPipeline == null)
			throw new ClassCastException("Can not cast the pipeline from " + pipeline.getType() + " to " + to);
		else
			pipeline = castPipeline;
	}
}
