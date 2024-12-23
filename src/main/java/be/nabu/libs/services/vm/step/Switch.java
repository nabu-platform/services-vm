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

package be.nabu.libs.services.vm.step;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.validator.api.Validation;

@XmlType(propOrder = { "query" })
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
			// if features have disabled the step, don't continue
			if (!isOkForFeatures(child, context)) {
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
						// if we can't directly convert it to boolean, we assume null to be false and not null to be true
						if (Boolean.class.isAssignableFrom(toMatch.getClass())) {
							converted = result != null;
						}
						else {
							throw new IllegalArgumentException("Can not convert the result '" + result + "' of the label to the type of the switch variable: " + toMatch.getClass());
						}
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
