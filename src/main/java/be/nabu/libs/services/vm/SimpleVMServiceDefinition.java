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

import be.nabu.libs.artifacts.FeatureImpl;
import be.nabu.libs.artifacts.api.Feature;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ExceptionDescription;
import be.nabu.libs.services.api.ModifiableServiceInterface;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.api.ServiceWithMetadata;
import be.nabu.libs.services.impl.ExceptionDescriptionImpl;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.BaseStepGroup;
import be.nabu.libs.services.vm.step.Invoke;
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

public class SimpleVMServiceDefinition implements VMService, ServiceWithMetadata {

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
		if (!mappable && fromType instanceof ComplexType && toType instanceof ComplexType && !TypeUtils.getAllChildrenIterator((ComplexType) toType).hasNext()) {
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
	public List<ExceptionDescription> getExceptions(ServiceContext serviceContext) {
		List<ExceptionDescription> exceptions = new ArrayList<ExceptionDescription>();
		getExceptions(serviceContext, getRoot(), exceptions);
		return ServiceUtils.unique(exceptions);
	}
	
	private void getExceptions(ServiceContext serviceContext, StepGroup group, List<ExceptionDescription> descriptions) {
		if (group.getChildren() != null) {
			for (Step child : group.getChildren()) {
				if (child instanceof Throw) {
					ExceptionDescriptionImpl exception = new ExceptionDescriptionImpl();
					exception.setId(child.getId());
					exception.setMessage(((Throw) child).getMessage());
					exception.setDescription(child.getDescription());
					exception.setCode(((Throw) child).getCode());
					exception.setContext(Arrays.asList(getId(), group.getId(), child.getId()));
					descriptions.add(exception);
				}
				if (child instanceof Invoke) {
					Service service = ((Invoke) child).getService(serviceContext);
					if (service instanceof ServiceWithMetadata) {
						descriptions.addAll(((ServiceWithMetadata) service).getExceptions(serviceContext));
					}
				}
				if (child instanceof StepGroup) {
					getExceptions(serviceContext, (StepGroup) child, descriptions);
				}
			}
		}
	}
	
}
