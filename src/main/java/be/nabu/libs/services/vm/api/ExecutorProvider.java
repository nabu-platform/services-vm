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

package be.nabu.libs.services.vm.api;

import java.util.List;
import java.util.Map;

import be.nabu.libs.property.api.Property;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

public interface ExecutorProvider {
	public ServiceRunner getRunner(String target, Map<String, ?> properties);
	// any additional properties that apply to the target
	public List<Property<?>> getTargetProperties(String target);
	public List<String> getTargets();
	public boolean isBatch(String target);
	// some executions are always asynchronous where the response can not be retrieved
	public boolean isAsynchronous(String target);
	
	public static ComplexType getBatchOutput(Service service) {
		Structure structure = new Structure();
		structure.setName("output");
		Structure result = new Structure();
		result.add(new SimpleElementImpl<String>("name", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), result, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
		result.add(new ComplexElementImpl("output", service.getServiceInterface().getOutputDefinition(), result));
		structure.add(new ComplexElementImpl("results", result, structure, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
		return structure;
	}
}
