package be.nabu.libs.services.vm.step;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.PipelineExtension;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class For extends BaseStepGroup implements LimitedStepGroup {

	private String variable, indexName, query;
	
	private SimpleTypeWrapper simpleTypeWrapper;
	
	/**
	 * The pipeline that is built is cached in order to increase performance
	 * This requires an explicit reset by the developer though to trigger the new pipeline definition
	 */
	private PipelineExtension pipeline;

	public For(Step...steps) {
		super(steps);
	}
	
	public For() {

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void execute(VMContext context) throws ServiceException {
		Object value = getVariable(context.getServiceInstance().getPipeline(), getQuery());
		if (value != null) {
			
			// if we have a boolean, we execute until it is not true
			if (value instanceof Boolean) {
				long index = 0;
				while (value instanceof Boolean && (Boolean) value) {
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
					
					// now set the variables (if applicable)
					if (indexName != null) {
						setVariable(context.getServiceInstance().getPipeline(), indexName, index++);
					}
					if (variable != null) {
						setVariable(context.getServiceInstance().getPipeline(), variable, value);
					}
					executeSteps(context);
					// check break count
					if (context.mustBreak()) {
						context.decreaseBreakCount();
						break;
					}
					else if (isAborted()) {
						break;
					}
					// evaluate it again
					value = getVariable(context.getServiceInstance().getPipeline(), getQuery());
				}
			}
			else if (value instanceof Number) {
				for (long i = 0; i < ((Number) value).longValue(); i++) {
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
					
					// now set the variables (if applicable)
					if (indexName != null) {
						setVariable(context.getServiceInstance().getPipeline(), indexName, i);
					}
					if (variable != null) {
						setVariable(context.getServiceInstance().getPipeline(), variable, i);
					}
					executeSteps(context);
					// check break count
					if (context.mustBreak()) {
						context.decreaseBreakCount();
						break;
					}
					else if (isAborted()) {
						break;
					}
				}
			}
			else if (value instanceof Iterable) {
				long index = 0;
				for (Object single : (Iterable) value) {
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
					
					if (indexName != null) {
						setVariable(context.getServiceInstance().getPipeline(), indexName, index++);
					}
					if (variable != null) {
						setVariable(context.getServiceInstance().getPipeline(), variable, single);
					}
					executeSteps(context);
					// check break count
					if (context.mustBreak()) {
						context.decreaseBreakCount();
						break;
					}
					else if (isAborted()) {
						break;
					}
				}
			}
			else {
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
				if (collectionHandler == null) {
					throw new IllegalArgumentException("The variable '" + value + "' does not point to a collection");
				}
				for (Object index : collectionHandler.getIndexes(value)) {
					// cast the pipeline to the proper definition
					// if the pipeline belongs to the parent, an additional local wrapper will be added
					// if the pipeline belongs to this guy, it is unchanged
					// if the pipeline belongs to a child scope, the additional parameters are unwrapped
					// this means anything in this scope (NOT a child scope) is retained
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
		
					// now set the variables (if applicable)
					if (indexName != null) {
						setVariable(context.getServiceInstance().getPipeline(), indexName, index);
					}
					if (variable != null) {
						setVariable(context.getServiceInstance().getPipeline(), variable, collectionHandler.get(value, index));
					}
					executeSteps(context);
					// check break count
					if (context.mustBreak()) {
						context.decreaseBreakCount();
						break;
					}
					else if (isAborted()) {
						break;
					}
				}
			}
		}
	}

	private void executeSteps(VMContext context) throws ServiceException {
		for (Step child : getChildren()) {
			if (child.isDisabled()) {
				continue;
			}
			execute(child, context);
			// check break count
			if (context.mustBreak()) {
				break;
			}
			else if (isAborted()) {
				break;
			}
		}
	}

	@XmlAttribute
	public String getVariable() {
		return variable;
	}
	public For setVariable(String variable) {
		this.variable = variable;
		this.pipeline = null;
		return this;
	}

	@XmlAttribute
	public String getIndex() {
		return indexName;
	}
	public For setIndex(String index) {
		this.indexName = index;
		this.pipeline = null;
		return this;
	}

	public String getQuery() {
		return query;
	}
	
	public For setQuery(String query) {
		this.query = query;
		return this;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComplexType getPipeline(ServiceContext serviceContext) {
		if (this.indexName != null || this.variable != null) {
			if (pipeline == null) {
				synchronized(this) {
					if (pipeline == null) {
						// create a new structure
						PipelineExtension pipeline = new PipelineExtension();
						// that extends the parent structure
						pipeline.setSuperType(getParent().getPipeline(serviceContext));
						// inherit the name to make it clearer when marshalling
						pipeline.setName(getParent().getPipeline(serviceContext).getName());
						// add the index if necessary
						if (indexName != null) {
							try {
								TypeOperation operation = getOperation(query);
								// the operation must be resolved against the parent pipeline
								// you may be using variables exposed by it rather then the original service
								ComplexType parentPipeline = getParent().getPipeline(serviceContext);
								CollectionHandlerProvider<?, ?> collectionHandler = operation.getReturnCollectionHandler(parentPipeline);
								Class<?> indexType = collectionHandler == null ? null : collectionHandler.getIndexClass();
								if (indexType == null) {
									indexType = Long.class;
								}
								DefinedSimpleType<?> wrapped = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(indexType);
								if (wrapped != null) {
									pipeline.add(new SimpleElementImpl(this.indexName, wrapped, pipeline, new ValueImpl<Integer>(new MinInclusiveProperty<Integer>(Integer.class), 0)), false);
								}
								else {
									pipeline.add(new ComplexElementImpl(this.indexName, (ComplexType) BeanResolver.getInstance().resolve(indexType), pipeline, new ValueImpl<Integer>(new MinInclusiveProperty<Integer>(Integer.class), 0)), false);	
								}
							}
							catch (ParseException e) {
								throw new IllegalArgumentException("The given operation '" + query + "' is not valid", e);
							}
						}
						// now the result of the query...
						if (variable != null) {
							try {
								TypeOperation operation = getOperation(query);
								// the operation must be resolved against the parent pipeline
								// you may be using variables exposed by it rather then the original service
								Type returnType = operation.getReturnType(getParent().getPipeline(serviceContext));
								Element<?> element = returnType instanceof ComplexType
									? new ComplexElementImpl(variable, (ComplexType) returnType, pipeline)
									: new SimpleElementImpl(variable, (SimpleType<?>) returnType, pipeline);
								pipeline.add(element, false);
							}
							catch (ParseException e) {
								throw new IllegalArgumentException("The given operation '" + query + "' is not valid", e);
							}
						}
						this.pipeline = pipeline;
					}
				}
			}
			return pipeline;
		}
		// if you don't add anything, use default behavior
		else
			return getParent().getPipeline(serviceContext);
	}

	@XmlTransient
	public SimpleTypeWrapper getSimpleTypeWrapper() {
		if (simpleTypeWrapper == null)
			simpleTypeWrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
		return simpleTypeWrapper;
	}
	
	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>(super.validate(serviceContext));
		if (variable != null) {
			messages.addAll(checkNameInScope(serviceContext, variable));
		}
		if (indexName != null) {
			messages.addAll(checkNameInScope(serviceContext, indexName));
		}
		if (query == null || query.isEmpty()) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "No query defined for the 'for' loop")));
		}
		else {
			messages.addAll(validateQuery(serviceContext, query));
		}
		return messages;
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
		return allowed;
	}

	@Override
	public void refresh() {
		pipeline = null;
	}
}
