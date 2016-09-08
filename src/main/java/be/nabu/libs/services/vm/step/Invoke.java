package be.nabu.libs.services.vm.step;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.ManagedCloseable;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.ManagedCloseable.Scope;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

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
	
	private List<ManagedCloseable> managedCloseables = new ArrayList<ManagedCloseable>();

	public Invoke() {
		
	}
	
	public Invoke(Link...links) {
		super(links);
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
			link.execute(context.getServiceInstance().getCurrentPipeline(), input);
		}
		// execute the service and map the result
		ComplexContent result = new ServiceRuntime(service, context.getExecutionContext()).run(input);
		
		// only map the result if you have set a name
		// note that you can only manage closeable objects if you map the result to the pipeline
		if (resultName != null) {
			setVariable(context.getServiceInstance().getCurrentPipeline(), resultName, result);
			// map the necessary closeables to the necessary scope handlers
			for (ManagedCloseable closeable : managedCloseables) {
				Object object = getVariable(context.getServiceInstance().getCurrentPipeline(), resultName + "/" + closeable.getQuery());
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
}
