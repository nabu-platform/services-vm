package be.nabu.libs.services.vm.step;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.PipelineExtension;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CommentProperty;

/**
 * Contains a try/catch/finally logic
 * Can be used to implicitly manage stuff like logging, transactions,...
 * 
 * @author alex
 *
 */
@XmlRootElement
public class Sequence extends BaseStepGroup implements LimitedStepGroup {

	private PipelineExtension pipeline;
	private String transactionVariable, step;

	private SimpleTypeWrapper simpleTypeWrapper;
	
	public Sequence(VMService definition, Step...steps) {
		super(definition, steps);
	}
	public Sequence(Step...steps) {
		super(steps);
	}
	public Sequence() {

	}
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		// cast pipeline
		context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
		String transactionId = null;
		if (transactionVariable != null) {
			transactionId = context.getExecutionContext().getTransactionContext().start();
			setVariable(context.getServiceInstance().getCurrentPipeline(), transactionVariable, transactionId);
		}
		if (step != null && ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
			ServiceRuntime.getRuntime().getRuntimeTracker().before(step);
		}
		Step lastExecuted = null;
		try {
			for (Step child : getChildren()) {
				if (child.isDisabled()) {
					continue;
				}
				if (!(child instanceof Catch) && !(child instanceof Finally)) {
					lastExecuted = child;
					execute(child, context);
					if (context.mustBreak()) {
						context.decreaseBreakCount();
						break;
					}
				}
				if (isAborted()) {
					break;
				}
			}
			if (transactionId != null) {
				if (isAborted()) {
					context.getExecutionContext().getTransactionContext().rollback(transactionId);
				}
				else {
					context.getExecutionContext().getTransactionContext().commit(transactionId);
				}
			}
			if (step != null && ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().after(step);
			}
		}
		catch (Exception e) {
			// roll back pending transaction if any
			if (transactionId != null) {
				context.getExecutionContext().getTransactionContext().rollback(transactionId);
			}
			if (step != null && ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().error(step, e);
			}
			boolean matchFound = false;
			Catch defaultCatchClause = null;
			boolean lastExecutedFound = false;
			for (Step child : getChildren()) {
				if (child.isDisabled()) {
					continue;
				}
				if (!lastExecutedFound) {
					if (child.equals(lastExecuted)) {
						lastExecutedFound = true;
					}
					continue;
				}
				else if (child instanceof Catch) {
					Catch catchClause = (Catch) child;
					if (catchClause.getTypes().size() == 0)
						defaultCatchClause = catchClause;
					else {
						for (Class<?> exceptionType : catchClause.getTypes()) {
							if (exceptionType.isAssignableFrom(e.getClass())) {
								matchFound = true;
								context.setCaughtException(e);
								execute(catchClause, context);
								context.setCaughtException(null);
								break;
							}
						}
					}
				}
			}
			// if no specific match was found and a default clause exists, use that
			// otherwise, propagate the exception
			if (!matchFound) { 
				if (defaultCatchClause != null) {
					context.setCaughtException(e);
					execute(defaultCatchClause, context);
					context.setCaughtException(null);
				}
				else if (e instanceof ServiceException)
					throw (ServiceException) e;
				else if (e instanceof RuntimeException)
					throw (RuntimeException) e;
				else
					throw new ServiceException(e);
			}
		}
		finally {
			boolean lastExecutedFound = false;
			for (Step child : getChildren()) {
				if (child.isDisabled()) {
					continue;
				}
				if (!lastExecutedFound) {
					if (child.equals(lastExecuted)) {
						lastExecutedFound = true;
					}
					continue;
				}
				else if (child instanceof Finally) {
					execute(child, context);
					break;
				}
			}
		}
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
		allowed.add(Finally.class);
		allowed.add(Catch.class);
		allowed.add(Break.class);
		return allowed;
	}
	
	@Override
	public ComplexType getPipeline(ServiceContext serviceContext) {
		if (this.transactionVariable != null) {
			if (pipeline == null) {
				// create a new structure
				pipeline = new PipelineExtension();
				// that extends the parent structure
				pipeline.setSuperType(super.getPipeline(serviceContext));
				// inherit the name to make it clearer when marshalling
				pipeline.setName(getServiceDefinition().getPipeline().getName());
				pipeline.setProperty(new ValueImpl<String>(CommentProperty.getInstance(), getId()));
				// add the variable
				pipeline.add(new SimpleElementImpl<String>(
					transactionVariable, 
					getSimpleTypeWrapper().wrap(String.class), 
					pipeline), false
				);
			}
			return pipeline;
		}
		// if you don't add anything, use default behavior
		else {
			return super.getPipeline(serviceContext);
		}
	}
	
	@XmlAttribute
	public String getTransactionVariable() {
		return transactionVariable;
	}
	public void setTransactionVariable(String transactionVariable) {
		this.transactionVariable = transactionVariable;
	}
	
	@XmlAttribute
	public String getStep() {
		return step;
	}
	public void setStep(String step) {
		this.step = step;
	}
	
	@XmlTransient
	public SimpleTypeWrapper getSimpleTypeWrapper() {
		if (simpleTypeWrapper == null)
			simpleTypeWrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
		return simpleTypeWrapper;
	}
	
	@Override
	public void refresh() {
		pipeline = null;
	}
}
