package be.nabu.libs.services.vm.step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.PipelineExtension;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.types.BaseTypeInstance;
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
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

@XmlType(propOrder = { "variable", "index", "query", "batchSize", "into" })
public class For extends BaseStepGroup implements LimitedStepGroup {

	// the "into" field allows you to indicate a target array (which must exist on the pipeline)
	// in each iteration you can choose to map [0, batchSize] amount of items into the target array which will then be added to the total result
	// this allows for easier construction of mappings without the need for a drop, add to list etc
	// the into remains an array, even in the loop, this is easier (cause the data structure doesn't change) but it is also more powerful
	// this allows you to reduce a loop from say 5000 elements into a result of say 3000
	// but you can also enrich from 5000 to for example 7000
	// or do a 1-1 mapping
	private String variable, indexName, query, batchSize, into;
	
	private SimpleTypeWrapper simpleTypeWrapper;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addInto(List into, Object result) {
		if (result != null) {
			if (!(result instanceof Iterable)) {
				CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(result.getClass());
				if (handler == null) {
					throw new IllegalArgumentException("Can not merge result into the requested target");
				}
				result = handler.getAsIterable(result);
			}
			for (Object single : (Iterable) result) {
				into.add(single);
			}
		}
	}
	
	private void addInto(VMContext context, Object resultingInto) throws ServiceException {
		if (into != null) {
			Object partialResult = getVariable(context.getServiceInstance().getPipeline(), into);
			addInto((List) resultingInto, partialResult);
			// reset to null again for next iteration
			setVariable(context.getServiceInstance().getPipeline(), into, null);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void execute(VMContext context) throws ServiceException {
		Object value = getVariable(context.getServiceInstance().getPipeline(), getQuery());
		if (value != null) {
			Object batchSize = this.batchSize == null ? null : getVariable(context.getServiceInstance().getPipeline(), this.batchSize);
			
			Object resultingInto = null;
			
			if (into != null) {
				resultingInto = getVariable(context.getServiceInstance().getPipeline(), into);
				if (resultingInto == null) {
					resultingInto = new ArrayList();
				}
				else if (!(resultingInto instanceof List)) {
					List list = new ArrayList();
					addInto(list, resultingInto);
					resultingInto = list;
				}
				// set the into to null so we can start creating sublists
				setVariable(context.getServiceInstance().getPipeline(), into, null);
			}
				
			long increment = batchSize instanceof Number ? ((Number) batchSize).longValue() : 1;
			// if we have a boolean, we execute until it is not true
			if (value instanceof Boolean) {
				// if we have a batch size, we start at that point, e.g. if you set 5, you immediately start with index 4
				long index = increment - 1;
				while (value instanceof Boolean && (Boolean) value) {
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
					
					// now set the variables (if applicable)
					if (indexName != null) {
						// if the definition exists, we created a list of this
						if (this.batchSize != null) {
							List list = new ArrayList();
							// if batch size is 5, the index is 4, 9,... and we want a list of [0-4], [5-9]...
							for (long i = index - increment + 1; i <= index; i++) {
								list.add(i);
							}
							setVariable(context.getServiceInstance().getPipeline(), indexName, list);
						}
						else {
							setVariable(context.getServiceInstance().getPipeline(), indexName, index);
						}
					}
					if (variable != null) {
						// if the definition exists, we created a list of this
						if (this.batchSize != null) {
							List list = new ArrayList();
							// if batch size is 5, the index is 4, 9,... and we want a list of [0-4], [5-9]...
							for (long i = index - increment + 1; i <= index; i++) {
								list.add(value);
							}
							setVariable(context.getServiceInstance().getPipeline(), variable, list);
						}
						else {
							setVariable(context.getServiceInstance().getPipeline(), variable, value);
						}
					}
					executeSteps(context);
					// do it _before_ we check for break
					addInto(context, resultingInto);
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
					index += increment;
				}
			}
			else if (value instanceof Number) {
				long longValue = ((Number) value).longValue();
				for (long i = increment - 1; i < longValue; i += Math.min(increment, longValue - i)) {
					context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
					
					// now set the variables (if applicable)
					if (indexName != null) {
						// if the definition exists, we created a list of this
						if (this.batchSize != null) {
							List list = new ArrayList();
							// if batch size is 5, the index is 4, 9,... and we want a list of [0-4], [5-9]...
							for (long j = i - increment + 1; j <= i; j++) {
								list.add(j);
							}
							setVariable(context.getServiceInstance().getPipeline(), indexName, list);
						}
						else {
							setVariable(context.getServiceInstance().getPipeline(), indexName, i);
						}
					}
					if (variable != null) {
						// if the definition exists, we created a list of this
						if (this.batchSize != null) {
							List list = new ArrayList();
							// if batch size is 5, the index is 4, 9,... and we want a list of [0-4], [5-9]...
							for (long j = i - increment + 1; j <= i; j++) {
								list.add(j);
							}
							setVariable(context.getServiceInstance().getPipeline(), variable, list);
						}
						else {
							setVariable(context.getServiceInstance().getPipeline(), variable, i);
						}
					}
					executeSteps(context);
					addInto(context, resultingInto);
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
				if (this.batchSize == null) {
					for (Object single : (Iterable) value) {
						context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
						if (indexName != null) {
							setVariable(context.getServiceInstance().getPipeline(), indexName, index++);
						}
						if (variable != null) {
							setVariable(context.getServiceInstance().getPipeline(), variable, single);
						}
						executeSteps(context);
						addInto(context, resultingInto);
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
					List indexes = indexName != null ? new ArrayList() : null;
					List values = variable != null ? new ArrayList() : null;
					long batchIndex = 0;
					for (Object single : (Iterable) value) {
						// always add it to the list
						if (indexes != null) {
							indexes.add(index++);
						}
						if (values != null) {
							values.add(single);
						}
						batchIndex++;
						// if we have reached the increment size, we run it
						if (batchIndex == increment) {
							context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
							if (indexName != null) {
								setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
							}
							if (variable != null) {
								setVariable(context.getServiceInstance().getPipeline(), variable, values);
							}
							executeSteps(context);
							addInto(context, resultingInto);
							// check break count
							if (context.mustBreak()) {
								context.decreaseBreakCount();
								break;
							}
							batchIndex = 0;
							if (indexes != null) {
								indexes.clear();
							}
							if (values != null) {
								values.clear();
							}
						}
						if (isAborted()) {
							break;
						}
					}
					// we have remaining in the batch...
					if (batchIndex > 0) {
						context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
						if (indexName != null) {
							setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
						}
						if (variable != null) {
							setVariable(context.getServiceInstance().getPipeline(), variable, values);
						}
						executeSteps(context);
						addInto(context, resultingInto);
					}
				}
			}
			// shameless copy of the else {} adapted to work with batches...
			// should probably be refactored at some point...
			else if (this.batchSize != null) {
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
				if (collectionHandler == null) {
					throw new IllegalArgumentException("The variable '" + value + "' does not point to a collection");
				}
				Class indexClass = collectionHandler.getIndexClass();
				
				List indexes = indexName != null ? new ArrayList() : null;
				List values = variable != null ? new ArrayList() : null;
				long batchIndex = 0;
				// if we have an integer index, we assume it is a list-like structure with an incremental index
				// for performance reasons it is much better to get the collection as iterable and generate the index rather than get the collection of indexes and use that versus the collection
				if (Integer.class.isAssignableFrom(indexClass)) {
					Iterable iterable = collectionHandler.getAsIterable(value);
					int index = 0;
					for (Object single : iterable) {
						// always add it to the list
						if (indexes != null) {
							indexes.add(index++);
						}
						if (values != null) {
							values.add(single);
						}
						batchIndex++;
						// if we have reached the increment size, we run it
						if (batchIndex == increment) {
							context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
							
							if (indexName != null) {
								setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
							}
							if (variable != null) {
								setVariable(context.getServiceInstance().getPipeline(), variable, values);
							}
							executeSteps(context);
							addInto(context, resultingInto);
							// check break count
							if (context.mustBreak()) {
								context.decreaseBreakCount();
								break;
							}
							batchIndex = 0;
							if (indexes != null) {
								indexes.clear();
							}
							if (values != null) {
								values.clear();
							}
						}
						if (isAborted()) {
							break;
						}
					}
					// we have remaining in the batch...
					if (batchIndex > 0) {
						context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
						if (indexName != null) {
							setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
						}
						if (variable != null) {
							setVariable(context.getServiceInstance().getPipeline(), variable, values);
						}
						executeSteps(context);
						addInto(context, resultingInto);
					}
				}
				else {
					for (Object index : collectionHandler.getIndexes(value)) {
						// always add it to the list
						if (indexes != null) {
							indexes.add(index);
						}
						if (values != null) {
							values.add(collectionHandler.get(value, index));
						}
						batchIndex++;
						// if we have reached the increment size, we run it
						if (batchIndex == increment) {
							// cast the pipeline to the proper definition
							// if the pipeline belongs to the parent, an additional local wrapper will be added
							// if the pipeline belongs to this guy, it is unchanged
							// if the pipeline belongs to a child scope, the additional parameters are unwrapped
							// this means anything in this scope (NOT a child scope) is retained
							context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
				
							// now set the variables (if applicable)
							if (indexName != null) {
								setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
							}
							if (variable != null) {
								setVariable(context.getServiceInstance().getPipeline(), variable, values);
							}
							executeSteps(context);
							addInto(context, resultingInto);
							// check break count
							if (context.mustBreak()) {
								context.decreaseBreakCount();
								break;
							}
							batchIndex = 0;
							if (indexes != null) {
								indexes.clear();
							}
							if (values != null) {
								values.clear();
							}
						}
						if (isAborted()) {
							break;
						}
					}
					// we have remaining in the batch...
					if (batchIndex > 0) {
						context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
						if (indexName != null) {
							setVariable(context.getServiceInstance().getPipeline(), indexName, indexes);
						}
						if (variable != null) {
							setVariable(context.getServiceInstance().getPipeline(), variable, values);
						}
						executeSteps(context);
						addInto(context, resultingInto);
					}
				}
			}
			else {
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
				if (collectionHandler == null) {
					throw new IllegalArgumentException("The variable '" + value + "' does not point to a collection");
				}
				Class indexClass = collectionHandler.getIndexClass();
				// if we have an integer index, we assume it is a list-like structure with an incremental index
				// for performance reasons it is much better to get the collection as iterable and generate the index rather than get the collection of indexes and use that versus the collection
				if (Integer.class.isAssignableFrom(indexClass)) {
					Iterable iterable = collectionHandler.getAsIterable(value);
					int index = 0;
					for (Object single : iterable) {
						context.getServiceInstance().castPipeline(getPipeline(context.getExecutionContext().getServiceContext()));
						
						if (indexName != null) {
							setVariable(context.getServiceInstance().getPipeline(), indexName, index++);
						}
						if (variable != null) {
							setVariable(context.getServiceInstance().getPipeline(), variable, single);
						}
						executeSteps(context);
						addInto(context, resultingInto);
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
						addInto(context, resultingInto);
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
			
			// we have been building a resultset, push it to the pipeline
			if (into != null) {
				setVariable(context.getServiceInstance().getPipeline(), into, resultingInto);
			}
		}
	}

	private void executeSteps(VMContext context) throws ServiceException {
		for (Step child : getChildren()) {
			if (child.isDisabled()) {
				continue;
			}
			executeIfLabel(child, context);
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
	public String getBatchSize() {
		return batchSize;
	}
	public void setBatchSize(String batchSize) {
		this.batchSize = batchSize;
	}

	@XmlAttribute
	public String getInto() {
		return into;
	}
	public void setInto(String into) {
		this.into = into;
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
						boolean isBatch = batchSize != null;
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
									pipeline.add(new SimpleElementImpl(this.indexName, wrapped, pipeline, new ValueImpl<Integer>(new MinInclusiveProperty<Integer>(Integer.class), 0),
										new ValueImpl<Integer>(MaxOccursProperty.getInstance(), isBatch ? 0 : 1)), false);
								}
								else {
									pipeline.add(new ComplexElementImpl(this.indexName, (ComplexType) BeanResolver.getInstance().resolve(indexType), pipeline, new ValueImpl<Integer>(new MinInclusiveProperty<Integer>(Integer.class), 0),
										new ValueImpl<Integer>(MaxOccursProperty.getInstance(), isBatch ? 0 : 1)), false);	
								}
							}
							catch (Exception e) {
								logger.error("Can not determine the return type for the index of the for loop: " + query, e);
							}
						}
						// now the result of the query...
						if (variable != null) {
							try {
								TypeOperation operation = getOperation(query);
								// the operation must be resolved against the parent pipeline
								// you may be using variables exposed by it rather then the original service
								Type returnType = operation.getReturnType(getParent().getPipeline(serviceContext));
								// in some very rare cases the min/max occurs can reside in the type (it shouldn't but notably the swagger client currently does this...)
								// in that case, we want to force the max occurs to be 1 at design time, the runtime will always make it 1 as well
								Element<?> element = returnType instanceof ComplexType
									? new ComplexElementImpl(variable, (ComplexType) returnType, pipeline)
									: new SimpleElementImpl(variable, (SimpleType<?>) returnType, pipeline);
								((BaseTypeInstance) element).setMaintainDefaultValues(true);
								element.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), isBatch ? 0 : 1));
								pipeline.add(element, false);
							}
							catch (Exception e) {
								logger.error("The value query in the for loop is invalid: " + query, e);
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
			messages.add(addContext(new ValidationMessage(Severity.WARNING, "No query defined for the 'for' loop")));
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
		allowed.add(Break.class);
		return allowed;
	}

	@Override
	public void refresh() {
		pipeline = null;
	}
}
