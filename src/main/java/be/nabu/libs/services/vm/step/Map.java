package be.nabu.libs.services.vm.step;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;
import be.nabu.libs.services.vm.PipelineExtension;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.HiddenProperty;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;


@XmlRootElement
public class Map extends BaseStepGroup implements LimitedStepGroup {
	
	private ComplexType pipeline;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public Map(Step...steps) {
		super(steps);
	}
	public Map() {

	}
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		// reinitializes the context map-level managed entities
		context.newMapStep();
		
		// cast the pipeline
		context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
		
		try {
			int maxInvocationOrder = 0;
			for (Step step : getChildren()) {
				if (step instanceof Invoke) {
					int invocationOrder = ((Invoke) step).getInvocationOrder();
					if (invocationOrder > maxInvocationOrder) {
						maxInvocationOrder = invocationOrder;
					}
				}
			}
			int invocationOrder = 0;
			// first we need to perform all the invokes in their invocation order
			while (invocationOrder <= maxInvocationOrder) {
				for (Step step : getChildren()) {
					if (step.isDisabled()) {
						continue;
					}
					if (step instanceof Invoke) {
						Invoke invoke = (Invoke) step;
						if (invoke.getInvocationOrder() == invocationOrder) {
							execute(step, context);
						}
					}
					if (isAborted()) {
						break;
					}
				}
				invocationOrder++;
				if (isAborted()) {
					break;
				}
			}
			// next invoke the plain link steps
			for (Step step : getChildren()) {
				if (step.isDisabled()) {
					continue;
				}
				if (step instanceof Link) {
					execute(step, context);
				}
				if (isAborted()) {
					break;
				}
			}
			// finally invoke the drops
			for (Step step : getChildren()) {
				if (step.isDisabled()) {
					continue;
				}
				if (step instanceof Drop) {
					execute(step, context);
				}
				if (isAborted()) {
					break;
				}
			}
		}
		// close managed entities
		finally {
			for (Closeable entity : context.getManaged(Scope.MAP)) {
				try {
					logger.info("Auto-closing map-managed entity: " + entity);
					entity.close();
				}
				// suppress
				catch (Exception e) {
					logger.error("Can not close map-managed entity", e);
				}
			}
		}
	}
	
	/**
	 * The map step has to keep track of all the invokes it performs and generate a pipeline variable for each of them
	 */
	@Override
	public ComplexType getPipeline(ServiceContext serviceContext) {
		if (pipeline == null) {
			synchronized(this) {
				if (pipeline == null) {
					// create a new structure
					PipelineExtension structure = new PipelineExtension();
					// that extends the parent structure
					structure.setSuperType(getParent().getPipeline(serviceContext));
					// inherit the name to make it clearer when marshalling
					structure.setName(getServiceDefinition().getPipeline().getName());
					structure.setProperty(new ValueImpl<String>(CommentProperty.getInstance(), getId()));
					boolean changed = false;
					for (Step child : getChildren()) {
						if (child instanceof Invoke) {
							Invoke invoke = (Invoke) child;
							// if you don't set a result name, the result will not be injected into the pipeline, hence no need to define it
							if (invoke.getResultName() != null) {
								changed = true;
								Service service = invoke.getService(serviceContext);
								if (service != null) {
									structure.add(new ComplexElementImpl(invoke.getResultName(), service.getServiceInterface().getOutputDefinition(), structure, new ValueImpl<Boolean>(HiddenProperty.getInstance(), true)), false);
								}
							}
						}
					}
					pipeline = changed ? structure : getParent().getPipeline(serviceContext);
				}
			}
		}
		return pipeline;
	}
	
	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		for (Step child : getChildren()) {
			if (child instanceof Invoke) {
				Invoke invoke = (Invoke) child;
				if (invoke.getResultName() != null) {
					messages.addAll(invoke.isTemporaryMapping() 
						? checkNameInScope(serviceContext, invoke.getResultName()) 
						: checkNameInScope(serviceContext, invoke.getResultName(), invoke.getService(serviceContext).getServiceInterface().getOutputDefinition()));
				}
			}
		}
		return messages;
	}
	
	@XmlTransient
	@Override
	public Set<Class<? extends Step>> getAllowedSteps() {
		Set<Class<? extends Step>> allowed = new HashSet<Class<? extends Step>>();
		allowed.add(Link.class);
		allowed.add(Invoke.class);
		allowed.add(Drop.class);
		return allowed;
	}

	// need to be able to find circular references and notify the user!
	public List<ValidationMessage> calculateInvocationOrder() {
		List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
		for (Step child : getChildren()) {
			if (child instanceof Invoke) {
				Invoke invoke = (Invoke) child;
				for (Step invokeChild : invoke.getChildren()) {
					Link link = (Link) invokeChild;
					if (!link.isFixedValue()) {
						ParsedPath path = new ParsedPath(link.getFrom());
						Invoke sourceInvoke = getInvokeByResult(path.getName());
						if (sourceInvoke != null && invoke.getInvocationOrder() <= sourceInvoke.getInvocationOrder()) {
							messages.add(new ValidationMessage(Severity.ERROR, "The service " + invoke.getServiceId() + " with invoke order " + invoke.getInvocationOrder() + " depends on the service " + sourceInvoke.getServiceId() + " that is invoked later in " + sourceInvoke.getInvocationOrder()));
						}
					}
				}
			}
		}
		return messages;
	}
	
	private Invoke getInvokeByResult(String resultName) {
		for (Step child : getChildren()) {
			if (child instanceof Invoke && resultName.equals(((Invoke) child).getResultName())) {
				return ((Invoke) child);
			}
		}
		return null;
	}
	
	@Override
	public void refresh() {
		pipeline = null;
	}
}
