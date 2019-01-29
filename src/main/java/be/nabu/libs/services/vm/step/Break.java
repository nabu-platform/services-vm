package be.nabu.libs.services.vm.step;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

/**
 * Allows you to break out of control structures
 */
@XmlType(propOrder = { "count" })
public class Break extends BaseStep {

	private int count = 1;

	@Override
	public void execute(VMContext context) throws ServiceException {
		context.setBreakCount(count);
	}

	public int getCount() {
		return count;
	}

	public Break setCount(int count) {
		this.count = count;
		return this;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		if (count <= 0) {
			messages.add(addContext(new ValidationMessage(Severity.ERROR, "The break count '" + count + "' is invalid")));
		}
		return messages;
	}

	@Override
	public void refresh() {
		// do nothing
	}
}
