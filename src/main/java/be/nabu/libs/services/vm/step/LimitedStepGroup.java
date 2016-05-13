package be.nabu.libs.services.vm.step;

import java.util.Set;

import be.nabu.libs.services.vm.api.Step;

public interface LimitedStepGroup {
	public Set<Class<? extends Step>> getAllowedSteps();
}
