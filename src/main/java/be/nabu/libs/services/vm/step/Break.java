package be.nabu.libs.services.vm.step;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

/**
 * Allows you to break out of control structures
 * 
 * Might want to add a "continue" option. If your final target is a for loop, we can offer a "continue" boolean, meaning that it will continue with the next iteration.
 * Continue is not needed in most cases so a dedicated control structure seems useless. If you have nested loops, you can "break" out of the inner loops and continue the outer loop with a single statement.
 * Only if breakcount is exactly 1 will the continue be taken into account.
 */
@XmlType(propOrder = { "count", "continueExecution" })
public class Break extends BaseStep {

	private int count = 1;
	// you can break a for loop but set to continue with next iteration
	private Boolean continueExecution;

	@Override
	public void execute(VMContext context) throws ServiceException {
		context.setBreakCount(count);
		context.setContinueExecution(continueExecution != null && continueExecution);
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
		messages.addAll(super.validate(serviceContext));
		return messages;
	}

	@Override
	public void refresh() {
		// do nothing
	}

	public Boolean getContinueExecution() {
		return continueExecution;
	}
	public void setContinueExecution(Boolean continueExecution) {
		this.continueExecution = continueExecution;
	}
}
