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

package be.nabu.libs.services.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.artifacts.ArtifactUtils;
import be.nabu.libs.artifacts.ExceptionDescriptionImpl;
import be.nabu.libs.artifacts.FeatureImpl;
import be.nabu.libs.artifacts.api.ArtifactWithExceptions;
import be.nabu.libs.artifacts.api.ExceptionDescription;
import be.nabu.libs.artifacts.api.ExceptionDescription.ExceptionType;
import be.nabu.libs.artifacts.api.Feature;
import be.nabu.libs.artifacts.api.Todo;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ModifiableServiceInterface;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.BaseStepGroup;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.TypeInstance;
import be.nabu.libs.types.base.StringMapCollectionHandlerProvider;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.properties.AspectProperty;
import be.nabu.libs.types.properties.CollectionHandlerProviderProperty;

public class SimpleVMServiceDefinition implements VMService, ArtifactWithExceptions {

	private ComplexType input, output;
	private Sequence root;
	private Pipeline pipeline;
	private ServiceInterface serviceInterface;
	private ExecutorProvider executorProvider;
	private String description;
	
	private String id;
	private TypeConverter typeConverter;
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 * Whether or not to allow this:
	 * class A; class B extends A;
	 * A a = new A();
	 * B b = (B) a;
	 * 
	 * This can not work in java but it can with structures the new B will share the properties with a and add a few new ones
	 * This boolean determines if such mapping is allowed
	 * TODO: this should be moved to the mapping engine
	 */
	private boolean allowTypeExpansion = true;
	
	private boolean allowEmptyStructureMapping = Boolean.parseBoolean(System.getProperty("allow.empty.structure.mapping", "false"));
	
	public SimpleVMServiceDefinition(ComplexType from, ComplexType to) {
		this.input = from;
		this.output = to;
	}
	
	public SimpleVMServiceDefinition(Pipeline pipeline) {
		this.pipeline = pipeline;
		this.input = (ComplexType) pipeline.get(Pipeline.INPUT).getType();
		this.output = (ComplexType) pipeline.get(Pipeline.OUTPUT).getType();
	}
	
	@XmlTransient
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public TypeConverter getTypeConverter() {
		if (typeConverter == null)
			typeConverter = TypeConverterFactory.getInstance().getConverter();
		return typeConverter;
	}

	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * The mapping engine should also take into account that you can't actually cast e.g. java objects etc
	 * If a cast is necessary, it should contemplate generating structures to fix it
	 */
	@SuppressWarnings("rawtypes")
	public boolean isMappable(TypeInstance fromItem, TypeInstance toItem) {
		// if both items are a java.util.Map, we can map it immediately
		Value<CollectionHandlerProvider> fromProperty = fromItem.getProperty(CollectionHandlerProviderProperty.getInstance());
		Value<CollectionHandlerProvider> toProperty = toItem.getProperty(CollectionHandlerProviderProperty.getInstance());
		if (fromProperty != null && fromProperty.getValue() instanceof StringMapCollectionHandlerProvider && toProperty != null && toProperty.getValue() instanceof StringMapCollectionHandlerProvider) {
			return true;
		}
		
		// if the target item is an aspect, check if the source item is a subset
		// no casting will be done as the target is merely an aspect, it expects a data interface
		if (ValueUtils.getValue(new AspectProperty(), toItem.getProperties()))
			return TypeUtils.isSubset(fromItem, toItem);
		else {
			boolean mappable = isMappable(fromItem.getType(), toItem.getType());
			// check if we have an automatic converter for this (e.g. string to int etc)
			return mappable ? mappable : getTypeConverter().canConvert(fromItem, toItem);
		}
	}
	
	public boolean isMappable(Type fromType, Type toType) {
		// see if you can cast the origin type to the target type
		boolean mappable = fromType.equals(toType) || !TypeUtils.getUpcastPath(fromType, toType).isEmpty();

		// the question of isMappable() is only relevant at design time
		// due to reloading etc, it is possible to have different instances of the same type at design time
		// however, at runtime they are guaranteed to be the same type so we allow it
		if (!mappable && fromType instanceof DefinedType && toType instanceof DefinedType) {
			String fromId = ((DefinedType) fromType).getId();
			String toId = ((DefinedType) toType).getId();
			if (fromId == null) {
				logger.warn("Could not get id of defined type: " + fromType);
			}
			else if (toId == null) {
				logger.warn("Could not get id of defined type: " + toType);
			}
			else if (fromId.equals(toId)) {
				mappable = true;
			}
		}
		
		// if the target type has no children, it's an empty one, it can hold anything, allow it
		if (allowEmptyStructureMapping && !mappable && fromType instanceof ComplexType && toType instanceof ComplexType && !TypeUtils.getAllChildrenIterator((ComplexType) toType).hasNext()) {
			mappable = true;
		}
		// if the target is java.lang.Object, you can assign anything
		else if (!mappable && ((toType instanceof BeanType && ((BeanType<?>) toType).getBeanClass().equals(Object.class)) || (fromType instanceof BeanType && ((BeanType<?>) fromType).getBeanClass().equals(Object.class)))) {
			mappable = true;
		}
		// allow from unknown to full as well?
		// this can cause a classcast exception at runtime though
		
		// see if you can downcast the other way. This is only supported by structures and will require special logic to make it work on generic types
		if (!mappable && allowTypeExpansion)
			mappable = !TypeUtils.getUpcastPath(toType, fromType).isEmpty();
		// check if we have an automatic converter for this (e.g. string to int etc)
		return mappable ? mappable : getTypeConverter().canConvert(new BaseTypeInstance(fromType), new BaseTypeInstance(toType));
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		if (serviceInterface == null) {
			serviceInterface = new ModifiableServiceInterface() {
				@Override
				public ComplexType getInputDefinition() {
					return input;
				}
				@Override
				public ComplexType getOutputDefinition() {
					return output;
				}
				@Override
				public ServiceInterface getParent() {
					return ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), getPipeline().getProperties());
				}
				@Override
				public void setParent(ServiceInterface parent) {
					if (parent instanceof DefinedServiceInterface) {
						getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), (DefinedServiceInterface) parent));
					}
				}
			};
		}
		return serviceInterface;
	}

	@Override
	public VMServiceInstance newInstance() {
		return new VMServiceInstance(this);
	}

	@Override
	public Set<String> getReferences() {
		return new HashSet<String>();
	}

	@Override
	public Sequence getRoot() {
		if (root == null) {
			setRoot(new Sequence());
		}
		return root;
	}
	public SimpleVMServiceDefinition setRoot(Sequence root) {
		this.root = root;
		if (root != null) {
			root.setDefinition(this);
		}
		return this;
	}

	@Override
	public Pipeline getPipeline() {
		if (pipeline == null) {
			pipeline = new Pipeline(this);
		}
		return pipeline;
	}

	public ExecutorProvider getExecutorProvider() {
		return executorProvider;
	}

	public void setExecutorProvider(ExecutorProvider executorProvider) {
		this.executorProvider = executorProvider;
	}

	@Override
	public String getDescription() {
		return description;
	}
	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public boolean isSupportsDescription() {
		return true;
	}

	@Override
	public List<Feature> getAvailableFeatures() {
		java.util.Map<String, Feature> features = new HashMap<String, Feature>();
		getAvailableFeatures(getRoot(), features);
		return new ArrayList<Feature>(features.values());
	}
	
	private void getAvailableFeatures(Step step, java.util.Map<String, Feature> features) {
		if (step.getFeatures() != null && !step.getFeatures().trim().isEmpty()) {
			List<String> names = BaseStepGroup.getFeatures(step.getFeatures());
			for (String name : names) {
				if (!features.containsKey(name)) {
					features.put(name, new FeatureImpl(name, null));
				}
			}
		}
		if (step instanceof StepGroup) {
			for (Step child : ((StepGroup) step).getChildren()) {
				getAvailableFeatures(child, features);
			}
		}
	}

	@Override
	public List<ExceptionDescription> getExceptions() {
		List<ExceptionDescription> exceptions = new ArrayList<ExceptionDescription>();
		getExceptions(getRoot(), exceptions);
		exceptions.addAll(getStandardExceptions());
		return ArtifactUtils.unique(exceptions);
	}
	
	private void getExceptions(StepGroup group, List<ExceptionDescription> descriptions) {
		if (group.getChildren() != null) {
			for (Step child : group.getChildren()) {
				if (child instanceof Throw) {
					ExceptionDescriptionImpl exception = new ExceptionDescriptionImpl();
					exception.setId(child.getId());
					exception.setMessage(((Throw) child).getMessage());
					exception.setDescription(child.getDescription());
					exception.setCode(((Throw) child).getCode());
					exception.setContext(Arrays.asList(getId(), group.getId(), child.getId()));
					exception.setType(ExceptionType.BUSINESS);
					descriptions.add(exception);
				}
				// don't recurse, we currently don't have a way to block circular references...
//				if (child instanceof Invoke) {
//					String serviceId = ((Invoke) child).getServiceId();
//					if (serviceId != null) {
//						DefinedService service = (DefinedService) ArtifactResolverFactory.getInstance().getResolver().resolve(serviceId);
//						if (service instanceof ArtifactWithExceptions) {
//							List<ExceptionDescription> exceptions = ((ArtifactWithExceptions) service).getExceptions();
//							if (exceptions != null) {
//								descriptions.addAll(exceptions);
//							}
//						}
//					}
//				}
				if (child instanceof StepGroup) {
					getExceptions((StepGroup) child, descriptions);
				}
			}
		}
	}
	
	private List<ExceptionDescription> getStandardExceptions() {
		List<ExceptionDescription> descriptions = new ArrayList<ExceptionDescription>();
		descriptions.add(new ExceptionDescriptionImpl("VM-1", "VM-1", "Invalid set operation", "Only variable operations are allowed when setting values", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-2", "VM-2", "Can only manage closeables", "Something marked as a closeable that should be managed, is not a closeable or a list of closeables", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-3", "VM-3", "Service not found", "Can not find the service that needs to be invoked, it may have been deleted", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-4", "VM-4", "Invalid input data", "The input for the service does not pass the required validation rules"));
		descriptions.add(new ExceptionDescriptionImpl("VM-5", "VM-5", "Invalid output data", "The output for the service does not pass the required validation rules"));
		descriptions.add(new ExceptionDescriptionImpl("VM-6", "VM-6", "Step execution error", "A general error occurred when executing the given step"));
		descriptions.add(new ExceptionDescriptionImpl("VM-7", "VM-7", "Can only mask complex types", "The design time value is not a complex type", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-8", "VM-8", "Can only mask complex content", "The runtime value can not be cast to a complex content", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-9", "VM-9", "Invalid target environment", "The configured execution target could not be found", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-10", "VM-10", "Link is missing 'to'", "The link does not have a to value", ExceptionType.DESIGN));
		descriptions.add(new ExceptionDescriptionImpl("VM-11", "VM-11", "Batch size is not a number", "The configured batch size is not a number or does not resolve to a number", ExceptionType.DESIGN));
		return descriptions;
	}

	@Override
	public List<Todo> getTodos() {
		List<Todo> todos = new ArrayList<Todo>();
		if (getDescription() != null) {
			todos.addAll(ArtifactUtils.scanForTodos(getId(), getDescription()));
		}
		scanTodo(getRoot(), todos);
		return todos;
	}
	private void scanTodo(StepGroup group, List<Todo> todos) {
		// we want to scan the root sequence as well
		scanTodoStep(group, todos);
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				scanTodo((StepGroup) child, todos);
			}
			else {
				scanTodoStep(child, todos);
			}
		}
	}
	private void scanTodoStep(Step step, List<Todo> todos) {
		if (step.getComment() != null) {
			todos.addAll(ArtifactUtils.scanForTodos(getId() + ":" + step.getId(), step.getComment()));
		}
		if (step.getDescription() != null) {
			todos.addAll(ArtifactUtils.scanForTodos(getId() + ":" + step.getId(), step.getDescription()));
		}
	}

}
