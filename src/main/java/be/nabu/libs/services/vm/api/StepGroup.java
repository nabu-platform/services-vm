package be.nabu.libs.services.vm.api;

import java.util.List;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.types.api.ComplexType;

public interface StepGroup extends Step {
	public List<Step> getChildren();
	public ComplexType getPipeline(ServiceContext serviceContext);
}
