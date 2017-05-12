package be.nabu.libs.services.vm.step;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.validator.api.Validation;

public class Switch extends BaseStepGroup implements LimitedStepGroup {

	/**
	 * Contains the query (if any) that must be matched by the case
	 * If no query is given, the check will be based on the boolean "true"
	 */
	private String query;

	public Switch() {
		// default creation
	}
	
	public Switch(Step...steps) {
		super(steps);
	}
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		Object toMatch = getComparison(context);
		for (Step child : getChildren()) {
			if (child.isDisabled()) {
				continue;
			}
			// default match
			if (child.getLabel() == null) {
				execute(child, context);
				break;
			}
			else {
				Object result = getVariable(context.getServiceInstance().getPipeline(), child.getLabel());
				if (toMatch != null && result != null) {
					Object converted = ConverterFactory.getInstance().getConverter().convert(result, toMatch.getClass());
					if (converted == null) {
						throw new IllegalArgumentException("Can not convert the result '" + result + "' of the label to the type of the switch variable: " + toMatch.getClass());
					}
					result = converted;
				}
				if ((result == null && toMatch == null) || (result != null && toMatch != null && result.equals(toMatch))) {
					execute(child, context);
					break;
				}
			}
			if (isAborted()) {
				break;
			}
		}
	}
	
	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		if (query != null) {
			messages.addAll(validateQuery(serviceContext, query));
		}
		return messages;
	}

	protected Object getComparison(VMContext context) throws ServiceException {
		if (query == null)
			return true;
		else
			return getVariable(context.getServiceInstance().getPipeline(), query);
	}
	
	@XmlAttribute
	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
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
