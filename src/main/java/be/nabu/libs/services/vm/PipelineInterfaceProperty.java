package be.nabu.libs.services.vm;

import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.types.properties.BaseProperty;
import be.nabu.libs.validator.api.Validator;

public class PipelineInterfaceProperty extends BaseProperty<DefinedServiceInterface> {

	private static PipelineInterfaceProperty instance = new PipelineInterfaceProperty();
	
	public static PipelineInterfaceProperty getInstance() {
		return instance;
	}
	
	@Override
	public String getName() {
		return "interface";
	}

	@Override
	public Validator<DefinedServiceInterface> getValidator() {
		return null;
	}

	@Override
	public Class<DefinedServiceInterface> getValueClass() {
		return DefinedServiceInterface.class;
	}

}
