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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.cluster.api.ClusterInstance;
import be.nabu.libs.cluster.api.ClusterLock;
import be.nabu.libs.cluster.local.LocalInstance;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.impl.TransactionReport;
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
@XmlType(propOrder = { "transactionVariable", "suppressException", "scopeDefaultTransaction", "synchronized" })
public class Sequence extends BaseStepGroup implements LimitedStepGroup {

	private PipelineExtension pipeline;
	private String transactionVariable;
	// you can choose to scope the default transaction to this sequence
	// that means any action with no explicit transaction id is committed or rolled back
	private Boolean scopeDefaultTransaction;
	
	private Boolean suppressException;
	
	/**
	 * You can do synchronized steps across the cluster
	 */
	private Boolean isSynchronized;
	
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
		String previousDefaultTransactionId = null;
		String localDefaultTransactionId = null;
		if (scopeDefaultTransaction != null && scopeDefaultTransaction) {
			previousDefaultTransactionId = context.getExecutionContext().getTransactionContext().getDefaultTransactionId();
			localDefaultTransactionId = UUID.randomUUID().toString().replace("-", "");
			context.getExecutionContext().getTransactionContext().setDefaultTransactionId(localDefaultTransactionId);
		}
		ClusterLock lock = null;
		try {
			if (isSynchronized != null && isSynchronized) {
				ClusterInstance cluster = context.getCluster();
				if (cluster == null) {
					cluster = LocalInstance.getInstance();
				}
				lock = cluster.lock(getId());
				lock.lock();
			}
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
						if (context.decreaseBreakCount() == 0 && context.isContinueExecution()) {
							continue;
						}
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
					reportData(new TransactionReport(transactionId, "rollback"));
				}
				else {
					context.getExecutionContext().getTransactionContext().commit(transactionId);
					reportData(new TransactionReport(transactionId, "commit"));
				}
			}
			if (scopeDefaultTransaction != null && scopeDefaultTransaction) {
				if (isAborted()) {
					context.getExecutionContext().getTransactionContext().rollback(localDefaultTransactionId);
					reportData(new TransactionReport(localDefaultTransactionId, "rollback"));
				}
				else {
					context.getExecutionContext().getTransactionContext().commit(localDefaultTransactionId);
					reportData(new TransactionReport(localDefaultTransactionId, "commit"));
				}
			}
		}
		catch (Exception e) {
			exception = e;
			// roll back pending transaction if any
			if (transactionId != null) {
				try {
					context.getExecutionContext().getTransactionContext().rollback(transactionId);
					reportData(new TransactionReport(transactionId, "rollback"));
				}
				catch (Exception f) {
					logger.warn("Could not rollback transaction context during sequence exception handling", f);
				}
			}
			if (scopeDefaultTransaction != null && scopeDefaultTransaction) {
				try {
					context.getExecutionContext().getTransactionContext().rollback(localDefaultTransactionId);
					reportData(new TransactionReport(localDefaultTransactionId, "rollback"));
				}
				catch (Exception f) {
					logger.warn("Could not rollback default transaction context during sequence exception handling", f);
				}
				// this is also "rolled back" in the finally, but we want to do it here already, if the catch is using transactions, it should not be this one
				context.getExecutionContext().getTransactionContext().setDefaultTransactionId(previousDefaultTransactionId);
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
					if (catchClause.getTypes().size() == 0 && (catchClause.getCodes() == null || catchClause.getCodes().isEmpty()) && catchClause.getStacktraceRegex() == null)
						defaultCatchClause = catchClause;
					// stacktrace match takes prio
					else if (catchClause.getStacktraceRegex() != null) {
						StringWriter writer = new StringWriter();
						PrintWriter printer = new PrintWriter(writer);
						e.printStackTrace(printer);
						printer.flush();
						String content = writer.toString();
						// always do the multiline matching?
						// not sure if there is ever a usecase where you don't want this? if so, we need to make this smarter
						if (content.matches("(?s)" + catchClause.getStacktraceRegex())) {
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
			if (scopeDefaultTransaction != null && scopeDefaultTransaction) {
				try {
					context.getExecutionContext().getTransactionContext().rollback(localDefaultTransactionId);
				}
				catch (Exception f) {
					logger.warn("Could not rollback default transaction context during sequence exception handling", f);
				}
				// this is also "rolled back" in the finally, but we want to do it here already, if the catch is using transactions, it should not be this one
				context.getExecutionContext().getTransactionContext().setDefaultTransactionId(previousDefaultTransactionId);
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
			if (lock != null) {
				try {
					lock.unlock();
				}
				catch (Exception e) {
					logger.error("Could not unlock sequence", e);
				}
			}
			// in the beginning of the finally, any finally step should not be using the localized default transaction
			if (scopeDefaultTransaction != null && scopeDefaultTransaction) {
				context.getExecutionContext().getTransactionContext().setDefaultTransactionId(previousDefaultTransactionId);
			}
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
	
	@XmlAttribute
	public Boolean getScopeDefaultTransaction() {
		return scopeDefaultTransaction;
	}
	public void setScopeDefaultTransaction(Boolean scopeDefaultTransaction) {
		this.scopeDefaultTransaction = scopeDefaultTransaction;
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
	
	@XmlAttribute
	public Boolean getSynchronized() {
		return isSynchronized;
	}
	public void setSynchronized(Boolean isSynchronized) {
		this.isSynchronized = isSynchronized;
	}
}
