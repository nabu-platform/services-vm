package be.nabu.libs.services.vm.api;

import be.nabu.libs.artifacts.api.FeaturedArtifact;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.types.api.TypeInstance;

public interface VMService extends DefinedService, FeaturedArtifact {
	public Sequence getRoot();
	public Pipeline getPipeline();
	
	public default void setDescription(String description) {
		// do nothing, don't really like this design... might split it off into a separate interface in the future
	}
	public default boolean isSupportsDescription() {
		return false;
	}
	
	/**
	 * When visualizing the service, you need to know whether something can be mapped to another item
	 * This can be context-dependent, for example if the service knows it's running with structure in the background, it can allow onorthodox casting
	 * However, if you are using java beans as your backend, you must follow those rules
	 */
	public boolean isMappable(TypeInstance fromItem, TypeInstance toItem);
	
	default ExecutorProvider getExecutorProvider() {
		return null;
	}
}
