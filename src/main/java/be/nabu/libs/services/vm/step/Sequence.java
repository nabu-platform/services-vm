package be.nabu.libs.services.vm.step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@XmlType(propOrder = { "transactionVariable", "suppressException" })
public class Sequence extends BaseStepGroup implements LimitedStepGroup {

	private PipelineExtension pipeline;
	private String transactionVariable;
	private Boolean suppressException;
	
	private Logger logger = LoggerFactory.getLogger(getClass());

	private SimpleTypeWrapper simpleTypeWrapper;
	
	private static Boolean LOG_ERRORS = Boolean.parseBoolean(System.getProperty("be.nabu.libs.services.vm.logErrors", "true"));
	
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
			setVariable(context.getServiceInstance().getPipeline(), transactionVariable, transactionId);
		}
		Step lastExecuted = null;
		Exception exception = null;
		boolean logException = LOG_ERRORS;
		// if we want to suppress the exception, don't log it (some errors are intentional, like not wanting to cache because we can't annotate it etc)
		if (logException && suppressException != null && suppressException) {
			logException = false;
		}
		try {
			for (Step child : getChildren()) {
				if (child.isDisabled()) {
					continue;
				}
				if (!(child instanceof Catch) && !(child instanceof Finally)) {
					// set the last executed _before_ executing it, just in case we get an exception
					// if we did not have to execute it (because of the label), it is still ok as it currently only serves as a pointer where we approximately got in the flow
					// so we can discard catches & finallys before it
					// this is especially important if it is the _first_ step of the sequence that fails!
					lastExecuted = child;
					executeIfLabel(child, context);
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
		}
		catch (Exception e) {
			exception = e;
			// roll back pending transaction if any
			if (transactionId != null) {
				try {
					context.getExecutionContext().getTransactionContext().rollback(transactionId);
				}
				catch (Exception f) {
					logger.warn("Could not rollback transaction context during sequence exception handling", f);
				}
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
					if (catchClause.getTypes().size() == 0 && (catchClause.getCodes() == null || catchClause.getCodes().isEmpty()))
						defaultCatchClause = catchClause;
					// if we have codes, they get precedence
					else if (!catchClause.getCodes().isEmpty()) {
						if (hasAnyCode(e, catchClause.getCodes())) {
							matchFound = true;
							context.setCaughtException(e);
							executeIfLabel(catchClause, context);
							context.setCaughtException(null);
							// if we have successfully handled the catch check if we should suppress the exception from the log
							if (catchClause.getSuppressException() != null && catchClause.getSuppressException()) {
								logException = false;
							}
						}
					}
					else {
						for (Class<?> exceptionType : catchClause.getTypes()) {
							Throwable toCheck = e;
							while (toCheck instanceof Exception) {
								if (exceptionType.isAssignableFrom(toCheck.getClass())) {
									matchFound = true;
									context.setCaughtException((Exception) toCheck);
									executeIfLabel(catchClause, context);
									context.setCaughtException(null);
									// if we have successfully handled the catch check if we should suppress the exception from the log
									if (catchClause.getSuppressException() != null && catchClause.getSuppressException()) {
										logException = false;
									}
									break;
								}
								toCheck = toCheck.getCause();
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
					executeIfLabel(defaultCatchClause, context);
					context.setCaughtException(null);
					if (defaultCatchClause.getSuppressException() != null && defaultCatchClause.getSuppressException()) {
						logException = false;
					}
				}
				else if (e instanceof ServiceException)
					throw (ServiceException) e;
				else if (e instanceof RuntimeException)
					throw (RuntimeException) e;
				else
					throw new ServiceException(e);
			}
		}
		catch (Error e) {
			logger.error("An error has occurred in the sequence " + getId() + ", the last executed child: " + (lastExecuted == null ? "unknown" : lastExecuted.getId()), e);
			// roll back pending transaction if any
			if (transactionId != null) {
				try {
					context.getExecutionContext().getTransactionContext().rollback(transactionId);
				}
				catch (Exception f) {
					logger.warn("Could not rollback transaction context during sequence exception handling", f);
				}
			}
			// we need the additional context to find the problem...
			if (e instanceof StackOverflowError) {
				throw new ServiceException(e);
			}
			else {
				throw e;
			}
		}
		finally {
			if (logException && exception != null) {
				LoggerFactory.getLogger(context.getServiceInstance().getDefinition().getId()).error("Sequence '" + getId() + "' exited with exception", exception);
			}
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
					executeIfLabel(child, context);
					break;
				}
			}
		}
	}
	
	private boolean hasAnyCode(Throwable throwable, List<String> codesToCheck) {
		List<String> codes = getCodes(throwable);
		List<String> original = new ArrayList<String>(codes);
		codes.removeAll(codesToCheck);
		return original.size() != codes.size();
	}
	
	private List<String> getCodes(Throwable throwable) {
		List<String> codes = new ArrayList<String>();
		// the deepest service exception (if there are multiple) is what we are interested in
		while(throwable != null) {
			if (throwable instanceof ServiceException && ((ServiceException) throwable).getCode() != null) {
				codes.add(((ServiceException) throwable).getCode());
			}
			throwable = throwable.getCause();
		}
		return codes;
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
				synchronized(this) {
					if (pipeline == null) {
						// create a new structure
						PipelineExtension pipeline = new PipelineExtension();
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
						this.pipeline = pipeline;
					}
				}
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
	
	@XmlAttribute
	public Boolean getSuppressException() {
		return suppressException;
	}
	public void setSuppressException(Boolean suppressException) {
		this.suppressException = suppressException;
	}
	
}
