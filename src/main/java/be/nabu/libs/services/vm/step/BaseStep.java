package be.nabu.libs.services.vm.step;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.impl.VariableOperation;
import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.evaluator.types.operations.TypeVariableOperation;
import be.nabu.libs.evaluator.types.operations.TypesOperationProvider;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.validator.api.ContextUpdatableValidation;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

abstract public class BaseStep implements Step {

	/**
	 * Keeps a map of all the analyzed operations for this step
	 * The analytical phase carries (by far) the biggest overhead, afterwards execution is fast
	 * Liken this to compiling a bit of java code for later re-execution
	 */
	private Map<String, TypeOperation> analyzedOperations = new HashMap<String, TypeOperation>();
	
	private String comment, name;
	
	private String id;
	
	private StepGroup parent;

	private VMService definition;
	
	private String label;
	
	private boolean isDisabled = false;
	
	public BaseStep(VMService definition) {
		this.definition = definition;
	}
	
	public BaseStep() {
		// will be set by setParent();
	}

	@XmlAttribute
	@Override
	public String getId() {
		if (id == null)
			id = UUID.randomUUID().toString();
		return id;
	}
	@Override
	public void setId(String id) {
		this.id = id;
	}
	
	@XmlTransient
	@Override
	public StepGroup getParent() {
		return parent;
	}
	@Override
	public void setParent(StepGroup parent) {
		this.parent = parent;
	}
	
	public Object getVariable(ComplexContent pipeline, String query) throws ServiceException {
		VariableOperation.registerRoot();
		try {
			return getOperation(query).evaluate(pipeline);
		}
		catch (EvaluationException e) {
			throw new ServiceException(e);
		}
		catch (ParseException e) {
			throw new ServiceException(e);
		}
		finally {
			VariableOperation.unregisterRoot();
		}
	}
	public void setVariable(ComplexContent pipeline, String query, Object value) throws ServiceException {
		try {
			TypeOperation operation = getOperation(query);
			if (!(operation instanceof TypeVariableOperation))
				throw new ServiceException("VM-1", "When setting you can only use variable operations");
			String path = ((TypeVariableOperation) operation).resolve(pipeline);
			pipeline.set(path, value);
		}
		catch(EvaluationException e) {
			throw new ServiceException(e);
		}
		catch (ParseException e) {
			throw new ServiceException(e);
		}		
	}
	
	protected TypeOperation getOperation(String query) throws ParseException {
		if (!analyzedOperations.containsKey(query)) {
			synchronized(analyzedOperations) {
				if (!analyzedOperations.containsKey(query))
					analyzedOperations.put(query, (TypeOperation) new PathAnalyzer<ComplexContent>(new TypesOperationProvider()).analyze(QueryParser.getInstance().parse(query)));
			}
		}
		return analyzedOperations.get(query);
	}
	
	@XmlTransient
	public VMService getServiceDefinition() {
		if (definition == null && parent != null)
			return parent.getServiceDefinition();
		else
			return definition;
	}
	public void setDefinition(VMService definition) {
		this.definition = definition;
	}

	protected String getContext() {
		return getClass().getSimpleName() + "[" + getIndexInParent() + "]" + ":" + getId();
	}
	private int getIndexInParent() {
		if (getParent() == null)
			return 0;
		for (int i = 0; i < getParent().getChildren().size(); i++) {
			if (getParent().getChildren().get(i).equals(this))
				return i;
		}
		return -1;
	}

	public List<ValidationMessage> checkNameInScope(ServiceContext serviceContext, String name, Type type) {
		List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
		if (name == null)
			messages.add(new ValidationMessage(Severity.ERROR, "The variable name can not be null"));
		else if (getParent() != null) {
			ComplexType pipeline = getParent().getPipeline(serviceContext);
			Element<?> element = pipeline.get(name);
			if (element == null)
				messages.add(new ValidationMessage(Severity.ERROR, "The variable '" + name + "' does not exist in the parent scope"));
			else if (!getServiceDefinition().isMappable(new BaseTypeInstance(type), element))
				messages.add(new ValidationMessage(Severity.ERROR, "The variable '" + name + "' that exists in the parent scope is not compatible with the type " + type));
		}
		return messages;
	}

	public List<Validation<?>> checkNameInScope(ServiceContext serviceContext, String name) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		if (name == null) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "The variable name can not be null")));
		}
		else if (getParent() != null) {
			ComplexType pipeline = getParent().getPipeline(serviceContext);
			if (pipeline.get(name) != null) {
				messages.add(addContext(new ValidationMessage(Severity.ERROR, "The variable '" + name + "' already exists in the parent scope, it can not be redefined")));
			}
		}
		return messages;
	}
	
	public List<Validation<?>> addContext(List<Validation<?>> messages) {
		for (Validation<?> message : messages) {
			addContext(message);
		}
		return messages;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Validation<?> addContext(Validation<?> message) {
		if (message instanceof ContextUpdatableValidation) {
			((ContextUpdatableValidation) message).addContext(getContext());
		}
		return message;
	}

	@Override
	@XmlAttribute
	public String getComment() {
		return comment;
	}
	@Override
	public void setComment(String comment) {
		this.comment = comment;
	}

	@Override
	@XmlAttribute
	public String getLabel() {
		return label;
	}

	@Override
	public void setLabel(String label) {
		this.label = label;
	}

	@XmlAttribute
	@Override	
	public boolean isDisabled() {
		return isDisabled;
	}
	@Override
	public void setDisabled(boolean isDisabled) {
		this.isDisabled = isDisabled;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		if (label != null) {
			// even step groups have to be validated against the pipeline of the parent
			// otherwise you might try to evaluate stuff that doesn't exist yet
			messages.addAll(validateQuery(serviceContext, label));
		}
		return messages;
	}

	protected List<Validation<?>> validateQuery(ServiceContext serviceContext, String query) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		try {
			messages.addAll(addContext(getOperation(query).validate(getParent().getPipeline(serviceContext))));
		}
		catch (ParseException e) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "The query '" + query+ "' can not be parsed: " + e.getMessage())));
		}
		return messages;
	}
	
	@Override
	public String toString() {
		return getContext();
	}

	@XmlAttribute
	@Override
	public String getName() {
		return name;
	}
	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	
}
