package be.nabu.libs.services.vm.step;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;

public class Finally extends BaseStepGroup implements LimitedStepGroup {

	public Finally() {
		
	}
	public Finally(Step...steps) {
		super(steps);
	}

	@Override
	public void execute(VMContext context) throws ServiceException {
		for (Step child : getChildren()) {
			if (child.isDisabled()) {
				continue;
			}
			executeIfLabel(child, context);
			if (isAborted()) {
				break;
			}
		}
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
		// do nothing
	}
}
