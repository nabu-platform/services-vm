package be.nabu.libs.services.vm.step;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.services.CombinedServiceRunner.CombinedServiceResult;
import be.nabu.libs.services.ServiceRunnable;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ForkableExecutionContext;
import be.nabu.libs.services.api.NamedServiceRunner;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunnableObserver;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.services.vm.ManagedCloseable;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

@XmlType(propOrder = {"serviceId", "resultName", "temporaryMapping", "x", "y", "invocationOrder", "target", "targetProperties", "asynchronous", "recache" })
public class Invoke extends BaseStepGroup implements LimitedStepGroup {

	private String resultName, serviceId;
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 * These allow positioning of the invoke by the display
	 */
	private double x, y;
	
	/**
	 * You can temporarily map the output to link to other invokes or you can map it more permanently in which case it must exist on the pipeline
	 */
	private boolean temporaryMapping = true;
	
	private int invocationOrder = 0;
	
	private String target;
	
	private boolean asynchronous = false, recache = false;
	
	private List<ManagedCloseable> managedCloseables = new ArrayList<ManagedCloseable>();
	
	private Map<String, String> targetProperties;

	public Invoke() {
		
	}
	
	public Invoke(Link...links) {
		super(links);
	}
	
	protected void execute(Link link, ComplexContent from, ComplexContent to) throws ServiceException {
		try {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().before(link);
			}
			link.execute(from, to);
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().after(link);
			}
		}
		catch (Exception e) {
			if (ServiceRuntime.getRuntime() != null && ServiceRuntime.getRuntime().getRuntimeTracker() != null) {
				ServiceRuntime.getRuntime().getRuntimeTracker().error(link, e);
			}
			if (e instanceof ServiceException) {
				throw (ServiceException) e;
			}
			else {
				throw new ServiceException("VM-6", link.getClass().getSimpleName() + ": " + link.getId(), e);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void execute(VMContext context) throws ServiceException {
		// execution (and retrieval) of a service _must_ go through the runtime as this is the only entity that has your session info
		// and as such the only one who can enforce permissions
		Service service = getService(context.getExecutionContext().getServiceContext());
		if (service == null) {
			logger.error("Could not find service: " + serviceId);
			throw new ServiceException("VM-3", "Could not find service: " + serviceId);
		}
		ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
		// now map all the inputs
		for (Step child : getChildren()) {
			Link link = (Link) child;
//			link.execute(context.getServiceInstance().getPipeline(), input);
			execute(link, context.getServiceInstance().getPipeline(), input);
		}
		
		ExecutorProvider executor = context.getServiceInstance().getDefinition().getExecutorProvider();
		// execute the service and map the result
		ComplexContent result;
		if (executor == null) {
			if (asynchronous) {
				// fork the execution context if possible, we don't want to asynchronously share an execution context
				// this could lead to failed transactions etc
				final ExecutionContext executionContext = context.getExecutionContext() instanceof ForkableExecutionContext
					? ((ForkableExecutionContext) context.getExecutionContext()).fork()
					: context.getExecutionContext();
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							ServiceRuntime serviceRuntime = new ServiceRuntime(service, executionContext);
							serviceRuntime.setRecache(recache);
							serviceRuntime.run(input);
						}
						catch (ServiceException e) {
							logger.error("Asynchronous execution exception occurred", e);
						}
					}
				}).start();
				result = null;
			}
			else {
				ServiceRuntime serviceRuntime = new ServiceRuntime(service, context.getExecutionContext());
				serviceRuntime.setRecache(recache);
				result = serviceRuntime.run(input);
			}
		}
		else {
			String target = this.target;
			if (target != null && target.startsWith("=")) {
				target = (String) getVariable(context.getServiceInstance().getPipeline(), target.substring(1));
			}
			Map<String, Object> targetProperties = new HashMap<String, Object>();
			if (this.targetProperties != null) {
				for (String key : this.targetProperties.keySet()) {
					Object value = this.targetProperties.get(key);
					if (value != null && ((String) value).startsWith("=")) {
						value = getVariable(context.getServiceInstance().getPipeline(), ((String) value).substring(1));
					}
					targetProperties.put(key, value);
				}
			}
			ServiceRunner runner = executor.getRunner(target, targetProperties);
			if (runner == null) {
				throw new ServiceException("VM-9", "Invalid target environment: " + target);
			}
			ExecutionContext executionContext = context.getExecutionContext();
			// fork the execution context if possible, we don't want to asynchronously share an execution context
			// this could lead to failed transactions etc
			if (asynchronous && executionContext instanceof ForkableExecutionContext) {
				executionContext = ((ForkableExecutionContext) executionContext).fork();
			}
			ServiceRunnableObserver [] observers = recache
				? new ServiceRunnableObserver[] { new ServiceRunnableObserver() {
					@Override
					public void start(ServiceRunnable runnable) {
						runnable.getRuntime().setRecache(true);
					}
					@Override
					public void stop(ServiceRunnable runnable) {
						
					}
				}}
				: new ServiceRunnableObserver[0];
			Future<ServiceResult> run = runner.run(service, executionContext, input, observers);
			if (!asynchronous && run != null) {
				try {
					ServiceResult serviceResult = run.get();
					if (serviceResult.getException() != null) {
						throw serviceResult.getException();
					}
					if (serviceResult instanceof CombinedServiceResult && getResultName() != null) {
						Map<ServiceRunner, ServiceResult> results = ((CombinedServiceResult) serviceResult).getResults();
						result = ((ComplexType) getPipeline(context.getExecutionContext().getServiceContext()).get(getResultName()).getType()).newInstance();
						int index = 0;
						for (ServiceRunner serviceRunner : results.keySet()) {
							 String name = serviceRunner instanceof NamedServiceRunner ? ((NamedServiceRunner) serviceRunner).getName() : null;
							 ComplexContent resultInstance = ((ComplexType) result.getType().get("results").getType()).newInstance();
							 resultInstance.set("name", name);
							 resultInstance.set("output", results.get(serviceRunner).getOutput());
							 result.set("results[" + index++ + "]", resultInstance);
						}
					}
					else {
						result = serviceResult.getOutput();
					}
				}
				catch (Exception e) {
					throw new ServiceException("VM-10", "Remote execution error", e);
				}
			}
			else {
				result = null;
			}
		}
		// only map the result if you have set a name
		// note that you can only manage closeable objects if you map the result to the pipeline
		if (resultName != null && result != null) {
			setVariable(context.getServiceInstance().getPipeline(), resultName, result);
			// map the necessary closeables to the necessary scope handlers
			for (ManagedCloseable closeable : managedCloseables) {
				Object object = getVariable(context.getServiceInstance().getPipeline(), resultName + "/" + closeable.getQuery());
				if (object instanceof Closeable)
					context.addManaged((Closeable) object, closeable.getScope());
				else if (object instanceof List)
					context.addManaged((List<Closeable>) object, closeable.getScope());
				else
					throw new ServiceException("VM-2", "Can only manage closeable objects, invalid query: " + closeable.getQuery(), closeable.getQuery());
			}
		}
	}
	
	@XmlAttribute
	public boolean isTemporaryMapping() {
		return temporaryMapping;
	}

	public void setTemporaryMapping(boolean temporaryMapping) {
		this.temporaryMapping = temporaryMapping;
	}

	public Service getService(ServiceContext context) {
		return context.getResolver(DefinedService.class).resolve(serviceId);
	}
	
	public void addManagedCloseable(String query, Scope scope) {
		ManagedCloseable closeable = new ManagedCloseable();
		closeable.setQuery(query);
		closeable.setScope(scope);
		managedCloseables.add(closeable);
	}

	public void setResultName(String resultName) {
		this.resultName = resultName;
	}
	@XmlAttribute	
	public String getResultName() {
		if (resultName == null) {
			// the uuid can generate strings that start with a number
			// these are not identified as valid targets by the query engine so prepend with a fixed part
			resultName = "result" + UUID.randomUUID().toString().replace("-", "");
		}
		return resultName;
	}
	
	@XmlAttribute
	public int getInvocationOrder() {
		return invocationOrder;
	}

	public void setInvocationOrder(int invokationOrder) {
		this.invocationOrder = invokationOrder;
	}

	@XmlAttribute
	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		if (getService(serviceContext) == null) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "Could not find service: " + serviceId)));
		}
		if (target != null && target.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, target.substring(1)));
		}
		if (targetProperties != null) {
			for (String key : targetProperties.keySet()) {
				String value = targetProperties.get(key);
				if (value != null && value.startsWith("=")) {
					messages.addAll(validateQuery(serviceContext, value.substring(1)));		
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
		return allowed;
	}

	@XmlAttribute
	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}

	@XmlAttribute
	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}
	
	@Override
	public void refresh() {
		// do nothing
	}

	@XmlAttribute
	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	@XmlAttribute
	public boolean isRecache() {
		return recache;
	}
	public void setRecache(boolean recache) {
		this.recache = recache;
	}

	@XmlAttribute
	public boolean isAsynchronous() {
		return asynchronous;
	}

	public void setAsynchronous(boolean asynchronous) {
		this.asynchronous = asynchronous;
	}
	
	//--------------------- key value pairs
	
	@XmlJavaTypeAdapter(value = KeyValueMapAdapter.class)
	public Map<String, String> getTargetProperties() {
		return targetProperties;
	}
	public void setTargetProperties(Map<String, String> targetProperties) {
		this.targetProperties = targetProperties;
	}

	@XmlRootElement(name = "property")
	public static class KeyValuePairImpl implements KeyValuePair {
		private String key, value;

		public KeyValuePairImpl(String key, String value) {
			this.key = key;
			this.value = value;
		}
		public KeyValuePairImpl() {
			// auto construction
		}

		@XmlAttribute
		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		@XmlValue
		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}
	public static class KeyValueMapAdapter extends XmlAdapter<KeyValueMapAdapter.MapRoot, Map<String, String>> {

		public static class MapRoot {
			private List<KeyValuePairImpl> properties = new ArrayList<KeyValuePairImpl>();

			@XmlElement(name = "property")
			public List<KeyValuePairImpl> getProperties() {
				return properties;
			}
			public void setProperties(List<KeyValuePairImpl> properties) {
				this.properties = properties;
			}
		}
		
		@Override
		public Map<String, String> unmarshal(MapRoot v) throws Exception {
			Map<String, String> map = new LinkedHashMap<String, String>();
			if (v == null) {
				return map;
			}
			for (KeyValuePair pair : v.getProperties()) {
				map.put(pair.getKey(), pair.getValue() == null || pair.getValue().trim().isEmpty() ? null : pair.getValue());
			}
			return map;
		}

		@Override
		public MapRoot marshal(Map<String, String> v) throws Exception {
			if (v == null) {
				return null;
			}
			MapRoot root = new MapRoot();
			for (String key : v.keySet()) {
				root.getProperties().add(new KeyValuePairImpl(key, v.get(key)));
			}
			return root;
		}

	}

}
